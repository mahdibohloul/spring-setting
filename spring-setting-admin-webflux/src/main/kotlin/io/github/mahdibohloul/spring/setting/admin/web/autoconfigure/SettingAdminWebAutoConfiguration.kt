package io.github.mahdibohloul.spring.setting.admin.web.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.audit.NoopSettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditPrincipalProvider
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.autoconfigure.SettingAdminAutoConfiguration
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminServiceImpl
import io.github.mahdibohloul.spring.setting.admin.web.SettingAdminWebProperties
import io.github.mahdibohloul.spring.setting.admin.web.acl.RoleBasedSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.web.acl.SettingAdminAclProperties
import io.github.mahdibohloul.spring.setting.admin.web.audit.ReactiveSecurityAuditPrincipalProvider
import io.github.mahdibohloul.spring.setting.admin.web.controllers.AdminFeaturesController
import io.github.mahdibohloul.spring.setting.admin.web.controllers.SettingAdminController
import io.github.mahdibohloul.spring.setting.admin.web.controllers.SettingAuditController
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureDescriptor
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureRegistry
import io.github.mahdibohloul.spring.setting.admin.web.features.SettingsAdminFeature
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminAuthProperties
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminSecurityConfiguration
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminWebExceptionHandler
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.annotation.Order
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * Top-level auto-configuration for `spring-setting-admin-webflux`.
 *
 * Activation order:
 * 1. After [SettingAdminAutoConfiguration] (so [SettingTypeRegistry] + the headless services exist).
 * 2. Gated by `@ConditionalOnClass(SecurityWebFilterChain)` — module is harmless if a consumer
 *    doesn't pull in Spring Security WebFlux.
 * 3. Strictly opt-in via `spring.setting.admin.web.enabled=true`.
 *
 * The configuration:
 * - Binds the three property classes ([SettingAdminWebProperties], [SettingAdminAclProperties],
 *   [SettingAdminAuthProperties]).
 * - Replaces the Phase 1 allow-all authorizer with [RoleBasedSettingAdminAuthorizer] (@Primary).
 * - Re-creates [SettingAdminServiceImpl] with that authorizer (using `@Primary` so callers don't
 *   have to disambiguate).
 * - Registers the controllers, the feature registry, the built-in [SettingsAdminFeature], and
 *   the JSON exception renderer.
 * - Registers [ReactiveSecurityAuditPrincipalProvider] so audit entries record the actual caller.
 * - Registers [SettingAuditController] when `spring.setting.audit.enabled=true`.
 * - Imports [SettingAdminSecurityConfiguration] which sets up the scoped `SecurityWebFilterChain`.
 */
@AutoConfiguration(after = [SettingAdminAutoConfiguration::class])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(SecurityWebFilterChain::class)
@ConditionalOnBean(SettingRepository::class)
@ConditionalOnProperty(prefix = "spring.setting.admin.web", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(
  SettingAdminWebProperties::class,
  SettingAdminAclProperties::class,
  SettingAdminAuthProperties::class,
)
@Import(SettingAdminSecurityConfiguration::class)
class SettingAdminWebAutoConfiguration {

  @Bean
  @Primary
  fun roleBasedSettingAdminAuthorizer(
    acl: SettingAdminAclProperties,
    registry: SettingTypeRegistry,
  ): SettingAdminAuthorizer = RoleBasedSettingAdminAuthorizer(acl, registry)

  /**
   * Spring-Security-aware principal provider — overrides the `"anonymous"` fallback registered
   * by [SettingAdminAutoConfiguration] so audit entries record the actual authenticated user.
   */
  @Bean
  @Primary
  fun reactiveSecurityAuditPrincipalProvider(): SettingAuditPrincipalProvider = ReactiveSecurityAuditPrincipalProvider()

  /**
   * Override the Phase 1 [SettingAdminService] bean with one wired to the role-based authorizer
   * and the Spring-Security-aware principal provider.
   * `@Primary` so injection points pick this implementation; the Phase 1 bean (allow-all) stays
   * available for tests or for consumers that explicitly @Qualifier away from it.
   */
  @Suppress("detekt.LongParameterList")
  @Bean
  @Primary
  fun roleBasedSettingAdminService(
    registry: SettingTypeRegistry,
    settingRepository: SettingRepository,
    objectMapper: ObjectMapper,
    authorizer: SettingAdminAuthorizer,
    settingReader: SettingReader,
    settingWriter: SettingWriter,
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

  @Bean
  fun settingAdminController(
    service: SettingAdminService,
  ): SettingAdminController = SettingAdminController(service)

  /**
   * Audit history and revert endpoints — only registered when audit is explicitly enabled.
   * Without `spring.setting.audit.enabled=true`, the routes are entirely absent.
   */
  @Bean
  @ConditionalOnProperty(prefix = "spring.setting.audit", name = ["enabled"], havingValue = "true")
  fun settingAuditController(service: SettingAdminService): SettingAuditController = SettingAuditController(service)

  @Bean
  fun adminFeatureRegistry(
    descriptors: List<AdminFeatureDescriptor>,
  ): AdminFeatureRegistry = AdminFeatureRegistry(descriptors)

  @Bean
  fun adminFeaturesController(
    registry: AdminFeatureRegistry,
  ): AdminFeaturesController = AdminFeaturesController(registry)

  @Bean
  @ConditionalOnMissingBean(name = ["settingsAdminFeature"])
  fun settingsAdminFeature(
    typeRegistry: SettingTypeRegistry,
    acl: SettingAdminAclProperties,
    webProperties: SettingAdminWebProperties,
  ): AdminFeatureDescriptor = SettingsAdminFeature(typeRegistry, acl, webProperties)

  /**
   * Must run before Spring Boot's [org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler]
   * which is registered at `@Order(-1)`. Without a lower number our JSON error bodies would be
   * swallowed by the default error page handler before they ever reach the client.
   */
  @Bean
  @Order(EXCEPTION_HANDLER_ORDER)
  fun settingAdminWebExceptionHandler(
    objectMapper: ObjectMapper,
  ): SettingAdminWebExceptionHandler = SettingAdminWebExceptionHandler(objectMapper)

  companion object {
    /**
     * Order of the exception handler bean.
     * Must be lower than Spring Boot's `DefaultErrorWebExceptionHandler` which sits at `-1`,
     * so our JSON error bodies are written before the default error page handler runs.
     */
    const val EXCEPTION_HANDLER_ORDER = -2
  }
}
