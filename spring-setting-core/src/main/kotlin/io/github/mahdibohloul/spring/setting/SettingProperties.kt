package io.github.mahdibohloul.spring.setting

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Represents configuration properties for managing settings in the application.
 *
 * The properties are bound to the `spring.setting` namespace in the application's configuration.
 *
 * @property createDefaultInstance Indicates whether to create a default instance of a setting
 * when no existing instance is found. Defaults to `true`.
 */
@ConfigurationProperties("spring.setting")
data class SettingProperties(
  val createDefaultInstance: Boolean = true,
)
