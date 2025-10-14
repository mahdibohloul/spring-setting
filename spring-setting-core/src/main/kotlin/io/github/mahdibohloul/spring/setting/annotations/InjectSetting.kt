package io.github.mahdibohloul.spring.setting.annotations

import io.github.mahdibohloul.spring.setting.Setting
import kotlin.reflect.KClass

/**
 * Annotation used for injecting a specific `Setting` into the context or method arguments.
 *
 * This annotation can be applied to classes or functions and is used in conjunction
 * with AOP to dynamically provide a `Setting` instance of the specified type at runtime.
 * The annotated element can then use this `Setting` object contextually or as an argument.
 *
 * @property settingClass Specifies the class of the `Setting` to be injected.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class InjectSetting(
  val settingClass: KClass<out Setting>,
)
