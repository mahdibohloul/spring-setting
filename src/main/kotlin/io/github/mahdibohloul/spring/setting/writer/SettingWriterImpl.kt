package io.github.mahdibohloul.spring.setting.writer

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.Setting

class SettingWriterImpl(
  private val objectMapper: ObjectMapper,
) : SettingWriter {
  override fun <T : Setting> writeSetting(setting: T): String = objectMapper.writeValueAsString(setting)
}
