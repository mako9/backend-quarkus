package api.controller

import api.dto.*
import common.ItemCategory
import common.PageConfig
import domain.model.ItemBookingModel
import domain.model.ItemModel
import domain.model.exception.ErrorCode
import domain.model.sort.ItemBookingSortBy
import infrastructure.entity.Community
import infrastructure.entity.Item
import infrastructure.entity.ItemBooking
import infrastructure.entity.User
import io.quarkus.panache.common.Sort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.keycloak.client.KeycloakTestClient
import io.restassured.RestAssured
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testUtils.EntityUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*

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
    private lateinit var item: Item
    private lateinit var itemBooking: ItemBooking

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser { it.mail = "alice@test.tld" }
        community = entityUtil.setupCommunity()
        val communityOne = entityUtil.setupCommunity(user.uuid)
        item = entityUtil.setupItem { it.name = "aaa"; it.communityUuid = communityOne.uuid }
        itemBooking = entityUtil.setupItemBooking { it.itemUuid = item.uuid; it.userUuid = user.uuid }

        Files.createDirectories(Paths.get(imagePath))
    }

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
        File(imagePath).deleteRecursively()
    }

    @Test
    fun `when retrieving paginated items owned by requesting user with default pagination, then correct page is returned`() {
        val itemOne =
            entityUtil.setupItem { it.name = "aaa"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val itemTwo =
            entityUtil.setupItem { it.name = "bbb"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
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
                ItemDto(ItemModel(Item.find("uuid", itemOne.uuid).singleResult())),
                ItemDto(ItemModel(Item.find("uuid", itemTwo.uuid).singleResult()))
            ),
            jsonPath.getList("content", ItemDto::class.java)
        )
    }

    @Test
    fun `when retrieving detailed item, then correct item is returned`() {
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .`when`()["/api/user/item/${item.uuid}"]
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(
            ItemDetailDto(ItemModel(Item.find("uuid", item.uuid).singleResult())),
            jsonPath.getObject("", ItemDetailDto::class.java)
        )
    }

    @Test
    fun `when retrieving paginated items for my communities, then correct page is returned`() {
        val myCommunityTwo = entityUtil.setupCommunity(user.uuid)
        val itemTwo = entityUtil.setupItem { it.name = "bbb"; it.communityUuid = myCommunityTwo.uuid }
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
                ItemDto(ItemModel(Item.find("uuid", item.uuid).singleResult())),
                ItemDto(ItemModel(Item.find("uuid", itemTwo.uuid).singleResult()))
            ),
            jsonPath.getList("content", ItemDto::class.java)
        )
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
        val ownedItem =
            entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val dto = ItemRequestDto(
            name = "updated ",
            categories = listOf(ItemCategory.GARDENING),
            communityUuid = community.uuid
        )
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .patch("/api/user/item/${ownedItem.uuid}")
            .then()
            .statusCode(200)
            .extract()
            .response()
            .jsonPath()

        assertEquals(ownedItem.uuid, jsonPath.getUUID("uuid"))
        assertEquals(dto.name, jsonPath.getString("name"))
        assertEquals(dto.categories.map { it.name }, jsonPath.getList("categories", String::class.java))
        assertEquals(dto.communityUuid, jsonPath.getUUID("communityUuid"))
    }

    @Test
    fun `when updating item to non-existing community, then status 404 is returned`() {
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
        val ownedItem =
            entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/item/${ownedItem.uuid}")
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
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/item/${item.uuid}")
            .then()
            .statusCode(403)
    }

    @Test
    fun `when getting image UUIDs for an item, then a list of UUIDs is returned`() {
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
        val ownedItem =
            entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val file = File.createTempFile("test", ".jpg")
        file.deleteOnExit()

        val response = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.MULTIPART)
            .multiPart(file)
            .post("/api/user/item/${ownedItem.uuid}/image")
            .then()
            .statusCode(201)
            .extract()
            .response()

        assertTrue(response.header("Location").contains("/image/"))
    }

    @Test
    fun `when getting item image by UUID, then a correct status and file is returned`() {
        val file = File("$imagePath/test.jpg")
        file.createNewFile()
        val itemImage = entityUtil.setupItemImage { it.itemUuid = item.uuid; it.path = file.path }

        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .get("/api/user/item/image/${itemImage.uuid}")
            .then()
            .statusCode(200)
    }

    @Test
    fun `when deleting item image by UUID, then a correct status is returned`() {
        val ownedItem =
            entityUtil.setupItem { it.name = "one"; it.communityUuid = community.uuid; it.userUuid = user.uuid }
        val file = File("$imagePath/test.jpg")
        file.createNewFile()
        val itemImage = entityUtil.setupItemImage { it.itemUuid = ownedItem.uuid; it.path = file.path }

        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .delete("/api/user/item/image/${itemImage.uuid}")
            .then()
            .statusCode(204)
    }

    @Test
    fun `when retrieving paginated item bookings by requesting user, then correct page is returned`() {
        val itemBookingTwo = entityUtil.setupItemBooking {
            it.itemUuid = item.uuid
            it.userUuid = user.uuid
            it.startAt = OffsetDateTime.now().plusDays(2)
            it.endAt = OffsetDateTime.now().plusDays(3)
        }

        run {
            val jsonPath = RestAssured.given().auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .`when`()["/api/user/item/booking"]
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
                    ItemBookingDto(ItemBookingModel(ItemBooking.find("uuid", itemBookingTwo.uuid).singleResult())),
                    ItemBookingDto(ItemBookingModel(ItemBooking.find("uuid", itemBooking.uuid).singleResult()))
                ),
                jsonPath.getList("content", ItemBookingDto::class.java)
            )
        }

        run {
            val pageConfig = PageConfig(1, 1)
            val jsonPath = RestAssured.given().auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .queryParam("pageNumber", pageConfig.pageNumber)
                .queryParam("pageSize", pageConfig.pageSize)
                .queryParam("sortBy", ItemBookingSortBy.START_AT)
                .queryParam("sortDirection", Sort.Direction.Ascending)
                .`when`()["/api/user/item/booking"]
                .then()
                .statusCode(200)
                .extract()
                .response()
                .jsonPath()

            assertEquals(false, jsonPath.getBoolean("firstPage"))
            assertEquals(true, jsonPath.getBoolean("lastPage"))
            assertEquals(1, jsonPath.getInt("pageNumber"))
            assertEquals(1, jsonPath.getInt("pageSize"))
            assertEquals(2, jsonPath.getInt("totalElements"))
            assertEquals(2, jsonPath.getInt("totalPages"))
            assertEquals(
                listOf(
                    ItemBookingDto(ItemBookingModel(ItemBooking.find("uuid", itemBookingTwo.uuid).singleResult()))
                ),
                jsonPath.getList("content", ItemBookingDto::class.java)
            )
        }
    }

    @Test
    fun `when retrieving paginated item bookings per item, then correct page is returned`() {
        val itemBookingTwo = entityUtil.setupItemBooking {
            it.itemUuid = item.uuid
            it.userUuid = user.uuid
            it.startAt = OffsetDateTime.now().plusDays(2)
            it.endAt = OffsetDateTime.now().plusDays(3)
        }

        run {
            val jsonPath = RestAssured.given().auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .`when`()["/api/user/item/${item.uuid}/booking"]
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
                    ItemBookingDto(ItemBookingModel(ItemBooking.find("uuid", itemBookingTwo.uuid).singleResult())),
                    ItemBookingDto(ItemBookingModel(ItemBooking.find("uuid", itemBooking.uuid).singleResult()))
                ),
                jsonPath.getList("content", ItemBookingDto::class.java)
            )
        }

        run {
            val pageConfig = PageConfig(1, 1)
            val jsonPath = RestAssured.given().auth().oauth2(accessToken)
                .contentType(ContentType.JSON)
                .queryParam("pageNumber", pageConfig.pageNumber)
                .queryParam("pageSize", pageConfig.pageSize)
                .queryParam("sortBy", ItemBookingSortBy.CREATED_AT)
                .queryParam("sortDirection", Sort.Direction.Descending)
                .`when`()["/api/user/item/${item.uuid}/booking"]
                .then()
                .statusCode(200)
                .extract()
                .response()
                .jsonPath()

            assertEquals(false, jsonPath.getBoolean("firstPage"))
            assertEquals(true, jsonPath.getBoolean("lastPage"))
            assertEquals(1, jsonPath.getInt("pageNumber"))
            assertEquals(1, jsonPath.getInt("pageSize"))
            assertEquals(2, jsonPath.getInt("totalElements"))
            assertEquals(2, jsonPath.getInt("totalPages"))
            assertEquals(
                listOf(
                    ItemBookingDto(ItemBookingModel(ItemBooking.find("uuid", itemBooking.uuid).singleResult()))
                ),
                jsonPath.getList("content", ItemBookingDto::class.java)
            )
        }
    }

    @Test
    fun `when retrieving non-existing item booking, then status 404 is returned`() {
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .`when`()["/api/user/item/booking/${UUID.randomUUID()}"]
            .then()
            .statusCode(404)
    }

    @Test
    fun `when booking item, then status 200 and link to item is returned`() {
        val dto = ItemBookingRequestDto(
            startAt = OffsetDateTime.now().plusDays(5),
            endAt = OffsetDateTime.now().plusDays(6)
        )
        val response = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .post("/api/user/item/${item.uuid}/booking")
            .then()
            .statusCode(201)
            .extract()
            .response()

        assertTrue(response.header("location").contains("/booking/"))
    }

    @Test
    fun `when booking non-existing item, then status 404 is returned`() {
        val dto = ItemBookingRequestDto(
            startAt = OffsetDateTime.now().plusDays(5),
            endAt = OffsetDateTime.now().plusDays(6)
        )
        RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .post("/api/user/item/${UUID.randomUUID()}/booking")
            .then()
            .statusCode(404)
    }

    @Test
    fun `when booking item with invalid parameters, then status 400 and correct code is returned`() {
        val dto = ItemBookingRequestDto(
            startAt = OffsetDateTime.now().plusDays(7),
            endAt = OffsetDateTime.now().plusDays(6)
        )
        val jsonPath = RestAssured.given().auth().oauth2(accessToken)
            .contentType(ContentType.JSON)
            .body(dto)
            .post("/api/user/item/${item.uuid}/booking")
            .then()
            .statusCode(400)
            .extract()
            .response()
            .jsonPath()

        assertEquals(listOf(ErrorCode.InvalidInputParam.code), jsonPath.getList("codes", String::class.java))
    }
}