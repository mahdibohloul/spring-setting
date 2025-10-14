package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import java.time.Instant
import kotlin.reflect.KClass
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.currentDate
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.set
import org.springframework.data.mongodb.core.query.setOnInsert
import reactor.core.publisher.Mono

class MongoSettingRepository(
  private val mongoTemplate: ReactiveMongoTemplate,
  private val settingReader: SettingReader,
  private val settingWriter: SettingWriter,
) : SettingRepository {

  override fun <T : Setting> findByName(key: String, type: KClass<T>): Mono<T> {
    val query = Query.query(Criteria.where("key").`is`(key))

    return mongoTemplate.findOne(query, MongoSettingDocument::class.java)
      .switchIfEmpty(Mono.error(NoSuchElementException("Setting with key $key not found")))
      .map { document ->
        settingReader.readSetting(document.metadata, type.java)
      }
  }

  override fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void> {
    val query = Query.query(Criteria.where("key").`is`(key))

    return mongoTemplate.remove(query, MongoSettingDocument::class.java)
      .then()
  }

  override fun <T : Setting> save(key: String, setting: T): Mono<Void> {
    val query = Query.query(MongoSettingDocument::key isEqualTo key)
    val update = Update()
      .set(MongoSettingDocument::metadata, settingWriter.writeSetting(setting))
      .currentDate(MongoSettingDocument::updatedAt)
      .setOnInsert(MongoSettingDocument::key, key)
      .setOnInsert(MongoSettingDocument::createdAt, Instant.now())

    return mongoTemplate.upsert(query, update, MongoSettingDocument::class.java)
      .then()
  }
}
