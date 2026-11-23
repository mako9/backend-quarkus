package testUtils

import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object AssertUtil {
    fun assertOffsetDateTimeEquals(expected: OffsetDateTime?, actual: OffsetDateTime?) {
        val expectedUtc = expected?.withOffsetSameInstant(ZoneOffset.UTC)
        val actualUtc = actual?.withOffsetSameInstant(ZoneOffset.UTC)
        assertEquals(expectedUtc?.toEpochSecond(), actualUtc?.toEpochSecond())
        assertEquals(expectedUtc?.toLocalTime()?.nano?.div(1000000), actualUtc?.toLocalTime()?.nano?.div(1000000))
    }

    /**
     * Booking events are fired with `fireAsync`, so the notification observer runs on another thread
     * and commits in its own transaction. Anything that asserts on the effect of such an event has to
     * wait for it. Polling against a deadline keeps the assertion honest - it still fails when the
     * effect never happens - and avoids taking on Awaitility for one test.
     */
    fun assertEventually(
        message: String,
        timeout: Duration = Duration.ofSeconds(5),
        pollInterval: Duration = Duration.ofMillis(25),
        condition: () -> Boolean
    ) {
        val deadline = Instant.now().plus(timeout)
        do {
            if (condition()) return
            Thread.sleep(pollInterval.toMillis())
        } while (Instant.now().isBefore(deadline))
        throw AssertionError(message + " (not met within ${timeout.seconds}s)")
    }
}
