package io.github.mahdibohloul.spring.setting.mongodb

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("spring.setting.mongodb")
data class MongoSettingProperties(
  val collectionName: String = "settings",
)
