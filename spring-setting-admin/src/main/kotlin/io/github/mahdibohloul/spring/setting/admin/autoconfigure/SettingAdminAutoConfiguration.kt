package io.github.mahdibohloul.spring.setting.admin.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.audit.NoopSettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditPrincipalProvider
import io.github.mahdibohloul.spring.setting.admin.authorization.AllowAllSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminServiceImpl
import io.github.mahdibohloul.spring.setting.autoconfigure.SettingAutoConfiguration
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.kotlin.core.publisher.toMono

@AutoConfiguration(after = [SettingAutoConfiguration::class])
@ConditionalOnBean(SettingRepository::class)
class SettingAdminAutoConfiguration {
  @ConditionalOnMissingBean(SettingAdminAuthorizer::class)
  @Bean
  fun allowAllSettingAdminAuthorizer(): SettingAdminAuthorizer = AllowAllSettingAdminAuthorizer()

  @ConditionalOnMissingBean(SettingTypeRegistry::class)
  @Bean
  fun settingTypeRegistry(
    descriptors: List<SettingTypeDescriptor<out Setting>>,
  ): SettingTypeRegistry = SettingTypeRegistry(descriptors)

  /**
   * No-op audit log — silently discards entries when audit is not enabled.
   *
   * Skipped when `spring.setting.audit.enabled=true` so that a real [SettingAuditLog]
   * implementation (e.g. [MongoSettingAuditLog]) can register without being blocked by
   * this fallback, even though [SettingAdminAutoConfiguration] runs before the storage
   * module's auto-configuration.
   */
  @ConditionalOnMissingBean(SettingAuditLog::class)
  @ConditionalOnProperty(
    prefix = "spring.setting.audit",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
  )
  @Bean
  fun noopSettingAuditLog(): SettingAuditLog = NoopSettingAuditLog()

  /** Anonymous principal fallback — used when no transport-specific provider is registered. */
  @ConditionalOnMissingBean(SettingAuditPrincipalProvider::class)
  @Bean
  fun anonymousAuditPrincipalProvider(): SettingAuditPrincipalProvider = SettingAuditPrincipalProvider {
    "anonymous".toMono()
  }

  @Suppress("detekt.LongParameterList")
  @ConditionalOnMissingBean(SettingAdminService::class)
  @ConditionalOnBean(ObjectMapper::class)
  @Bean
  fun settingAdminService(
    registry: SettingTypeRegistry,
    settingRepository: SettingRepository,
    objectMapper: ObjectMapper,
    authorizer: SettingAdminAuthorizer,
    settingWriter: SettingWriter,
    settingReader: SettingReader,
    auditLog: ObjectProvider<SettingAuditLog>,
    principalProvider: SettingAuditPrincipalProvider,
    txOperator: ObjectProvider<TransactionalOperator>,
  ): SettingAdminService = SettingAdminServiceImpl(
    registry = registry,
    settingRepository = settingRepository,
    objectMapper = objectMapper,
    authorizer = authorizer,
    settingWriter = settingWriter,
    settingReader = settingReader,
    auditLog = auditLog.getIfAvailable(::NoopSettingAuditLog),
    principalProvider = principalProvider,
    txOperator = txOperator.ifAvailable,
  )
}
