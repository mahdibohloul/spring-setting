package io.github.mahdibohloul.spring.setting

import io.github.mahdibohloul.spring.setting.services.SettingService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import kotlin.reflect.KClass

fun <TSetting : Setting, T> fluxWithLoadSetting(
  settingService: SettingService,
  clazz: KClass<TSetting>,
  monoFactory: Function<in TSetting, out Flux<out T>>,
): Flux<T> = settingService.loadSetting(clazz)
  .flatMapMany { setting -> monoFactory.apply(setting) }

fun <TSetting : Setting, T> monoWithLoadSetting(
  settingService: SettingService,
  clazz: KClass<TSetting>,
  monoFactory: Function<in TSetting, out Mono<out T>>,
): Mono<T> = settingService.loadSetting(clazz)
  .flatMap { setting -> monoFactory.apply(setting) }
