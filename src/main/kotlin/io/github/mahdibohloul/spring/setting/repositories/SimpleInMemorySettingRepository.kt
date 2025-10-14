package io.github.mahdibohloul.spring.setting.repositories

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KClass

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
