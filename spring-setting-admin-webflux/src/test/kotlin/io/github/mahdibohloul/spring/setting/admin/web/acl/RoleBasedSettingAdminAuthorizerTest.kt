package io.github.mahdibohloul.spring.setting.admin.web.acl

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer.Operation
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import reactor.test.StepVerifier
import kotlin.reflect.KClass

class RoleBasedSettingAdminAuthorizerTest {
  data class FooSetting(val x: Int = 0) : Setting
  data class BarSetting(val y: Int = 0) : Setting

  private class Descriptor<T : Setting>(
    override val settingClass: KClass<T>,
    private val factory: () -> T,
    override val aclProfile: String = settingClass.java.simpleName,
  ) : SettingTypeDescriptor<T> {
    override fun default(): T = factory()
  }

  private val registry = SettingTypeRegistry(
    listOf(
      Descriptor(FooSetting::class, factory = { FooSetting() }),
      Descriptor(BarSetting::class, factory = { BarSetting() }),
    ),
  )

  private fun authorizer(acl: SettingAdminAclProperties) = RoleBasedSettingAdminAuthorizer(acl, registry)

  private fun withRoles(vararg roles: String) = ReactiveSecurityContextHolder.withSecurityContext(
    reactor.core.publisher.Mono.just(
      SecurityContextImpl(
        UsernamePasswordAuthenticationToken(
          "tester",
          "n/a",
          roles.map { SimpleGrantedAuthority(it) },
        ),
      ),
    ),
  )

  @Test
  fun `LIST requires a global role`() {
    // given
    val acl = SettingAdminAclProperties(
      global = SettingAdminAclProperties.GlobalRoles(list = setOf("ops-tribe")),
    )
    val auth = authorizer(acl)

    // when / verify — allowed
    StepVerifier.create(auth.authorize(Operation.LIST, null).contextWrite(withRoles("ops-tribe")))
      .verifyComplete()

    // when / verify — denied
    StepVerifier.create(auth.authorize(Operation.LIST, null).contextWrite(withRoles("other")))
      .expectError(AccessDeniedException::class.java)
      .verify()
  }

  @Test
  fun `per-type PATCH uses profile-specific roles when set`() {
    // given
    val acl = SettingAdminAclProperties(
      defaults = SettingAdminAclProperties.OperationRoles(patch = setOf("ops-manager")),
      profiles = mapOf(
        "FooSetting" to SettingAdminAclProperties.OperationRoles(patch = setOf("tech-manager")),
      ),
    )
    val auth = authorizer(acl)

    // when / verify — FooSetting requires tech-manager, ops-manager is rejected
    StepVerifier.create(
      auth.authorize(Operation.PATCH, "FooSetting").contextWrite(withRoles("ops-manager")),
    ).expectError(AccessDeniedException::class.java).verify()

    StepVerifier.create(
      auth.authorize(Operation.PATCH, "FooSetting").contextWrite(withRoles("tech-manager")),
    ).verifyComplete()

    // BarSetting falls back to defaults (ops-manager passes)
    StepVerifier.create(
      auth.authorize(Operation.PATCH, "BarSetting").contextWrite(withRoles("ops-manager")),
    ).verifyComplete()
  }

  @Test
  fun `DELETE requires the configured tech-manager role`() {
    // given — Plan example: ops-tribe view, ops-manager edit, tech-manager delete
    val acl = SettingAdminAclProperties(
      defaults = SettingAdminAclProperties.OperationRoles(
        read = setOf("ops-tribe", "ops-manager", "tech-manager"),
        patch = setOf("ops-manager", "tech-manager"),
        replace = setOf("ops-manager", "tech-manager"),
        delete = setOf("tech-manager"),
      ),
    )
    val auth = authorizer(acl)

    // when / verify
    StepVerifier.create(auth.authorize(Operation.READ, "FooSetting").contextWrite(withRoles("ops-tribe")))
      .verifyComplete()
    StepVerifier.create(auth.authorize(Operation.PATCH, "FooSetting").contextWrite(withRoles("ops-tribe")))
      .expectError(AccessDeniedException::class.java).verify()
    StepVerifier.create(auth.authorize(Operation.PATCH, "FooSetting").contextWrite(withRoles("ops-manager")))
      .verifyComplete()
    StepVerifier.create(auth.authorize(Operation.DELETE, "FooSetting").contextWrite(withRoles("ops-manager")))
      .expectError(AccessDeniedException::class.java).verify()
    StepVerifier.create(auth.authorize(Operation.DELETE, "FooSetting").contextWrite(withRoles("tech-manager")))
      .verifyComplete()
  }

  @Test
  fun `unauthenticated context denies any operation`() {
    // given
    val acl = SettingAdminAclProperties(
      global = SettingAdminAclProperties.GlobalRoles(list = setOf("ops-tribe")),
    )
    val auth = authorizer(acl)

    // when / verify — no security context at all
    StepVerifier.create(auth.authorize(Operation.LIST, null))
      .expectError(AccessDeniedException::class.java)
      .verify()
  }

  @Test
  fun `no configured roles for the operation denies even authenticated callers`() {
    // given — empty defaults, empty profile
    val acl = SettingAdminAclProperties()
    val auth = authorizer(acl)

    // when / verify
    StepVerifier.create(auth.authorize(Operation.PATCH, "FooSetting").contextWrite(withRoles("tech-manager")))
      .expectError(AccessDeniedException::class.java)
      .verify()
  }

  @Test
  fun `ROLE_ prefix from Spring Security authorities is stripped`() {
    // given — Spring Security usually prepends ROLE_ to authority names
    val acl = SettingAdminAclProperties(
      defaults = SettingAdminAclProperties.OperationRoles(patch = setOf("ops-manager")),
    )
    val auth = authorizer(acl)

    // when / verify
    StepVerifier.create(
      auth.authorize(Operation.PATCH, "FooSetting").contextWrite(withRoles("ROLE_ops-manager")),
    ).verifyComplete()
  }
}
