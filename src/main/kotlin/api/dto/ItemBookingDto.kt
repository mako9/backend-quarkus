package api.dto

import common.BookingStatus
import common.CancelledBy
import domain.model.ItemBookingModel
import java.time.OffsetDateTime
import java.util.*

data class ItemBookingDto(
    val uuid: UUID,
    val itemUuid: UUID,
    val userUuid: UUID,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val status: BookingStatus,
    val cancelledBy: CancelledBy?,
    val cancelledAt: OffsetDateTime?
) {
    constructor(itemBookingModel: ItemBookingModel) : this(
        uuid = itemBookingModel.uuid,
        itemUuid = itemBookingModel.itemUuid,
        userUuid = itemBookingModel.userUuid,
        startAt = itemBookingModel.startAt,
        endAt = itemBookingModel.endAt,
        createdAt = itemBookingModel.createdAt,
        status = itemBookingModel.status,
        cancelledBy = itemBookingModel.cancelledBy,
        cancelledAt = itemBookingModel.cancelledAt
    )
}