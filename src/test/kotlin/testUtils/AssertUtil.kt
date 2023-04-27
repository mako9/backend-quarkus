package testUtils

import org.junit.jupiter.api.Assertions.assertEquals
import java.time.OffsetDateTime
import java.time.ZoneOffset

object AssertUtil {
    fun assertOffsetDateTimeEquals(expected: OffsetDateTime?, actual: OffsetDateTime?) {
        assertEquals(
            expected?.withOffsetSameInstant(ZoneOffset.UTC),
            actual?.withOffsetSameInstant(ZoneOffset.UTC)
        )
    }
}