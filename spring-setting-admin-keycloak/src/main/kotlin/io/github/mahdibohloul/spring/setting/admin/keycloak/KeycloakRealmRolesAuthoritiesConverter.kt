package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Reads realm-level roles out of a Keycloak-issued JWT's `realm_access.roles` claim and turns
 * them into [GrantedAuthority] instances Spring Security can use.
 *
 * Keycloak's standard token shape:
 * ```json
 * {
 *   "realm_access": { "roles": ["ops-tribe", "ops-manager"] },
 *   "resource_access": { ... },   // client-scoped roles — not read here
 *   ...
 * }
 * ```
 *
 * If `authorityPrefix` is empty (the default), authority names match the roles exactly so the
 * ACL config can use the same names. Set it to "ROLE_" only if your downstream code expects the
 * legacy Spring Security prefix.
 */
class KeycloakRealmRolesAuthoritiesConverter(
  private val authorityPrefix: String = "",
) : Converter<Jwt, Collection<GrantedAuthority>> {

  @Suppress("detekt.ReturnCount")
  override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
    val realmAccess = jwt.claims[REALM_ACCESS_CLAIM] as? Map<*, *> ?: return emptyList()

    @Suppress("UNCHECKED_CAST")
    val roles = realmAccess[ROLES_KEY] as? List<String> ?: return emptyList()
    return roles.map { SimpleGrantedAuthority(authorityPrefix + it) }
  }

  private companion object {
    const val REALM_ACCESS_CLAIM = "realm_access"
    const val ROLES_KEY = "roles"
  }
}
