package domain.service

import common.NotificationType
import common.PageConfig
import domain.model.exception.CustomForbiddenException
import infrastructure.entity.Notification
import infrastructure.entity.User
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import testUtils.EntityUtil
import testUtils.UnitTestProfile

@QuarkusTest
@TestProfile(UnitTestProfile::class)
class NotificationServiceTest {

    @Inject
    private lateinit var entityUtil: EntityUtil

    @Inject
    private lateinit var notificationService: NotificationService

    private lateinit var user: User
    private lateinit var otherUser: User

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser()
        otherUser = entityUtil.setupUser()
    }

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
    }

    @Test
    fun `when getting notification page, then only user's notifications are returned`() {
        val notificationOne = entityUtil.setupNotification { it.userUuid = user.uuid }
        val notificationTwo = entityUtil.setupNotification { it.userUuid = user.uuid }
        entityUtil.setupNotification { it.userUuid = otherUser.uuid }

        val page = notificationService.getNotificationsPage(user.uuid, PageConfig())

        assertEquals(2, page.totalElements)
        assertTrue(page.content.any { it.uuid == notificationOne.uuid })
        assertTrue(page.content.any { it.uuid == notificationTwo.uuid })
    }

    @Test
    fun `when getting unread count, then only unread notifications are counted`() {
        entityUtil.setupNotification { it.userUuid = user.uuid; it.read = false }
        entityUtil.setupNotification { it.userUuid = user.uuid; it.read = false }
        entityUtil.setupNotification { it.userUuid = user.uuid; it.read = true }
        entityUtil.setupNotification { it.userUuid = otherUser.uuid; it.read = false }

        val count = notificationService.getUnreadCount(user.uuid)

        assertEquals(2L, count)
    }

    @Test
    fun `when marking notification as read, then notification is marked read`() {
        val notification = entityUtil.setupNotification { it.userUuid = user.uuid; it.read = false }

        notificationService.markRead(user.uuid, notification.uuid)

        val stored = Notification.find("uuid", notification.uuid).firstResult()
        assertEquals(true, stored?.read)
    }

    @Test
    fun `when marking notification of other user as read, then exception is thrown`() {
        val notification = entityUtil.setupNotification { it.userUuid = user.uuid }

        assertThrows<CustomForbiddenException> {
            notificationService.markRead(otherUser.uuid, notification.uuid)
        }
    }

    @Test
    fun `when marking all notifications as read, then all user notifications are read`() {
        entityUtil.setupNotification { it.userUuid = user.uuid; it.read = false }
        entityUtil.setupNotification { it.userUuid = user.uuid; it.read = false }
        val otherNotification = entityUtil.setupNotification { it.userUuid = otherUser.uuid; it.read = false }

        notificationService.markAllRead(user.uuid)

        val userNotifications = Notification.find("userUuid", user.uuid).list()
        assertTrue(userNotifications.all { it.read })

        val otherUserNotification = Notification.find("uuid", otherNotification.uuid).firstResult()
        assertEquals(false, otherUserNotification?.read)
    }
}
