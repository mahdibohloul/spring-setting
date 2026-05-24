package io.github.mahdibohloul.spring.setting.admin.services

import io.github.mahdibohloul.spring.setting.admin.audit.AuditEntry
import reactor.core.publisher.Mono

/**
 * Headless façade for the admin API. Exposes the operations a UI / CLI / RPC controller
 * needs to manage [io.github.mahdibohloul.spring.setting.Setting] documents over the wire,
 * without dictating a transport.
 *
 * All operations return JSON strings rather than typed `Setting` instances so the wire layer
 * (REST, gRPC, ...) can forward the payload without re-serializing.
 */
interface SettingAdminService {
  /** Returns the sorted list of registered setting type names. */
  fun listTypeNames(): Mono<List<String>>

  /**
   * Returns the JSON-serialized current value of [typeName], or the registered default
   * when no value is persisted.
   */
  fun getAsJson(typeName: String): Mono<String>

  /**
   * Applies an RFC 7396 JSON Merge Patch to the current value of [typeName] (or to the
   * registered default if none is persisted), saves the result, and returns it as JSON.
   */
  fun patch(typeName: String, jsonMergePatch: String): Mono<String>

  /** Replaces the current value of [typeName] with [jsonValue] (full deserialize-then-save). */
  fun replace(typeName: String, jsonValue: String): Mono<String>

  /**
   * Deletes the persisted value for [typeName] across every backing repository, so subsequent
   * reads fall back to the descriptor's default. Completes silently if no value is persisted.
   */
  fun delete(typeName: String): Mono<Void>

  /**
   * Returns the [limit] most-recent audit entries for [typeName], ordered newest-first.
   * [limit] is clamped to `[1, 200]`. Requires the `HISTORY` ACL operation to be permitted.
   *
   * Returns an empty list when audit is disabled (no-op log) or when no entries exist yet.
   */
  fun getHistory(typeName: String, limit: Int): Mono<List<AuditEntry>>

  /**
   * Restores the setting identified by [typeName] to the `previousValue` recorded in [entryId].
   * Internally delegates to [replace], so it re-checks `REPLACE` ACL and records its own audit
   * entry with operation `REPLACE`. The revert operation itself is also ACL-gated by `REVERT`.
   *
   * Returns the JSON of the restored value (same shape as [replace]).
   */
  fun revert(typeName: String, entryId: String): Mono<String>
}
