package io.github.mahdibohloul.spring.setting.admin.authorization

import reactor.core.publisher.Mono

/**
 * Permission seam for the admin API.
 *
 * The headless `spring-setting-admin` module is transport-agnostic — it does not depend
 * on Spring Security, gRPC interceptors, or any specific identity provider. Implementations
 * of this interface adapt whatever authentication context the host application has
 * (an HTTP filter, a gRPC `Context`, a custom header, …) into a deny / allow decision per
 * admin operation.
 *
 * Implementations should return `Mono.empty()` to allow and a `Mono.error(...)` to deny.
 * The default bean shipped by the module is allow-all so a service can opt-in to
 * permissions by providing its own bean (e.g. via the future `spring-setting-admin-webflux`
 * + `spring-setting-admin-keycloak` modules).
 */
interface SettingAdminAuthorizer {
  enum class Operation { LIST, READ, PATCH, REPLACE, DELETE, HISTORY, REVERT }

  /**
   * Decides whether the current caller may perform [operation] on [typeName].
   *
   * @param typeName the setting type the call targets, or `null` for type-agnostic
   *   operations such as `LIST` and `CLEAR_ALL`.
   * @return `Mono.empty()` to permit; `Mono.error(...)` to deny.
   */
  fun authorize(operation: Operation, typeName: String?): Mono<Void>
}
