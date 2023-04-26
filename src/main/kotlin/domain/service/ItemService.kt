package domain.service

import com.fasterxml.jackson.databind.ObjectMapper
import common.PageConfig
import domain.model.ItemBookingModel
import domain.model.ItemModel
import domain.model.PageModel
import domain.model.containsDates
import domain.model.exception.CustomBadRequestException
import domain.model.exception.CustomForbiddenException
import domain.model.exception.ErrorCode
import domain.model.sort.ItemBookingSortBy
import domain.model.sort.ItemSortBy
import infrastructure.entity.Item
import infrastructure.entity.ItemBooking
import infrastructure.entity.ItemImage
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional
import kotlin.io.path.Path
import kotlin.io.path.pathString

@ApplicationScoped
@Transactional
class ItemService {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String

    @Inject
    lateinit var objectMapper: ObjectMapper

    fun getItemsPageOfCommunities(
        communityUuids: List<UUID>,
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemModel> {
        val sortByValue = sortBy?.name ?: ItemSortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Item
            .find(
                "communityUuid IN ?1 AND userUuid <> ?2",
                sort = Sort.by(sortByValue, sortDirectionValue),
                communityUuids,
                userUuid
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemModel)
    }

    fun getItemsPageOfUser(
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemModel> {
        val sortByValue = sortBy?.name ?: ItemSortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Item
            .find("userUuid", sort = Sort.by(sortByValue, sortDirectionValue), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemModel)
    }

    fun getItem(uuid: UUID): ItemModel? {
        val item = getItemByUuid(uuid) ?: return null
        return ItemModel(item)
    }

    fun insertItem(itemModel: ItemModel): ItemModel {
        val item = itemModel.toItem()
        item.persist()
        return ItemModel(item)
    }

    fun updateItem(itemModel: ItemModel): ItemModel {
        val item = getItemByUuid(itemModel.uuid) ?: throw EntityNotFoundException("No item for UUID: ${itemModel.uuid}")
        checkUserRight(itemModel.userUuid, item)
        item.name = itemModel.name
        item.categories = itemModel.categories.toTypedArray()
        item.street = itemModel.street
        item.houseNumber = itemModel.houseNumber
        item.postalCode = itemModel.postalCode
        item.city = itemModel.city
        item.communityUuid = itemModel.communityUuid
        item.isActive = itemModel.isActive
        item.availability = ObjectMapper().writeValueAsString(itemModel.availability)
        item.description = itemModel.description
        item.updatedAt = OffsetDateTime.now()
        item.persist()
        return ItemModel(item)
    }

    fun deleteItem(uuid: UUID, userUuid: UUID) {
        val item = getItemByUuid(uuid) ?: throw EntityNotFoundException("No item for UUID: $uuid")
        checkUserRight(userUuid, item)
        item.delete()
    }

    fun getImageUuids(itemUuid: UUID): List<UUID> {
        val itemImages = ItemImage
            .find("itemUuid", itemUuid)
            .list()
        return itemImages.map { it.uuid }
    }

    fun saveItemImage(itemUuid: UUID, fileUpload: FileUpload, userUuid: UUID): UUID {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        checkUserRight(userUuid, item)
        val path = createImagePath(itemUuid, fileUpload.fileName())
        Files.copy(fileUpload.uploadedFile(), path)
        val image = ItemImage(
            itemUuid,
            path.pathString
        )
        ItemImage.persist(image)
        return image.uuid
    }

    fun getItemImageFile(uuid: UUID): File {
        val itemImage = getItemImage(uuid)
        return File(itemImage.path)
    }

    fun deleteItemImage(uuid: UUID, userUuid: UUID) {
        val itemImage = getItemImage(uuid)
        val item = getItemByUuid(itemImage.itemUuid) ?: throw EntityNotFoundException("No item for UUID: $uuid")
        checkUserRight(userUuid, item)
        Files.deleteIfExists(Path(itemImage.path))
        itemImage.delete()
    }

    fun getItemBookingsPageOfUser(
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemBookingSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemBookingModel> {
        val sortByValue = sortBy?.name ?: ItemBookingSortBy.END_AT.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Descending
        val query = ItemBooking
            .find("userUuid", sort = Sort.by(sortByValue, sortDirectionValue), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemBookingModel)
    }

    fun getItemBookingsPageOfItem(
        userUuid: UUID,
        itemUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemBookingSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemBookingModel> {
        val sortByValue = sortBy?.name ?: ItemBookingSortBy.END_AT.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Descending
        val query = ItemBooking
            .find(
                "userUuid = ?1 AND itemUuid = ?2",
                sort = Sort.by(sortByValue, sortDirectionValue),
                userUuid,
                itemUuid
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemBookingModel)
    }

    fun getItemBooking(
        userUuid: UUID,
        uuid: UUID
    ): ItemBookingModel {
        val itemBooking = ItemBooking
            .find("uuid", uuid)
            .firstResult() ?: throw EntityNotFoundException("No item booking for UUID: $uuid")
        if (itemBooking.userUuid != userUuid) throw CustomForbiddenException("User not allowed to see item booking")
        return ItemBookingModel(itemBooking)
    }

    fun bookItem(itemUuid: UUID, userUuid: UUID, startAt: OffsetDateTime, endAt: OffsetDateTime): ItemBookingModel {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        if (item.userUuid == userUuid) throw CustomBadRequestException(message = "Can not book owned item")
        checkTimeAvailability(item, startAt, endAt)
        val itemBooking = ItemBooking(
            itemUuid = itemUuid,
            userUuid = userUuid,
            startAt = startAt,
            endAt = endAt
        )
        itemBooking.persist()
        return ItemBookingModel(itemBooking)
    }

    private fun getItemByUuid(uuid: UUID): Item? {
        return Item.find("uuid", uuid).firstResult()
    }

    private fun getItemImage(uuid: UUID): ItemImage {
        return ItemImage
            .find("uuid", uuid)
            .firstResult() ?: throw EntityNotFoundException("No item image for UUID: $uuid")
    }

    private fun checkUserRight(userUuid: UUID, item: Item) {
        if (item.userUuid != userUuid) throw CustomForbiddenException("User has no right to manipulate item: ${item.uuid}")
    }

    private fun createImagePath(itemUuid: UUID, fileName: String): Path {
        val extension = fileName.substringAfterLast(".")
        val dateTime = LocalDate.now().toString()
        val imageName = "${itemUuid}_${dateTime}_${UUID.randomUUID()}.${extension}"
        return Paths.get("${imagePath}/${imageName}")
    }

    private fun checkTimeAvailability(item: Item, startAt: OffsetDateTime, endAt: OffsetDateTime) {
        if (endAt <= startAt) throw CustomBadRequestException(
            code = ErrorCode.InvalidInputParam,
            message = "End of booking must be after start of booking: $endAt <= $startAt"
        )

        val itemModel = ItemModel(item)
        if (itemModel.availability.isNotEmpty() && !itemModel.availability.containsDates(
                startAt,
                endAt
            )
        ) throw CustomBadRequestException(
            code = ErrorCode.InvalidInputParam,
            message = "The requested booking interval is outside availability intervals"
        )
    }
}