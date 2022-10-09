package api.controller

import api.dto.CommunityDto
import api.dto.CommunityRequestDto
import common.PageConfig
import domain.model.CommunityModel
import domain.model.sort.CommunitySortBy
import infrastructure.entity.User
import io.quarkus.panache.common.Sort
import io.quarkus.test.junit.QuarkusIntegrationTest
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.keycloak.client.KeycloakTestClient
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
import java.util.*
import javax.inject.Inject

@QuarkusIntegrationTest
class CommunityControllerIntegrationTest: CommunityControllerTest()

@QuarkusTest
class CommunityControllerTest {
    @Inject
    private lateinit var entityUtil: EntityUtil
    private val keycloakClient = KeycloakTestClient()
    private val accessToken = keycloakClient.getAccessToken("alice")
    private lateinit var user: User

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser { it.mail = "alice@test.tld" }
    }

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
    }

    @Test
    fun `when retrieving paginated communities with default pagination, then correct page is returned`() {
        val communityOne = entityUtil.setupCommunity { it.name = "one"; it.city = "one" }
        val communityTwo = entityUtil.setupCommunity { it.name = "two"; it.city = "two" }
        val pageConfig = PageConfig()

        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(pageConfig)
            .`when`()["/api/user/community"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(true, jsonPath.getBoolean("firstPage"))
        assertEquals(true, jsonPath.getBoolean("lastPage"))
        assertEquals(pageConfig.pageNumber, jsonPath.getInt("pageNumber"))
        assertEquals(pageConfig.pageSize, jsonPath.getInt("pageSize"))
        assertEquals(2, jsonPath.getInt("totalElements"))
        assertEquals(1, jsonPath.getInt("totalPages"))
        assertEquals(
            listOf(
                CommunityDto(CommunityModel(communityOne)),
                CommunityDto(CommunityModel(communityTwo))
            ),
            jsonPath.getList("content", CommunityDto::class.java))
    }

    @Test
    fun `when retrieving paginated communities with specified pagination, then correct page is returned`() {
        entityUtil.setupCommunity { it.name = "one"; it.city = "one" }
        val communityTwo = entityUtil.setupCommunity { it.name = "two"; it.city = "two" }
        val pageConfig = PageConfig(1, 1)

        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(pageConfig)
            .`when`()["/api/user/community"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(false, jsonPath.getBoolean("firstPage"))
        assertEquals(true, jsonPath.getBoolean("lastPage"))
        assertEquals(pageConfig.pageNumber, jsonPath.getInt("pageNumber"))
        assertEquals(pageConfig.pageSize, jsonPath.getInt("pageSize"))
        assertEquals(2, jsonPath.getInt("totalElements"))
        assertEquals(2, jsonPath.getInt("totalPages"))
        assertEquals(
            listOf(
                CommunityDto(CommunityModel(communityTwo))
            ),
            jsonPath.getList("content", CommunityDto::class.java))
    }

    @Test
    fun `when retrieving paginated communities with specified sorting, then correct page is returned`() {
        val communityOne = entityUtil.setupCommunity { it.name = "one"; it.city = "one" }
        val communityTwo = entityUtil.setupCommunity { it.name = "two"; it.city = "two" }
        val pageConfig = PageConfig()

        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(pageConfig)
            .queryParam("sortBy", CommunitySortBy.CITY)
            .queryParam("sortDirection", Sort.Direction.Descending)
            .`when`()["/api/user/community"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(
            listOf(
                CommunityDto(CommunityModel(communityTwo)),
                CommunityDto(CommunityModel(communityOne))
            ),
            jsonPath.getList("content", CommunityDto::class.java))
    }

    @Test
    fun `when creating community, then community is returned`() {
        val requestDto = CommunityRequestDto(
            "test",
            null,
            null,
            null,
            null,
            10
        )
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(requestDto)
            .post("/api/user/community")
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(requestDto.name, jsonPath.getString("name"))
        assertEquals(10, jsonPath.getInt("radius"))
        assertEquals(user.uuid, jsonPath.getUUID("adminUuid"))
    }

    @Test
    fun `when updating community, then updated community is returned`() {
        val community = entityUtil.setupCommunity { it.adminUuid = user.uuid }
        val requestDto = CommunityRequestDto(
            "test - update",
            "street - update",
            null,
            null,
            null,
            11
        )
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(requestDto)
            .patch("/api/user/community/${community.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(requestDto.name, jsonPath.getString("name"))
        assertEquals(requestDto.street, jsonPath.getString("street"))
        assertEquals(requestDto.radius, jsonPath.getInt("radius"))
    }

    @Test
    fun `when updating community when not admin, then 403 is returned`() {
        val community = entityUtil.setupCommunity()
        val requestDto = CommunityRequestDto(
            "test - update",
            "street - update",
            null,
            null,
            null,
            11
        )
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(requestDto)
            .patch("/api/user/community/${community.uuid}")
            .then()
            .statusCode(403)
    }

    @Test
    fun `when updating non existing community, then 404 is returned`() {
        val requestDto = CommunityRequestDto(
            "test - update",
            "street - update",
            null,
            null,
            null,
            11
        )
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(requestDto)
            .patch("/api/user/community/${UUID.randomUUID()}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `when deleting community, then status 204 is returned`() {
        val community = entityUtil.setupCommunity { it.adminUuid = user.uuid }
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/community/${community.uuid}")
            .then()
            .statusCode(204)
    }

    @Test
    fun `when deleting community when not being admin, then status 403 is returned`() {
        val community = entityUtil.setupCommunity()
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/community/${community.uuid}")
            .then()
            .statusCode(403)
    }

    @Test
    fun `when deleting non-existing community, then status 404 is returned`() {
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/community/${UUID.randomUUID()}")
            .then()
            .statusCode(404)
    }

    companion object {
        init {
            RestAssured.useRelaxedHTTPSValidation()
        }
    }
}