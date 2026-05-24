package io.github.mahdibohloul.spring.setting.admin

import io.github.mahdibohloul.spring.setting.Setting

/**
 * Discovers and indexes all [SettingTypeDescriptor] beans known to the application.
 *
 * The registry is the open-closed seam for the admin API: a new setting type is
 * onboarded by registering a `SettingTypeDescriptor` bean — no admin code changes
 * are required.
 */
class SettingTypeRegistry(
  descriptors: List<SettingTypeDescriptor<out Setting>>,
) {
  private val byTypeName: Map<String, SettingTypeDescriptor<out Setting>> =
    descriptors.associateBy { it.typeName }

  /** All registered type names, sorted alphabetically for stable wire output. */
  fun listTypeNames(): List<String> = byTypeName.keys.sorted()

  /**
   * Looks up the descriptor for [typeName]. Throws [UnknownSettingTypeException]
   * when no descriptor is registered — the admin service translates this into the
   * appropriate transport-level error.
   */
  fun get(typeName: String): SettingTypeDescriptor<out Setting> = byTypeName[typeName]
    ?: throw UnknownSettingTypeException(typeName, byTypeName.keys)

  /** Every registered descriptor — used by bulk operations like `clearAll`. */
  fun describedTypes(): Collection<SettingTypeDescriptor<out Setting>> = byTypeName.values
}
