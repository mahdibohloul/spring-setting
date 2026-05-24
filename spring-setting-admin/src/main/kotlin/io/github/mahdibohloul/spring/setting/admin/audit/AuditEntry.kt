package io.github.mahdibohloul.spring.setting.admin.audit

import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import java.time.Instant

/**
 * Immutable record of a single mutating admin operation.
 *
 * - [previousValue] is `null` when the setting had no persisted value before the operation
 *   (e.g. first-ever DELETE or REPLACE on a type that only had a compiled default).
 * - [newValue] is `null` for DELETE operations (there is no resulting value).
 * - [id] is assigned by the backing [SettingAuditLog] implementation; it is `null` until persisted.
 */
data class AuditEntry(
  val id: String? = null,
  val typeName: String,
  val operation: SettingAdminAuthorizer.Operation,
  val previousValue: String?,
  val newValue: String?,
  val changedBy: String,
  val changedAt: Instant,
)
