package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeycloakRealmRolesAuthoritiesConverterTest {

  private fun jwt(claims: Map<String, Any?>): Jwt = Jwt.withTokenValue("dummy")
    .header("alg", "RS256")
    .claims { it.putAll(claims) }
    .issuedAt(Instant.now())
    .expiresAt(Instant.now().plusSeconds(60))
    .build()

  @Test
  fun `extracts realm roles into authorities without any prefix by default`() {
    // given
    val converter = KeycloakRealmRolesAuthoritiesConverter()
    val token = jwt(mapOf("realm_access" to mapOf("roles" to listOf("ops-tribe", "ops-manager"))))

    // when
    val authorities = converter.convert(token).map { it.authority }.toSet()

    // verify
    assertEquals(setOf("ops-tribe", "ops-manager"), authorities)
  }

  @Test
  fun `applies optional authority prefix when configured`() {
    // given
    val converter = KeycloakRealmRolesAuthoritiesConverter(authorityPrefix = "ROLE_")
    val token = jwt(mapOf("realm_access" to mapOf("roles" to listOf("tech-manager"))))

    // when
    val authorities = converter.convert(token).map { it.authority }.toSet()

    // verify
    assertEquals(setOf("ROLE_tech-manager"), authorities)
  }

  @Test
  fun `returns empty when realm_access claim is missing`() {
    // given
    val converter = KeycloakRealmRolesAuthoritiesConverter()
    val token = jwt(mapOf("scope" to "openid"))

    // when
    val authorities = converter.convert(token)

    // verify
    assertTrue(authorities.isEmpty())
  }

  @Test
  fun `returns empty when realm_access exists but roles is missing`() {
    // given
    val converter = KeycloakRealmRolesAuthoritiesConverter()
    val token = jwt(mapOf("realm_access" to mapOf("other" to "value")))

    // when
    val authorities = converter.convert(token)

    // verify
    assertTrue(authorities.isEmpty())
  }

  @Test
  fun `returns empty when realm_access has unexpected shape`() {
    // given — defensive: malformed claim should not blow up
    val converter = KeycloakRealmRolesAuthoritiesConverter()
    val token = jwt(mapOf("realm_access" to "not-an-object"))

    // when
    val authorities = converter.convert(token)

    // verify
    assertTrue(authorities.isEmpty())
  }
}
