package io.github.mahdibohloul.spring.setting.reader

interface SettingReader {
  fun <T : Any> readSetting(input: String, settingClass: Class<T>): T
}
