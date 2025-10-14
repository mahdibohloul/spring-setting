package io.github.mahdibohloul.spring.setting.memory

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.mahdibohloul.spring.setting.autoconfigure.SettingAutoConfiguration
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [SettingAutoConfiguration::class])
@ConditionalOnClass(Caffeine::class)
@EnableConfigurationProperties(MemorySettingProperties::class)
class MemorySettingAutoConfiguration {

  @Bean("memorySettingRepository")
  fun memorySettingRepository(
    properties: MemorySettingProperties,
  ): SettingRepository {
    val cache: AsyncCache<String, Any> = Caffeine.newBuilder()
      .maximumSize(properties.maximumSize)
      .expireAfterWrite(properties.expireAfterWrite)
      .buildAsync()

    return CaffeineSettingRepository(cache)
  }
}
