package io.github.mahdibohloul.spring.setting.admin.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.SettingHelper
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.audit.AuditEntry
import io.github.mahdibohloul.spring.setting.admin.audit.NoopSettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditPrincipalProvider
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer.Operation
import io.github.mahdibohloul.spring.setting.admin.patch.JsonMergePatch
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.slf4j.LoggerFactory
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Default implementation of [SettingAdminService].
 *
 * **Transactionality** — when a [TransactionalOperator] is provided (e.g. via
 * `spring-setting-mongodb` registering a `ReactiveMongoTransactionManager`), the
 * setting-save and audit-write are wrapped in a single atomic transaction:
 * both commit together or both roll back. Without a [txOperator] the audit write
 * is best-effort: a failure is logged and the setting save still succeeds.
 *
 * **Audit ordering** — audit entries are captured *after* the backing repository
 * confirms the write, so a rollback (or a pre-write error) never produces a phantom
 * audit entry for an operation that did not persist.
 */
@Suppress("detekt.LongParameterList", "detekt.TooManyFunctions")
class SettingAdminServiceImpl(
  private val registry: SettingTypeRegistry,
  private val settingRepository: SettingRepository,
  private val objectMapper: ObjectMapper,
  private val authorizer: SettingAdminAuthorizer,
  private val settingWriter: SettingWriter,
  private val settingReader: SettingReader,
  private val auditLog: SettingAuditLog = NoopSettingAuditLog(),
  private val principalProvider: SettingAuditPrincipalProvider =
    SettingAuditPrincipalProvider { Mono.just("anonymous") },
  /**
   * When non-null, the setting-save and audit-write are wrapped in this operator so they
   * commit or roll back as a unit. Supply a `ReactiveMongoTransactionManager`-backed
   * operator (requires a MongoDB replica set).
   * When null, audit writes are best-effort: failures are logged but do not surface to
   * the caller.
   */
  private val txOperator: TransactionalOperator? = null,
) : SettingAdminService {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun listTypeNames(): Mono<List<String>> = authorizer.authorize(Operation.LIST, null)
    .then(Mono.fromCallable { registry.listTypeNames() })

  override fun getAsJson(typeName: String): Mono<String> = authorizer.authorize(Operation.READ, typeName)
    .then(Mono.defer { loadOrDefault(registry.get(typeName)).map(::writeJson) })

  override fun patch(
    typeName: String,
    jsonMergePatch: String,
  ): Mono<String> = authorizer.authorize(Operation.PATCH, typeName).then(
    Mono.defer {
      @Suppress("UNCHECKED_CAST")
      val descriptor = registry.get(typeName) as SettingTypeDescriptor<Setting>
      val patchNode = objectMapper.readTree(jsonMergePatch)

      loadOrDefault(descriptor).flatMap { currentSetting ->
        val previousJson = writeJson(currentSetting)
        val merged = objectMapper.treeToValue(
          JsonMergePatch.merge(objectMapper.valueToTree(currentSetting), patchNode),
          descriptor.settingClass.java,
        )
        settingRepository.save(repositoryName(descriptor), merged)
          .thenReturn(writeJson(merged))
          .doOnSuccess { logger.debug("Patched setting {}", typeName) }
          .flatMap { newJson ->
            recordAudit(typeName, Operation.PATCH, previousJson, newJson).thenReturn(newJson)
          }
      }.inTransaction()
    },
  )

  override fun replace(
    typeName: String,
    jsonValue: String,
  ): Mono<String> = authorizer.authorize(Operation.REPLACE, typeName).then(
    Mono.defer {
      @Suppress("UNCHECKED_CAST")
      val descriptor = registry.get(typeName) as SettingTypeDescriptor<Setting>

      loadOrDefault(descriptor).flatMap { currentSetting ->
        val previousJson = writeJson(currentSetting)
        val replacedSetting =
          settingReader.readSetting(input = jsonValue, settingClass = descriptor.settingClass.java)
        settingRepository.save(repositoryName(descriptor), replacedSetting)
          .thenReturn(writeJson(replacedSetting))
          .doOnSuccess { logger.debug("Replaced setting {}", typeName) }
          .flatMap { newJson ->
            recordAudit(typeName, Operation.REPLACE, previousJson, newJson).thenReturn(newJson)
          }
      }.inTransaction()
    },
  )

  override fun delete(typeName: String): Mono<Void> = authorizer.authorize(Operation.DELETE, typeName).then(
    Mono.defer {
      val descriptor = registry.get(typeName)

      loadOrDefault(descriptor).flatMap { currentSetting ->
        val previousJson = writeJson(currentSetting)
        @Suppress("UNCHECKED_CAST")
        settingRepository.deleteByName(
          repositoryName(descriptor),
          descriptor.settingClass as KClass<Setting>,
        ).doOnSuccess { logger.debug("Deleted setting {}", typeName) }
          .then(recordAudit(typeName, Operation.DELETE, previousJson, null))
      }.inTransaction()
    },
  )

  override fun getHistory(typeName: String, limit: Int): Mono<List<AuditEntry>> {
    val clamped = limit.coerceIn(MIN_HISTORY_LIMIT, MAX_HISTORY_LIMIT)
    return authorizer.authorize(Operation.HISTORY, typeName)
      .then(auditLog.findByTypeName(typeName, clamped))
  }

  @Suppress("detekt.ReturnCount")
  override fun revert(typeName: String, entryId: String): Mono<String> {
    return authorizer.authorize(Operation.REVERT, typeName).then(
      auditLog.findEntryById(entryId).flatMap { entry ->
        val target = entry.previousValue
          ?: return@flatMap Mono.error(
            IllegalStateException("Entry $entryId has no previousValue to revert to"),
          )
        replace(typeName, target)
      },
    )
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  /**
   * Records an audit entry after a mutating operation.
   *
   * - **With [txOperator]**: errors propagate so the enclosing transaction rolls back both the
   *   setting change and the (attempted) audit write atomically.
   * - **Without [txOperator]**: errors are caught, logged as ERROR, and the caller receives a
   *   successful response (the setting was saved — only the audit entry is missing).
   */
  private fun recordAudit(
    typeName: String,
    operation: Operation,
    previousValue: String?,
    newValue: String?,
  ): Mono<Void> {
    val auditMono = principalProvider.currentPrincipal().flatMap { changedBy ->
      auditLog.record(
        AuditEntry(
          typeName = typeName,
          operation = operation,
          previousValue = previousValue,
          newValue = newValue,
          changedBy = changedBy,
          changedAt = Instant.now(),
        ),
      )
    }
    // When transactional, let errors propagate so the transaction rolls back everything.
    // When best-effort, swallow errors so the setting save still reaches the caller.
    return if (txOperator != null) {
      auditMono
    } else {
      auditMono.onErrorResume { ex ->
        logger.error(
          "Audit record failed for {} on '{}' — setting was saved but audit entry was NOT persisted",
          operation,
          typeName,
          ex,
        )
        Mono.empty()
      }
    }
  }

  /**
   * Wraps [this] in the [txOperator] transaction when one is configured.
   * A no-op pass-through when [txOperator] is null.
   */
  private fun <T> Mono<T>.inTransaction(): Mono<T> = txOperator?.transactional(this) ?: this

  private companion object {
    const val MIN_HISTORY_LIMIT = 1
    const val MAX_HISTORY_LIMIT = 200

    /**
     * Persistence key — always derived via [SettingHelper.getSettingName] so the admin API
     * stays interoperable with `SettingService.loadSetting(...)` and `@InjectSetting` callers
     * even when a descriptor overrides its wire-visible `typeName`.
     */
    @Suppress("detekt.MaxLineLength")
    fun repositoryName(descriptor: SettingTypeDescriptor<out Setting>): String = SettingHelper.getSettingName(descriptor.settingClass)
  }

  private fun loadOrDefault(
    descriptor: SettingTypeDescriptor<out Setting>,
  ): Mono<Setting> = settingRepository.findByName(repositoryName(descriptor), descriptor.settingClass)
    .cast(Setting::class.java)
    .onErrorResume(NoSuchElementException::class.java) { Mono.fromCallable(descriptor::default) }

  private fun writeJson(setting: Setting): String = settingWriter.writeSetting(setting)
}
