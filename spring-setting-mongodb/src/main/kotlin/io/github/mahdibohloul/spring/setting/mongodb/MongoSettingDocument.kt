package io.github.mahdibohloul.spring.setting.mongodb

import java.time.Instant
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "settings")
data class MongoSettingDocument(
  @Id val id: String? = null,
  @Indexed(unique = true) val key: String,
  val metadata: String,
  @CreatedDate val createdAt: Instant? = null,
  @LastModifiedDate val updatedAt: Instant? = null,
)
