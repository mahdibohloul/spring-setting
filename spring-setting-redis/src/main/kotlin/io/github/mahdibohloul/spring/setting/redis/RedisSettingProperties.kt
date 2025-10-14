package io.github.mahdibohloul.spring.setting.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "spring.setting.redis")
data class RedisSettingProperties(
  val prefix: String = "setting:",
  val ttl: Duration = Duration.ofMinutes(5),
)
