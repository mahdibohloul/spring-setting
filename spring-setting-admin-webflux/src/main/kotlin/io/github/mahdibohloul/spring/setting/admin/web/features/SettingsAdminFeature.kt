package io.github.mahdibohloul.spring.setting.admin.web.features

import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.web.SettingAdminWebProperties
import io.github.mahdibohloul.spring.setting.admin.web.acl.SettingAdminAclProperties

/**
 * Built-in feature advertising the headless setting CRUD as four operations:
 * READ / PATCH / REPLACE / DELETE per registered type. The SPA renders one sidebar entry
 * named "Settings" with a sub-list of every type the caller is allowed to read.
 *
 * Operation [AdminOperation.requiredRoles] is sourced from [SettingAdminAclProperties] so
 * the UI can pre-emptively hide actions the caller can't perform — though the final
 * enforcement still happens server-side in `RoleBasedSettingAdminAuthorizer`.
 */
class SettingsAdminFeature(
  private val typeRegistry: SettingTypeRegistry,
  private val acl: SettingAdminAclProperties,
  private val webProperties: SettingAdminWebProperties,
) : AdminFeatureDescriptor {
  override val id: String = "settings"
  override val label: String = "Settings"
  override val routePrefix: String = "/settings"
  override val visibleToRoles: Set<String> = acl.global.list

  override fun operations(): List<AdminOperation> {
    val basePath = webProperties.basePath.trimEnd('/')
    val ops = mutableListOf<AdminOperation>()
    typeRegistry.describedTypes().forEach { descriptor ->
      val profile = acl.profiles[descriptor.aclProfile]
      val typeName = descriptor.typeName
      val pathTemplate = "$basePath/settings/$typeName"
      ops += AdminOperation.JsonMergePatch(
        id = "settings.$typeName.patch",
        label = "Patch $typeName",
        pathTemplate = pathTemplate,
        requiredRoles = (profile?.patch?.takeIf { it.isNotEmpty() } ?: acl.defaults.patch),
      )
      ops += AdminOperation.Replace(
        id = "settings.$typeName.replace",
        label = "Replace $typeName",
        pathTemplate = pathTemplate,
        requiredRoles = (profile?.replace?.takeIf { it.isNotEmpty() } ?: acl.defaults.replace),
      )
      ops += AdminOperation.Delete(
        id = "settings.$typeName.delete",
        label = "Reset $typeName to default",
        pathTemplate = pathTemplate,
        requiredRoles = (profile?.delete?.takeIf { it.isNotEmpty() } ?: acl.defaults.delete),
      )
    }
    return ops
  }
}
