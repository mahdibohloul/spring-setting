package io.github.mahdibohloul.spring.setting.services

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

interface SettingService {
  fun <T : Setting> loadSetting(type: KClass<T>): Mono<T>
  fun <T : Setting> saveSetting(setting: T): Mono<Void>
  fun <T : Setting> deleteSetting(setting: T): Mono<Void>
}
