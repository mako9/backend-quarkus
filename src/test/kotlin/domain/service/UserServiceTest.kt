package domain.service

import common.JwtUserInfo
import common.PageConfig
import common.UserRole
import domain.model.UserModel
import infrastructure.entity.User
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
import testUtils.UnitTestProfile
import java.util.*

@QuarkusTest
@TestProfile(UnitTestProfile::class)
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
    fun `when retrieving existing user by JwtUserInfo, then correct user model is returned`() {
        val user = entityUtil.setupUser()
        val jwtUserInfo = JwtUserInfo(user.mail, user.firstName, user.lastName, user.roles.toList())
        val fetchedUser = userService.getUserByJwtUserInfo(jwtUserInfo)
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
    fun `when retrieving non-existing user by JwtUserInfo, then correct user model is returned and user is created`() {
        val jwtUserInfo = JwtUserInfo("mail@mail.tld", "John", "Doe", listOf(UserRole.USER))
        val fetchedUser = userService.getUserByJwtUserInfo(jwtUserInfo)
        assertEquals(jwtUserInfo.firstName, fetchedUser.firstName)
        assertEquals(jwtUserInfo.lastName, fetchedUser.lastName)
        assertEquals(jwtUserInfo.mail, fetchedUser.mail)
        assertEquals(jwtUserInfo.roles.toList(), fetchedUser.roles)

        val persistedUser = User.find("uuid", fetchedUser.uuid).firstResult()
        assertEquals(persistedUser?.firstName, fetchedUser.firstName)
        assertEquals(persistedUser?.lastName, fetchedUser.lastName)
        assertEquals(persistedUser?.mail, fetchedUser.mail)
        assertEquals(persistedUser?.roles?.toList(), fetchedUser.roles)
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