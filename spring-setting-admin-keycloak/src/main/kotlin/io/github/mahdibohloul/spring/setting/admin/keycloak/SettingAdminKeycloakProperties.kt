package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the Keycloak-flavoured JWT auth on the admin REST surface.
 *
 * Set [issuerUri] to your Keycloak realm URL, e.g. `https://auth.example.com/realms/my-realm`.
 * The JWKS endpoint is derived automatically as `${issuerUri}/protocol/openid-connect/certs`.
 */
@ConfigurationProperties("spring.setting.admin.keycloak")
data class SettingAdminKeycloakProperties(
  /** Strict opt-in. Bringing the dep onto the classpath does not auto-activate Keycloak auth. */
  val enabled: Boolean = false,

  /** Keycloak realm URL. The JWKS endpoint is derived as `${issuerUri}/protocol/openid-connect/certs`. */
  val issuerUri: String = "",

  /**
   * Optional prefix prepended to authority names. Leave empty for plain role names that match
   * the ACL config 1:1 (recommended). Set to "ROLE_" if you prefer the legacy Spring Security
   * convention — the role-based authorizer strips that prefix before checking either way.
   */
  val authorityPrefix: String = "",
)
