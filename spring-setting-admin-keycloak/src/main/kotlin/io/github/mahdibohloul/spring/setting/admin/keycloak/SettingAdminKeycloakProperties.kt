package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the Keycloak-flavoured JWT auth on the admin REST surface.
 *
 * Set [issuerUri] to your Keycloak realm URL, e.g. `https://auth.example.com/realms/my-realm`.
 * The JWKS endpoint is derived automatically as `${issuerUri}/protocol/openid-connect/certs`.
 *
 * **Required when enabled:** [issuerUri] has no default and must be an absolute HTTP(S) URL.
 * The application will refuse to start if `enabled=true` and [issuerUri] is blank or not a valid
 * absolute URL — this is intentional fail-fast behaviour to prevent silent JWT misconfigurations.
 */
@ConfigurationProperties("spring.setting.admin.keycloak")
data class SettingAdminKeycloakProperties(
  /** Strict opt-in. Bringing the dep onto the classpath does not auto-activate Keycloak auth. */
  val enabled: Boolean = false,

  /**
   * Keycloak realm URL. **Required when [enabled] is `true`** — no default, must be set explicitly.
   * The JWKS endpoint is derived automatically as `${issuerUri}/protocol/openid-connect/certs`.
   *
   * Example: `https://auth.example.com/realms/my-realm`
   */
  val issuerUri: String = "",

  /**
   * Optional prefix prepended to authority names. Leave empty for plain role names that match
   * the ACL config 1:1 (recommended). Set to "ROLE_" if you prefer the legacy Spring Security
   * convention — the role-based authorizer strips that prefix before checking either way.
   */
  val authorityPrefix: String = "",

  /**
   * Keycloak client-id whose `resource_access.{clientId}.roles` should be included alongside
   * the realm-level roles. Leave empty (default) to read only `realm_access.roles`.
   *
   * Use this when your users carry their meaningful roles as client-scoped roles rather than
   * realm roles. Both sources are merged into a single authority collection — no role is lost.
   *
   * Example: `delivery-admin-panel`
   */
  val clientId: String = "",
) {
  init {
    if (enabled) {
      val uri = issuerUri.trim()
      require(uri.isNotBlank()) {
        "spring.setting.admin.keycloak.issuer-uri must not be blank when " +
          "spring.setting.admin.keycloak.enabled=true. " +
          "Set it to your Keycloak realm URL, e.g. https://auth.example.com/realms/my-realm"
      }
      require(uri.startsWith("http://") || uri.startsWith("https://")) {
        "spring.setting.admin.keycloak.issuer-uri must be an absolute HTTP(S) URL " +
          "(starting with 'http://' or 'https://'), got: '$uri'"
      }
    }
  }
}
