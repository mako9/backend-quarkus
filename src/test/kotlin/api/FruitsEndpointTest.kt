import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Test


@QuarkusTest
class FruitsEndpointTest {
    @Test
    fun `List all, should have all 3 fruits the database has initially`() {
        given()
            .`when`().get("/api/public/fruits")
            .then()
            .statusCode(200)
            .body(
                containsString("Cherry"),
                containsString("Apple"),
                containsString("Banana")
            )

        //Delete the Cherry:
        given()
            .`when`().delete("/api/public/fruits/1")
            .then()
            .statusCode(204)

        //List all, cherry should be missing now:
        given()
            .`when`().get("/api/public/fruits")
            .then()
            .statusCode(200)
            .body(
                not(containsString("Cherry")),
                containsString("Apple"),
                containsString("Banana")
            )
    }
}