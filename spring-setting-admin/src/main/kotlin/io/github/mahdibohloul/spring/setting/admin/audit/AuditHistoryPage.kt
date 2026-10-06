package io.github.mahdibohloul.spring.setting.admin.audit

/**
 * One page of the audit history of a setting type, newest first.
 *
 * [nextCursor] is the opaque cursor to send as `before` for the next (older) page. It is `null` on the last page.
 */
data class AuditHistoryPage(
  val entries: List<AuditEntry>,
  val nextCursor: String?,
)
