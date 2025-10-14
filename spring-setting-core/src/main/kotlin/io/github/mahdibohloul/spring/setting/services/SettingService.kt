package io.github.mahdibohloul.spring.setting.services

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

/**
 * Service interface that provides operations to manage and interact with system settings.
 * Allows for loading, saving, and deleting settings in a reactive, non-blocking manner.
 */
interface SettingService {
  /**
   * Loads a setting of the specified type from the repository.
   *
   * @param type the class reference of the setting type to be loaded
   * @return a Mono emitting the loaded setting instance if found, or completing empty if not found
   */
  fun <T : Setting> loadSetting(type: KClass<T>): Mono<T>

  /**
   * Saves the given setting to the underlying storage.
   *
   * @param setting the setting object to be saved. The setting must implement the `Setting` interface.
   * @return a `Mono<Void>` indicating completion or an error signal if the save operation fails.
   */
  fun <T : Setting> saveSetting(setting: T): Mono<Void>

  /**
   * Deletes the specified setting.
   *
   * This method removes a setting from the repository based on its type and content.
   * The operation is idempotent, meaning if the setting does not exist, the method completes without error.
   *
   * @param setting The setting instance to be deleted. Must be a subclass of `Setting`.
   * @return A `Mono<Void>` indicating the completion of the delete operation.
   */
  fun <T : Setting> deleteSetting(setting: T): Mono<Void>
}
