package io.github.mahdibohloul.spring.setting.admin.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.SettingHelper
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer.Operation
import io.github.mahdibohloul.spring.setting.admin.patch.JsonMergePatch
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

class SettingAdminServiceImpl(
  private val registry: SettingTypeRegistry,
  private val settingRepository: SettingRepository,
  private val objectMapper: ObjectMapper,
  private val authorizer: SettingAdminAuthorizer,
  private val settingWriter: SettingWriter,
  private val settingReader: SettingReader,
) : SettingAdminService {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun listTypeNames(): Mono<List<String>> = authorizer.authorize(Operation.LIST, null)
    .then(Mono.fromCallable { registry.listTypeNames() })

  override fun getAsJson(typeName: String): Mono<String> = authorizer.authorize(Operation.READ, typeName)
    .then(Mono.defer { loadOrDefault(registry.get(typeName)).map(::writeJson) })

  override fun patch(
    typeName: String,
    jsonMergePatch: String,
  ): Mono<String> = authorizer.authorize(Operation.PATCH, typeName).then(
    Mono.defer {
      @Suppress("UNCHECKED_CAST")
      val descriptor = registry.get(typeName) as SettingTypeDescriptor<Setting>

      val patchNode = objectMapper.readTree(jsonMergePatch)
      loadOrDefault(descriptor).flatMap { currentSetting ->
        val merged = objectMapper.treeToValue(
          JsonMergePatch.merge(objectMapper.valueToTree(currentSetting), patchNode),
          descriptor.settingClass.java,
        )
        settingRepository.save(repositoryName(descriptor), merged)
          .thenReturn(writeJson(merged))
          .doOnSuccess { logger.debug("Patched setting {}", typeName) }
      }
    },
  )

  override fun replace(
    typeName: String,
    jsonValue: String,
  ): Mono<String> = authorizer.authorize(Operation.REPLACE, typeName).then(
    Mono.defer {
      @Suppress("UNCHECKED_CAST")
      val descriptor = registry.get(typeName) as SettingTypeDescriptor<Setting>
      val replacedSetting = settingReader.readSetting(input = jsonValue, settingClass = descriptor.settingClass.java)
      settingRepository.save(repositoryName(descriptor), replacedSetting)
        .thenReturn(writeJson(replacedSetting))
        .doOnSuccess { logger.debug("Replaced setting {}", typeName) }
    },
  )

  override fun delete(typeName: String): Mono<Void> = authorizer.authorize(Operation.DELETE, typeName).then(
    Mono.defer {
      val descriptor = registry.get(typeName)
      @Suppress("UNCHECKED_CAST")
      settingRepository.deleteByName(
        repositoryName(descriptor),
        descriptor.settingClass as KClass<Setting>,
      ).doOnSuccess { logger.debug("Deleted setting {}", typeName) }
    },
  )

  private fun loadOrDefault(
    descriptor: SettingTypeDescriptor<out Setting>,
  ): Mono<Setting> = settingRepository.findByName(repositoryName(descriptor), descriptor.settingClass)
    .cast(Setting::class.java)
    .onErrorResume(NoSuchElementException::class.java) { Mono.fromCallable(descriptor::default) }

  private fun writeJson(setting: Setting): String = settingWriter.writeSetting(setting)

  /**
   * Persistence key — always derived via [SettingHelper.getSettingName] so the admin API
   * stays interoperable with `SettingService.loadSetting(...)` and `@InjectSetting` callers
   * even when a descriptor overrides its wire-visible `typeName`.
   */
  private fun repositoryName(
    descriptor: SettingTypeDescriptor<out Setting>,
  ): String = SettingHelper.getSettingName(descriptor.settingClass)
}
