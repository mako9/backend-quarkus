package domain.service

import com.fasterxml.jackson.databind.ObjectMapper
import common.ItemCategory
import common.PageConfig
import domain.model.ItemBookingModel
import domain.model.ItemModel
import domain.model.TimeIntervalModel
import domain.model.exception.CustomBadRequestException
import domain.model.exception.CustomForbiddenException
import domain.model.exception.ErrorCode
import domain.model.sort.ItemBookingSortBy
import domain.model.sort.ItemSortBy
import infrastructure.entity.*
import io.quarkus.panache.common.Sort
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import jakarta.persistence.EntityNotFoundException
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import testUtils.AssertUtil.assertOffsetDateTimeEquals
import testUtils.EntityUtil
import testUtils.UnitTestProfile
import testUtils.mock.FileUploadMock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.io.path.Path

@QuarkusTest
@TestProfile(UnitTestProfile::class)
class ItemServiceTest {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String

    @Inject
    private lateinit var entityUtil: EntityUtil

    @Inject
    private lateinit var itemService: ItemService

    private lateinit var user: User
    private lateinit var requestingUser: User
    private lateinit var community: Community
    private lateinit var itemOne: Item
    private lateinit var itemTwo: Item
    private lateinit var itemThree: Item
    private lateinit var itemFour: Item
    private lateinit var itemBookingOne: ItemBooking
    private lateinit var itemBookingTwo: ItemBooking

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser()
        requestingUser = entityUtil.setupUser()
        community = entityUtil.setupCommunity()
        itemOne = entityUtil.setupItem {
            it.name = "aaa"; it.communityUuid = community.uuid; it.userUuid = user.uuid; it.city =
            "aaa"; it.availableUntil = OffsetDateTime.now().plusDays(4)
        }
        itemTwo = entityUtil.setupItem {
            it.name = "bbb"; it.communityUuid = community.uuid; it.userUuid = user.uuid; it.city =
            "bbb"; it.availability =
            ObjectMapper().writeValueAsString(
                listOf(
                    TimeIntervalModel(1, 1, 60, 180)
                )
            )
        }
        itemThree = entityUtil.setupItem { it.name = "ccc"; it.communityUuid = community.uuid; it.city = "aaa" }
        itemFour = entityUtil.setupItem { it.name = "ddd"; it.communityUuid = community.uuid; it.city = "bbb" }

        itemBookingOne = entityUtil.setupItemBooking { it.itemUuid = itemThree.uuid; it.userUuid = requestingUser.uuid }
        itemBookingTwo = entityUtil.setupItemBooking {
            it.itemUuid = itemThree.uuid; it.userUuid = requestingUser.uuid; it.startAt =
            OffsetDateTime.now().plusDays(7); it.endAt = OffsetDateTime.now().plusDays(8)
        }

