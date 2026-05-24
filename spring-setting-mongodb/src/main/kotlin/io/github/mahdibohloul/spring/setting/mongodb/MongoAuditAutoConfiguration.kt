package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.autoconfigure.SettingAdminAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.ReactiveMongoTransactionManager
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * Registers [MongoSettingAuditLog] when:
 * 1. `spring.setting.audit.enabled=true` — explicit opt-in.
 * 2. `ReactiveMongoTemplate` is on the classpath (checked via [ConditionalOnClass]).
 * 3. `SettingAuditLog` is on the classpath — i.e. `spring-setting-admin` is a dependency.
 *
 * **Why no `@ConditionalOnBean(ReactiveMongoTemplate::class)`** — class-level `@ConditionalOnBean`
 * is evaluated before Spring Data MongoDB's own auto-configuration has necessarily created the
 * template bean, which causes a false-negative even when a `ReactiveMongoTemplate` is present.
 * Following the same pattern as [MongoSettingAutoConfiguration], we rely on `@ConditionalOnClass`
 * to verify the type is on the classpath, and let Spring inject the bean at creation time.
 *
 * If `spring-setting-admin` is absent, the [ConditionalOnClass] guard silently skips this
 * configuration, keeping `spring-setting-mongodb` usable as a standalone module.
 *
 * **Transaction support** — when a [ReactiveMongoTransactionManager] bean is present
 * (requires a MongoDB replica set), a [TransactionalOperator] is registered so the
 * admin service can wrap the setting-save and audit-write in a single atomic transaction.
 * Without a transaction manager the audit write is best-effort.
 */
@AutoConfiguration(after = [MongoSettingAutoConfiguration::class, SettingAdminAutoConfiguration::class])
@ConditionalOnClass(ReactiveMongoTemplate::class, SettingAuditLog::class)
@ConditionalOnProperty(prefix = "spring.setting.audit", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(MongoAuditProperties::class)
class MongoAuditAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(SettingAuditLog::class)
  fun mongoSettingAuditLog(
    mongoTemplate: ReactiveMongoTemplate,
    props: MongoAuditProperties,
  ): SettingAuditLog = MongoSettingAuditLog(mongoTemplate, props.collectionName, props.maxEntriesPerType)

  /**
   * Creates a [TransactionalOperator] backed by the MongoDB reactive transaction manager.
   *
   * Only registered when a [ReactiveMongoTransactionManager] bean is present — meaning the
   * user has a replica set and has explicitly configured transaction management. If none is
   * present (standalone MongoDB, or transactions not configured), this bean is skipped and
   * the audit write falls back to best-effort mode.
   *
   * A [ConditionalOnMissingBean] guard ensures a user-supplied [TransactionalOperator] always
   * takes precedence.
   */
  @Bean
  @ConditionalOnMissingBean(TransactionalOperator::class)
  @ConditionalOnBean(ReactiveMongoTransactionManager::class)
  fun settingAuditTransactionalOperator(
    tm: ReactiveMongoTransactionManager,
  ): TransactionalOperator = TransactionalOperator.create(tm)
}
