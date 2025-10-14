package io.github.mahdibohloul.spring.setting.writer

import io.github.mahdibohloul.spring.setting.Setting

interface SettingWriter {
  fun <T : Setting> writeSetting(setting: T): String
}
