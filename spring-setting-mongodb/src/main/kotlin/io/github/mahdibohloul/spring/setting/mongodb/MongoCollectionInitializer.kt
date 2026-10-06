package io.github.mahdibohloul.spring.setting.mongodb

import com.mongodb.MongoCommandException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.IndexResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Creates a library collection at startup, together with its indexes when auto index creation is on.
 *
 * Spring Data creates the indexes of an entity on its first use, asynchronously and outside any transaction.
 * On a new database that `createIndexes` also creates the collection. When the first use is an admin write,
 * the transaction creates the same collection with its insert, and the commit fails with a `WriteConflict`.
 * When the collection and its indexes exist before the first use, the later `createIndexes` changes nothing.
 *
 * The indexes come from the annotations of [entityClass], so they are the same indexes that Spring Data creates.
 * When auto index creation is off, only the collection is created. A failure is logged and the startup continues.
 */
class MongoCollectionInitializer(
  private val mongoTemplate: ReactiveMongoTemplate,
  private val collectionName: String,
  private val entityClass: Class<*>,
  private val timeout: Duration = DEFAULT_TIMEOUT,
) : SmartInitializingSingleton {
  private val logger = LoggerFactory.getLogger(this::class.java)

  @Suppress("detekt.TooGenericExceptionCaught")
  override fun afterSingletonsInstantiated() {
    try {
      initialize().block(timeout)
    } catch (ex: RuntimeException) {
      logger.warn("Could not initialize the MongoDB collection '{}'", collectionName, ex)
    }
  }

  fun initialize(): Mono<Void> = createCollection().then(Mono.defer(::createIndexes))

  private fun createIndexes(): Mono<Void> {
    val mappingContext = mongoTemplate.converter.mappingContext
    if ((mappingContext as? MongoMappingContext)?.isAutoIndexCreation != true) return Mono.empty()

    val indexOperations = mongoTemplate.indexOps(collectionName)
    return Flux.fromIterable(IndexResolver.create(mappingContext).resolveIndexFor(entityClass))
      .concatMap(indexOperations::createIndex)
      .then()
  }

  /** Another instance can create the collection at the same time, so "already exists" is not an error. */
  private fun createCollection(): Mono<Void> = Mono.defer { mongoTemplate.collectionExists(collectionName) }
    .flatMap { exists -> if (exists) Mono.empty() else mongoTemplate.createCollection(collectionName).then() }
    .onErrorResume(::isNamespaceExists) { Mono.empty() }

  private fun isNamespaceExists(error: Throwable): Boolean = generateSequence(error) { cause -> cause.cause }
    .any { cause -> cause is MongoCommandException && cause.errorCode == NAMESPACE_EXISTS }

  private companion object {
    val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(10)
    const val NAMESPACE_EXISTS = 48
  }
}
