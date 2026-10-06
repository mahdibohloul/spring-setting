package io.github.mahdibohloul.spring.setting.admin.audit

import io.github.mahdibohloul.spring.setting.admin.InvalidHistoryCursorException
import java.time.Instant
import java.util.Base64

/**
 * The keyset position of one audit entry in the history order (`changedAt` DESC, then `id` DESC).
 *
 * A history page returns the cursor of its last entry. The next request sends it back as `before`, and the
 * backend returns only the entries after that position. The `id` tie-break keeps the order total, so entries
 * with the same `changedAt` are not skipped or returned twice.
 *
 * The wire form is opaque: base64url of `<changedAtEpochMillis>:<id>`. Clients must not parse it.
 */
data class AuditCursor(
  val changedAt: Instant,
  val id: String,
) {
  /** Returns the opaque wire form of this cursor. */
  fun encode(): String = ENCODER.encodeToString("${changedAt.toEpochMilli()}$SEPARATOR$id".toByteArray())

  companion object {
    private const val SEPARATOR = ':'
    private val ENCODER = Base64.getUrlEncoder().withoutPadding()

    /** Returns the cursor of [entry], or `null` when the entry has no id yet. */
    fun of(entry: AuditEntry): AuditCursor? = entry.id?.let { id -> AuditCursor(changedAt = entry.changedAt, id = id) }

    /** Reads the wire form made by [encode]. Throws [InvalidHistoryCursorException] for any other value. */
    fun decode(value: String): AuditCursor {
      val decoded = runCatching { String(Base64.getUrlDecoder().decode(value)) }
        .getOrElse { throw InvalidHistoryCursorException(value) }
      val millis = decoded.substringBefore(SEPARATOR, missingDelimiterValue = "").toLongOrNull()
      val id = decoded.substringAfter(SEPARATOR, missingDelimiterValue = "")
      if (millis == null || id.isBlank()) throw InvalidHistoryCursorException(value)
      return AuditCursor(changedAt = Instant.ofEpochMilli(millis), id = id)
    }
  }
}
