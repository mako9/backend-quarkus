package domain.model

import infrastructure.entity.ItemBooking
import java.time.OffsetDateTime
import java.util.*

data class ItemBookingModel(
    val uuid: UUID,
    val itemUuid: UUID,
    val userUuid: UUID,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val createdAt: OffsetDateTime
) {
    constructor(itemBooking: ItemBooking) : this(
        uuid = itemBooking.uuid,
        itemUuid = itemBooking.itemUuid,
        userUuid = itemBooking.userUuid,
        startAt = itemBooking.startAt,
        endAt = itemBooking.endAt,
        createdAt = itemBooking.createdAt
    )
}