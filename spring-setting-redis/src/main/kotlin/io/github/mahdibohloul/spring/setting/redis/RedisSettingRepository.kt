package io.github.mahdibohloul.spring.setting.redis

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.reader.SettingReader
import io.github.mahdibohloul.spring.setting.repositories.SettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriter
import kotlin.reflect.KClass
import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Mono

/**
 * A repository implementation for managing settings in Redis.
 *
 * This class provides reactive operations for saving, retrieving, and deleting settings
 * in a Redis data store. The settings are stored as serialized strings, with support
 * for custom serialization and deserialization logic provided through external handlers.
 *
 * ## Responsibilities
 *
 * - Store settings in Redis with a configurable key prefix and TTL.
 * - Retrieve and deserialize settings from Redis, ensuring type safety.
 * - Remove settings from the Redis database.
 * - Manage Redis key preparation with a consistent prefix.
 *
 * ## Configuration
 *
 * The repository relies on `RedisSettingProperties` for configuration, which defines:
 * - `prefix`: A string prefix appended to all Redis keys.
 * - `ttl`: The default Time-To-Live duration for stored keys.
 *
 * ## Components
 *
 * - `RedisTemplate`: Facilitates interaction with Redis.
 * - `SettingWriter`: Handles serialization of settings before storage.
 * - `SettingReader`: Handles deserialization of stored settings into typed objects.
 * - `RedisSettingProperties`: Holds configuration for Redis key prefix and TTL.
 *
 * ## Error Handling
 *
 * - `findByName`: Returns `Mono.error(NoSuchElementException)` if the setting does not exist.
 * - `save`: May throw serialization-related errors if the `SettingWriter` encounters issues.
 *
 * ## Thread Safety
 *
 * This class is thread-safe and can be used in multi-threaded environments.
 *
 * @constructor Creates an instance of `RedisSettingRepository`.
 * @param redisTemplate The reactive Redis template for executing operations.
 * @param settingWriter The writer responsible for serializing settings.
 * @param settingReader The reader responsible for deserializing stored settings.
 * @param settingProperties The configuration properties for Redis settings, including key prefix and TTL.
 */
class RedisSettingRepository(
  private val redisTemplate: ReactiveRedisTemplate<String, String>,
  private val settingWriter: SettingWriter,
  private val settingReader: SettingReader,
  private val settingProperties: RedisSettingProperties,
) : SettingRepository {

  override fun <T : Setting> findByName(key: String, type: KClass<T>): Mono<T> =
    redisTemplate.opsForValue()
      .get(prepareKey(key))
      .switchIfEmpty(Mono.error(NoSuchElementException("Setting with key $key not found")))
      .map { settingReader.readSetting(input = it, settingClass = type.java) }


  override fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void> = redisTemplate.opsForValue()
    .delete(prepareKey(key))
    .then()

  override fun <T : Setting> save(key: String, setting: T): Mono<Void> = redisTemplate.opsForValue()
    .set(prepareKey(key), settingWriter.writeSetting(setting), settingProperties.ttl)
    .then()

  private fun prepareKey(name: String): String = "${settingProperties.prefix}$name"
}
