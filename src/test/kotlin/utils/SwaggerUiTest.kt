package utils

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.Test

@QuarkusTest
class SwaggerUiTest {
    // Note: Swagger UI is only available when Quarkus is started in dev or test mode
    @Test
    fun testSwaggerUi() {
        RestAssured.given()
            .`when`()["/q/swagger-ui"]
            .then()
            .statusCode(200)
            .body(CoreMatchers.containsString("/openapi"))
    }
}