package utils

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.Test

@QuarkusTest
class OpenApiTest {
    @Test
    fun testOpenApi() {
        RestAssured.given()
            .`when`()["/q/openapi"]
            .then()
            .statusCode(200)
            .body(CoreMatchers.containsString("openapi"))
    }
}