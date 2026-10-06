package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.InvalidHistoryCursorException
import io.github.mahdibohloul.spring.setting.admin.audit.AuditEntry
import io.github.mahdibohloul.spring.setting.admin.audit.AuditHistoryPage
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant

private const val BASE = "/spring-setting/admin/settings"

/**
 * REST layer tests for the history endpoint of [SettingAuditController]: `limit` and `before` pass-through,
 * the `{entries, nextCursor}` shape, and [InvalidHistoryCursorException] → 400.
 */
@WebFluxTest(SettingAuditController::class)
@Import(AdminWebTestConfig::class)
class SettingAuditControllerTest {

  @Autowired
  private lateinit var client: WebTestClient

  @MockitoBean
  private lateinit var service: SettingAdminService

  private val entry = AuditEntry(
    id = "6720aa0000000000000000b1",
    typeName = "FooSetting",
    operation = SettingAdminAuthorizer.Operation.PATCH,
    previousValue = """{"x":1}""",
    newValue = """{"x":2}""",
    changedBy = "tester",
    changedAt = Instant.parse("2026-10-04T18:20:00Z"),
  )

  @Test
  fun `GET history without before returns the newest page and the next cursor`() {
    // given
    val page = AuditHistoryPage(entries = listOf(entry), nextCursor = "next-cursor")

    // when
    whenever(service.getHistory("FooSetting", 20, null)).thenReturn(Mono.just(page))

    // verify
    client.get().uri("$BASE/FooSetting/history")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.entries.length()").isEqualTo(1)
      .jsonPath("$.entries[0].id").isEqualTo("6720aa0000000000000000b1")
      .jsonPath("$.entries[0].operation").isEqualTo("PATCH")
      .jsonPath("$.nextCursor").isEqualTo("next-cursor")
  }

  @Test
  fun `GET history passes limit and before to the service`() {
    // given
    val page = AuditHistoryPage(entries = listOf(entry), nextCursor = null)

    // when
    whenever(service.getHistory("FooSetting", 5, "some-cursor")).thenReturn(Mono.just(page))

    // verify
    client.get().uri("$BASE/FooSetting/history?limit=5&before=some-cursor")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.entries.length()").isEqualTo(1)
      .jsonPath("$.nextCursor").doesNotExist()
  }

  @Test
  fun `GET history with an unknown cursor returns 400`() {
    // given / when
    whenever(service.getHistory("FooSetting", 20, "bad")).thenReturn(
      Mono.error(InvalidHistoryCursorException("bad")),
    )

    // verify
    client.get().uri("$BASE/FooSetting/history?before=bad")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.code").isEqualTo("invalid-cursor")
  }
}
