package io.github.mahdibohloul.spring.setting.admin.web.autoconfigure

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditLog
import io.github.mahdibohloul.spring.setting.admin.audit.SettingAuditPrincipalProvider
import io.github.mahdibohloul.spring.setting.admin.authorization.AllowAllSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.transaction.SettingTransactionRetryPolicy
import io.github.mahdibohloul.spring.setting.reader.SettingReaderImpl
import io.github.mahdibohloul.spring.setting.repositories.SimpleInMemorySettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriterImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper

class SettingAdminWebAutoConfigurationTest {
  class SampleSetting(var label: String = "default") : Setting

  private class TransientError : RuntimeException("transient")

  private inline fun <reified T : Any> providerOf(value: T): ObjectProvider<T> {
    val provider = mock<ObjectProvider<T>>()
    whenever(provider.ifAvailable).thenReturn(value)
    whenever(provider.getIfAvailable(any())).thenReturn(value)
    return provider
  }

  @Test
  fun `roleBasedSettingAdminService runs the transaction again when the retry policy accepts the error`() {
    // given
    val mapper = JsonMapper.builder().build()
    val auditLog = mock<SettingAuditLog>()
    val txOperator = mock<TransactionalOperator>()
    val descriptor = object : SettingTypeDescriptor<SampleSetting> {
      override val settingClass = SampleSetting::class
      override fun default() = SampleSetting()
    }
    val service = SettingAdminWebAutoConfiguration().roleBasedSettingAdminService(
      registry = SettingTypeRegistry(listOf(descriptor)),
      settingRepository = SimpleInMemorySettingRepository(),
      objectMapper = mapper,
      authorizer = AllowAllSettingAdminAuthorizer(),
      settingReader = SettingReaderImpl(mapper),
      settingWriter = SettingWriterImpl(mapper),
      auditLog = providerOf(auditLog),
      principalProvider = SettingAuditPrincipalProvider { Mono.just("tester") },
      txOperator = providerOf(txOperator),
      transactionRetryPolicy = providerOf(SettingTransactionRetryPolicy { error -> error is TransientError }),
    )

    // when
    whenever(txOperator.transactional(any<Mono<Any>>())).thenAnswer { invocation -> invocation.getArgument(0) }
    whenever(auditLog.record(any())).thenReturn(Mono.error(TransientError()), Mono.empty())

    // verify
    StepVerifier.create(service.patch("SampleSetting", """{"label":"patched"}"""))
      .assertNext { json -> check(json.contains("\"label\":\"patched\"")) }
      .verifyComplete()
    verify(auditLog, times(2)).record(any())
  }
}
