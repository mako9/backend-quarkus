package testUtils

import org.junit.jupiter.api.Assertions.assertTrue
import java.time.OffsetDateTime
import java.time.ZoneOffset

object AssertUtil {
    fun assertOffsetDateTimeEquals(expected: OffsetDateTime?, actual: OffsetDateTime?) {
        assertTrue(
            expected?.withOffsetSameInstant(ZoneOffset.UTC)
                ?.isEqual(actual?.withOffsetSameInstant(ZoneOffset.UTC)) == true
        )
    }
}