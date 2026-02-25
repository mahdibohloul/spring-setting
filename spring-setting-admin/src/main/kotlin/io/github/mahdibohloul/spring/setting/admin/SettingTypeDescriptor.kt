package io.github.mahdibohloul.spring.setting.admin

import io.github.mahdibohloul.spring.setting.Setting
import kotlin.reflect.KClass

/**
 * Describes a [Setting] type that the admin API can read, patch, replace, and clear.
 *
 * Register one bean per `Setting` subtype. The recommended location is next to the
 * `Setting` definition itself, so the descriptor lives where the type is owned.
 *
 * Adding a new admin-managed setting therefore requires no changes to the admin
 * service, the authorizer, or any controller — the descriptor bean is picked up
 * by [SettingTypeRegistry] at startup.
 *
 * @param T The concrete `Setting` subtype the admin API exposes.
 */
interface SettingTypeDescriptor<T : Setting> {
  /**
   * Wire-visible type name. Defaults to the class simple name (e.g. `DelaySignalPolicySetting`).
   * Override if you need a different identifier on the wire.
   */
  val typeName: String
    get() = settingClass.java.simpleName

  /**
   * The concrete `Setting` class. Used by Jackson for (de)serialization and by the
   * underlying [io.github.mahdibohloul.spring.setting.repositories.SettingRepository] for type-safe access.
   */
  val settingClass: KClass<T>

  /**
   * Returns an instance with all library / business defaults applied.
   * Used by the admin API when patching a setting that has not been persisted yet.
   */
  fun default(): T

  /**
   * ACL profile name this setting belongs to. Defaults to [typeName] so every type starts with
   * its own profile. Override to share a single ACL profile across multiple types — handy when
   * several settings naturally belong to the same configuration domain (e.g. a "delay-policies"
   * profile covering both signal and weekly evaluation settings).
   *
   * The name is resolved against `spring.setting.admin.acl.profiles.<profile-name>.*` properties
   * by the admin-webflux module's role-based authorizer.
   */
  val aclProfile: String
    get() = typeName
}
