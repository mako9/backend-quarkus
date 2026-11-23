package api.dto

import java.time.OffsetDateTime

data class ItemBookingDateRangeDto(
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime
)
