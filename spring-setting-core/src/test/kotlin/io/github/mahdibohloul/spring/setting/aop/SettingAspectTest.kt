package io.github.mahdibohloul.spring.setting.aop

import box.tapsi.libs.utilities.reactor.fluxDeferWithObject
import box.tapsi.libs.utilities.reactor.monoDeferWithObject
import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.annotations.InjectSetting
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class SettingAspectTest {
  data class TestSetting(val value: String = "default") : Setting

  @Component
  class TestService {

    @InjectSetting(TestSetting::class)
    fun testMethod(): Mono<Void> = monoDeferWithObject(TestSetting::class) {
      assert(it.value == "default")
      return@monoDeferWithObject Mono.empty()
    }

    @InjectSetting(TestSetting::class)
    fun testMethodArgs(setting: TestSetting? = null): Mono<Void> {
      requireNotNull(setting)
      assert(setting.value == "default")
      return Mono.empty()
    }

    @InjectSetting(TestSetting::class)
    fun fluxMethodArgs(setting: TestSetting? = null): Flux<Setting> {
      requireNotNull(setting)
      assert(setting.value == "default")
      return Flux.just(setting)
    }

    @InjectSetting(TestSetting::class)
    fun fluxMethod(): Flux<Setting> = fluxDeferWithObject(TestSetting::class) {
      assert(it.value == "default")
      return@fluxDeferWithObject Flux.just(it)
    }
  }
}
