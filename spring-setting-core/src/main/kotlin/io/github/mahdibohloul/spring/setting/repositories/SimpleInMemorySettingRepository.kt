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

  override fun <T : Setting> findByName(name: String, type: KClass<T>): Mono<T> = Mono.fromCallable { store[name] }
    .switchIfEmpty(Mono.error(NoSuchElementException("Setting with key $name not found")))
    .filter { type.isInstance(it) }
    .switchIfEmpty(Mono.error(ClassCastException("Setting with key $name is not of type ${type.simpleName}")))
    .cast(type.java)

  override fun <T : Setting> deleteByName(name: String, type: KClass<T>): Mono<Void> = Mono.fromRunnable<Unit> {
    store.remove(name)
  }.then()

  override fun <T : Setting> save(name: String, setting: T): Mono<Void> = Mono.fromCallable {
    store[name] = setting
  }.then()

  override fun close() {
    store.clear()
  }
}
