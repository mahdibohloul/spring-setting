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
   * [limit] is clamped to `[1, 200]` by the service layer before this is called.
   */
  fun findByTypeName(typeName: String, limit: Int): Mono<List<AuditEntry>>

  /**
   * Returns the single entry identified by [entryId], or `Mono.error(NoSuchElementException)`
   * if no such entry exists.
   */
  fun findEntryById(entryId: String): Mono<AuditEntry>
}
