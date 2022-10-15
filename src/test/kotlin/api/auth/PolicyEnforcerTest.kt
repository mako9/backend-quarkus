package api.auth

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.keycloak.client.KeycloakTestClient
import io.restassured.RestAssured
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

@QuarkusIntegrationTest
class NativePolicyEnforcerIT : PolicyEnforcerTest()

@QuarkusTest
class PolicyEnforcerTest {
    private val keycloakClient = KeycloakTestClient()

    @Test
    fun testAccessUserResource() {
        RestAssured.given().auth().oauth2(getAccessToken("alice"))
            .`when`()["/api/user/me"]
            .then()
            .statusCode(200)
        RestAssured.given().auth().oauth2(getAccessToken("jdoe"))
            .`when`()["/api/user/me"]
            .then()
            .statusCode(200)
    }

    @Test
    fun testAccessAdminResource() {
        RestAssured.given().auth().oauth2(getAccessToken("alice"))
            .`when`()["/api/admin"]
            .then()
            .statusCode(403)
        RestAssured.given().auth().oauth2(getAccessToken("jdoe"))
            .`when`()["/api/admin"]
            .then()
            .statusCode(403)
        RestAssured.given().auth().oauth2(getAccessToken("admin"))
            .`when`()["/api/admin"]
            .then()
            .statusCode(200)
    }

    @Test
    fun testPublicResource() {
        RestAssured.given()
            .`when`()["/api/public"]
            .then()
            .statusCode(204)
    }

    private fun getAccessToken(userName: String): String {
        return keycloakClient.getAccessToken(userName)
    }

    companion object {
        init {
            RestAssured.useRelaxedHTTPSValidation()
        }
    }
}