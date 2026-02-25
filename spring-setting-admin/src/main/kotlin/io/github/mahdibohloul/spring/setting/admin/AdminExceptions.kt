package io.github.mahdibohloul.spring.setting.admin

/**
 * Raised by the admin API when a caller references a setting type name that
 * is not registered (no [io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor] bean exists for it).
 */
class UnknownSettingTypeException(
  val typeName: String,
  val available: Collection<String>,
) : RuntimeException("Unknown setting type '$typeName'. Available: $available")
