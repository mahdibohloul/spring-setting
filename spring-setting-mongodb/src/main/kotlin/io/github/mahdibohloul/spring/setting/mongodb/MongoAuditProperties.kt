package io.github.mahdibohloul.spring.setting.mongodb

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the MongoDB audit log.
 *
 * ```properties
 * spring.setting.audit.mongodb.collection-name=setting_audit_logs
 * spring.setting.audit.mongodb.max-entries-per-type=50
 * ```
 */
@ConfigurationProperties("spring.setting.audit.mongodb")
data class MongoAuditProperties(
  /** MongoDB collection used to store audit entries. Defaults to `setting_audit_logs`. */
  val collectionName: String = "setting_audit_logs",

  /**
   * Maximum number of audit entries retained per setting type.
   * After each write, the oldest entries beyond this limit are deleted.
   * Set to `0` or negative to keep entries indefinitely (not recommended for production).
   * Defaults to `50`.
   */
  val maxEntriesPerType: Int = 50,
)
