package io.github.mahdibohloul.spring.setting.admin.services

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.SettingHelper
import io.github.mahdibohloul.spring.setting.admin.SettingTypeDescriptor
import io.github.mahdibohloul.spring.setting.admin.SettingTypeRegistry
import io.github.mahdibohloul.spring.setting.admin.UnknownSettingTypeException
import io.github.mahdibohloul.spring.setting.admin.authorization.AllowAllSettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import io.github.mahdibohloul.spring.setting.reader.SettingReaderImpl
import io.github.mahdibohloul.spring.setting.repositories.SimpleInMemorySettingRepository
import io.github.mahdibohloul.spring.setting.writer.SettingWriterImpl
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration
import kotlin.reflect.KClass

class SettingAdminServiceImplTest {
  data class SamplePolicy(
    val minOrders: Int = 5,
    val maxAllowedDelay: Duration = Duration.ofMinutes(0),
  )

  data class SampleSetting(
    val delay: SamplePolicy = SamplePolicy(),
    val label: String = "default",
  ) : Setting

  private class Descriptor<T : Setting>(
    override val settingClass: KClass<T>,
    private val factory: () -> T,
  ) : SettingTypeDescriptor<T> {
    override fun default(): T = factory()
  }

  private fun newService(
    authorizer: SettingAdminAuthorizer = AllowAllSettingAdminAuthorizer(),
  ): Pair<SettingAdminServiceImpl, SimpleInMemorySettingRepository> {
    val repo = SimpleInMemorySettingRepository()
    val registry = SettingTypeRegistry(listOf(Descriptor(SampleSetting::class) { SampleSetting() }))
    val mapper = JsonMapper.builder()
      .addModule(KotlinModule.Builder().build())
      .build()
    val settingReader = SettingReaderImpl(mapper)
    val settingWriter = SettingWriterImpl(mapper)

    return SettingAdminServiceImpl(
      registry = registry,
      settingRepository = repo,
      objectMapper = mapper,
      authorizer = authorizer,
      settingWriter = settingWriter,
      settingReader = settingReader,
    ) to repo
  }

  @Test
  fun `listTypeNames returns registered type names`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.listTypeNames())
      .expectNext(listOf("SampleSetting"))
      .verifyComplete()
  }

  @Test
  fun `getAsJson returns default when nothing is persisted`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.getAsJson("SampleSetting"))
      .assertNext { json ->
        check(json.contains("\"minOrders\":5"))
        check(json.contains("\"maxAllowedDelay\":\"PT0S\""))
        check(json.contains("\"label\":\"default\""))
      }
      .verifyComplete()
  }

  @Test
  fun `patch merges nested fields and persists the result`() {
    // given
    val (service, repo) = newService()
    val mergePatch = """{"delay":{"maxAllowedDelay":"PT30S"}}"""

    // when
    val patched = service.patch("SampleSetting", mergePatch).block()!!

    // verify
    check(patched.contains("\"maxAllowedDelay\":\"PT30S\""))
    check(patched.contains("\"minOrders\":5")) { "siblings must be preserved" }
    check(patched.contains("\"label\":\"default\""))

    val persistedKey = SettingHelper.getSettingName(SampleSetting::class)
    val persisted = repo.findByName(persistedKey, SampleSetting::class).block()!!
    check(persisted.delay.maxAllowedDelay == Duration.ofSeconds(30))
    check(persisted.delay.minOrders == 5)
  }

  @Test
  fun `patch removes a field when value is null`() {
    // given
    val (service, _) = newService()
    service.replace(
      "SampleSetting",
      """{"delay":{"minOrders":7,"maxAllowedDelay":"PT0S"},"label":"original"}""",
    ).block()

    // when
    val patched = service.patch("SampleSetting", """{"label":null}""").block()!!

    // verify — Jackson reads missing string field as default value, so re-deserialization gives default label
    check(patched.contains("\"label\":\"default\"")) { "removed field falls back to default on re-deserialize" }
  }

  @Test
  fun `replace deserializes the full json and persists it`() {
    // given
    val (service, repo) = newService()

    // when
    val json = """{"delay":{"minOrders":10,"maxAllowedDelay":"PT5M"},"label":"replaced"}"""
    val result = service.replace("SampleSetting", json).block()!!

    // verify
    check(result.contains("\"label\":\"replaced\""))
    val persisted = repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class).block()!!
    check(persisted.delay.minOrders == 10)
    check(persisted.delay.maxAllowedDelay == Duration.ofMinutes(5))
    check(persisted.label == "replaced")
  }

  @Test
  fun `delete removes the persisted value so reads fall back to default`() {
    // given
    val (service, repo) = newService()
    service.replace("SampleSetting", """{"label":"to-delete"}""").block()

    // when
    service.delete("SampleSetting").block()

    // verify — repository no longer has the document
    StepVerifier.create(repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class))
      .expectError(NoSuchElementException::class.java)
      .verify()

    // verify — re-reading through the service yields the descriptor default
    val afterDelete = service.getAsJson("SampleSetting").block()!!
    check(afterDelete.contains("\"label\":\"default\""))
  }

  @Test
  fun `delete completes silently when no value is persisted`() {
    // given — no replace/patch beforehand
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.delete("SampleSetting"))
      .verifyComplete()
  }

  @Test
  fun `delete on unknown type yields UnknownSettingTypeException`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.delete("DoesNotExist"))
      .expectError(UnknownSettingTypeException::class.java)
      .verify()
  }

  @Test
  fun `denying authorizer short-circuits patch`() {
    // given
    val denying = object : SettingAdminAuthorizer {
      override fun authorize(
        operation: SettingAdminAuthorizer.Operation,
        typeName: String?,
      ): Mono<Void> = Mono.error(SecurityException("denied"))
    }
    val (service, repo) = newService(denying)

    // when / verify
    StepVerifier.create(service.patch("SampleSetting", """{"label":"hack"}"""))
      .expectError(SecurityException::class.java)
      .verify()

    StepVerifier.create(repo.findByName(SettingHelper.getSettingName(SampleSetting::class), SampleSetting::class))
      .expectError(NoSuchElementException::class.java)
      .verify()
  }

  @Test
  fun `unknown type yields UnknownSettingTypeException`() {
    // given
    val (service, _) = newService()

    // when / verify
    StepVerifier.create(service.getAsJson("DoesNotExist"))
      .expectError(UnknownSettingTypeException::class.java)
      .verify()
  }
}
