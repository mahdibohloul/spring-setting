package io.github.mahdibohloul.spring.setting.admin.services

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.SettingHelper
import io.github.mahdibohloul.spring.setting.admin.InvalidHistoryCursorException
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.UnknownSettingTypeException
import io.github.mahdibohloul.spring.setting.admin.audit.AuditCursor
import io.github.mahdibohloul.spring.setting.admin.audit.AuditEntry
import io.github.mahdibohloul.spring.setting.admin.audit.NoopSettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.authorization.AllowAllSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.transaction.SettingTransactionRetryPolicy
import io.github.mahdibohloul.spring.setting.reader.SettingReaderImpl
import io.github.mahdibohloul.spring.setting.repositories.SimpleInMemorySettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriterImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

class SettingAdminServiceImplTest {
  data class SamplePolicy(
    val minOrders: Int = 5,
    val maxAllowedDelay: Duration = Duration.ofMinutes(0),
  )

  data class SampleSetting(
    val delay: SamplePolicy = SamplePolicy(),
    val label: String = "default",
  ) : Setting

  private class Descriptor<T : Setting>(
    override val settingClass: KClass<T>,
    private val factory: () -> T,
  ) : SettingTypeDescriptor<T> {
    override fun default(): T = factory()
  }

  private fun newService(
    authorizer: SettingAdminAuthorizer = AllowAllSettingAdminAuthorizer(),
    auditLog: SettingAuditLog = NoopSettingAuditLog(),
    txOperator: TransactionalOperator? = null,
    transactionRetryPolicy: SettingTransactionRetryPolicy? = null,
  ): Pair<SettingAdminServiceImpl, SimpleInMemorySettingRepository> {
    val repo = SimpleInMemorySettingRepository()
    val registry = SettingTypeRegistry(listOf(Descriptor(SampleSetting::class) { SampleSetting() }))
    val mapper = JsonMapper.builder()
      .addModule(KotlinModule.Builder().build())
      .build()
    val settingReader = SettingReaderImpl(mapper)
    val settingWriter = SettingWriterImpl(mapper)

    return SettingAdminServiceImpl(
      registry = registry,
      settingRepository = repo,
      objectMapper = mapper,
      authorizer = authorizer,
      settingWriter = settingWriter,
      settingReader = settingReader,
      auditLog = auditLog,
      txOperator = txOperator,
      transactionRetryPolicy = transactionRetryPolicy,
    ) to repo
  }

