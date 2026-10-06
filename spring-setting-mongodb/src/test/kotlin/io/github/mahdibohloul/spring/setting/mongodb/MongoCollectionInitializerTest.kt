package io.github.mahdibohloul.spring.setting.mongodb

import com.mongodb.MongoCommandException
import com.mongodb.ServerAddress
import com.mongodb.reactivestreams.client.MongoCollection
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class MongoCollectionInitializerTest {
  private val mongoTemplate = mock<ReactiveMongoTemplate>()
  private val indexOperations = mock<ReactiveIndexOperations>()
  private val initializer = MongoCollectionInitializer(mongoTemplate, COLLECTION, MongoSettingDocument::class.java)

  private fun useMappingContext(autoIndexCreation: Boolean) {
    val applicationContext = GenericApplicationContext().apply { refresh() }
    val mappingContext = MongoMappingContext().apply {
      setAutoIndexCreation(autoIndexCreation)
      setSimpleTypeHolder(MongoCustomConversions(emptyList<Any>()).simpleTypeHolder)
      setApplicationContext(applicationContext)
    }
    whenever(mongoTemplate.converter).thenReturn(MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext))
  }

  @Test
  fun `initialize creates the collection and its annotated indexes when auto index creation is on`() {
    // given
    useMappingContext(autoIndexCreation = true)

    // when
    whenever(mongoTemplate.collectionExists(COLLECTION)).thenReturn(Mono.just(false))
    whenever(mongoTemplate.createCollection(COLLECTION)).thenReturn(Mono.just(mock<MongoCollection<Document>>()))
    whenever(mongoTemplate.indexOps(COLLECTION)).thenReturn(indexOperations)
    whenever(indexOperations.createIndex(any())).thenReturn(Mono.just("name"))

    // verify
    StepVerifier.create(initializer.initialize()).verifyComplete()
    verify(mongoTemplate).createCollection(COLLECTION)
    val captor = argumentCaptor<IndexDefinition>()
    verify(indexOperations).createIndex(captor.capture())
    check(captor.firstValue.indexKeys == Document("name", 1))
    check(captor.firstValue.indexOptions["unique"] == true)
  }

  @Test
  fun `initialize creates only the collection when auto index creation is off`() {
    // given
    useMappingContext(autoIndexCreation = false)

    // when
    whenever(mongoTemplate.collectionExists(COLLECTION)).thenReturn(Mono.just(false))
    whenever(mongoTemplate.createCollection(COLLECTION)).thenReturn(Mono.just(mock<MongoCollection<Document>>()))

    // verify
    StepVerifier.create(initializer.initialize()).verifyComplete()
    verify(mongoTemplate).createCollection(COLLECTION)
    verify(mongoTemplate, never()).indexOps(COLLECTION)
  }

  @Test
  fun `initialize keeps an existing collection and still creates the indexes`() {
    // given
    useMappingContext(autoIndexCreation = true)

    // when
    whenever(mongoTemplate.collectionExists(COLLECTION)).thenReturn(Mono.just(true))
    whenever(mongoTemplate.indexOps(COLLECTION)).thenReturn(indexOperations)
    whenever(indexOperations.createIndex(any())).thenReturn(Mono.just("name"))

    // verify
    StepVerifier.create(initializer.initialize()).verifyComplete()
    verify(mongoTemplate, never()).createCollection(COLLECTION)
    verify(indexOperations, times(1)).createIndex(any())
  }

  @Test
  fun `initialize accepts a collection that another instance created at the same time`() {
    // given
    useMappingContext(autoIndexCreation = true)
    val namespaceExists = MongoCommandException(
      BsonDocument("code", BsonInt32(48)).append("errmsg", BsonString("Collection already exists")),
      ServerAddress(),
    )

    // when
    whenever(mongoTemplate.collectionExists(COLLECTION)).thenReturn(Mono.just(false))
    whenever(mongoTemplate.createCollection(COLLECTION)).thenReturn(Mono.error(namespaceExists))
    whenever(mongoTemplate.indexOps(COLLECTION)).thenReturn(indexOperations)
    whenever(indexOperations.createIndex(any())).thenReturn(Mono.just("name"))

    // verify
    StepVerifier.create(initializer.initialize()).verifyComplete()
    verify(indexOperations, times(1)).createIndex(any())
  }

  @Test
  fun `afterSingletonsInstantiated does not fail the startup when MongoDB fails`() {
    // given
    useMappingContext(autoIndexCreation = true)

    // when
    whenever(mongoTemplate.collectionExists(COLLECTION)).thenReturn(Mono.error(IllegalStateException("down")))

    // verify
    initializer.afterSingletonsInstantiated()
    verify(mongoTemplate, never()).indexOps(COLLECTION)
  }

  private companion object {
    const val COLLECTION = "settings"
  }
}
