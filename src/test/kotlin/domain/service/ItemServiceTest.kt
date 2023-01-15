package domain.service

import common.ItemCategory
import common.PageConfig
import domain.model.ItemModel
import domain.model.exception.CustomForbiddenException
import domain.model.sort.ItemSortBy
import infrastructure.entity.Community
import infrastructure.entity.Item
import infrastructure.entity.ItemImage
import infrastructure.entity.User
import io.quarkus.panache.common.Sort
import io.quarkus.test.junit.QuarkusTest
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import testUtils.EntityUtil
import testUtils.mock.FileUploadMock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject

@QuarkusTest
class ItemServiceTest {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String
    @Inject
    private lateinit var entityUtil: EntityUtil
    @Inject
    private lateinit var itemService: ItemService

    private lateinit var user: User
    private lateinit var community: Community
    private lateinit var itemOne: Item
    private lateinit var itemTwo: Item
    private lateinit var itemThree: Item
    private lateinit var itemFour: Item

    @BeforeEach
    fun beforeEach() {
        user = entityUtil.setupUser()
        community = entityUtil.setupCommunity()
        itemOne = entityUtil.setupItem {
            it.name = "aaa"; it.communityUuid = community.uuid; it.userUuid = user.uuid; it.city = "aaa"
        }
        itemTwo = entityUtil.setupItem {
            it.name = "bbb"; it.communityUuid = community.uuid; it.userUuid = user.uuid; it.city = "bbb"
        }
        itemThree = entityUtil.setupItem { it.name = "ccc"; it.communityUuid = community.uuid; it.city = "aaa" }
        itemFour = entityUtil.setupItem { it.name = "ddd"; it.communityUuid = community.uuid; it.city = "bbb" }

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
        assertEquals(ItemModel(itemThree), communityPage.content.first())
        assertEquals(ItemModel(itemFour), communityPage.content.last())
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
        assertEquals(ItemModel(itemThree), communityPage.content.first())
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
        assertEquals(ItemModel(itemOne), communityPage.content.first())
        assertEquals(ItemModel(itemTwo), communityPage.content.last())
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
        assertEquals(ItemModel(itemOne), communityPage.content.first())
    }

    @Test
    fun `when inserting item, then item is persisted and item model is returned`() {
        val itemModel = ItemModel(
            UUID.randomUUID(),
            "test",
            listOf(ItemCategory.TOOL, ItemCategory.ELECTRIC_DEVICE),
            communityUuid = community.uuid,
            userUuid = user.uuid,
            availability = "[{day: 0, start: 60, end 1800}]",
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
        itemService.deleteItem(itemOne.uuid, user.uuid)

        assertNull(Item.find("uuid", itemOne.uuid).firstResult())
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

        itemService.saveItemImages(itemOne.uuid, fileUpload)

        val storedItemImage = ItemImage.findAll().firstResult()
        assertEquals(itemOne.uuid, storedItemImage?.itemUuid)
    }

    @Test
    fun `when getting item image, then file is returned`() {
        val file = File("$imagePath/test.jpg")
        file.createNewFile()
        val storedItemImage = entityUtil.setupItemImage { it.itemUuid = itemOne.uuid; it.path = file.path }

        val result = itemService.getItemImage(storedItemImage.uuid)
        assertEquals(file.path, result.path)
    }
}