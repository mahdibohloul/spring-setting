package io.github.mahdibohloul.spring.setting.repositories

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

/**
 * Interface for a repository that handles CRUD operations for settings.
 *
 * The `SettingRepository` interface provides a reactive abstraction for managing application settings
 * across different storage backends. It supports multiple implementations including in-memory caching,
 * Redis, MongoDB, and composite repositories that chain multiple storage layers.
 *
 * ## Key Features
 *
 * - **Reactive Operations**: All methods return `Mono` for non-blocking, reactive programming
 * - **Type Safety**: Generic type parameters ensure compile-time type checking
 * - **Flexible Storage**: Supports various storage backends through different implementations
 * - **Composite Pattern**: Multiple repositories can be chained for multi-level storage
 *
 * ## Repository Implementations
 *
 * - **SimpleInMemorySettingRepository**: Basic in-memory storage for development/testing
 * - **CaffeineSettingRepository**: High-performance in-memory cache with TTL support
 * - **RedisSettingRepository**: Redis-based storage for distributed caching
 * - **MongoSettingRepository**: MongoDB-based persistent storage
 * - **CompositeSettingRepository**: Chains multiple repositories with cache warming
 *
 * ## Usage Example
 *
 * ```kotlin
 * @Service
 * class MyService(private val settingRepository: SettingRepository) {
 *
 *     fun getDatabaseConfig(): Mono<DatabaseConfig> {
 *         return settingRepository.findByName("database", DatabaseConfig::class)
 *     }
 *
 *     fun saveDatabaseConfig(config: DatabaseConfig): Mono<Void> {
 *         return settingRepository.save("database", config)
 *     }
 * }
 * ```
 *
 * ## Error Handling
 *
 * - `findByName`: Returns `Mono.error(NoSuchElementException)` if setting not found
 * - `deleteByName`: Returns `Mono.error(NoSuchElementException)` if setting not found
 * - `save`: Returns `Mono.error(ClassCastException)` if type mismatch occurs
 *
 * ## Thread Safety
 *
 * All implementations are designed to be thread-safe and can be used concurrently
 * across multiple threads without additional synchronization.
 *
 * @param T The type of the setting, which must extend the `Setting` interface.
 * @see Setting The base interface that all settings must implement
 * @see CompositeSettingRepository For chaining multiple repositories
 * @since 0.0.1-SNAPSHOT
 */
interface SettingRepository {
  /**
   * Retrieves a setting by its name and type.
   *
   * This method attempts to find a setting with the specified key and type.
   * If the setting is found, it is deserialized to the requested type.
   * If the setting is not found, a `NoSuchElementException` is returned.
   * If the setting exists but cannot be deserialized to the requested type,
   * a `ClassCastException` is returned.
   *
   * @param name The unique identifier for the setting
   * @param type The Kotlin class representing the type of setting to retrieve
   * @return A `Mono` containing the setting if found, or an error if not found or type mismatch
   * @throws NoSuchElementException if no setting with the given key exists
   * @throws ClassCastException if the setting exists but cannot be cast to the requested type
   */
  fun <T : Setting> findByName(name: String, type: KClass<T>): Mono<T>

  /**
   * Deletes a setting by its name and type.
   *
   * This method removes a setting with the specified key and type from the repository.
   * The operation is idempotent - deleting a non-existent setting will not cause an error.
   * However, if a setting exists but cannot be cast to the requested type,
   * a `ClassCastException` may be returned.
   *
   * @param name The unique identifier for the setting to delete
   * @param type The Kotlin class representing the type of setting to delete
   * @return A `Mono` indicating completion of the delete operation
   * @throws ClassCastException if the setting exists but cannot be cast to the requested type
   */
  fun <T : Setting> deleteByName(name: String, type: KClass<T>): Mono<Void>

  /**
   * Saves or updates a setting with the given name.
   *
   * This method stores a setting in the repository. If a setting with the same key
   * already exists, it will be updated. The setting is serialized before storage.
   * If serialization fails, a `JsonProcessingException` will be wrapped in the returned error.
   *
   * @param name The unique identifier for the setting
   * @param setting The setting object to save
   * @return A `Mono` indicating completion of the save operation
   */
  fun <T : Setting> save(name: String, setting: T): Mono<Void>
}
