package io.github.mahdibohloul.spring.setting.writer

import io.github.mahdibohloul.spring.setting.Setting
import tools.jackson.databind.ObjectMapper

class SettingWriterImpl(
  private val objectMapper: ObjectMapper,
) : SettingWriter {
  override fun <T : Setting> writeSetting(setting: T): String = objectMapper.writeValueAsString(setting)
}
