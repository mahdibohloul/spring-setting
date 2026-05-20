package io.github.mahdibohloul.spring.setting.admin.web.security

import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [NoopAuthenticationFilter] — no Spring context required.
 *
 * The filter is used in NOOP auth mode and inserts a synthetic [UsernamePasswordAuthenticationToken]
 * into the reactive security context so the per-type ACL can still be exercised in tests and
 * local dev without a real identity provider.
 */
class NoopAuthenticationFilterTest {

  private fun exchange(): MockServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build())

  // ── Authentication injection ──────────────────────────────────────────────────

  @Test
  fun `filter injects synthetic Authentication with configured roles and principal`() {
    val noop = SettingAdminAuthProperties.Noop(
      roles = setOf("ops-tribe", "ops-manager"),
      syntheticPrincipal = "dev-noop",
    )
    val filter = NoopAuthenticationFilter(noop)
    val captured = AtomicReference<SecurityContext>()

    val chain = WebFilterChain { _ ->
      ReactiveSecurityContextHolder.getContext()
        .doOnNext { captured.set(it) }
        .then()
    }

    StepVerifier.create(filter.filter(exchange(), chain))
      .verifyComplete()

    val auth = captured.get()?.authentication
    assertNotNull(auth, "Authentication must be present in the security context")
    assertTrue(auth is UsernamePasswordAuthenticationToken)
    assertEquals("dev-noop", auth.name)
    assertEquals(
      setOf("ops-tribe", "ops-manager"),
      auth.authorities.map { it.authority }.toSet(),
    )
    assertTrue(auth.isAuthenticated)
  }

  @Test
  fun `filter injects empty authority set when no roles are configured`() {
    val noop = SettingAdminAuthProperties.Noop(roles = emptySet(), syntheticPrincipal = "nobody")
    val filter = NoopAuthenticationFilter(noop)
    val captured = AtomicReference<SecurityContext>()

    val chain = WebFilterChain { _ ->
      ReactiveSecurityContextHolder.getContext()
        .doOnNext { captured.set(it) }
        .then()
    }

    StepVerifier.create(filter.filter(exchange(), chain))
      .verifyComplete()

    assertEquals(emptySet(), captured.get()?.authentication?.authorities?.map { it.authority }?.toSet())
  }

  // ── Chain delegation ──────────────────────────────────────────────────────────

  @Test
  fun `filter passes the exchange to the downstream chain`() {
    val filter = NoopAuthenticationFilter(SettingAdminAuthProperties.Noop())
    var chainInvoked = false

    val chain = WebFilterChain { _ ->
      chainInvoked = true
      Mono.empty()
    }

    StepVerifier.create(filter.filter(exchange(), chain))
      .verifyComplete()

    assertTrue(chainInvoked, "Filter must delegate to the next WebFilter")
  }

  @Test
  fun `filter completes the Mono returned by the chain`() {
    val filter = NoopAuthenticationFilter(SettingAdminAuthProperties.Noop())
    val chain = WebFilterChain { _ -> Mono.empty() }

    // verifyComplete() will time out if filter swallows the completion signal
    StepVerifier.create(filter.filter(exchange(), chain))
      .verifyComplete()
  }
}
