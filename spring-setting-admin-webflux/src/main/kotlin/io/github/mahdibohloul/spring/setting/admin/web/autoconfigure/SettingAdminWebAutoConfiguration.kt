package io.github.mahdibohloul.spring.setting.admin.web.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.autoconfigure.SettingAdminAutoConfiguration
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminServiceImpl
import io.github.mahdibohloul.spring.setting.admin.web.SettingAdminWebProperties
import io.github.mahdibohloul.spring.setting.admin.web.acl.RoleBasedSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.web.acl.SettingAdminAclProperties
import io.github.mahdibohloul.spring.setting.admin.web.controllers.AdminFeaturesController
import io.github.mahdibohloul.spring.setting.admin.web.controllers.SettingAdminController
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureDescriptor
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureRegistry
import io.github.mahdibohloul.spring.setting.admin.web.features.SettingsAdminFeature
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminAuthProperties
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminSecurityConfiguration
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminWebExceptionHandler
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
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
import org.springframework.security.web.server.SecurityWebFilterChain

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
   * Override the Phase 1 [SettingAdminService] bean with one wired to the role-based authorizer.
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
  ): SettingAdminService = SettingAdminServiceImpl(
    registry,
    settingRepository,
    objectMapper,
    authorizer,
    settingWriter,
    settingReader,
  )

  @Bean
  fun settingAdminController(
    service: SettingAdminService,
  ): SettingAdminController = SettingAdminController(service)

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

  @Bean
  fun settingAdminWebExceptionHandler(
    objectMapper: ObjectMapper,
  ): SettingAdminWebExceptionHandler = SettingAdminWebExceptionHandler(objectMapper)
}
