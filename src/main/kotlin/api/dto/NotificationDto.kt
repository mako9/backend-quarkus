package api.dto

import common.NotificationType
import domain.model.NotificationModel
import java.time.OffsetDateTime
import java.util.*

data class NotificationDto(
    val uuid: UUID,
    val type: NotificationType,
    val bookingUuid: UUID?,
    val read: Boolean,
    val createdAt: OffsetDateTime
) {
    constructor(model: NotificationModel) : this(
        uuid = model.uuid,
        type = model.type,
        bookingUuid = model.bookingUuid,
        read = model.read,
        createdAt = model.createdAt
    )
}

data class UnreadCountDto(val count: Long)
