package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.autoconfigure.SettingAutoConfiguration
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@AutoConfiguration(after = [SettingAutoConfiguration::class, MongoReactiveAutoConfiguration::class])
@ConditionalOnClass(ReactiveMongoTemplate::class)
@EnableConfigurationProperties(MongoSettingProperties::class)
class MongoSettingAutoConfiguration {

  @Bean("mongoSettingRepository")
  @ConditionalOnBean(ReactiveMongoTemplate::class, SettingReader::class, SettingWriter::class)
  fun mongoSettingRepository(
    mongoTemplate: ReactiveMongoTemplate,
    settingWriter: SettingWriter,
    settingReader: SettingReader,
  ): SettingRepository = MongoSettingRepository(mongoTemplate, settingReader, settingWriter)
}
