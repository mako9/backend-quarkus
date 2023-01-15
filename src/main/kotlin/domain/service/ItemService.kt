package domain.service

import common.PageConfig
import domain.model.ItemModel
import domain.model.PageModel
import domain.model.exception.CustomForbiddenException
import domain.model.sort.ItemSortBy
import infrastructure.entity.Item
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
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional
import kotlin.io.path.pathString

@ApplicationScoped
@Transactional
class ItemService {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String

    fun getItemsPageOfCommunities(communityUuids: List<UUID>, userUuid: UUID, pageConfig: PageConfig, sortBy: ItemSortBy?, sortDirection: Sort.Direction?): PageModel<ItemModel> {
        val sortByValue = sortBy?.name ?: ItemSortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Item
            .find("communityUuid IN ?1 AND userUuid <> ?2", sort = Sort.by(sortByValue, sortDirectionValue), communityUuids, userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemModel)
    }

    fun getItemsPageOfUser(userUuid: UUID, pageConfig: PageConfig, sortBy: ItemSortBy?, sortDirection: Sort.Direction?): PageModel<ItemModel> {
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
        item.availability = itemModel.availability
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

    fun saveItemImages(itemUuid: UUID, fileUpload: FileUpload) {
        val path = createImagePath(itemUuid, fileUpload.fileName())
        Files.copy(fileUpload.uploadedFile(), path)
        ItemImage.persist(ItemImage(
            itemUuid,
            path.pathString
        ))
    }

    fun getItemImage(uuid: UUID): File {
        val itemImage = ItemImage
            .find("uuid", uuid)
            .firstResult() ?: throw EntityNotFoundException("No item image for UUID: $uuid")
        return File(itemImage.path)
    }

    private fun getItemByUuid(uuid: UUID): Item? {
        return Item.find("uuid", uuid).firstResult()
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
}