package io.github.mahdibohloul.spring.setting.reader

import tools.jackson.databind.ObjectMapper

class SettingReaderImpl(
  private val objectMapper: ObjectMapper,
) : SettingReader {
  override fun <T : Any> readSetting(
    input: String,
    settingClass: Class<T>,
  ): T = objectMapper.readValue(input, settingClass)
}
