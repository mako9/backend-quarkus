package api.controller

import api.dto.UserPatchDto
import infrastructure.entity.User
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.keycloak.client.KeycloakTestClient
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
import javax.inject.Inject

@QuarkusIntegrationTest
class UserControllerIntegrationTest: UserControllerTest()

@QuarkusTest
class UserControllerTest {
    @Inject
    private lateinit var entityUtil: EntityUtil
    private val keycloakClient = KeycloakTestClient()

    @Nested
    @DisplayName("given the user is authenticated")
    inner class GivenUserIsAuthenticated {
        private val accessToken = keycloakClient.getAccessToken("alice")

        @AfterEach
        fun afterEach() {
            entityUtil.deleteAllData()
        }

        @Test
        fun `when retrieving own user information with non-existing user entity, then correct data is returned and user exists in database`() {
            RestAssured.given().auth().oauth2(accessToken)
                .`when`()["/api/users/me"]
                .then()
                .statusCode(200)
                .body("firstName", equalTo("alice"))
                .body("lastName", equalTo("alice"))
                .body("mail", equalTo("alice@test.tld"))

            assertNotNull(User.find("mail", "alice@test.tld").firstResult())
        }

        @Test
        fun `when retrieving own user information with existing user entity, then correct data is returned`() {
            val existingUser = entityUtil.setupUser()
            RestAssured.given().auth().oauth2(accessToken)
                .`when`()["/api/users/me"]
                .then()
                .statusCode(200)
                .body("firstName", equalTo(existingUser.firstName))
                .body("lastName", equalTo(existingUser.lastName))
                .body("mail", equalTo(existingUser.mail))

            assertEquals(1, User.count("mail", existingUser.mail))
        }

        @Test
        fun `when updating own user information, then correct data is returned`() {
            val existingUser = entityUtil.setupUser()
            RestAssured.given().auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .body(UserPatchDto(firstName = "new"))
                .patch("/api/users/me")
                .then()
                .statusCode(200)
                .body("firstName", equalTo("new"))
                .body("mail", equalTo(existingUser.mail))

            assertEquals(1, User.count("mail", existingUser.mail))
        }
    }


    companion object {
        init {
            RestAssured.useRelaxedHTTPSValidation()
        }
    }
}