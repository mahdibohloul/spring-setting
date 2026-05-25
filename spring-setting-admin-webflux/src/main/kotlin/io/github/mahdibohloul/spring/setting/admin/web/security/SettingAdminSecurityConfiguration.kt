package io.github.mahdibohloul.spring.setting.admin.web.security

import io.github.mahdibohloul.spring.setting.admin.web.SettingAdminWebProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

/**
 * Spring Security WebFlux wiring for the admin REST surface.
 *
 * The filter chain is **scoped** via `securityMatcher(basePath + slash-double-star)` so a host
 * application can keep its own Spring Security config on every other path — they never conflict.
 *
 * Mode selection (see [SettingAdminAuthProperties.Mode]):
 * - `KEYCLOAK` — `oauth2ResourceServer().jwt()`; needs a `ReactiveJwtDecoder` bean on the context.
 * - `BASIC` — `httpBasic()` against the in-memory hardcoded user. **Non-prod only.**
 * - `NOOP` — auth bypassed; [NoopAuthenticationFilter] inserts a synthetic Authentication so the
 *   downstream [io.github.mahdibohloul.spring.setting.admin.web.acl.RoleBasedSettingAdminAuthorizer]
 *   still evaluates roles.
 *
 * The chain is given an explicit @Order so consumer applications can layer their own chains
 * around the admin one (lower numbers run first; `@Order(50)` keeps this above the default
 * `@Order(100)` chain Spring Boot creates).
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.setting.admin.web", name = ["enabled"], havingValue = "true")
class SettingAdminSecurityConfiguration(private val objectMapper: ObjectMapper) {

  @Bean
  @Order(ADMIN_FILTER_CHAIN_ORDER)
  fun settingAdminSecurityWebFilterChain(
    http: ServerHttpSecurity,
    properties: SettingAdminWebProperties,
    auth: SettingAdminAuthProperties,
    jwtConverterProvider: ObjectProvider<Converter<Jwt, Mono<AbstractAuthenticationToken>>>,
  ): SecurityWebFilterChain {
    val matcher = PathPatternParserServerWebExchangeMatcher("${properties.basePath}/**")
    val base = http.securityMatcher(matcher)
      .csrf { it.disable() }
      .formLogin { it.disable() }
      .cors { it.configurationSource(corsSource(properties)) }
      .exceptionHandling { spec ->
        spec.accessDeniedHandler { exchange, ex ->
          writeJsonError(exchange, HttpStatus.FORBIDDEN, "forbidden", ex)
        }
        spec.authenticationEntryPoint { exchange, ex ->
          writeJsonError(exchange, HttpStatus.UNAUTHORIZED, "unauthenticated", ex)
        }
      }

    return when (auth.mode) {
      SettingAdminAuthProperties.Mode.KEYCLOAK -> {
        // ReactiveJwtDecoder is auto-picked from context by the jwt() DSL.
        // The authentication converter is NOT auto-picked — must be wired explicitly;
        // otherwise the default ReactiveJwtAuthenticationConverter runs and produces
        // only SCOPE_* authorities from the scope claim, never the Keycloak role authorities.
        val jwtConverter = jwtConverterProvider.ifAvailable
        base
          .httpBasic { it.disable() }
          .oauth2ResourceServer { rs ->
            rs.jwt { jwt -> jwtConverter?.let { jwt.jwtAuthenticationConverter(it) } }
          }
          .authorizeExchange { it.anyExchange().authenticated() }
          .build()
      }

      SettingAdminAuthProperties.Mode.BASIC ->
        base
          // MapReactiveUserDetailsService bean below provides the in-memory user
          .httpBasic { }
          .authorizeExchange { it.anyExchange().authenticated() }
          .build()

      SettingAdminAuthProperties.Mode.NOOP ->
        base
          .httpBasic { it.disable() }
          .authorizeExchange { it.anyExchange().permitAll() }
          .addFilterAt(NoopAuthenticationFilter(auth.noop), SecurityWebFiltersOrder.AUTHENTICATION)
          .build()
    }
  }

  private fun writeJsonError(
    exchange: ServerWebExchange,
    status: HttpStatus,
    code: String,
    ex: Throwable,
  ): Mono<Void> {
    val response = exchange.response
    response.statusCode = status
    response.headers.contentType = MediaType.APPLICATION_JSON
    val payload = objectMapper.writeValueAsBytes(
      mapOf("code" to code, "message" to (ex.message ?: code)),
    )
    return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)))
  }

  /**
   * Suppresses Spring Boot's UserDetailsServiceAutoConfiguration warning in reactive (WebFlux)
   * applications. Registering a no-op UserDetailsService here satisfies the @ConditionalOnMissingBean
   * guard and prevents the auto-configuration from creating an in-memory user with a
   * logged random password.
   */
  @Bean
  @ConditionalOnMissingBean(UserDetailsService::class)
  fun noopUserDetailsService(): UserDetailsService = UserDetailsService { throw UsernameNotFoundException(it) }

  /**
   * Hardcoded in-memory user for [SettingAdminAuthProperties.Mode.BASIC].
   * Wires only when BASIC mode is selected — production builds with KEYCLOAK never instantiate this.
   *
   * `{noop}` is Spring Security's "plain-text" encoder. Acceptable here because BASIC is for
   * non-prod use only; the safety net is that production helm sets `mode=KEYCLOAK`.
   */
  @Bean
  @ConditionalOnProperty(prefix = "spring.setting.admin.web.auth", name = ["mode"], havingValue = "BASIC")
  fun settingAdminBasicUserDetailsService(auth: SettingAdminAuthProperties): MapReactiveUserDetailsService {
    val user = User.withUsername(auth.basic.username)
      .password("{noop}${auth.basic.password}")
      .authorities(auth.basic.roles.map { org.springframework.security.core.authority.SimpleGrantedAuthority(it) })
      .build()
    return MapReactiveUserDetailsService(user)
  }

  private fun corsSource(properties: SettingAdminWebProperties): CorsConfigurationSource {
    val cors = properties.cors
    val config = CorsConfiguration().apply {
      allowedOrigins = cors.allowedOrigins.takeIf { it.isNotEmpty() }
      allowedMethods = cors.allowedMethods
      allowedHeaders = cors.allowedHeaders
      exposedHeaders = cors.exposedHeaders.takeIf { it.isNotEmpty() }
      allowCredentials = cors.allowCredentials
      maxAge = cors.maxAge.seconds
    }
    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration(properties.basePath + "/**", config)
    return source
  }

  companion object {
    /**
     * Order of the admin `SecurityWebFilterChain`.
     *
     * In Spring Security WebFlux, when several `SecurityWebFilterChain` beans exist they are sorted
     * by `@Order` (ascending) and the first one whose `securityMatcher` matches a request handles it.
     * Our chain is path-scoped to the configured base path, so it must be evaluated *before* any
     * broad / catch-all chain a host application may declare (those usually sit at
     * `Ordered.LOWEST_PRECEDENCE` when left unannotated).
     *
     * 50 is a deliberately low-but-not-zero value: it places the admin chain early enough to win
     * over a default host chain, while leaving headroom on both sides — a host can register an
     * even-higher-priority chain at 0-49 (e.g. to intercept a health path first) or a
     * lower-priority one at 51+ without colliding with this number.
     */
    private const val ADMIN_FILTER_CHAIN_ORDER = 50
  }
}
