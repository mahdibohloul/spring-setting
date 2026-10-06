package io.github.mahdibohloul.spring.setting.mongodb

import io.github.mahdibohloul.spring.setting.admin.InvalidHistoryCursorException
import io.github.mahdibohloul.spring.setting.admin.audit.AuditCursor
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant

class MongoSettingAuditLogTest {
  private val mongoTemplate = mock<ReactiveMongoTemplate>()
  private val auditLog = MongoSettingAuditLog(mongoTemplate = mongoTemplate, collectionName = COLLECTION)

  @Test
  fun `findByTypeName without a cursor reads the newest entries in the keyset order`() {
    // given
    val document = MongoAuditDocument(
      id = "6720aa0000000000000000b1",
      typeName = "FooSetting",
      operation = "PATCH",
      previousValue = "{}",
      newValue = "{}",
      changedBy = "tester",
      changedAt = Instant.ofEpochMilli(1_000),
    )

    // when
    whenever(mongoTemplate.find(any<Query>(), eq(MongoAuditDocument::class.java), eq(COLLECTION)))
      .thenReturn(Flux.just(document))

    // verify
    StepVerifier.create(auditLog.findByTypeName("FooSetting", 21, before = null))
      .assertNext { entries -> check(entries.single().id == "6720aa0000000000000000b1") }
      .verifyComplete()
    val query = captureQuery()
    check(query.queryObject == Document("typeName", "FooSetting"))
    check(query.sortObject == Document("changedAt", -1).append("_id", -1))
    check(query.limit == 21)
  }

  @Test
  fun `findByTypeName with a cursor reads only the entries after it`() {
    // given
    val before = AuditCursor(changedAt = Instant.ofEpochMilli(1_000), id = "6720aa0000000000000000b1")

    // when
    whenever(mongoTemplate.find(any<Query>(), eq(MongoAuditDocument::class.java), eq(COLLECTION)))
      .thenReturn(Flux.empty())

    // verify
    StepVerifier.create(auditLog.findByTypeName("FooSetting", 21, before))
      .assertNext { entries -> check(entries.isEmpty()) }
      .verifyComplete()
    val query = captureQuery()
    val expected = Document("typeName", "FooSetting").append(
      "\$or",
      listOf(
        Document("changedAt", Document("\$lt", before.changedAt)),
        Document("changedAt", before.changedAt).append("_id", Document("\$lt", ObjectId(before.id))),
      ),
    )
    check(query.queryObject == expected) { "unexpected query ${query.queryObject}" }
    check(query.sortObject == Document("changedAt", -1).append("_id", -1))
  }

  @Test
  fun `findByTypeName rejects a cursor whose id is not an ObjectId`() {
    // given
    val before = AuditCursor(changedAt = Instant.ofEpochMilli(1_000), id = "not-an-object-id")

    // when / verify
    StepVerifier.create(auditLog.findByTypeName("FooSetting", 21, before))
      .expectError(InvalidHistoryCursorException::class.java)
      .verify()
    verify(mongoTemplate, never()).find(any<Query>(), eq(MongoAuditDocument::class.java), eq(COLLECTION))
  }

  private fun captureQuery(): Query {
    val captor = argumentCaptor<Query>()
    verify(mongoTemplate).find(captor.capture(), eq(MongoAuditDocument::class.java), eq(COLLECTION))
    return captor.firstValue
  }

  private companion object {
    const val COLLECTION = "setting_audit_logs"
  }
}
