package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.admin.transaction.SettingTransactionRetryPolicy
import io.github.mahdibohloul.spring.setting.autoconfigure.SettingAutoConfiguration
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@AutoConfiguration(after = [SettingAutoConfiguration::class, MongoAutoConfiguration::class])
@ConditionalOnClass(ReactiveMongoTemplate::class)
@EnableConfigurationProperties(MongoSettingProperties::class)
class MongoSettingAutoConfiguration {
  @Bean("mongoSettingRepository")
  fun mongoSettingRepository(
    mongoTemplate: ReactiveMongoTemplate,
    settingWriter: SettingWriter,
    settingReader: SettingReader,
  ): SettingRepository = MongoSettingRepository(mongoTemplate, settingReader, settingWriter)

  @Bean
  @ConditionalOnProperty(
    prefix = "spring.setting.mongodb",
    name = ["initialize-collections"],
    havingValue = "true",
    matchIfMissing = true,
  )
  fun mongoSettingCollectionInitializer(
    mongoTemplate: ReactiveMongoTemplate,
    props: MongoSettingProperties,
  ): MongoCollectionInitializer = MongoCollectionInitializer(
    mongoTemplate = mongoTemplate,
    collectionName = props.collectionName,
    entityClass = MongoSettingDocument::class.java,
  )

  /** Registered only when `spring-setting-admin` is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(SettingTransactionRetryPolicy::class)
  class AdminTransactionConfiguration {
    @Bean
    @ConditionalOnMissingBean(SettingTransactionRetryPolicy::class)
    fun mongoTransientTransactionRetryPolicy(): SettingTransactionRetryPolicy = MongoTransientTransactionRetryPolicy()
  }
}
