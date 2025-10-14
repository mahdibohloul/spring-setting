package io.github.mahdibohloul.spring.setting.redis

import io.github.mahdibohloul.spring.setting.autoconfigure.SettingAutoConfiguration
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveRedisTemplate

@AutoConfiguration(after = [SettingAutoConfiguration::class, RedisAutoConfiguration::class])
@ConditionalOnClass(ReactiveRedisTemplate::class)
@EnableConfigurationProperties(RedisSettingProperties::class)
class RedisSettingAutoConfiguration {

  @Bean("redisSettingRepository")
  @ConditionalOnBean(ReactiveRedisTemplate::class, SettingReader::class, SettingWriter::class)
  fun redisSettingRepository(
    redisTemplate: ReactiveRedisTemplate<String, String>,
    settingProperties: RedisSettingProperties,
    settingReader: SettingReader,
    settingWriter: SettingWriter,
  ): SettingRepository = RedisSettingRepository(
    redisTemplate,
    settingWriter,
    settingReader,
    settingProperties,
  )
}
