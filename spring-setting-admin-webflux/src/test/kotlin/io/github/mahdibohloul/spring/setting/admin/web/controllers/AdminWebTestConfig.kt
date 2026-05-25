package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminWebExceptionHandler
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.util.function.Supplier

/**
 * Shared `@TestConfiguration` for `@WebFluxTest` slices.
 *
 * Import with `@Import(AdminWebTestConfig::class)` on each test class.
 *
 * Provides:
 * - A permissive [SecurityWebFilterChain] (no CSRF, permitAll) so controller tests focus on
 *   request/response behaviour rather than authentication mechanics. Security itself is tested
 *   separately via [io.github.mahdibohloul.spring.setting.admin.web.acl.RoleBasedSettingAdminAuthorizerTest].
 * - [SettingAdminWebExceptionHandler] at `@Order(-2)` so service-layer exceptions always produce
 *   JSON bodies in tests (and in production, before Spring Boot's DefaultErrorWebExceptionHandler).
 */
@TestConfiguration
@ImportAutoConfiguration(ReactiveWebSecurityAutoConfiguration::class)
class AdminWebTestConfig(private val objectMapper: ObjectMapper) {

  @Bean
  @Order(-2)
  fun settingAdminWebExceptionHandler(): SettingAdminWebExceptionHandler = SettingAdminWebExceptionHandler(objectMapper)

  /**
   * Replicates Spring Security's [SecurityMockServerConfigurers.springSecurity] `MutatorFilter`.
   *
   * [SecurityMockServerConfigurers.mockUser] registers a `SetupMutatorFilter` at position 0 which
   * stores the mock [SecurityContext] as an exchange attribute keyed `"context"`. The counterpart
   * `MutatorFilter` (added by [SecurityMockServerConfigurers.springSecurity]) reads that attribute
   * and applies `.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(...))` so that
   * [ReactiveSecurityContextHolder.getContext] sees the mock authentication.
   *
   * In SB4 [org.springframework.boot.webflux.test.autoconfigure.WebFluxTest], security
   * auto-configuration is NOT included in the slice, so `springSecurity()` is never auto-applied.
   * Registering this bean ensures the mock security context reaches the Reactor context without
   * having to call `springSecurity()` in every test method.
   */
  // Must run before WebFilterChainProxy (order -100) so that ExceptionTranslationWebFilter inside
  // the security chain can read the mock principal from the Reactor context.
  @Bean
  @Order(-101)
  fun mockUserMutatorFilter(): WebFilter = WebFilter { exchange, chain ->
    @Suppress("UNCHECKED_CAST")
    val contextSupplier = exchange.getAttribute<Supplier<Mono<SecurityContext>>>("context")
    if (contextSupplier != null) {
      exchange.attributes.remove("context")
      chain.filter(exchange)
        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(contextSupplier.get()))
    } else {
      chain.filter(exchange)
    }
  }

  @Bean
  fun testSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
    .csrf { it.disable() }
    .httpBasic { it.disable() }
    .formLogin { it.disable() }
    .authorizeExchange { it.anyExchange().permitAll() }
    .exceptionHandling { spec ->
      // Configure JSON error body for 403 so tests asserting on the body shape pass.
      // Without this, Spring Security's default AccessDeniedHandler returns 403 with empty body.
      spec.accessDeniedHandler { exchange, ex ->
        writeJsonError(exchange, HttpStatus.FORBIDDEN, "forbidden", ex)
      }
    }
    .build()

  private fun writeJsonError(
    exchange: ServerWebExchange,
    status: HttpStatus,
    code: String,
    ex: Throwable,
  ): Mono<Void> {
    val response = exchange.response
    response.statusCode = status
    response.headers.contentType = MediaType.APPLICATION_JSON
    val payload = objectMapper.writeValueAsBytes(
      mapOf("code" to code, "message" to (ex.message ?: code)),
    )
    return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)))
  }
}
