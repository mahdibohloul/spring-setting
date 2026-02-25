package io.github.mahdibohloul.spring.setting.admin.keycloak

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import reactor.core.publisher.Mono

/**
 * Auto-configuration that wires Keycloak as the OAuth2 Resource Server backing the admin REST.
 *
 * Activates only when both of the following hold:
 * - The admin webflux module's classes are on the classpath (`NimbusReactiveJwtDecoder`).
 * - `spring.setting.admin.keycloak.enabled=true` is set — strict opt-in so adding the dependency
 *   alone does not change runtime behaviour.
 *
 * Provides:
 * - A `ReactiveJwtDecoder` against the JWKS endpoint of the configured realm.
 * - A `Converter<Jwt, Mono<AbstractAuthenticationToken>>` that extracts realm-level roles using
 *   [KeycloakRealmRolesAuthoritiesConverter].
 *
 * Consumers can replace either bean by registering their own `@Bean` — the
 * `@ConditionalOnMissingBean` guards mean a project-specific decoder always wins.
 */
@AutoConfiguration
@ConditionalOnClass(NimbusReactiveJwtDecoder::class)
@ConditionalOnProperty(prefix = "spring.setting.admin.keycloak", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(SettingAdminKeycloakProperties::class)
class KeycloakJwtConfiguration {

  @Bean
  @ConditionalOnMissingBean(ReactiveJwtDecoder::class)
  fun keycloakJwtDecoder(properties: SettingAdminKeycloakProperties): ReactiveJwtDecoder {
    val jwkSetUri = properties.issuerUri.trimEnd('/') + "/protocol/openid-connect/certs"
    return NimbusReactiveJwtDecoder
      .withJwkSetUri(jwkSetUri)
      .jwsAlgorithm(SignatureAlgorithm.RS256)
      .build()
  }

  @Bean
  @ConditionalOnMissingBean(name = ["keycloakJwtAuthenticationConverter"])
  fun keycloakJwtAuthenticationConverter(
    properties: SettingAdminKeycloakProperties,
  ): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
    val rolesConverter = KeycloakRealmRolesAuthoritiesConverter(properties.authorityPrefix)
    val jwtConverter = JwtAuthenticationConverter()
    jwtConverter.setJwtGrantedAuthoritiesConverter(rolesConverter)
    return ReactiveJwtAuthenticationConverterAdapter(jwtConverter)
  }
}
