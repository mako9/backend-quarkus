package domain.model

import common.NotificationType
import infrastructure.entity.Notification
import java.time.OffsetDateTime
import java.util.*

data class NotificationModel(
    val uuid: UUID,
    val userUuid: UUID,
    val type: NotificationType,
    val bookingUuid: UUID?,
    val read: Boolean,
    val createdAt: OffsetDateTime
) {
    constructor(notification: Notification) : this(
        uuid = notification.uuid,
        userUuid = notification.userUuid,
        type = notification.type,
        bookingUuid = notification.bookingUuid,
        read = notification.read,
        createdAt = notification.createdAt
    )
}
