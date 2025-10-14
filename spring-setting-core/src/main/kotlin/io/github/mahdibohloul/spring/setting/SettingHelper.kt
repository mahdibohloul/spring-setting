package io.github.mahdibohloul.spring.setting

import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object SettingHelper {
  fun <T : Setting> getSettingName(type: KClass<T>): String = type.simpleName!!.trim().lowercase()
  fun <T : Setting> createSetting(type: KClass<T>): T = type.createInstance()
}