        Files.createDirectories(Paths.get(imagePath))
    }

    @AfterEach
    fun afterEach() {
        entityUtil.deleteAllData()
        File(imagePath).deleteRecursively()
    }

    @Test
    fun `when retrieving paginated items for specific community, then correct page of community models is returned`() {
        val pageConfig = PageConfig()
        val communityPage =
            itemService.getItemsPageOfCommunities(listOf(community.uuid), user.uuid, pageConfig, null, null)
        assertEquals(1, communityPage.totalPages)
        assertEquals(true, communityPage.isFirstPage)
        assertEquals(true, communityPage.isLastPage)
        assertEquals(pageConfig.pageSize, communityPage.pageSize)
        assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
        assertEquals(2, communityPage.content.size)
        assertEquals(ItemModel(Item.find("uuid", itemThree.uuid).singleResult()), communityPage.content.first())
        assertEquals(ItemModel(Item.find("uuid", itemFour.uuid).singleResult()), communityPage.content.last())
    }

    @Test
    fun `when retrieving paginated items for specific community with pagination and sorting, then correct page of community models is returned`() {
        val pageConfig = PageConfig(pageSize = 1, pageNumber = 1)
        val communityPage = itemService.getItemsPageOfCommunities(
            listOf(community.uuid),
            user.uuid,
            pageConfig,
            ItemSortBy.NAME,
            Sort.Direction.Descending
        )
        assertEquals(2, communityPage.totalPages)
        assertEquals(false, communityPage.isFirstPage)
        assertEquals(true, communityPage.isLastPage)
        assertEquals(pageConfig.pageSize, communityPage.pageSize)
        assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
        assertEquals(1, communityPage.content.size)
        assertEquals(ItemModel(Item.find("uuid", itemThree.uuid).singleResult()), communityPage.content.first())
    }

    @Test
    fun `when retrieving my paginated items, then correct page of community models is returned`() {
        // item of different user
        entityUtil.setupItem { it.communityUuid = community.uuid }
        val pageConfig = PageConfig()
        val communityPage = itemService.getItemsPageOfUser(user.uuid, pageConfig, null, null)
        assertEquals(1, communityPage.totalPages)
        assertEquals(true, communityPage.isFirstPage)
        assertEquals(true, communityPage.isLastPage)
        assertEquals(pageConfig.pageSize, communityPage.pageSize)
        assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
        assertEquals(2, communityPage.content.size)
        assertEquals(ItemModel(Item.find("uuid", itemOne.uuid).singleResult()), communityPage.content.first())
        assertEquals(ItemModel(Item.find("uuid", itemTwo.uuid).singleResult()), communityPage.content.last())
    }

    @Test
    fun `when retrieving my paginated items with pagination and sorting, then correct page of community models is returned`() {
        val pageConfig = PageConfig(pageSize = 1, pageNumber = 1)
        val communityPage =
            itemService.getItemsPageOfUser(user.uuid, pageConfig, ItemSortBy.CITY, Sort.Direction.Descending)
        assertEquals(2, communityPage.totalPages)
        assertEquals(false, communityPage.isFirstPage)
        assertEquals(true, communityPage.isLastPage)
        assertEquals(pageConfig.pageSize, communityPage.pageSize)
        assertEquals(pageConfig.pageNumber, communityPage.pageNumber)
        assertEquals(1, communityPage.content.size)
        assertEquals(ItemModel(Item.find("uuid", itemOne.uuid).singleResult()), communityPage.content.first())
    }

    @Test
    fun `when inserting item, then item is persisted and item model is returned`() {
        val itemModel = ItemModel(
            UUID.randomUUID(),
            "test",
            listOf(ItemCategory.TOOL, ItemCategory.ELECTRIC_DEVICE),
            communityUuid = community.uuid,
            userUuid = user.uuid,
            availability = listOf(TimeIntervalModel(1, 3, 60, 180)),
            description = "test"
        )
        val result = itemService.insertItem(itemModel)

        assertEquals(itemModel, result)
        assertNotNull(Item.find("uuid", itemModel.uuid).firstResult())
    }

    @Test
    fun `when updating item, then item is persisted and item model is returned`() {
        val itemModel = ItemModel(itemOne)
        itemModel.name = "updated name"
        val result = itemService.updateItem(itemModel)

        assertEquals(itemModel.name, result.name)
        assertTrue(itemOne.updatedAt > itemOne.createdAt)
        assertNotNull(Item.find("uuid", itemModel.uuid).firstResult())
    }

    @Test
    fun `when updating item of other user, then exception is thrown`() {
        val itemModel = ItemModel(itemOne)
        itemModel.userUuid = UUID.randomUUID()
        assertThrows<CustomForbiddenException>("User has no right to manipulate item: ${itemOne.uuid}") {
            itemService.updateItem(itemModel)
        }
    }

    @Test
    fun `when deleting item, then item is not persisted anymore`() {
        val itemImage = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid }
        itemService.deleteItem(itemOne.uuid, user.uuid)

        assertNull(Item.find("uuid", itemOne.uuid).firstResult())
        assertNull(ItemImage.find("uuid", itemImage.uuid).firstResult())
    }

    @Test
    fun `when deleting item when not being the owner, then item is still persisted and exception is thrown`() {
        assertThrows<CustomForbiddenException>("User has no right to manipulate item: ${itemOne.uuid}") {
            itemService.deleteItem(itemOne.uuid, UUID.randomUUID())
        }

        assertNotNull(Item.find("uuid", itemOne.uuid).firstResult())
    }

    @Test
    fun `when getting image UUIDs for item, then list of UUIDs is returned`() {
        val itemImageOne = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid }
        val itemImageTwo = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid }
        val otherItemImage = entityUtil.setupItemImage { it.itemUuid = itemTwo.uuid }

        val result = itemService.getImageUuids(itemOne.uuid)
        assertEquals(2, result.size)
        assertTrue(result.contains(itemImageOne.uuid))
        assertTrue(result.contains(itemImageTwo.uuid))
        assertFalse(result.contains(otherItemImage.uuid))
    }

    @Test
    fun `when saving image for item, then Item image entity is created`() {
        val fileUpload = FileUploadMock()

        itemService.saveItemImage(itemOne.uuid, fileUpload, user.uuid)

        val storedItemImage = ItemImage.findAll().firstResult()
        assertEquals(itemOne.uuid, storedItemImage?.itemUuid)
    }

    @Test
    fun `when saving image for item with no rights, then Item image entity is not created`() {
        val fileUpload = FileUploadMock()

        assertThrows<CustomForbiddenException> {
            itemService.saveItemImage(itemOne.uuid, fileUpload, UUID.randomUUID())
        }

        val storedItemImage = ItemImage.findAll().firstResult()
        assertNull(storedItemImage)
    }

    @Test
    fun `when getting item image, then file is returned`() {
        val file = File("$imagePath/test.jpg")
        file.createNewFile()
        val storedItemImage = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid; it.path = file.path }

        val result = itemService.getItemImageFile(storedItemImage.uuid)
        assertEquals(file.path, result.path)
    }

    @Test
    fun `when deleting item image, then file does not exist anymore`() {
        val file = File("$imagePath/test.jpg")
        file.createNewFile()
        val storedItemImage = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid; it.path = file.path }

        itemService.deleteItemImage(storedItemImage.uuid, user.uuid)
        assertFalse(Files.exists(Path(file.path)))
        assertNull(ItemImage.find("uuid", storedItemImage.uuid).firstResult())
    }

    @Test
    fun `when deleting image with no rights, then Item image is not deleted`() {
        val storedItemImage = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid }

        assertThrows<CustomForbiddenException> {
            itemService.deleteItemImage(storedItemImage.uuid, UUID.randomUUID())
        }

        assertNotNull(ItemImage.find("uuid", storedItemImage.uuid).firstResult())
    }

    @Test
    fun `when retrieving item bookings of user, then page of item booking is returned`() {
        val page = itemService.getItemBookingsPageOfUser(requestingUser.uuid, PageConfig(), null, null)

        assertEquals(page.pageSize, 50)
        assertEquals(page.pageNumber, 0)
        assertEquals(page.isFirstPage, true)
        assertEquals(page.isLastPage, true)
        assertEquals(page.totalElements, 2)
        assertEquals(page.content.first().uuid, itemBookingTwo.uuid)
        assertEquals(page.content.last().uuid, itemBookingOne.uuid)
    }

    @Test
    fun `when retrieving item bookings of item, then page of item booking is returned`() {
        val page = itemService.getItemBookingsPageOfItem(
            requestingUser.uuid,
            itemThree.uuid,
            PageConfig(1, 1),
            ItemBookingSortBy.CREATED_AT,
            Sort.Direction.Ascending
        )

        assertEquals(page.pageSize, 1)
        assertEquals(page.pageNumber, 1)
        assertEquals(page.isFirstPage, false)
        assertEquals(page.isLastPage, true)
        assertEquals(page.totalElements, 2)
        assertEquals(page.content.first().uuid, itemBookingTwo.uuid)
    }

    @Test
    fun `when retrieving item booking, then item booking is returned`() {
        val itemBookingModel = ItemBookingModel(itemBookingOne)
        val retrievedItemBookingModel = itemService.getItemBooking(itemBookingModel.userUuid, itemBookingModel.uuid)

        assertEquals(itemBookingModel.uuid, retrievedItemBookingModel.uuid)
        assertEquals(itemBookingModel.itemUuid, retrievedItemBookingModel.itemUuid)
        assertEquals(itemBookingModel.userUuid, retrievedItemBookingModel.userUuid)
        assertOffsetDateTimeEquals(itemBookingModel.startAt, retrievedItemBookingModel.startAt)
        assertOffsetDateTimeEquals(itemBookingModel.endAt, retrievedItemBookingModel.endAt)
        assertOffsetDateTimeEquals(itemBookingModel.createdAt, retrievedItemBookingModel.createdAt)
    }

    @Test
    fun `when retrieving not existing item booking, then item booking is returned`() {
        assertThrows(EntityNotFoundException::class.java) {
            itemService.getItemBooking(UUID.randomUUID(), itemOne.uuid)
        }

        assertThrows(EntityNotFoundException::class.java) {
            itemService.getItemBooking(requestingUser.uuid, UUID.randomUUID())
        }
    }

    @Test
    fun `when booking item, then item booking is created`() {
        val itemBookingModel = itemService.bookItem(
            itemOne.uuid,
            requestingUser.uuid,
            OffsetDateTime.now(),
            OffsetDateTime.now().plusDays(2)
        )

        val storedItemBooking = ItemBooking.find("uuid", itemBookingModel.uuid).firstResult()

        assertEquals(itemBookingModel.uuid, storedItemBooking?.uuid)
        assertEquals(itemBookingModel.userUuid, storedItemBooking?.userUuid)
        assertEquals(itemBookingModel.itemUuid, storedItemBooking?.itemUuid)
        assertOffsetDateTimeEquals(itemBookingModel.startAt, storedItemBooking?.startAt)
        assertOffsetDateTimeEquals(itemBookingModel.endAt, storedItemBooking?.endAt)
        assertOffsetDateTimeEquals(itemBookingModel.createdAt, storedItemBooking?.createdAt)
    }

    @Test
    fun `when booking item with invalid params, then correct error is thrown`() {
        val exception = assertThrows(CustomBadRequestException::class.java) {
            itemService.bookItem(
                itemOne.uuid,
                requestingUser.uuid,
                OffsetDateTime.now().plusDays(2),
                OffsetDateTime.now()
            )
        }

        assertEquals(listOf(ErrorCode.InvalidInputParam), exception.codes)
        assertEquals(0, ItemBooking.find("itemUuid = ?1", itemOne.uuid).count())
    }

    @Test
    fun `when booking item with date which exceeds availableUntil date, then correct error is thrown`() {
        val exception = assertThrows(CustomBadRequestException::class.java) {
            itemService.bookItem(
                itemOne.uuid,
                requestingUser.uuid,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(10)
            )
        }

        assertEquals(listOf(ErrorCode.DateExceedsAvailableUntil), exception.codes)
        assertEquals(0, ItemBooking.find("itemUuid = ?1", itemOne.uuid).count())
    }

    @Test
    fun `when booking item with dates which are not in availability intervals, then correct error is thrown`() {
        val exception = assertThrows(CustomBadRequestException::class.java) {
            itemService.bookItem(
                itemTwo.uuid,
                requestingUser.uuid,
                OffsetDateTime.of(2023, 4, 17, 1, 1, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2023, 4, 18, 1, 20, 0, 0, ZoneOffset.UTC)
            )
        }

        assertEquals(listOf(ErrorCode.DatesNotInInterval), exception.codes)
        assertEquals(0, ItemBooking.find("itemUuid = ?1", itemTwo.uuid).count())
    }

    @Test
    fun `when booking item which is already booked, then correct error is thrown`() {
        entityUtil.setupItemBooking {
            it.itemUuid = itemOne.uuid
            it.startAt = OffsetDateTime.now()
            it.endAt = OffsetDateTime.now().plusDays(3)
        }
        val exception = assertThrows(CustomBadRequestException::class.java) {
            itemService.bookItem(
                itemOne.uuid,
                requestingUser.uuid,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(2)
            )
        }

        assertEquals(listOf(ErrorCode.ItemReserved), exception.codes)
        assertEquals(1, ItemBooking.find("itemUuid = ?1", itemOne.uuid).count())
    }
}