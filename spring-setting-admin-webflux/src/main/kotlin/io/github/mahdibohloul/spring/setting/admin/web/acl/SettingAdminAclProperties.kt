package io.github.mahdibohloul.spring.setting.admin.web.acl

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Per-operation role mapping for the admin REST surface.
 *
 * Three layers, evaluated in order: profile-specific → defaults → empty (deny).
 *
 * ```yaml
 * spring.setting.admin.acl:
 *   global:
 *     list: [ops-tribe, ops-manager, tech-manager]
 *   defaults:
 *     read:    [ops-tribe, ops-manager, tech-manager]
 *     patch:   [ops-manager, tech-manager]
 *     replace: [ops-manager, tech-manager]
 *     delete:  [tech-manager]
 *   profiles:
 *     DelaySignalPolicySetting:
 *       patch: [ops-manager]   # narrower than defaults
 * ```
 *
 * Profile name = [io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor.aclProfile]
 * which defaults to the type's simple name.
 */
@ConfigurationProperties("spring.setting.admin.acl")
data class SettingAdminAclProperties(
  /** Per-op fallbacks applied when a profile omits an operation. Empty set = deny. */
  val defaults: OperationRoles = OperationRoles(),

  /** Operations that are not bound to a single setting type. */
  val global: GlobalRoles = GlobalRoles(),

  /** Per-profile role lists. */
  val profiles: Map<String, OperationRoles> = emptyMap(),
) {
  data class OperationRoles(
    val read: Set<String> = emptySet(),
    val patch: Set<String> = emptySet(),
    val replace: Set<String> = emptySet(),
    val delete: Set<String> = emptySet(),
  )

  data class GlobalRoles(
    val list: Set<String> = emptySet(),
  )
}
