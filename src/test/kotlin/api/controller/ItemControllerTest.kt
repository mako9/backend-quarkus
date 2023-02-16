package api.controller

import api.dto.ItemDetailDto
import api.dto.ItemDto
import api.dto.ItemRequestDto
import common.ItemCategory
import domain.model.ItemModel
import infrastructure.entity.Community
import infrastructure.entity.Item
import infrastructure.entity.User
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.keycloak.client.KeycloakTestClient
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

@QuarkusTest
class ItemControllerTest {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String
    @Inject
    private lateinit var entityUtil: EntityUtil
    private val keycloakClient = KeycloakTestClient()
    private val accessToken = keycloakClient.getAccessToken("alice")
    private lateinit var user: User
    private lateinit var community: Community

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser { it.mail = "alice@test.tld" }
        community = entityUtil.setupCommunity()

        Files.createDirectories(Paths.get(imagePath))
    }

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
        File(imagePath).deleteRecursively()
    }

    @Test
    fun `when retrieving paginated items owned by requesting user with default pagination, then correct page is returned`() {
        val itemOne = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val itemTwo = entityUtil.setupItem { it.name = "two"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .`when`()["/api/user/item/owned"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(true, jsonPath.getBoolean("firstPage"))
        assertEquals(true, jsonPath.getBoolean("lastPage"))
        assertEquals(0, jsonPath.getInt("pageNumber"))
        assertEquals(50, jsonPath.getInt("pageSize"))
        assertEquals(2, jsonPath.getInt("totalElements"))
        assertEquals(1, jsonPath.getInt("totalPages"))
        assertEquals(
            listOf(
                ItemDto(ItemModel(itemOne)),
                ItemDto(ItemModel(itemTwo))
            ),
            jsonPath.getList("content", ItemDto::class.java))
    }

    @Test
    fun `when retrieving detailed item, then correct item is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid }
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .`when`()["/api/user/item/${item.uuid}"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(ItemDetailDto(ItemModel(Item.find("uuid", item.uuid).singleResult())), jsonPath.getObject("", ItemDetailDto::class.java))
    }

    @Test
    fun `when retrieving paginated items for my communities, then correct page is returned`() {
        val myCommunityOne = entityUtil.setupCommunity(user.uuid)
        val myCommunityTwo = entityUtil.setupCommunity(user.uuid)
        val itemOne = entityUtil.setupItem { it.name = "one"; it.communityUuid = myCommunityOne.uuid }
        val itemTwo = entityUtil.setupItem { it.name = "two"; it.communityUuid = myCommunityTwo.uuid }
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .`when`()["/api/user/item/my"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(true, jsonPath.getBoolean("firstPage"))
        assertEquals(true, jsonPath.getBoolean("lastPage"))
        assertEquals(0, jsonPath.getInt("pageNumber"))
        assertEquals(50, jsonPath.getInt("pageSize"))
        assertEquals(2, jsonPath.getInt("totalElements"))
        assertEquals(1, jsonPath.getInt("totalPages"))
        assertEquals(
            listOf(
                ItemDto(ItemModel(itemOne)),
                ItemDto(ItemModel(itemTwo))
            ),
            jsonPath.getList("content", ItemDto::class.java))
    }

    @Test
    fun `when retrieving non-existing detailed item, then status 404 is returned`() {
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .`when`()["/api/user/item/${UUID.randomUUID()}"]
            .then()
            .statusCode(404)
    }

    @Test
    fun `when creating item successfully, then status 200 and ItemDto is returned`() {
        val dto = ItemRequestDto(
            name = "Test",
            categories = listOf(ItemCategory.GARDENING),
            communityUuid = community.uuid
        )
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .post("/api/user/item")
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(dto.name, jsonPath.getString("name"))
        assertEquals(dto.categories.map { it.name }, jsonPath.getList("categories", String::class.java))
        assertEquals(dto.communityUuid, jsonPath.getUUID("communityUuid"))
    }

    @Test
    fun `when creating item for non-existing community, then status 404 is returned`() {
        val dto = ItemRequestDto(
            name = "Test",
            categories = listOf(ItemCategory.GARDENING),
            communityUuid = UUID.randomUUID()
        )
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .post("/api/user/item")
            .then()
            .statusCode(404)
    }

    @Test
    fun `when updating item successfully, then status 200 and ItemDto is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val dto = ItemRequestDto(
            name = "updated ",
            categories = listOf(ItemCategory.GARDENING),
            communityUuid = community.uuid
        )
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .patch("/api/user/item/${item.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(item.uuid, jsonPath.getUUID("uuid"))
        assertEquals(dto.name, jsonPath.getString("name"))
        assertEquals(dto.categories.map { it.name }, jsonPath.getList("categories", String::class.java))
        assertEquals(dto.communityUuid, jsonPath.getUUID("communityUuid"))
    }

    @Test
    fun `when updating item to non-existing community, then status 404 is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid }
        val dto = ItemRequestDto(
            name = "Test",
            categories = listOf(ItemCategory.GARDENING),
            communityUuid = UUID.randomUUID()
        )
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .patch("/api/user/item/${item.uuid}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `when deleting item successfully, then status 204 is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/item/${item.uuid}")
            .then()
            .statusCode(204)
    }

    @Test
    fun `when deleting non-existing item, then status 404 is returned`() {
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/item/${UUID.randomUUID()}")
            .then()
            .statusCode(404)
    }

    @Test
    fun `when deleting item of other user, then status 403 is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid }
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/item/${item.uuid}")
            .then()
            .statusCode(403)
    }

    @Test
    fun `when getting image UUIDs for an item, then a list of UUIDs is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid }
        val itemImage = entityUtil.setupItemImage { it.itemUuid = item.uuid }
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .get("/api/user/item/${item.uuid}/image")
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(itemImage.uuid, jsonPath.getList("", UUID::class.java).first())
    }

    @Test
    fun `when uploading image for an item, then a correct status is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid }
        val file = File.createTempFile("test", ".jpg")
        file.deleteOnExit()

        val response = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.MULTIPART)
            .multiPart(file)
            .post("/api/user/item/${item.uuid}/image")
            .then()
            .statusCode(201)
            .extract()
            .response()
            .asByteArray()

        assertNotNull(response)
    }

    @Test
    fun `when getting item image by UUID, then a correct status and file is returned`() {
        val item = entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid }
        val file = File("$imagePath/test.jpg")
        file.createNewFile()
        val itemImage = entityUtil.setupItemImage { it.itemUuid = item.uuid; it.path = file.path }

        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .get("/api/user/item/image/${itemImage.uuid}")
            .then()
            .statusCode(200)
    }
}