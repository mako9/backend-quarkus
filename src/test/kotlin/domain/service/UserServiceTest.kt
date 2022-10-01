package domain.service

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
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
}