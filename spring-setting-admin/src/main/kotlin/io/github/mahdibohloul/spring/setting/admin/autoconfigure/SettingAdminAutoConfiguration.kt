package io.github.mahdibohloul.spring.setting.admin.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.authorization.AllowAllSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminServiceImpl
import io.github.mahdibohloul.spring.setting.autoconfigure.SettingAutoConfiguration
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

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
  ): SettingAdminService = SettingAdminServiceImpl(
    registry,
    settingRepository,
    objectMapper,
    authorizer,
    settingWriter,
    settingReader,
  )
}