  @Test
  fun `listTypeNames returns registered type names`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.listTypeNames())
      .expectNext(listOf("SampleSetting"))
      .verifyComplete()
  }

  @Test
  fun `getAsJson returns default when nothing is persisted`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.getAsJson("SampleSetting"))
      .assertNext { json ->
        check(json.contains("\"minOrders\":5"))
        check(json.contains("\"maxAllowedDelay\":\"PT0S\""))
        check(json.contains("\"label\":\"default\""))
      }
      .verifyComplete()
  }

  @Test
  fun `patch merges nested fields and persists the result`() {
    // given
    val (service, repo) = newService()
    val mergePatch = """{"delay":{"maxAllowedDelay":"PT30S"}}"""

    // when
    val patched = service.patch("SampleSetting", mergePatch).block()!!

    // verify
    check(patched.contains("\"maxAllowedDelay\":\"PT30S\""))
    check(patched.contains("\"minOrders\":5")) { "siblings must be preserved" }
    check(patched.contains("\"label\":\"default\""))

    val persistedKey = SettingHelper.getSettingName(SampleSetting::class)
    val persisted = repo.findByName(persistedKey, SampleSetting::class).block()!!
    check(persisted.delay.maxAllowedDelay == Duration.ofSeconds(30))
    check(persisted.delay.minOrders == 5)
  }

  @Test
  fun `patch removes a field when value is null`() {
    // given
    val (service, _) = newService()
    service.replace(
      "SampleSetting",
      """{"delay":{"minOrders":7,"maxAllowedDelay":"PT0S"},"label":"original"}""",
    ).block()

    // when
    val patched = service.patch("SampleSetting", """{"label":null}""").block()!!

    // verify — Jackson reads missing string field as default value, so re-deserialization gives default label
    check(patched.contains("\"label\":\"default\"")) { "removed field falls back to default on re-deserialize" }
  }

  @Test
  fun `replace deserializes the full json and persists it`() {
    // given
    val (service, repo) = newService()

    // when
    val json = """{"delay":{"minOrders":10,"maxAllowedDelay":"PT5M"},"label":"replaced"}"""
    val result = service.replace("SampleSetting", json).block()!!

    // verify
    check(result.contains("\"label\":\"replaced\""))
    val persisted = repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class).block()!!
    check(persisted.delay.minOrders == 10)
    check(persisted.delay.maxAllowedDelay == Duration.ofMinutes(5))
    check(persisted.label == "replaced")
  }

  @Test
  fun `delete removes the persisted value so reads fall back to default`() {
    // given
    val (service, repo) = newService()
    service.replace("SampleSetting", """{"label":"to-delete"}""").block()

    // when
    service.delete("SampleSetting").block()

    // verify — repository no longer has the document
    StepVerifier.create(repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class))
      .expectError(NoSuchElementException::class.java)
      .verify()

    // verify — re-reading through the service yields the descriptor default
    val afterDelete = service.getAsJson("SampleSetting").block()!!
    check(afterDelete.contains("\"label\":\"default\""))
  }

  @Test
  fun `delete completes silently when no value is persisted`() {
    // given — no replace/patch beforehand
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.delete("SampleSetting"))
      .verifyComplete()
  }

  @Test
  fun `delete on unknown type yields UnknownSettingTypeException`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.delete("DoesNotExist"))
      .expectError(UnknownSettingTypeException::class.java)
      .verify()
  }

  @Test
  fun `denying authorizer short-circuits patch`() {
    // given
    val denying = object : SettingAdminAuthorizer {
      override fun authorize(
        operation: SettingAdminAuthorizer.Operation,
        typeName: String?,
      ): Mono<Void> = Mono.error(SecurityException("denied"))
    }
    val (service, repo) = newService(denying)

    // when / verify
    StepVerifier.create(service.patch("SampleSetting", """{"label":"hack"}"""))
      .expectError(SecurityException::class.java)
      .verify()

    StepVerifier.create(repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class))
      .expectError(NoSuchElementException::class.java)
      .verify()
  }

  @Test
  fun `unknown type yields UnknownSettingTypeException`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.getAsJson("DoesNotExist"))
      .expectError(UnknownSettingTypeException::class.java)
      .verify()
  }

  // ── History ───────────────────────────────────────────────────────────────────

  private fun auditEntry(id: String, changedAt: Instant) = AuditEntry(
    id = id,
    typeName = "SampleSetting",
    operation = SettingAdminAuthorizer.Operation.PATCH,
    previousValue = "{}",
    newValue = "{}",
    changedBy = "tester",
    changedAt = changedAt,
  )

  @Test
  fun `getHistory reads one entry more and returns the cursor of the last entry when an older page exists`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val (service, _) = newService(auditLog = auditLog)
    val newest = auditEntry(id = "6720aa0000000000000000b2", changedAt = Instant.ofEpochMilli(2_000))
    val last = auditEntry(id = "6720aa0000000000000000b1", changedAt = Instant.ofEpochMilli(1_000))
    val lookahead = auditEntry(id = "6720aa0000000000000000b0", changedAt = Instant.ofEpochMilli(1_000))

    // when
    whenever(auditLog.findByTypeName("SampleSetting", 3, null)).thenReturn(Mono.just(listOf(newest, last, lookahead)))

    // verify
    StepVerifier.create(service.getHistory("SampleSetting", 2))
      .assertNext { page ->
        check(page.entries == listOf(newest, last))
        check(page.nextCursor == AuditCursor(changedAt = last.changedAt, id = "6720aa0000000000000000b1").encode())
      }
      .verifyComplete()
  }

  @Test
  fun `getHistory returns no cursor on the last page`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val (service, _) = newService(auditLog = auditLog)
    val only = auditEntry(id = "6720aa0000000000000000b2", changedAt = Instant.ofEpochMilli(2_000))
    val before = AuditCursor(changedAt = Instant.ofEpochMilli(3_000), id = "6720aa0000000000000000b3")

    // when
    whenever(auditLog.findByTypeName("SampleSetting", 3, before)).thenReturn(Mono.just(listOf(only)))

    // verify
    StepVerifier.create(service.getHistory("SampleSetting", 2, before.encode()))
      .assertNext { page ->
        check(page.entries == listOf(only))
        check(page.nextCursor == null)
      }
      .verifyComplete()
  }

  @Test
  fun `getHistory clamps the page size to two hundred and reads one entry more`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val (service, _) = newService(auditLog = auditLog)

    // when
    whenever(auditLog.findByTypeName(any(), any(), eq(null))).thenReturn(Mono.just(emptyList()))

    // verify
    StepVerifier.create(service.getHistory("SampleSetting", 5_000))
      .assertNext { page -> check(page.entries.isEmpty() && page.nextCursor == null) }
      .verifyComplete()
    verify(auditLog).findByTypeName("SampleSetting", 201, null)
  }

  @Test
  fun `getHistory rejects a cursor that the API did not return`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val (service, _) = newService(auditLog = auditLog)

    // when / verify
    StepVerifier.create(service.getHistory("SampleSetting", 20, "not-a-cursor"))
      .expectError(InvalidHistoryCursorException::class.java)
      .verify()
    verify(auditLog, never()).findByTypeName(any(), any(), any())
  }

  @Test
  fun `patch runs a new transaction when the retry policy accepts the error`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val txOperator = mock<TransactionalOperator>()
    val (service, repo) = newService(
      auditLog = auditLog,
      txOperator = txOperator,
      transactionRetryPolicy = SettingTransactionRetryPolicy { error -> error is TransientError },
    )

    // when
    whenever(txOperator.transactional(any<Mono<Any>>())).thenAnswer { invocation -> invocation.getArgument(0) }
    whenever(auditLog.record(any())).thenReturn(Mono.error(TransientError()), Mono.empty())

    // verify
    StepVerifier.create(service.patch("SampleSetting", """{"label":"patched"}"""))
      .assertNext { json -> check(json.contains("\"label\":\"patched\"")) }
      .verifyComplete()
    verify(auditLog, times(2)).record(any())
    val persisted = repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class).block()!!
    check(persisted.label == "patched")
  }

  @Test
  fun `patch does not run the transaction again when the retry policy rejects the error`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val txOperator = mock<TransactionalOperator>()
    val (service, _) = newService(
      auditLog = auditLog,
      txOperator = txOperator,
      transactionRetryPolicy = SettingTransactionRetryPolicy { error -> error is TransientError },
    )

    // when
    whenever(txOperator.transactional(any<Mono<Any>>())).thenAnswer { invocation -> invocation.getArgument(0) }
    whenever(auditLog.record(any())).thenReturn(Mono.error(IllegalStateException("audit down")))

    // verify
    StepVerifier.create(service.patch("SampleSetting", """{"label":"patched"}"""))
      .expectErrorMessage("audit down")
      .verify()
    verify(auditLog, times(1)).record(any())
  }

  @Test
  fun `patch fails with the last error when the transaction retries run out`() {
    // given
    val auditLog = mock<SettingAuditLog>()
    val txOperator = mock<TransactionalOperator>()
    val (service, _) = newService(
      auditLog = auditLog,
      txOperator = txOperator,
      transactionRetryPolicy = SettingTransactionRetryPolicy { error -> error is TransientError },
    )

    // when
    whenever(txOperator.transactional(any<Mono<Any>>())).thenAnswer { invocation -> invocation.getArgument(0) }
    whenever(auditLog.record(any())).thenReturn(Mono.error(TransientError()))

    // verify
    StepVerifier.create(service.patch("SampleSetting", """{"label":"patched"}"""))
      .expectError(TransientError::class.java)
      .verify()
    verify(auditLog, times(4)).record(any())
  }

  private class TransientError : RuntimeException("transient")
}
