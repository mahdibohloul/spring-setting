package io.github.mahdibohloul.spring.setting.memory

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import org.springframework.beans.factory.DisposableBean
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * A Caffeine-based implementation of the `SettingRepository` interface.
 *
 * This repository uses a high-performance, in-memory Caffeine cache to store and manage application settings.
 * It provides reactive operations for retrieving, saving, and deleting settings. Settings are cached with a
 * time-to-live (TTL) of one hour by default and rely on JSON serialization/deserialization for storage and retrieval.
 *
 * ## Key Features
 * - In-memory caching implementation backed by Caffeine.
 * - Configurable maximum size and TTL for cached items.
 * - Reactive, non-blocking interactions using `Mono` for all operations.
 * - Serialization and deserialization of settings through Jackson's `ObjectMapper`.
 *
 * ## Constructor Parameters
 * @param objectMapper Used for serializing and deserializing settings in JSON format.
 * @param cache The Caffeine `AsyncCache` instance. If not provided, a default cache with a maximum size
 * of 10,000 entries and an expiration time of 1 hour will be used.
 *
 * ## Error Handling
 * - `findByName`:
 *   - Returns `Mono.error(NoSuchElementException)` if the setting is not found in the cache.
 *   - Returns `Mono.error(ClassCastException)` if deserialization to the requested type fails.
 * - `deleteByName`:
 *   - Silently performs the operation if the key does not exist, as deletions are idempotent.
 * - `save`:
 *   - If serialization of the setting fails, wraps the exception in the returned `Mono`.
 *
 * ## Thread Safety
 * Thread-safe implementation where the underlying Caffeine cache supports concurrent operations
 * without additional synchronization.
 *
 * ## Companion Object
 * Contains a default constant for the maximum cache size: `DEFAULT_MAXIMUM_SIZE`.
 *
 * ## Method Behaviors
 * - `findByName`: Retrieves a setting by its name and type, returning a `Mono` of the setting if found,
 *   or an error otherwise.
 * - `deleteByName`: Deletes a setting by its name, completing the operation reactively.
 * - `save`: Saves or updates a setting by its name, serializing it to JSON before storage.
 */
class CaffeineSettingRepository(
  private val cache: AsyncCache<String, Any> = Caffeine.newBuilder()
    .maximumSize(DEFAULT_MAXIMUM_SIZE.toLong())
    .expireAfterWrite(Duration.ofHours(1))
    .buildAsync(),
) : SettingRepository,
  AutoCloseable,
  DisposableBean {
  override fun <T : Setting> findByName(
    key: String,
    type: KClass<T>,
  ): Mono<T> = cache.getIfPresent(key)?.let { future ->
    Mono.fromFuture(future)
      .map { value -> type.cast(value) }
  } ?: Mono.error(NoSuchElementException("Setting with key $key not found"))

  override fun <T : Setting> deleteByName(
    key: String,
    type: KClass<T>,
  ): Mono<Void> = Mono.fromRunnable { cache.synchronous().invalidate(key) }

  override fun <T : Setting> save(key: String, setting: T): Mono<Void> = Mono.fromCallable {
    cache.put(key, Mono.justOrEmpty(setting).toFuture())
  }.then()

  override fun destroy() {
    doClose()
  }

  override fun close() {
    doClose()
  }

  private fun doClose() {
    cache.synchronous().invalidateAll()
  }

  companion object {
    private const val DEFAULT_MAXIMUM_SIZE = 10_000
  }
}
