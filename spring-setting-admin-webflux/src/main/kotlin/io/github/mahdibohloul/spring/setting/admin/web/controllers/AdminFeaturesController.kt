package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureDescriptor
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminFeatureRegistry
import io.github.mahdibohloul.spring.setting.admin.web.features.AdminOperation
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Exposes the catalogue of admin features visible to the current caller. The SPA calls
 * `GET ${basePath}/features` at boot and renders sidebar + operation forms from the result.
 *
 * Filtering is done in two passes:
 *  1. Feature-level: [AdminFeatureDescriptor.visibleToRoles] hides whole entries from users
 *     who shouldn't see them.
 *  2. Operation-level: [AdminOperation.requiredRoles] lets the UI grey out individual buttons.
 *
 * The server still enforces ACL on every actual write, so this filtering is a UX nicety —
 * not a security boundary.
 */
@RestController
@RequestMapping("\${spring.setting.admin.web.base-path:/spring-setting/admin}")
class AdminFeaturesController(
  private val registry: AdminFeatureRegistry,
) {
  @GetMapping("/features")
  fun list(): Mono<FeaturesResponse> = ReactiveSecurityContextHolder.getContext()
    .mapNotNull { it.authentication }
    .map { it.authorities.map { authority -> authority.authority?.removePrefix("ROLE_").orEmpty() }.toSet() }
    .defaultIfEmpty(emptySet())
    .map { grantedRoles -> FeaturesResponse(registry.visibleTo(grantedRoles).map(::toDto)) }

  @GetMapping("/me")
  fun me(): Mono<MeResponse> = ReactiveSecurityContextHolder.getContext()
    .mapNotNull { it.authentication }
    .map { authentication ->
      MeResponse(
        principal = authentication.name,
        roles = authentication.authorities.map { it.authority?.removePrefix("ROLE_").orEmpty() }.toSet(),
      )
    }
    .defaultIfEmpty(MeResponse(principal = "anonymous", roles = emptySet()))

  private fun toDto(descriptor: AdminFeatureDescriptor): FeatureDto = FeatureDto(
    id = descriptor.id,
    label = descriptor.label,
    routePrefix = descriptor.routePrefix,
    visibleToRoles = descriptor.visibleToRoles,
    operations = descriptor.operations().map { op ->
      OperationDto(
        id = op.id,
        label = op.label,
        kind = when (op) {
          is AdminOperation.JsonMergePatch -> "json-merge-patch"
          is AdminOperation.Replace -> "replace"
          is AdminOperation.Delete -> "delete"
          is AdminOperation.Action -> "action"
        },
        httpMethod = op.httpMethod,
        pathTemplate = op.pathTemplate,
        requiredRoles = op.requiredRoles,
      )
    },
  )

  data class FeaturesResponse(val features: List<FeatureDto>)
  data class MeResponse(val principal: String, val roles: Set<String>)
  data class FeatureDto(
    val id: String,
    val label: String,
    val routePrefix: String,
    val visibleToRoles: Set<String>,
    val operations: List<OperationDto>,
  )
  data class OperationDto(
    val id: String,
    val label: String,
    val kind: String,
    val httpMethod: String,
    val pathTemplate: String,
    val requiredRoles: Set<String>,
  )
}
