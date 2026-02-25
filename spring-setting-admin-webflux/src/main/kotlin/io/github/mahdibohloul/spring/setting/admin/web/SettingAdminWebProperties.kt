package io.github.mahdibohloul.spring.setting.admin.web

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Wire-level configuration for the admin REST surface.
 *
 * The whole stack is **opt-in**: a consumer must set `spring.setting.admin.web.enabled=true`
 * before any endpoints get exposed. This keeps the dependency safe to add without immediately
 * widening a service's HTTP surface.
 */
@ConfigurationProperties("spring.setting.admin.web")
data class SettingAdminWebProperties(
  /** Strict opt-in. Adding the dependency does not open endpoints. */
  val enabled: Boolean = false,

  /** Endpoint prefix. Consumers usually override this to fit their existing URL space. */
  val basePath: String = "/spring-setting/admin",

  /** CORS settings for the admin paths (independent of the host application's CORS). */
  val cors: Cors = Cors(),
) {
  data class Cors(
    val allowedOrigins: List<String> = emptyList(),
    val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
    val allowedHeaders: List<String> = listOf("Authorization", "Content-Type"),
    val exposedHeaders: List<String> = emptyList(),
    val allowCredentials: Boolean = false,
    val maxAge: Duration = Duration.ofHours(1),
  )
}
