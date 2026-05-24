package io.github.mahdibohloul.spring.setting.services

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.SettingHelper
import io.github.mahdibohloul.spring.setting.SettingProperties
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import org.slf4j.LoggerFactory
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.Validator
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.onErrorMap
import reactor.kotlin.core.publisher.onErrorResume
import reactor.kotlin.core.publisher.toMono
import kotlin.reflect.KClass

class SettingServiceImpl(
  private val settingRepository: SettingRepository,
  private val settingProperties: SettingProperties,
  private val validator: Validator?,
) : SettingService {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun <T : Setting> deleteSetting(setting: T): Mono<Void> = Mono.defer {
    val settingName = SettingHelper.getSettingName(setting::class)
    return@defer settingRepository.deleteByName(settingName, setting::class)
  }.doOnSuccess {
    logger.debug("Deleted {} settings", SettingHelper.getSettingName(setting::class))
  }.doOnError {
    logger.error("Error deleting settings", it)
  }

  override fun <T : Setting> loadSetting(type: KClass<T>): Mono<T> = Mono.defer {
    val settingName = SettingHelper.getSettingName(type)
    return@defer settingRepository.findByName(settingName, type)
  }.onErrorResume(NoSuchElementException::class) { error ->
    maybeCreateDefaultInstance(type, error)
  }.map(::validateOrError)
    .doOnNext {
      logger.debug("Setting loaded for {} with value {}", type.simpleName, it)
    }.doOnError {
      logger.error("Error loading setting for ${type.simpleName}", it)
    }

  override fun <T : Setting> saveSetting(setting: T): Mono<Void> = Mono.defer {
    // Validate the setting before saving. If validation fails,
    // it will throw an exception and the setting won't be saved.
    validateOrError(setting)

    val settingName = SettingHelper.getSettingName(setting::class)
    return@defer settingRepository.save(settingName, setting)
  }
    .doOnError { logger.error("Error saving setting", it) }
    .doOnSuccess { logger.debug("Setting saved for {} with value {}", setting::class.simpleName, setting) }

  private fun <T : Setting> maybeCreateDefaultInstance(type: KClass<T>, error: Throwable): Mono<T> = Mono.defer {
    if (!settingProperties.createDefaultInstance) {
      return@defer Mono.error(error)
    }
    return@defer SettingHelper.createSetting(type).toMono()
  }.onErrorMap(IllegalArgumentException::class) { error }

  private fun <T : Setting> validateOrError(setting: T): T {
    if (validator == null) {
      return setting
    }
    val errors = BeanPropertyBindingResult(setting, setting::class.simpleName ?: "Setting")
    validator.validate(setting, errors)
    errors.failOnError {
      IllegalArgumentException(
        "Validation failed for setting ${setting::class.simpleName}: ${
          errors.allErrors.joinToString(separator = ", ") { err -> err.toErrorMessage() }
        }",
      )
    }
    return setting
  }

  private fun ObjectError.toErrorMessage(): String = (
    (this as? FieldError)?.defaultMessage
      ?: this.defaultMessage
      ?: this.code
      ?: this.toString()
    )
}
