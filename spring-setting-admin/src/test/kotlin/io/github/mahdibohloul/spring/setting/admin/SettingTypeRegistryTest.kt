package io.github.mahdibohloul.spring.setting.admin

import io.github.mahdibohloul.spring.setting.Setting
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingTypeRegistryTest {
  data class AlphaSetting(val a: Int = 1) : Setting
  data class BetaSetting(val b: Int = 2) : Setting

  private class Descriptor<T : Setting>(
    override val settingClass: KClass<T>,
    private val factory: () -> T,
  ) : SettingTypeDescriptor<T> {
    override fun default(): T = factory()
  }

  @Test
  fun `listTypeNames returns sorted simple names`() {
    // given
    val registry = SettingTypeRegistry(
      listOf(
        Descriptor(BetaSetting::class) { BetaSetting() },
        Descriptor(AlphaSetting::class) { AlphaSetting() },
      ),
    )

    // when
    val names = registry.listTypeNames()

    // verify
    assertEquals(listOf("AlphaSetting", "BetaSetting"), names)
  }

  @Test
  fun `get returns the matching descriptor`() {
    // given
    val descriptor = Descriptor(AlphaSetting::class) { AlphaSetting(a = 99) }
    val registry = SettingTypeRegistry(listOf(descriptor))

    // when
    val resolved = registry.get("AlphaSetting")

    // verify
    assertEquals(AlphaSetting::class, resolved.settingClass)
    assertEquals(99, (resolved.default() as AlphaSetting).a)
  }

  @Test
  fun `get on unknown type throws with available names`() {
    // given
    val registry = SettingTypeRegistry(
      listOf(Descriptor(AlphaSetting::class) { AlphaSetting() }),
    )

    // when
    val failure = assertFailsWith<UnknownSettingTypeException> {
      registry.get("DoesNotExist")
    }

    // verify
    assertEquals("DoesNotExist", failure.typeName)
    assertContains(failure.available, "AlphaSetting")
  }

  @Test
  fun `describedTypes returns all registered descriptors`() {
    // given
    val a = Descriptor(AlphaSetting::class) { AlphaSetting() }
    val b = Descriptor(BetaSetting::class) { BetaSetting() }
    val registry = SettingTypeRegistry(listOf(a, b))

    // when
    val described = registry.describedTypes()

    // verify
    assertEquals(2, described.size)
    assertContains(described.map { it.typeName }, "AlphaSetting")
    assertContains(described.map { it.typeName }, "BetaSetting")
  }
}
