package io.github.mahdibohloul.spring.setting.aop

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.SettingHelper
import io.github.mahdibohloul.spring.setting.annotations.InjectSetting
import io.github.mahdibohloul.spring.setting.isReturningFlux
import io.github.mahdibohloul.spring.setting.isReturningMono
import io.github.mahdibohloul.spring.setting.isReturningPublisher
import io.github.mahdibohloul.spring.setting.services.SettingService
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.annotation.Order
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * Aspect responsible for injecting setting instances dynamically into annotated classes
 * or methods at runtime. The aspect intercepts method calls or class executions annotated
 * with `@InjectSetting` and provides the specified `Setting` by either injecting it into
 * the method arguments or the reactive context.
 *
 * This class operates with methods that return reactive `Publisher` types like `Mono` or `Flux`.
 * It ensures the correct `Setting` instance, as declared in the `InjectSetting` annotation,
 * is loaded and injected using the `SettingService`.
 *
 * The aspect performs two modes of injection:
 * - Argumental Injection: If the method's last parameter matches the `Setting` type, the
 *   `Setting` instance is injected as an argument.
 * - Contextual Injection: If the method parameters do not match the `Setting` type, the setting
 *   is injected into the reactive context instead.
 *
 * Dependencies:
 * - `Logger` for logging warnings, debug, and error messages during aspect execution.
 * - `SettingService` for loading the appropriate `Setting` instance based on the annotation metadata.
 *
 * AOP Pointcuts:
 * - `@within`: Applies to methods of classes annotated with `@InjectSetting`.
 * - `@annotation`: Applies to methods specifically annotated with `@InjectSetting`.
 *
 * Warnings:
 * If the intercepted method does not return a `Publisher` (e.g., `Mono` or `Flux`),
 * a warning is logged, and the method proceeds without injection.
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
class InjectSettingAspect(
  private val settingService: SettingService,
) {
  private val logger = LoggerFactory.getLogger(this::class.java)

  @Around("@within(io.github.mahdibohloul.spring.setting.annotations.InjectSetting)")
  fun injectSettingInClass(joinPoint: ProceedingJoinPoint): Any? {
    val method = (joinPoint.signature as MethodSignature).method
    if (!method.isReturningPublisher()) {
      logger.warn("InjectSettingAspect is only applicable to methods returning Publisher")
      return joinPoint.proceed()
    }
    val declaringClass = method.declaringClass
    return AnnotationUtils.findAnnotation(declaringClass, InjectSetting::class.java)?.let { annotation ->
      proceedPublisher(annotation, method, joinPoint)
    } ?: joinPoint.proceed()
  }

  @Around("@annotation(io.github.mahdibohloul.spring.setting.annotations.InjectSetting)")
  fun injectSetting(joinPoint: ProceedingJoinPoint): Any? {
    val method = (joinPoint.signature as MethodSignature).method
    if (!method.isReturningPublisher()) {
      logger.warn("InjectSettingAspect is only applicable to methods returning Publisher")
      return joinPoint.proceed()
    }
    return AnnotationUtils.findAnnotation(method, InjectSetting::class.java)?.let { annotation ->
      proceedPublisher(annotation, method, joinPoint)
    } ?: joinPoint.proceed()
  }

  private fun proceedPublisher(
    annotation: InjectSetting,
    method: Method,
    joinPoint: ProceedingJoinPoint,
  ): Publisher<out Any> {
    val settingMono = findSetting(annotation)
    return if (lastArgumentIsSetting(method, annotation.settingClass)) {
      settingMono.proceedJoinPointArgumental(joinPoint, method)
    } else {
      settingMono.proceedJoinPointContextual(joinPoint, method)
    }
  }

  private fun findSetting(
    injectSettingAnnotation: InjectSetting,
  ): Mono<out Setting> = settingService.loadSetting(injectSettingAnnotation.settingClass)
    .doOnNext {
      logger.debug("Setting ${it::class.simpleName} loaded to be injected")
    }
    .doOnError {
      logger.error("Error loading setting ${injectSettingAnnotation.settingClass.simpleName}", it)
    }

  private fun lastArgumentIsSetting(
    method: Method,
    settingClass: KClass<out Setting>,
  ): Boolean = method.parameterTypes.lastOrNull() == settingClass.java

  private fun Mono<out Setting>.proceedJoinPointArgumental(
    joinPoint: ProceedingJoinPoint,
    method: Method,
  ): Publisher<*> {
    val mono = this.map {
      joinPoint.args[joinPoint.args.lastIndex] = it
      return@map it
    }
    return if (method.isReturningMono()) {
      mono.flatMap { joinPoint.proceed(joinPoint.args) as Mono<*> }
    } else if (method.isReturningFlux()) {
      mono.flatMapMany { joinPoint.proceed(joinPoint.args) as Flux<*> }
    } else {
      error("Method is not returning Publisher")
    }
  }

  private fun Mono<out Setting>.proceedJoinPointContextual(
    joinPoint: ProceedingJoinPoint,
    method: Method,
  ): Publisher<*> = if (method.isReturningMono()) {
    this.flatMap { setting ->
      (joinPoint.proceed(joinPoint.args) as Mono<*>)
        .contextWrite { it.put(SettingHelper.getSettingName(setting::class), setting) }
    }
  } else if (method.isReturningFlux()) {
    this.flatMapMany { setting ->
      (joinPoint.proceed(joinPoint.args) as Flux<*>)
        .contextWrite { it.put(SettingHelper.getSettingName(setting::class), setting) }
    }
  } else {
    error("Method is not returning Publisher")
  }
}
