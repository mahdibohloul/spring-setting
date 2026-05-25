package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureDescriptor
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureRegistry
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminOperation
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient

private const val BASE = "/spring-setting/admin"

/**
 * REST layer tests for [AdminFeaturesController].
 *
 * Covers:
 * - `GET /features`: role-based feature visibility, operation shape (kind, httpMethod,
 *   pathTemplate, requiredRoles), empty list when no features match the caller's roles.
 * - `GET /me`: principal name and role set (ROLE_ prefix stripped), multiple roles.
 *
 * Role mechanics: `SecurityMockServerConfigurers.mockUser().roles("tech-manager")` stores the
 * authority as `ROLE_tech-manager`. The controller strips `ROLE_` before passing the role set
 * to [AdminFeatureRegistry.visibleTo], so stubs are set up against the stripped names.
 */
@WebFluxTest(AdminFeaturesController::class)
@Import(AdminWebTestConfig::class)
class AdminFeaturesControllerTest {

  @Autowired
  private lateinit var client: WebTestClient

  @MockitoBean
  private lateinit var registry: AdminFeatureRegistry

  // ── /features ─────────────────────────────────────────────────────────────────

  @Test
  fun `GET features returns features visible to caller roles`() {
    val feature = feature("settings", visibleToRoles = setOf("tech-manager"))
    // mockUser().roles("tech-manager") → authority ROLE_tech-manager → stripped to "tech-manager"
    whenever(registry.visibleTo(setOf("tech-manager"))).thenReturn(listOf(feature))

    client.mutateWith(mockUser("admin").roles("tech-manager"))
      .get().uri("$BASE/features")
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.features.length()").isEqualTo(1)
      .jsonPath("$.features[0].id").isEqualTo("settings")
      .jsonPath("$.features[0].label").isEqualTo("Settings")
      .jsonPath("$.features[0].routePrefix").isEqualTo("/settings")
      .jsonPath("$.features[0].visibleToRoles[0]").isEqualTo("tech-manager")
  }

  @Test
  fun `GET features serialises operation kind and httpMethod correctly`() {
    val feature = feature("settings", visibleToRoles = emptySet())
    whenever(registry.visibleTo(any())).thenReturn(listOf(feature))

    client.mutateWith(mockUser("admin").roles("tech-manager"))
      .get().uri("$BASE/features")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      // three operations: JsonMergePatch, Replace, Delete
      .jsonPath("$.features[0].operations.length()").isEqualTo(3)
      .jsonPath("$.features[0].operations[0].kind").isEqualTo("json-merge-patch")
      .jsonPath("$.features[0].operations[0].httpMethod").isEqualTo("PATCH")
      .jsonPath("$.features[0].operations[0].pathTemplate").isEqualTo("/admin/settings/patch")
      .jsonPath("$.features[0].operations[0].requiredRoles[0]").isEqualTo("tech-manager")
      .jsonPath("$.features[0].operations[1].kind").isEqualTo("replace")
      .jsonPath("$.features[0].operations[1].httpMethod").isEqualTo("PUT")
      .jsonPath("$.features[0].operations[2].kind").isEqualTo("delete")
      .jsonPath("$.features[0].operations[2].httpMethod").isEqualTo("DELETE")
  }

  @Test
  fun `GET features returns empty list when caller holds no matching role`() {
    whenever(registry.visibleTo(any())).thenReturn(emptyList())

    client.mutateWith(mockUser("limited").roles("viewer"))
      .get().uri("$BASE/features")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.features.length()").isEqualTo(0)
  }

  @Test
  fun `GET features includes multiple features when registry returns them`() {
    val f1 = feature("settings")
    val f2 = feature("orders")
    whenever(registry.visibleTo(any())).thenReturn(listOf(f1, f2))

    client.mutateWith(mockUser("admin").roles("tech-manager"))
      .get().uri("$BASE/features")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.features.length()").isEqualTo(2)
      .jsonPath("$.features[0].id").isEqualTo("settings")
      .jsonPath("$.features[1].id").isEqualTo("orders")
  }

  // ── /me ───────────────────────────────────────────────────────────────────────

  @Test
  fun `GET me returns principal name and roles with ROLE_ prefix stripped`() {
    client.mutateWith(mockUser("alice").roles("ops-tribe", "ops-manager"))
      .get().uri("$BASE/me")
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("$.principal").isEqualTo("alice")
      // roles() adds ROLE_ prefix; controller strips it back
      .jsonPath("$.roles.length()").isEqualTo(2)
  }

  @Test
  fun `GET me includes all roles when user holds multiple authorities`() {
    client.mutateWith(mockUser("superuser").roles("ops-tribe", "ops-manager", "tech-manager"))
      .get().uri("$BASE/me")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.principal").isEqualTo("superuser")
      .jsonPath("$.roles.length()").isEqualTo(3)
  }

  // ── helpers ───────────────────────────────────────────────────────────────────

  /**
   * Builds a minimal [AdminFeatureDescriptor] with three operations (Patch / Replace / Delete)
   * for use in feature catalogue assertions.
   */
  private fun feature(
    id: String,
    visibleToRoles: Set<String> = emptySet(),
  ): AdminFeatureDescriptor = object : AdminFeatureDescriptor {
    override val id = id
    override val label = "Settings"
    override val routePrefix = "/settings"
    override val visibleToRoles = visibleToRoles
    override fun operations() = listOf(
      AdminOperation.JsonMergePatch(
        id = "$id.patch",
        label = "Patch",
        pathTemplate = "/admin/settings/patch",
        requiredRoles = setOf("tech-manager"),
      ),
      AdminOperation.Replace(
        id = "$id.replace",
        label = "Replace",
        pathTemplate = "/admin/settings/replace",
        requiredRoles = setOf("tech-manager"),
      ),
      AdminOperation.Delete(
        id = "$id.delete",
        label = "Delete",
        pathTemplate = "/admin/settings/delete",
        requiredRoles = setOf("tech-manager"),
      ),
    )
  }
}
