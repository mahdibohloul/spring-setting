package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.admin.audit.AuditEntry
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * MongoDB-backed implementation of [SettingAuditLog].
 *
 * All queries are performed against the collection name supplied at construction time
 * (driven by [MongoAuditProperties]). The `@Indexed` on [MongoAuditDocument.typeName] ensures
 * history lookups are efficient even in collections with many entries.
 *
 * **Retention** — [record] appends the new entry and then evicts the oldest entries that exceed
 * [maxEntriesPerType]. Eviction is best-effort: a failure is logged and does not surface to the
 * caller or trigger a transaction rollback.
 */
class MongoSettingAuditLog(
  private val mongoTemplate: ReactiveMongoTemplate,
  private val collectionName: String,
  private val maxEntriesPerType: Int = 50,
) : SettingAuditLog {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun record(entry: AuditEntry): Mono<Void> = mongoTemplate.save(entry.toDocument(), collectionName)
    .then(evictOldEntries(entry.typeName))

  override fun findByTypeName(typeName: String, limit: Int): Mono<List<AuditEntry>> {
    val query = Query(Criteria.where("typeName").`is`(typeName))
      .with(Sort.by(Sort.Direction.DESC, "changedAt"))
      .limit(limit)

    return mongoTemplate.find(query, MongoAuditDocument::class.java, collectionName)
      .map { it.toEntry() }
      .collectList()
  }

  override fun findEntryById(entryId: String): Mono<AuditEntry> {
    val query = Query(Criteria.where("_id").`is`(entryId))

    return mongoTemplate.findOne(query, MongoAuditDocument::class.java, collectionName)
      .map { it.toEntry() }
      .switchIfEmpty(Mono.error(NoSuchElementException("No audit entry found with id '$entryId'")))
  }

  // ── Eviction ─────────────────────────────────────────────────────────────────

  /**
   * Deletes entries for [typeName] that fall beyond the [maxEntriesPerType] limit,
   * keeping only the most-recent ones (ordered by `changedAt` DESC).
   *
   * Non-positive [maxEntriesPerType] disables eviction entirely. Failures are logged
   * and swallowed so they never propagate to the caller.
   */
  private fun evictOldEntries(typeName: String): Mono<Void> {
    if (maxEntriesPerType <= 0) return Mono.empty()

    // Find all entries beyond the retention window (oldest first, skip the keepers)
    val findExcess = Query(Criteria.where("typeName").`is`(typeName))
      .with(Sort.by(Sort.Direction.ASC, "changedAt"))
      .skip(maxEntriesPerType.toLong())

    return mongoTemplate.find(findExcess, MongoAuditDocument::class.java, collectionName)
      .mapNotNull { it.id }
      .collectList()
      .flatMap { idsToDelete ->
        if (idsToDelete.isEmpty()) return@flatMap Mono.empty<Void>()
        mongoTemplate.remove(
          Query(Criteria.where("_id").`in`(idsToDelete)),
          MongoAuditDocument::class.java,
          collectionName,
        ).then()
      }
      .onErrorResume { ex ->
        logger.warn(
          "Failed to evict old audit entries for '{}' — excess entries may remain in '{}'",
          typeName,
          collectionName,
          ex,
        )
        Mono.empty()
      }
  }

  // ── Mapping ───────────────────────────────────────────────────────────────────

  private fun AuditEntry.toDocument() = MongoAuditDocument(
    id = id,
    typeName = typeName,
    operation = operation.name,
    previousValue = previousValue,
    newValue = newValue,
    changedBy = changedBy,
    changedAt = changedAt,
  )

  private fun MongoAuditDocument.toEntry() = AuditEntry(
    id = id,
    typeName = typeName,
    operation = SettingAdminAuthorizer.Operation.valueOf(operation),
    previousValue = previousValue,
    newValue = newValue,
    changedBy = changedBy,
    changedAt = changedAt ?: Instant.EPOCH,
  )
}
