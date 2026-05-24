package io.github.mahdibohloul.spring.setting.admin.web.controllers

import io.github.mahdibohloul.spring.setting.admin.audit.AuditEntry
import io.github.mahdibohloul.spring.setting.admin.services.SettingAdminService
import io.github.mahdibohloul.spring.setting.admin.web.controllers.SettingAdminController.SettingPayload
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * REST surface for the audit-log feature.
 *
 * Both endpoints are only registered when `spring.setting.audit.enabled=true` — the bean
 * is guarded by `@ConditionalOnProperty` in
 * [io.github.mahdibohloul.spring.setting.admin.web.autoconfigure.SettingAdminWebAutoConfiguration].
 *
 * Endpoints:
 * - `GET  /settings/{type}/history?limit=20` — returns the [limit] most-recent audit entries.
 * - `POST /settings/{type}/revert/{entryId}` — restores the setting to the `previousValue`
 *   recorded in the given entry; returns the same payload shape as PATCH/PUT.
 */
@RestController
@RequestMapping("\${spring.setting.admin.web.base-path:/spring-setting/admin}/settings")
class SettingAuditController(private val service: SettingAdminService) {

  @GetMapping("/{type}/history")
  fun history(
    @PathVariable type: String,
    @RequestParam(defaultValue = "20") limit: Int,
  ): Mono<HistoryResponse> = service.getHistory(typeName = type, limit = limit)
    .map { HistoryResponse(it.map(::toDto)) }

  @PostMapping("/{type}/revert/{entryId}")
  fun revert(
    @PathVariable type: String,
    @PathVariable entryId: String,
  ): Mono<SettingPayload> = service.revert(typeName = type, entryId = entryId)
    .map { SettingPayload(typeName = type, jsonValue = it) }

  // ── Response DTOs ─────────────────────────────────────────────────────────────

  data class HistoryResponse(val entries: List<AuditEntryDto>)

  data class AuditEntryDto(
    val id: String?,
    val typeName: String,
    val operation: String,
    val previousValue: String?,
    val newValue: String?,
    val changedBy: String,
    val changedAt: Instant,
  )

  private fun toDto(entry: AuditEntry) = AuditEntryDto(
    id = entry.id,
    typeName = entry.typeName,
    operation = entry.operation.name,
    previousValue = entry.previousValue,
    newValue = entry.newValue,
    changedBy = entry.changedBy,
    changedAt = entry.changedAt,
  )
}
