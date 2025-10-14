package io.github.mahdibohloul.spring.setting.repositories

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

/**
 * An in-memory implementation of the `SettingRepository` interface.
 *
 * Stores settings in an internal concurrent map and provides
 * CRUD operations for accessing and modifying settings. This implementation
 * is primarily intended for development or testing purposes where a lightweight,
 * non-persistent repository is sufficient.
 *
 * ## Thread Safety
 * This repository is thread-safe as it uses a `ConcurrentMap` for internal storage.
 *
 * ## Usage Notes
 * - Settings are stored in-memory and are cleared when the repository is closed.
 * - This implementation does not provide persistence across application restarts.
 * - Type safety is enforced during retrieval and deletion operations.
 *
 * ## Error Handling
 * - `findByName`: Returns a `NoSuchElementException` if no setting with the given key exists.
 *   Returns a `ClassCastException` if the setting exists but cannot be cast to the requested type.
 * - `deleteByName`: Silently removes the setting if it exists, no errors are thrown for non-existent keys.
 * - `save`: Overwrites any existing setting with the same key.
 */
class SimpleInMemorySettingRepository :
  SettingRepository,
  AutoCloseable {
  private val store: ConcurrentMap<String, Setting> = ConcurrentHashMap()

  override fun <T : Setting> findByName(key: String, type: KClass<T>): Mono<T> = Mono.fromCallable { store[key] }
    .switchIfEmpty(Mono.error(NoSuchElementException("Setting with key $key not found")))
    .filter { type.isInstance(it) }
    .switchIfEmpty(Mono.error(ClassCastException("Setting with key $key is not of type ${type.simpleName}")))
    .cast(type.java)

  override fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void> = Mono.fromRunnable<T?> {
    store.remove(key)
  }.then()

  override fun <T : Setting> save(key: String, setting: T): Mono<Void> = Mono.fromCallable {
    store[key] = setting
  }.then()

  override fun close() {
    store.clear()
  }
}
