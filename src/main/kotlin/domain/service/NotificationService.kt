package domain.service

import common.NotificationType
import common.PageConfig
import domain.event.BookingEvent
import domain.model.NotificationModel
import domain.model.PageModel
import domain.model.exception.CustomBadRequestException
import domain.model.exception.CustomForbiddenException
import domain.model.exception.ErrorCode
import infrastructure.entity.Notification
import infrastructure.entity.User
import io.quarkus.logging.Log
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.ObservesAsync
import jakarta.inject.Inject
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class NotificationService {

    @Inject
    lateinit var mailer: Mailer

    @Transactional
    fun onBookingEvent(@ObservesAsync event: BookingEvent) {
        val notification = Notification(
            userUuid = event.recipientUserUuid,
            type = event.notificationType,
            bookingUuid = event.bookingUuid
        )
        notification.persist()

        val recipient = User.find("uuid", event.recipientUserUuid).firstResult()
        val recipientEmail = recipient?.mail ?: return

        val subject = emailSubject(event.notificationType)
        val body = emailBody(event)

        try {
            mailer.send(Mail.withText(recipientEmail, subject, body))
        } catch (e: Exception) {
            Log.warn("Failed to send notification email to $recipientEmail: ${e.message}")
        }
    }

    fun getNotificationsPage(userUuid: UUID, pageConfig: PageConfig): PageModel<NotificationModel> {
        val query = Notification
            .find("userUuid", Sort.by("createdAt", Sort.Direction.Descending), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::NotificationModel)
    }

    fun getUnreadCount(userUuid: UUID): Long {
        return Notification.count("userUuid = ?1 AND read = false", userUuid)
    }

    fun markRead(userUuid: UUID, uuid: UUID) {
        val notification = Notification.find("uuid", uuid).firstResult()
            ?: throw EntityNotFoundException("No notification for UUID: $uuid")
        if (notification.userUuid != userUuid) throw CustomForbiddenException("User not allowed to access this notification")
        notification.read = true
        notification.persist()
    }

    @Transactional
    fun markAllRead(userUuid: UUID) {
        Notification.update("read = true WHERE userUuid = ?1", userUuid)
    }

    private fun emailSubject(type: NotificationType): String = when (type) {
        NotificationType.BOOKING_REQUEST -> "New booking request for your item"
        NotificationType.BOOKING_CONFIRMED -> "Your booking has been confirmed"
        NotificationType.BOOKING_DECLINED -> "Your booking has been declined"
        NotificationType.BOOKING_CANCELLED -> "A booking has been cancelled"
    }

    private fun emailBody(event: BookingEvent): String = when (event.notificationType) {
        NotificationType.BOOKING_REQUEST ->
            "You have a new booking request for \"${event.itemName}\" from ${event.requesterName}."
        NotificationType.BOOKING_CONFIRMED ->
            "Your booking for \"${event.itemName}\" has been confirmed."
        NotificationType.BOOKING_DECLINED ->
            "Your booking request for \"${event.itemName}\" has been declined."
        NotificationType.BOOKING_CANCELLED ->
            "A booking for \"${event.itemName}\" has been cancelled."
    }
}
