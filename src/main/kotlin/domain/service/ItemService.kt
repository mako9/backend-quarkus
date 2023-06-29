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
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.pathString

@ApplicationScoped
@Transactional
class ItemService {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String

    fun getItemsPageOfCommunities(
        communityUuids: List<UUID>,
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemModel> {
        val sortByValue = sortBy?.value ?: ItemSortBy.NAME.value
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
        val sortByValue = sortBy?.value ?: ItemSortBy.NAME.value
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

    @Transactional
    fun deleteItem(uuid: UUID, userUuid: UUID) {
        val item = getItemByUuid(uuid) ?: throw EntityNotFoundException("No item for UUID: $uuid")
        checkUserRight(userUuid, item)
        val imageUuids = getImageUuids(uuid)
        ItemImage.delete("uuid IN ?1", imageUuids)
        item.delete()
        val itemImages = ItemImage.find("uuid IN ?1", imageUuids).list()
        itemImages.forEach {
            Files.deleteIfExists(Path(it.path))
        }
    }

    fun getImageUuids(itemUuid: UUID): List<UUID> {
        val itemImages = ItemImage
            .find("itemUuid", itemUuid)
            .list()
        return itemImages.map { it.uuid }
    }

    fun getNewestItemImages(itemUuids: List<UUID>): List<Pair<UUID, UUID>> {
        val itemImages = ItemImage.find(
            "FROM ItemImage i WHERE i.itemUuid IN ?1 AND i.createdAt = (SELECT MAX(createdAt) FROM ItemImage WHERE itemUuid = i.itemUuid)",
            itemUuids
        ).list()
        return itemImages.map { Pair(it.itemUuid, it.uuid) }
    }

    fun saveItemImage(itemUuid: UUID, fileUpload: FileUpload, userUuid: UUID): UUID {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        checkUserRight(userUuid, item)
        val path = createImagePath(itemUuid, fileUpload.fileName())
        Files.copy(fileUpload.uploadedFile(), path)
        val itemImage = ItemImage(
            itemUuid,
            path.pathString
        )
        itemImage.persist()
        return itemImage.uuid
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
        val sortByValue = sortBy?.value ?: ItemBookingSortBy.END_AT.value
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
        val sortByValue = sortBy?.value ?: ItemBookingSortBy.END_AT.value
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
        checkInputDate(startAt, endAt)
        checkItemAvailability(item, startAt, endAt)
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
        val dateTime = OffsetDateTime.now().toString()
        val imageName = "${itemUuid}_${dateTime}_${UUID.randomUUID()}.${extension}"
        return Paths.get("${imagePath}/${imageName}")
    }

    private fun checkInputDate(startAt: OffsetDateTime, endAt: OffsetDateTime) {
        if (endAt <= startAt) throw CustomBadRequestException(
            code = ErrorCode.InvalidInputParam
        )
    }

    private fun checkItemAvailability(item: Item, startAt: OffsetDateTime, endAt: OffsetDateTime) {
        val itemModel = ItemModel(item)
        if (itemModel.availableUntil != null && endAt > itemModel.availableUntil) throw CustomBadRequestException(
            code = ErrorCode.DateExceedsAvailableUntil
        )

        if (itemModel.availability.isNotEmpty() && !itemModel.availability.containsDates(
                startAt,
                endAt
            )
        ) throw CustomBadRequestException(
            code = ErrorCode.DatesNotInInterval
        )

        if (ItemBooking.find(
                "itemUuid = ?1 AND ((?2 BETWEEN startAt AND endAt) OR (?3 BETWEEN startAt AND endAt))",
                item.uuid, startAt, endAt
            ).count() > 0
        ) throw CustomBadRequestException(
            code = ErrorCode.ItemReserved
        )
    }
}