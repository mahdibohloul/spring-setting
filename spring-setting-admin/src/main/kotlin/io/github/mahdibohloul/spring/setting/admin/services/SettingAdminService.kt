package io.github.mahdibohloul.spring.setting.admin.services

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
}
