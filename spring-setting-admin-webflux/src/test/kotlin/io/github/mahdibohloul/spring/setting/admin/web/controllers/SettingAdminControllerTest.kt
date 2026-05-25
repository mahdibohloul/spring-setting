package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.UnknownSettingTypeException
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

private const val BASE = "/spring-setting/admin/settings"

/**
 * REST layer tests for [SettingAdminController].
 *
 * Uses `@WebFluxTest` (web slice only — no database, no real service) with a permissive
 * security chain from [AdminWebTestConfig]. Every test mocks [SettingAdminService] and drives
 * the HTTP surface via [WebTestClient].
 *
 * What is covered:
 * - All five CRUD endpoints: LIST, GET, PATCH, PUT, DELETE.
 * - Happy-path response shapes (status, Content-Type, JSON body fields).
 * - Exception → HTTP status mapping via [SettingAdminWebExceptionHandler]:
 *   [UnknownSettingTypeException] → 404, [AccessDeniedException] → 403,
 *   [BadCredentialsException] → 401, unexpected runtime exception → 500.
 */
@WebFluxTest(SettingAdminController::class)
@Import(AdminWebTestConfig::class)
class SettingAdminControllerTest {

  @Autowired
  private lateinit var client: WebTestClient

  @MockitoBean
  private lateinit var service: SettingAdminService

  // ── LIST ──────────────────────────────────────────────────────────────────────

  @Test
  fun `GET settings returns type names list`() {
    whenever(service.listTypeNames()).thenReturn(Mono.just(listOf("BarSetting", "FooSetting")))

    client.get().uri(BASE)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.typeNames.length()").isEqualTo(2)
      .jsonPath("$.typeNames[0]").isEqualTo("BarSetting")
      .jsonPath("$.typeNames[1]").isEqualTo("FooSetting")
  }

  @Test
  fun `GET settings returns empty list when no types registered`() {
    whenever(service.listTypeNames()).thenReturn(Mono.just(emptyList()))

    client.get().uri(BASE)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.typeNames.length()").isEqualTo(0)
  }

  // ── GET ───────────────────────────────────────────────────────────────────────

  @Test
  fun `GET settings type returns typeName and current JSON value`() {
    whenever(service.getAsJson("FooSetting")).thenReturn(Mono.just("""{"x":42}"""))

    client.get().uri("$BASE/FooSetting")
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.typeName").isEqualTo("FooSetting")
      .jsonPath("$.jsonValue").isEqualTo("""{"x":42}""")
  }

  @Test
  fun `GET settings type returns 404 for unregistered type`() {
    whenever(service.getAsJson("Ghost")).thenReturn(
      Mono.error(UnknownSettingTypeException("Ghost", listOf("FooSetting"))),
    )

    client.get().uri("$BASE/Ghost")
      .exchange()
      .expectStatus().isNotFound
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.code").isEqualTo("unknown-setting-type")
      .jsonPath("$.message").isNotEmpty
  }

  // ── PATCH ─────────────────────────────────────────────────────────────────────

  @Test
  fun `PATCH settings type applies merge-patch and returns updated payload`() {
    whenever(service.patch("FooSetting", """{"x":1}""")).thenReturn(Mono.just("""{"x":1}"""))

    client.patch().uri("$BASE/FooSetting")
      .contentType(MediaType.valueOf(SettingAdminController.APPLICATION_MERGE_PATCH_JSON_VALUE))
      .bodyValue("""{"x":1}""")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.typeName").isEqualTo("FooSetting")
      .jsonPath("$.jsonValue").isEqualTo("""{"x":1}""")
  }

  @Test
  fun `PATCH settings type returns 404 when type is unknown`() {
    whenever(service.patch("Ghost", """{"x":1}""")).thenReturn(
      Mono.error(UnknownSettingTypeException("Ghost", emptyList())),
    )

    client.patch().uri("$BASE/Ghost")
      .contentType(MediaType.valueOf(SettingAdminController.APPLICATION_MERGE_PATCH_JSON_VALUE))
      .bodyValue("""{"x":1}""")
      .exchange()
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.code").isEqualTo("unknown-setting-type")
  }

  // ── PUT ───────────────────────────────────────────────────────────────────────

  @Test
  fun `PUT settings type replaces value and returns updated payload`() {
    whenever(service.replace("FooSetting", """{"x":99}""")).thenReturn(Mono.just("""{"x":99}"""))

    client.put().uri("$BASE/FooSetting")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"x":99}""")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.typeName").isEqualTo("FooSetting")
      .jsonPath("$.jsonValue").isEqualTo("""{"x":99}""")
  }

  @Test
  fun `PUT settings type returns 404 when type is unknown`() {
    whenever(service.replace("Ghost", """{"x":1}""")).thenReturn(
      Mono.error(UnknownSettingTypeException("Ghost", emptyList())),
    )

    client.put().uri("$BASE/Ghost")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"x":1}""")
      .exchange()
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.code").isEqualTo("unknown-setting-type")
  }

  // ── DELETE ────────────────────────────────────────────────────────────────────

  @Test
  fun `DELETE settings type calls service and returns 204 with no body`() {
    whenever(service.delete("FooSetting")).thenReturn(Mono.empty())

    client.delete().uri("$BASE/FooSetting")
      .exchange()
      .expectStatus().isNoContent
      .expectBody().isEmpty
  }

  @Test
  fun `DELETE settings type returns 404 when type is unknown`() {
    whenever(service.delete("Ghost")).thenReturn(
      Mono.error(UnknownSettingTypeException("Ghost", emptyList())),
    )

    client.delete().uri("$BASE/Ghost")
      .exchange()
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.code").isEqualTo("unknown-setting-type")
  }

  // ── Exception → HTTP status mapping ──────────────────────────────────────────

  @Test
  fun `AccessDeniedException from service maps to 403 with code forbidden`() {
    whenever(service.getAsJson("FooSetting")).thenReturn(
      Mono.error(AccessDeniedException("missing role")),
    )

    // Must use an authenticated (non-anonymous) user: Spring Security's ExceptionTranslationWebFilter
    // converts AccessDeniedException for anonymous users to InsufficientAuthenticationException → 401.
    // With a real authentication the filter calls accessDeniedHandler → 403.
    client.mutateWith(mockUser("tester"))
      .get().uri("$BASE/FooSetting")
      .exchange()
      .expectStatus().isForbidden
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.code").isEqualTo("forbidden")
      .jsonPath("$.message").isEqualTo("missing role")
  }

  @Test
  fun `AuthenticationException from service maps to 401 with code unauthenticated`() {
    whenever(service.getAsJson("FooSetting")).thenReturn(
      Mono.error(BadCredentialsException("expired token")),
    )

    client.get().uri("$BASE/FooSetting")
      .exchange()
      .expectStatus().isUnauthorized
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.code").isEqualTo("unauthenticated")
      .jsonPath("$.message").isEqualTo("expired token")
  }

  @Test
  fun `unexpected service exception maps to 500 with code error and preserves message`() {
    whenever(service.getAsJson("FooSetting")).thenReturn(
      Mono.error(RuntimeException("database is on fire")),
    )

    client.get().uri("$BASE/FooSetting")
      .exchange()
      .expectStatus().is5xxServerError
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.code").isEqualTo("error")
      .jsonPath("$.message").isEqualTo("database is on fire")
  }
}
