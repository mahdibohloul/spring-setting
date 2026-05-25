package io.github.mahdibohloul.spring.setting

import io.github.mahdibohloul.spring.setting.services.SettingService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.util.function.Function
import kotlin.reflect.KClass

fun <TSetting : Setting, T : Any> fluxWithLoadSetting(
  settingService: SettingService,
  clazz: KClass<TSetting>,
  monoFactory: Function<in TSetting, out Flux<out T>>,
): Flux<T> = settingService.loadSetting(clazz)
  .flatMapMany { setting -> monoFactory.apply(setting) }

fun <TSetting : Setting, T : Any> monoWithLoadSetting(
  settingService: SettingService,
  clazz: KClass<TSetting>,
  monoFactory: Function<in TSetting, out Mono<out T>>,
): Mono<T> = settingService.loadSetting(clazz)
  .flatMap { setting -> monoFactory.apply(setting) }

internal fun Method.isReturningPublisher(): Boolean = when (this.returnType) {
  Mono::class.java -> true
  Flux::class.java -> true
  else -> false
}

internal fun Method.isReturningMono(): Boolean = this.returnType == Mono::class.java

internal fun Method.isReturningFlux(): Boolean = this.returnType == Flux::class.java
