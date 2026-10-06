package io.github.mahdibohloul.spring.setting.admin

/**
 * Raised by the admin API when a caller references a setting type name that
 * is not registered (no [io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor] bean exists for it).
 */
class UnknownSettingTypeException(
  val typeName: String,
  val available: Collection<String>,
) : RuntimeException("Unknown setting type '$typeName'. Available: $available")

/**
 * Raised by the admin API when a history `before` cursor is not a value that the API returned.
 */
class InvalidHistoryCursorException(
  val cursor: String,
) : IllegalArgumentException("Invalid history cursor '$cursor'")
