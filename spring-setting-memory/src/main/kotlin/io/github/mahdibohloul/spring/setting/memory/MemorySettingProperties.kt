package io.github.mahdibohloul.spring.setting.memory

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "spring.setting.memory")
data class MemorySettingProperties(
  val maximumSize: Long = 10_000,
  val expireAfterWrite: Duration = Duration.ofMinutes(1),
)
