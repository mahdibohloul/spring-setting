package io.github.mahdibohloul.spring.setting.repositories

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

interface SettingRepository {
  fun <T : Setting> findByName(key: String, type: KClass<T>): Mono<T>
  fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void>
  fun <T : Setting> save(key: String, setting: T): Mono<Void>
}
