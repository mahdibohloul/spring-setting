package io.github.mahdibohloul.spring.setting.admin.patch

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonMergePatchTest {
  private val mapper = ObjectMapper()

  @Test
  fun `add a top-level field`() {
    // given
    val target = mapper.readTree("""{"a":1}""")
    val patch = mapper.readTree("""{"b":2}""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    assertEquals(1, merged.get("a").asInt())
    assertEquals(2, merged.get("b").asInt())
  }

  @Test
  fun `replace a top-level field`() {
    // given
    val target = mapper.readTree("""{"a":1,"b":2}""")
    val patch = mapper.readTree("""{"b":99}""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    assertEquals(1, merged.get("a").asInt())
    assertEquals(99, merged.get("b").asInt())
  }

  @Test
  fun `null removes a field`() {
    // given
    val target = mapper.readTree("""{"a":1,"b":2}""")
    val patch = mapper.readTree("""{"b":null}""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    assertEquals(1, merged.get("a").asInt())
    assertFalse(merged.has("b"))
  }

  @Test
  fun `nested merge preserves siblings`() {
    // given
    val target = mapper.readTree("""{"delay":{"minOrders":5,"maxAllowedBreachRate":0.2}}""")
    val patch = mapper.readTree("""{"delay":{"minOrders":3}}""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    assertEquals(3, merged.get("delay").get("minOrders").asInt())
    assertEquals(0.2, merged.get("delay").get("maxAllowedBreachRate").asDouble())
  }

  @Test
  fun `array in patch replaces array in target wholesale`() {
    // given
    val target = mapper.readTree("""{"items":[1,2,3]}""")
    val patch = mapper.readTree("""{"items":[4]}""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    val items = merged.get("items")
    assertTrue(items.isArray)
    assertEquals(1, items.size())
    assertEquals(4, items.get(0).asInt())
  }

  @Test
  fun `non-object patch replaces target wholesale`() {
    // given
    val target = mapper.readTree("""{"a":1}""")
    val patch = mapper.readTree("""42""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    assertTrue(merged.isInt)
    assertEquals(42, merged.asInt())
  }

  @Test
  fun `Duration field round-trips as ISO-8601 string via merge patch`() {
    // given — simulate a setting with a Duration field already stored
    val target = mapper.readTree("""{"delay":{"maxAllowedDelay":"PT0S"}}""")
    val patch = mapper.readTree("""{"delay":{"maxAllowedDelay":"PT30S"}}""")

    // when
    val merged = JsonMergePatch.merge(target, patch)

    // verify
    assertEquals("PT30S", merged.get("delay").get("maxAllowedDelay").asText())
  }
}
