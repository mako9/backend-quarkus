package domain.event

import common.NotificationType
import java.util.UUID

data class BookingEvent(
    val notificationType: NotificationType,
    val recipientUserUuid: UUID,
    val bookingUuid: UUID,
    val itemName: String,
    val requesterName: String
)
