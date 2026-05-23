package io.github.mahdibohloul.spring.setting.admin.audit

import reactor.core.publisher.Mono

/**
 * No-op implementation of [SettingAuditLog] used when `spring.setting.audit.enabled` is not set.
 *
 * - [record] discards the entry silently.
 * - [findByTypeName] always returns an empty list.
 * - [findEntryById] always returns `Mono.error(NoSuchElementException)`.
 */
class NoopSettingAuditLog : SettingAuditLog {
  override fun record(entry: AuditEntry): Mono<Void> = Mono.empty()

  override fun findByTypeName(typeName: String, limit: Int): Mono<List<AuditEntry>> = Mono.just(emptyList())

  @Suppress("detekt.MaxLineLength")
  override fun findEntryById(entryId: String): Mono<AuditEntry> = Mono.error(NoSuchElementException("Audit log is disabled; no entry with id '$entryId'"))
}
