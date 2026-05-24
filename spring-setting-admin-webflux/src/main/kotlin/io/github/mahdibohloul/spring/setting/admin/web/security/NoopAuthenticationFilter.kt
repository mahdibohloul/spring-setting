package io.github.mahdibohloul.spring.setting.admin.web.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * WebFilter used when [SettingAdminAuthProperties.Mode.NOOP] is active. Inserts a synthetic
 * [org.springframework.security.core.Authentication] carrying the configured roles into the
 * reactive security context so the downstream per-type ACL still runs against role data —
 * useful for integration tests of the ACL itself.
 */
class NoopAuthenticationFilter(
  private val noop: SettingAdminAuthProperties.Noop,
) : WebFilter {
  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val authorities = noop.roles.map { SimpleGrantedAuthority(it) }
    val authentication = UsernamePasswordAuthenticationToken(noop.syntheticPrincipal, "n/a", authorities)
    val context = SecurityContextImpl(authentication)
    return chain.filter(exchange)
      .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)))
  }
}
