package io.github.mahdibohloul.spring.setting.admin.web.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.admin.web.security.SettingAdminWebExceptionHandler
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

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
class AdminWebTestConfig(private val objectMapper: ObjectMapper) {

  @Bean
  @Order(-2)
  fun settingAdminWebExceptionHandler(): SettingAdminWebExceptionHandler = SettingAdminWebExceptionHandler(objectMapper)

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
