package io.github.mahdibohloul.spring.setting.admin.patch

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

/**
 * RFC 7396 (JSON Merge Patch) implementation against Jackson's tree model.
 *
 * Semantics:
 * - If the patch is not an object, the target is replaced wholesale.
 * - If a field's value in the patch is JSON `null`, the field is removed from the target.
 * - Otherwise the field is recursively merged.
 *
 * Tree merge is preferred over `ObjectReader.readerForUpdating(existing)` because the
 * updating reader does not give us RFC-7396 null-as-removal semantics and is sensitive
 * to Kotlin data-class deserialization quirks for partial JSON.
 */
object JsonMergePatch {
  fun merge(target: JsonNode, patch: JsonNode): JsonNode {
    if (patch !is ObjectNode) return patch
    val result: ObjectNode = if (target is ObjectNode) {
      target.deepCopy()
    } else {
      JsonNodeFactory.instance.objectNode()
    }
    patch.properties().forEach { (key, value) ->
      if (value.isNull) {
        result.remove(key)
      } else {
        val existing = result.get(key) ?: JsonNodeFactory.instance.objectNode()
        result.set(key, merge(existing, value))
      }
    }
    return result
  }
}
