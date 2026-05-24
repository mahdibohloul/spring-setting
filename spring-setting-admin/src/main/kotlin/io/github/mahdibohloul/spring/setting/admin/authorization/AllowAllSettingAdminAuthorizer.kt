package io.github.mahdibohloul.spring.setting.admin.authorization

import reactor.core.publisher.Mono

/**
 * Default `SettingAdminAuthorizer` registered when no other bean is present.
 * Permits every operation — appropriate for development/simulation profiles only.
 */
class AllowAllSettingAdminAuthorizer : SettingAdminAuthorizer {
  override fun authorize(operation: SettingAdminAuthorizer.Operation, typeName: String?): Mono<Void> = Mono.empty()
}
