package io.github.mahdibohloul.spring.setting.admin.audit

import io.github.mahdibohloul.spring.setting.admin.InvalidHistoryCursorException
import io.github.mahdibohloul.spring.setting.admin.authorization.SettingAdminAuthorizer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Base64

class AuditCursorTest {
  @Test
  fun `decode reads back the encoded cursor`() {
    // given
    val cursor = AuditCursor(changedAt = Instant.ofEpochMilli(1_759_600_000_123), id = "6720aa0000000000000000b1")

    // when
    val decoded = AuditCursor.decode(cursor.encode())

    // verify
    check(decoded == cursor)
  }

  @Test
  fun `of returns the cursor of a persisted entry and null for an entry without id`() {
    // given
    val entry = AuditEntry(
      id = "6720aa0000000000000000b1",
      typeName = "SampleSetting",
      operation = SettingAdminAuthorizer.Operation.PATCH,
      previousValue = null,
      newValue = "{}",
      changedBy = "tester",
      changedAt = Instant.ofEpochMilli(1_000),
    )

    val expected = AuditCursor(changedAt = Instant.ofEpochMilli(1_000), id = "6720aa0000000000000000b1")

    // when / verify
    check(AuditCursor.of(entry) == expected)
    check(AuditCursor.of(entry.copy(id = null)) == null)
  }

  @Test
  fun `decode rejects a value that encode did not make`() {
    // given: empty, not base64url, "1000" (no separator), "abc:id" (no millis), "1000:" (no id)
    val values = listOf("", "%%%", "MTAwMA", "YWJjOmlk", "MTAwMDo")

    // when / verify
    values.forEach { value -> assertThrows<InvalidHistoryCursorException> { AuditCursor.decode(value) } }
  }

  @Test
  fun `the wire form is base64url of the epoch millis and the id`() {
    // given
    val cursor = AuditCursor(changedAt = Instant.ofEpochMilli(1_000), id = "6720aa0000000000000000b1")

    // when
    val decoded = String(Base64.getUrlDecoder().decode(cursor.encode()))

    // verify
    check(decoded == "1000:6720aa0000000000000000b1")
  }
}
