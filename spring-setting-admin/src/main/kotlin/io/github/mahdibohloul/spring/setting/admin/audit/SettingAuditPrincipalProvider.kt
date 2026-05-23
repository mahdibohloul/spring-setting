package io.github.mahdibohloul.spring.setting.admin.audit

import reactor.core.publisher.Mono

/**
 * SPI for resolving the identity of the caller that is performing a mutating admin operation.
 *
 * This interface is defined in `spring-setting-admin` (which has no Spring Security dependency)
 * so that the headless service layer can record audit entries without coupling to any specific
 * authentication framework. The WebFlux module contributes a Spring-Security-aware implementation
 * (`ReactiveSecurityAuditPrincipalProvider`) that reads the principal from
 * `ReactiveSecurityContextHolder`.
 *
 * The default bean registered by the auto-configuration returns `"anonymous"` unconditionally.
 */
fun interface SettingAuditPrincipalProvider {
  /** Returns the current caller's identifier, e.g. a username or subject claim. Never empty. */
  fun currentPrincipal(): Mono<String>
}
