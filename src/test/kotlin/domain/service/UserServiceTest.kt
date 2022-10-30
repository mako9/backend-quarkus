package domain.service

import common.PageConfig
import domain.model.UserModel
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
import java.util.*
import javax.inject.Inject

@QuarkusTest
class UserServiceTest {
    @Inject
    private lateinit var entityUtil: EntityUtil
    @Inject
    private lateinit var userService: UserService

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
    }

    @Test
    fun `when retrieving existing user by UUID, then correct user model is returned`() {
        val user = entityUtil.setupUser()
        val fetchedUser = userService.getUserByUuid(user.uuid)!!
        assertEquals(user.uuid, fetchedUser.uuid)
        assertEquals(user.firstName, fetchedUser.firstName)
        assertEquals(user.lastName, fetchedUser.lastName)
        assertEquals(user.mail, fetchedUser.mail)
        assertEquals(user.street, fetchedUser.street)
        assertEquals(user.houseNumber, fetchedUser.houseNumber)
        assertEquals(user.postalCode, fetchedUser.postalCode)
        assertEquals(user.city, fetchedUser.city)
        assertEquals(user.roles.toList(), fetchedUser.roles)
    }

    @Test
    fun `when retrieving non-existing user by UUID, then null is returned`() {
        assertNull(userService.getUserByUuid(UUID.randomUUID()))
    }

    @Test
    fun `when retrieving existing user by mail, then correct user model is returned`() {
        val user = entityUtil.setupUser()
        val fetchedUser = userService.getUserByMail(user.mail)!!
        assertEquals(user.uuid, fetchedUser.uuid)
        assertEquals(user.firstName, fetchedUser.firstName)
        assertEquals(user.lastName, fetchedUser.lastName)
        assertEquals(user.mail, fetchedUser.mail)
        assertEquals(user.street, fetchedUser.street)
        assertEquals(user.houseNumber, fetchedUser.houseNumber)
        assertEquals(user.postalCode, fetchedUser.postalCode)
        assertEquals(user.city, fetchedUser.city)
        assertEquals(user.roles.toList(), fetchedUser.roles)
    }

    @Test
    fun `when retrieving non-existing user by mail, then null is returned`() {
        assertNull(userService.getUserByMail("non-exiting@test.tld"))
    }

    @Test
    fun `when user page by community UUID, then correct page is returned`() {
        val userOne = entityUtil.setupUser { it.firstName = "One"; it.lastName = "One" }
        val userTwo = entityUtil.setupUser { it.firstName = "Two"; it.lastName = "Two" }
        val community = entityUtil.setupCommunity(userOne.uuid)
        entityUtil.setupUserCommunityRelation { it.communityUuid = community.uuid; it.userUuid = userTwo.uuid }

        val pageConfig = PageConfig()
        val page = userService.getUsersByCommunityUuid(community.uuid, pageConfig)
        assertEquals(1, page.totalPages)
        assertEquals(true, page.isFirstPage)
        assertEquals(true, page.isLastPage)
        assertEquals(pageConfig.pageSize, page.pageSize)
        assertEquals(pageConfig.pageNumber, page.pageNumber)
        assertEquals(2, page.content.size)
        assertEquals(UserModel(userOne), page.content.first())
        assertEquals(UserModel(userTwo), page.content.last())
    }
}