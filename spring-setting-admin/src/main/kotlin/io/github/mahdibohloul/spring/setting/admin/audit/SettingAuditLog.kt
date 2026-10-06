package io.github.mahdibohloul.spring.setting.admin.audit

import reactor.core.publisher.Mono

/**
 * SPI for persisting and querying audit entries produced by the admin service.
 *
 * The default bean registered by `spring-setting-admin`'s auto-configuration is a no-op
 * ([NoopSettingAuditLog]). Production persistence is provided by `spring-setting-mongodb`'s
 * `MongoSettingAuditLog` when `spring.setting.audit.enabled=true`.
 */
interface SettingAuditLog {
  /**
   * Persists [entry] and returns `Mono.empty()` on success. Errors should propagate as
   * `Mono.error(...)` so the calling chain can handle or log them.
   */
  fun record(entry: AuditEntry): Mono<Void>

  /**
   * Returns the [limit] most-recent audit entries for [typeName], ordered newest-first.
   * The service layer clamps the page size to `[1, 200]` and asks for one entry more, so [limit] is at most 201.
   */
  fun findByTypeName(typeName: String, limit: Int): Mono<List<AuditEntry>>

  /**
   * Returns at most [limit] audit entries for [typeName] that come after [before] in the history order
   * (`changedAt` DESC, then `id` DESC): `changedAt < before.changedAt`, or the same `changedAt` and
   * `id < before.id`. A `null` [before] means the newest entries, the same as [findByTypeName] without a cursor.
   * [limit] is at most 201, the same as for [findByTypeName].
   *
   * The default body supports only a `null` [before], so a backend written before cursors existed still
   * compiles. Override it to support paging past the first page.
   */
  fun findByTypeName(typeName: String, limit: Int, before: AuditCursor?): Mono<List<AuditEntry>> = if (before == null) {
    findByTypeName(typeName, limit)
  } else {
    Mono.error(UnsupportedOperationException("${this::class.simpleName} does not support history cursors"))
  }

  /**
   * Returns the single entry identified by [entryId], or `Mono.error(NoSuchElementException)`
   * if no such entry exists.
   */
  fun findEntryById(entryId: String): Mono<AuditEntry>
}
