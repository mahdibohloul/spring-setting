package io.github.mahdibohloul.spring.setting.annotations

import io.github.mahdibohloul.spring.setting.Setting
import kotlin.reflect.KClass

/**
 * Annotation to inject a setting.
 *
 * This annotation should be used on bean public functions.
 *
 * If the annotated function's last parameter is a [Setting] then the setting will be injected into it.
 * Otherwise, the setting will be injected into the context of the reactive pipeline.
 * @param settingClass The class of the setting to be injected.
 */

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class InjectSetting(
  val settingClass: KClass<out Setting>,
)
