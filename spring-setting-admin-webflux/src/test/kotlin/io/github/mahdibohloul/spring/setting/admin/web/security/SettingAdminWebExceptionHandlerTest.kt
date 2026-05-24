package io.github.mahdibohloul.spring.setting.admin.web.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.mahdibohloul.spring.setting.admin.UnknownSettingTypeException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for [SettingAdminWebExceptionHandler] — no Spring context required.
 *
 * Each test drives the handler directly via [MockServerWebExchange] and asserts on the
 * HTTP status code and JSON body produced by the handler.
 */
class SettingAdminWebExceptionHandlerTest {

  private val objectMapper = ObjectMapper().registerKotlinModule()
  private val handler = SettingAdminWebExceptionHandler(objectMapper)

  private fun exchange(): MockServerWebExchange = MockServerWebExchange.from(
    MockServerHttpRequest.get("/spring-setting/admin/settings").build(),
  )

  /**
   * Reads the accumulated response body as a parsed [Map] after the handler has completed.
   * Relies on [MockServerHttpResponse.getBodyAsString] which accumulates all written DataBuffers.
   */
  @Suppress("UNCHECKED_CAST")
  private fun body(exchange: MockServerWebExchange): Map<String, Any?> {
    val raw = exchange.response.getBodyAsString().block()!!
    return objectMapper.readValue(raw, Map::class.java) as Map<String, Any?>
  }

  // ── AuthenticationException → 401 ────────────────────────────────────────────

  @Test
  fun `AuthenticationException maps to 401 with code unauthenticated`() {
    val exchange = exchange()
    StepVerifier.create(handler.handle(exchange, BadCredentialsException("expired token")))
      .verifyComplete()

    assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    val body = body(exchange)
    assertEquals("unauthenticated", body["code"])
    assertEquals("expired token", body["message"])
  }

  // ── AccessDeniedException → 403 ───────────────────────────────────────────────

  @Test
  fun `AccessDeniedException maps to 403 with code forbidden`() {
    val exchange = exchange()
    StepVerifier.create(handler.handle(exchange, AccessDeniedException("missing role")))
      .verifyComplete()

    assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    val body = body(exchange)
    assertEquals("forbidden", body["code"])
    assertEquals("missing role", body["message"])
  }

  // ── UnknownSettingTypeException → 404 ────────────────────────────────────────

  @Test
  fun `UnknownSettingTypeException maps to 404 with code unknown-setting-type`() {
    val exchange = exchange()
    val ex = UnknownSettingTypeException("Ghost", listOf("FooSetting", "BarSetting"))
    StepVerifier.create(handler.handle(exchange, ex))
      .verifyComplete()

    assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode)
    val body = body(exchange)
    assertEquals("unknown-setting-type", body["code"])
    assertNotNull(body["message"])
  }

  // ── ResponseStatusException → proxied status ──────────────────────────────────

  @Test
  fun `ResponseStatusException proxies its status code`() {
    val exchange = exchange()
    StepVerifier.create(
      handler.handle(exchange, ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "bad input")),
    ).verifyComplete()

    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exchange.response.statusCode)
    assertEquals("error", body(exchange)["code"])
  }

  @Test
  fun `ResponseStatusException with non-standard status falls back to 500`() {
    val exchange = exchange()
    // 999 is not a standard HttpStatus — resolve() returns null → falls back to 500
    StepVerifier.create(
      handler.handle(exchange, ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(999))),
    ).verifyComplete()

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.response.statusCode)
  }

  // ── Unhandled → 500 ───────────────────────────────────────────────────────────

  @Test
  fun `unexpected exception maps to 500 with code error and message`() {
    val exchange = exchange()
    StepVerifier.create(handler.handle(exchange, RuntimeException("database is on fire")))
      .verifyComplete()

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.response.statusCode)
    val body = body(exchange)
    assertEquals("error", body["code"])
    assertEquals("database is on fire", body["message"])
  }

  // ── Response headers ──────────────────────────────────────────────────────────

  @Test
  fun `response Content-Type is always application-json regardless of exception type`() {
    listOf(
      BadCredentialsException("auth"),
      AccessDeniedException("acl"),
      UnknownSettingTypeException("T", emptyList()),
      RuntimeException("other"),
    ).forEach { ex ->
      val exchange = exchange()
      StepVerifier.create(handler.handle(exchange, ex)).verifyComplete()
      assertEquals(
        "application/json",
        exchange.response.headers.contentType.toString(),
        "Expected application/json for ${ex::class.simpleName}",
      )
    }
  }
}
