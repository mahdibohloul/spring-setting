package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.currentDate
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.set
import org.springframework.data.mongodb.core.query.setOnInsert
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.reflect.KClass

/**
 * A MongoDB-specific implementation of the `SettingRepository` interface.
 *
 * The `MongoSettingRepository` provides functionality to perform CRUD operations
 * on application settings using a MongoDB database as the storage backend. It
 * leverages Spring Data's `ReactiveMongoTemplate` for reactive data access.
 *
 * ## Key Features
 *
 * - Supports **CRUD Operations**: Implements `findByName`, `deleteByName`, and `save` methods.
 * - **Type Serialization/Deserialization**: Utilizes `SettingReader` to deserialize settings from
 *   stored metadata and `SettingWriter` to serialize settings before persisting.
 * - **Reactive Programming**: All methods return `Mono` for non-blocking, reactive processing.
 * - **MongoDB Integration**: Leverages MongoDB's query and update capabilities to perform
 *   efficient operations.
 *
 * ## Responsibilities
 *
 * - Retrieves settings by their unique key and converts them to the requested type.
 * - Deletes settings identified by their unique key.
 * - Persists new settings or updates existing settings based on their key.
 *
 * ## Error Handling
 *
 * - `findByName`: Throws `NoSuchElementException` if the setting does not exist.
 * - `save`: Errors may arise due to serialization failures or database conflicts.
 * - `deleteByName`: Deletes settings silently if they exist; does not throw errors for missing keys.
 *
 * ## Implementation Details
 *
 * - Uses `ReactiveMongoTemplate` for executing queries and updates on the `settings` collection.
 * - Relies on `MongoSettingDocument` to structure stored data in MongoDB.
 * - Uses the `key` field as the unique identifier for settings.
 *
 * ## Thread Safety
 *
 * This class is thread-safe as it relies on reactive programming and the immutability of Kotlin.
 *
 * @param mongoTemplate The reactive MongoDB template for database interactions.
 * @param settingReader The component responsible for deserializing settings from stored metadata.
 * @param settingWriter The component responsible for serializing settings before persisting them.
 */
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
