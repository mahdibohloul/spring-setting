package io.github.mahdibohloul.spring.setting.repositories

import io.github.mahdibohloul.spring.setting.Setting
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

/**
 * A composite implementation of the `SettingRepository` interface that combines multiple repositories.
 * The composite enables delegating operations to the provided repositories in a cascading or parallel manner.
 *
 * @constructor Creates a `CompositeSettingRepository` with the specified list of repositories.
 * Throws an `IllegalArgumentException` if the list is empty.
 *
 * @property repositories The list of repositories to be combined into a composite repository.
 *
 * The behaviors of the operations in this class are as follows:
 *
 * - `findByName`: Attempts to find a setting in the first repository that contains the requested key and type.
 *   If the setting is found, caching is optionally performed on previous repositories to warm up caches.
 *   If no repository contains the requested setting, a `NoSuchElementException` is returned.
 *
 * - `deleteByName`: Deletes a setting identified by the given key and type from all repositories in parallel.
 *
 * - `save`: Saves the setting with the given key into all repositories in parallel.
 *
 * This class facilitates combining various underlying implementations of `SettingRepository` to provide
 * features like failover, prioritization, or multi-level caching while keeping the API consistent.
 */
class CompositeSettingRepository(
  private val repositories: List<SettingRepository>,
) : SettingRepository {

  init {
    require(repositories.isNotEmpty()) { "At least one repository must be provided" }
  }

  override fun <T : Setting> findByName(
    key: String,
    type: KClass<T>,
  ): Mono<T> = repositories.fold(
    Mono.error<T>(NoSuchElementException("Setting with key $key not found")),
  ) { mono, repository ->
    mono.onErrorResume { _ ->
      repository.findByName(key, type)
        .flatMap { setting ->
          // "Acquire then save" pattern: save it to all previous repositories for cache warming
          saveToPreviousRepositories(key, setting, repositories.takeWhile { it != repository })
            .thenReturn(setting)
        }
    }
  }

  override fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void> = Mono.`when`(
    repositories.map { repository ->
      repository.deleteByName(key, type)
    },
  )

  override fun <T : Setting> save(key: String, setting: T): Mono<Void> = Mono.`when`(
    repositories.map { repository ->
      repository.save(key, setting)
    },
  )

  private fun <T : Setting> saveToPreviousRepositories(
    key: String,
    setting: T,
    previousRepositories: List<SettingRepository>,
  ): Mono<Void> = Mono.defer {
    if (previousRepositories.isEmpty()) {
      return@defer Mono.empty()
    }

    return@defer Mono.`when`(
      previousRepositories.map { repository ->
        repository.save(key, setting)
      },
    )
  }
}
