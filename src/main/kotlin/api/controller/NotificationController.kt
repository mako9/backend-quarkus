package api.controller

import api.dto.NotificationDto
import api.dto.PageDto
import api.dto.UnreadCountDto
import common.PageConfig
import domain.service.NotificationService
import jakarta.inject.Inject
import jakarta.validation.constraints.Min
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement
import org.hibernate.validator.constraints.Range
import org.jboss.resteasy.reactive.RestPath
import java.util.*

@Path("/user/notification")
@SecurityRequirement(name = "bearerAuth")
class NotificationController : Controller() {
    @Inject
    lateinit var notificationService: NotificationService

    @GET
    @Path("/")
    fun getMyNotifications(
        @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
        @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
    ): PageDto<NotificationDto> {
        val userUuid = getUserUuid()
        val page = notificationService.getNotificationsPage(userUuid, PageConfig(pageNumber, pageSize))
        return PageDto.of(page, ::NotificationDto)
    }

    @GET
    @Path("/unread-count")
    fun getUnreadCount(): UnreadCountDto {
        val userUuid = getUserUuid()
        return UnreadCountDto(notificationService.getUnreadCount(userUuid))
    }

    @PATCH
    @Path("/{uuid}/read")
    fun markRead(@RestPath uuid: UUID): Response {
        val userUuid = getUserUuid()
        notificationService.markRead(userUuid, uuid)
        return Response.noContent().build()
    }

    @PATCH
    @Path("/read-all")
    fun markAllRead(): Response {
        val userUuid = getUserUuid()
        notificationService.markAllRead(userUuid)
        return Response.noContent().build()
    }
}
