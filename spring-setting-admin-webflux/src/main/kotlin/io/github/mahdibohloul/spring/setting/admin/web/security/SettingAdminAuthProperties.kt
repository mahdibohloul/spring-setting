package io.github.mahdibohloul.spring.setting.admin.web.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Selects how the admin REST endpoints authenticate callers.
 *
 * - [Mode.KEYCLOAK] (default) — standard OAuth2 JWT bearer auth. Requires a `ReactiveJwtDecoder`
 *   bean on the context, normally provided by `spring-setting-admin-keycloak`. Fails fast at
 *   startup if the decoder is missing so a misconfigured prod deployment cannot accidentally
 *   serve unauthenticated traffic.
 * - [Mode.BASIC] — HTTP Basic against a hardcoded user from these properties. For backyard
 *   dev / QA demos. **Never enable in production.**
 * - [Mode.NOOP] — skip authentication; a `WebFilter` inserts a synthetic `Authentication`
 *   carrying [Noop.roles] so the per-type ACL still runs. Intended for tests and laptop dev.
 *
 * In every mode the per-type per-operation ACL from
 * `spring.setting.admin.acl.profiles.*` still applies.
 */
@ConfigurationProperties("spring.setting.admin.web.auth")
data class SettingAdminAuthProperties(
  val mode: Mode = Mode.KEYCLOAK,
  val basic: Basic = Basic(),
  val noop: Noop = Noop(),
) {
  enum class Mode { KEYCLOAK, BASIC, NOOP }

  data class Basic(
    val username: String = "admin",
    val password: String = "admin",
    /** Roles granted to the hardcoded basic-auth user. */
    val roles: Set<String> = setOf("ops-tribe", "ops-manager", "tech-manager"),
  )

  data class Noop(
    /** Roles attached to the synthetic Authentication in NOOP mode. */
    val roles: Set<String> = setOf("ops-tribe", "ops-manager", "tech-manager"),
    val syntheticPrincipal: String = "dev-noop",
  )
}
