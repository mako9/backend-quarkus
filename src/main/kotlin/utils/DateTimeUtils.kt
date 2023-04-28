package utils

import java.time.OffsetTime
import java.time.ZoneOffset

object DateTimeUtils {
    fun getOffsetTimeOfMinutes(minutes: Int): OffsetTime =
        OffsetTime.of(minutes / 60, minutes % 60, 0, 0, ZoneOffset.UTC)

    fun OffsetTime.getMinutesOfOffsetTime(): Int =
        this.hour * 60 + this.minute
}