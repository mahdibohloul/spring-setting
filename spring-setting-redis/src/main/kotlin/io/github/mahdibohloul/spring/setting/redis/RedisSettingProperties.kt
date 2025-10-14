package io.github.mahdibohloul.spring.setting.redis

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "spring.setting.redis")
data class RedisSettingProperties(
  val prefix: String = "setting:",
  val ttl: Duration = Duration.ofMinutes(5)
)
