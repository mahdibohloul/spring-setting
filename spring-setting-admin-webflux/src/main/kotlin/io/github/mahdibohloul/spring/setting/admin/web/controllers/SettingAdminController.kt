package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * REST surface for the headless [SettingAdminService]. One verb per operation.
 *
 * Mount path is configurable via `spring.setting.admin.web.base-path`, defaulting to
 * `/spring-setting/admin`. The controller deliberately knows nothing about authorization —
 * that is enforced by the service (via `SettingAdminAuthorizer`) and the surrounding
 * Spring Security filter chain.
 */
@RestController
@RequestMapping("\${spring.setting.admin.web.base-path:/spring-setting/admin}/settings")
class SettingAdminController(
  private val service: SettingAdminService,
) {
  @GetMapping
  fun list(): Mono<TypeNamesResponse> = service.listTypeNames().map { TypeNamesResponse(it) }

  @GetMapping("/{type}")
  fun get(@PathVariable type: String): Mono<SettingPayload> = service.getAsJson(type).map { SettingPayload(type, it) }

  @PatchMapping("/{type}", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun patch(
    @PathVariable type: String,
    @RequestBody body: PatchBody,
  ): Mono<SettingPayload> = service.patch(type, body.jsonMergePatch).map { SettingPayload(type, it) }

  @PutMapping("/{type}", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun replace(
    @PathVariable type: String,
    @RequestBody body: ReplaceBody,
  ): Mono<SettingPayload> = service.replace(type, body.jsonValue).map { SettingPayload(type, it) }

  @DeleteMapping("/{type}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun delete(@PathVariable type: String): Mono<Void> = service.delete(type)

  data class TypeNamesResponse(val typeNames: List<String>)
  data class SettingPayload(val typeName: String, val jsonValue: String)
  data class PatchBody(val jsonMergePatch: String)
  data class ReplaceBody(val jsonValue: String)
}
