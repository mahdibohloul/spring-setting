package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import reactor.core.publisher.Mono
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

  override fun <T : Setting> findByName(name: String, type: KClass<T>): Mono<T> {
    val query = Query.query(MongoSettingDocument::name isEqualTo name)

    return mongoTemplate.findOne(query, MongoSettingDocument::class.java)
      .switchIfEmpty(Mono.error(NoSuchElementException("Setting with key $name not found")))
      .map { document ->
        settingReader.readSetting(document.metadata, type.java)
      }
  }

  override fun <T : Setting> deleteByName(name: String, type: KClass<T>): Mono<Void> {
    val query = Query.query(MongoSettingDocument::name isEqualTo name)

    return mongoTemplate.remove(query, MongoSettingDocument::class.java)
      .then()
  }

  override fun <T : Setting> save(name: String, setting: T): Mono<Void> = mongoTemplate.findOne(
    Query.query(MongoSettingDocument::name isEqualTo name),
    MongoSettingDocument::class.java,
  ).flatMap { existingDocument ->
    mongoTemplate.save(
      existingDocument.copy(metadata = settingWriter.writeSetting(setting)),
    )
  }.switchIfEmpty(
    mongoTemplate.save(
      MongoSettingDocument(
        name = name,
        metadata = settingWriter.writeSetting(setting),
      ),
    ),
  ).then()
}
