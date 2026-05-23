package io.github.mahdibohloul.spring.setting.admin.web.acl

import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer.Operation
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

/**
 * Bridges Spring Security's reactive [org.springframework.security.core.Authentication] into the
 * headless [SettingAdminAuthorizer] SPI exposed by `spring-setting-admin`.
 *
 * Roles for each operation are resolved with this precedence:
 *
 * 1. [Operation.LIST] reads [SettingAdminAclProperties.global]`.list` (it is not bound to a type).
 * 2. Per-type ops ([Operation.READ], [Operation.PATCH], [Operation.REPLACE], [Operation.DELETE])
 *    look up the type's `aclProfile` in [SettingAdminAclProperties.profiles]; the matching
 *    operation list is returned. If the profile is missing or doesn't declare the operation,
 *    [SettingAdminAclProperties.defaults] supplies the fallback.
 * 3. If neither layer yields any role, the request is denied — strict-by-default.
 */
class RoleBasedSettingAdminAuthorizer(
  private val acl: SettingAdminAclProperties,
  private val registry: SettingTypeRegistry,
) : SettingAdminAuthorizer {
  override fun authorize(operation: Operation, typeName: String?): Mono<Void> {
    val required = resolveRequiredRoles(operation, typeName)
    if (required.isEmpty()) {
      return Mono.error(AccessDeniedException("No roles configured for operation $operation"))
    }
    return ReactiveSecurityContextHolder.getContext()
      .mapNotNull { it.authentication }
      .switchIfEmpty(Mono.error(AccessDeniedException("Unauthenticated")))
      .flatMap { authentication ->
        val granted = authentication.authorities.map { it.authority.removePrefix("ROLE_") }.toSet()
        if (granted.intersect(required).isNotEmpty()) {
          Mono.empty()
        } else {
          Mono.error(
            AccessDeniedException(
              "Missing role for $operation; needs one of $required, got $granted",
            ),
          )
        }
      }
  }

  @Suppress("detekt.ReturnCount", "detekt.CyclomaticComplexMethod")
  private fun resolveRequiredRoles(operation: Operation, typeName: String?): Set<String> {
    // LIST is the only type-agnostic operation — it resolves from global roles, not per-type.
    if (operation == Operation.LIST) return acl.global.list
    if (typeName == null) return emptySet()

    val profileName = registry.get(typeName).aclProfile
    val profile = acl.profiles[profileName]

    return when (operation) {
      Operation.READ -> profile?.read?.takeIf { it.isNotEmpty() } ?: acl.defaults.read
      Operation.PATCH -> profile?.patch?.takeIf { it.isNotEmpty() } ?: acl.defaults.patch
      Operation.REPLACE -> profile?.replace?.takeIf { it.isNotEmpty() } ?: acl.defaults.replace
      Operation.DELETE -> profile?.delete?.takeIf { it.isNotEmpty() } ?: acl.defaults.delete
      Operation.HISTORY -> profile?.history?.takeIf { it.isNotEmpty() } ?: acl.defaults.history
      Operation.REVERT -> profile?.revert?.takeIf { it.isNotEmpty() } ?: acl.defaults.revert
      Operation.LIST -> acl.global.list // exhaustive; unreachable (handled above)
    }
  }
}
