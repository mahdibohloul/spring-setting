package io.github.mahdibohloul.spring.setting.admin.web.audit

import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditPrincipalProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono

/**
 * Resolves the current principal from Spring Security's reactive security context.
 *
 * For JWT-based authentication (e.g. Keycloak), the `preferred_username` claim is used as the
 * principal name so audit entries contain a human-readable identifier rather than the opaque `sub`
 * UUID that `Authentication.getName()` returns by default.  Falls back to `Authentication.getName()`
 * (i.e. the `sub` claim) when `preferred_username` is absent from the token.
 *
 * For non-JWT authentication (HTTP Basic, NOOP) `Authentication.getName()` is used directly.
 *
 * Falls back to `"anonymous"` when no authentication is present in the context.
 *
 * The [JwtAuthenticationToken] check is wrapped in a [NoClassDefFoundError] catch so the provider
 * is safe even when `spring-security-oauth2-resource-server` is not on the runtime classpath (e.g.
 * when using NOOP or Basic auth without the OAuth2 resource server dependency).
 */
class ReactiveSecurityAuditPrincipalProvider : SettingAuditPrincipalProvider {
  override fun currentPrincipal(): Mono<String> = ReactiveSecurityContextHolder.getContext()
    .flatMap { ctx ->
      val auth = ctx.authentication ?: return@flatMap Mono.empty()
      Mono.just(resolveUsername(auth))
    }
    .defaultIfEmpty("anonymous")

  private fun resolveUsername(auth: Authentication): String = try {
    if (auth is JwtAuthenticationToken) {
      auth.token.getClaimAsString("preferred_username") ?: auth.name
    } else {
      auth.name
    }
  } catch (_: NoClassDefFoundError) {
    // spring-security-oauth2-resource-server not on runtime classpath — fall back to sub / username
    auth.name
  }
}
