package io.github.mahdibohloul.spring.setting.mongodb

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "#{@environment.getProperty('spring.setting.mongodb.collection-name', 'settings')}")
@TypeAlias("#{@environment.getProperty('spring.setting.mongodb.type-alias', 'MongoSettingDocument')}")
data class MongoSettingDocument(
  @Id val id: String? = null,
  @Indexed(unique = true) val name: String,
  val metadata: String,
  @CreatedDate val createdAt: Instant? = null,
  @LastModifiedDate val updatedAt: Instant? = null,
)
