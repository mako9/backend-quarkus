package testUtils

import org.junit.jupiter.api.Assertions.assertEquals
import java.time.OffsetDateTime
import java.time.ZoneOffset

object AssertUtil {
    fun assertOffsetDateTimeEquals(expected: OffsetDateTime?, actual: OffsetDateTime?) {
        val expectedUtc = expected?.withOffsetSameInstant(ZoneOffset.UTC)
        val actualUtc = actual?.withOffsetSameInstant(ZoneOffset.UTC)
        assertEquals(expectedUtc?.toEpochSecond(), actualUtc?.toEpochSecond())
        assertEquals(expectedUtc?.toLocalTime()?.nano?.div(1000000), actualUtc?.toLocalTime()?.nano?.div(1000000))
    }
}