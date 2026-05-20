package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingAdminKeycloakPropertiesTest {

  @Test
  fun `enabled=false with blank issuerUri is allowed`() {
    // should not throw — validation only fires when enabled=true
    val props = SettingAdminKeycloakProperties(enabled = false, issuerUri = "")
    assertEquals(false, props.enabled)
  }

  @Test
  fun `enabled=true with valid https issuerUri is accepted`() {
    val props = SettingAdminKeycloakProperties(
      enabled = true,
      issuerUri = "https://auth.example.com/realms/my-realm",
    )
    assertEquals("https://auth.example.com/realms/my-realm", props.issuerUri)
  }

  @Test
  fun `enabled=true with valid http issuerUri is accepted (local dev)`() {
    val props = SettingAdminKeycloakProperties(
      enabled = true,
      issuerUri = "http://localhost:8080/realms/dev",
    )
    assertEquals("http://localhost:8080/realms/dev", props.issuerUri)
  }

  @Test
  fun `enabled=true with blank issuerUri throws IllegalArgumentException`() {
    val ex = assertFailsWith<IllegalArgumentException> {
      SettingAdminKeycloakProperties(enabled = true, issuerUri = "")
    }
    assert(ex.message!!.contains("spring.setting.admin.keycloak.issuer-uri must not be blank")) {
      "Expected message about blank issuer-uri, got: ${ex.message}"
    }
  }

  @Test
  fun `enabled=true with whitespace-only issuerUri throws IllegalArgumentException`() {
    val ex = assertFailsWith<IllegalArgumentException> {
      SettingAdminKeycloakProperties(enabled = true, issuerUri = "   ")
    }
    assert(ex.message!!.contains("spring.setting.admin.keycloak.issuer-uri must not be blank")) {
      "Expected message about blank issuer-uri, got: ${ex.message}"
    }
  }

  @Test
  fun `enabled=true with relative path issuerUri throws IllegalArgumentException`() {
    val ex = assertFailsWith<IllegalArgumentException> {
      SettingAdminKeycloakProperties(enabled = true, issuerUri = "/realms/my-realm")
    }
    assert(ex.message!!.contains("must be an absolute HTTP(S) URL")) {
      "Expected message about non-HTTP URL, got: ${ex.message}"
    }
  }

  @Test
  fun `enabled=true with unresolved placeholder throws IllegalArgumentException`() {
    // Catches the case where an environment variable was not substituted
    val ex = assertFailsWith<IllegalArgumentException> {
      SettingAdminKeycloakProperties(enabled = true, issuerUri = "\${KEYCLOAK_ISSUER_URI}")
    }
    assert(ex.message!!.contains("must be an absolute HTTP(S) URL")) {
      "Expected message about non-HTTP URL, got: ${ex.message}"
    }
  }
}
