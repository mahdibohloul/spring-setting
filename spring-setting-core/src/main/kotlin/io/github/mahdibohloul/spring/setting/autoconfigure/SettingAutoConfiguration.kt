package io.github.mahdibohloul.spring.setting.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.reader.SettingReaderImpl
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.repositories.SimpleInMemorySettingRepository
import io.github.mahdibohloul.spring.setting.services.SettingService
import io.github.mahdibohloul.spring.setting.services.SettingServiceImpl
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import io.github.mahdibohloul.spring.setting.writer.SettingWriterImpl
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class SettingAutoConfiguration {
  @ConditionalOnMissingBean(SettingWriter::class)
  @ConditionalOnBean(ObjectMapper::class)
  @Bean
  fun settingWriter(objectMapper: ObjectMapper): SettingWriter = SettingWriterImpl(objectMapper)

  @ConditionalOnMissingBean(SettingReader::class)
  @ConditionalOnBean(ObjectMapper::class)
  @Bean
  fun settingReader(objectMapper: ObjectMapper): SettingReader = SettingReaderImpl(objectMapper)

  @ConditionalOnMissingBean(SettingService::class)
  @ConditionalOnBean(SettingRepository::class)
  @Bean
  fun settingService(settingRepository: SettingRepository): SettingService = SettingServiceImpl(settingRepository)

  @ConditionalOnMissingBean(SettingRepository::class)
  @Bean
  fun settingRepository(): SettingRepository = SimpleInMemorySettingRepository()
}
