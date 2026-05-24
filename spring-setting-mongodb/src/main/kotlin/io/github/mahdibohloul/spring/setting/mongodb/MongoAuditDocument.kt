package io.github.mahdibohloul.spring.setting.mongodb

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB persistence document for a single audit entry produced by the admin service.
 *
 * The collection name is driven by [MongoAuditProperties] and defaults to `setting_audit_log`.
 * `changedAt` is populated by Spring Data's `@CreatedDate` auditing on first save — this field
 * is intentionally immutable (no `@LastModifiedDate`) because audit records must never change.
 */
@Document(
  collection =
  "#{@environment.getProperty('spring.setting.audit.mongodb.collection-name', 'setting_audit_logs')}",
)
data class MongoAuditDocument(
  @Id val id: String? = null,
  @Indexed val typeName: String,
  val operation: String,
  val previousValue: String?,
  val newValue: String?,
  val changedBy: String,
  @CreatedDate val changedAt: Instant? = null,
)
