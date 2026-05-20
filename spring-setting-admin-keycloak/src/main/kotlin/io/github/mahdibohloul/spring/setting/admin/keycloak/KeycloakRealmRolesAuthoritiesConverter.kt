package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Reads roles out of a Keycloak-issued JWT and turns them into [GrantedAuthority] instances
 * Spring Security can use.
 *
 * Keycloak's standard token shape:
 * ```json
 * {
 *   "realm_access": { "roles": ["ops-tribe", "ops-manager"] },
 *   "resource_access": {
 *     "my-client": { "roles": ["my-client-admin", "my-client-viewer"] }
 *   },
 *   ...
 * }
 * ```
 *
 * **Realm roles** (`realm_access.roles`) are always included.
 *
 * **Client roles** (`resource_access.{clientId}.roles`) are included when [clientId] is set.
 * Use this when users carry their meaningful roles as client-scoped roles rather than realm
 * roles — both sources are merged so no authority is lost.
 *
 * If `authorityPrefix` is empty (the default), authority names match the role names exactly
 * so the ACL config can use the same names without any prefix transformation.
 */
class KeycloakRealmRolesAuthoritiesConverter(
  private val authorityPrefix: String = "",
  private val clientId: String = "",
) : Converter<Jwt, Collection<GrantedAuthority>> {

  override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
    val realmRoles = extractRealmRoles(jwt)
    val clientRoles = extractClientRoles(jwt)
    return (realmRoles + clientRoles).map { SimpleGrantedAuthority(authorityPrefix + it) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun extractRealmRoles(jwt: Jwt): List<String> {
    val realmAccess = jwt.claims[REALM_ACCESS_CLAIM] as? Map<*, *> ?: return emptyList()
    return realmAccess[ROLES_KEY] as? List<String> ?: emptyList()
  }

  @Suppress("UNCHECKED_CAST", "detekt.ReturnCount")
  private fun extractClientRoles(jwt: Jwt): List<String> {
    if (clientId.isBlank()) return emptyList()
    val resourceAccess = jwt.claims[RESOURCE_ACCESS_CLAIM] as? Map<*, *> ?: return emptyList()
    val clientAccess = resourceAccess[clientId] as? Map<*, *> ?: return emptyList()
    return clientAccess[ROLES_KEY] as? List<String> ?: emptyList()
  }

  private companion object {
    const val REALM_ACCESS_CLAIM = "realm_access"
    const val RESOURCE_ACCESS_CLAIM = "resource_access"
    const val ROLES_KEY = "roles"
  }
}
