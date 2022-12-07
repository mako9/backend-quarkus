package domain.service

import common.PageConfig
import domain.model.ItemModel
import domain.model.PageModel
import domain.model.exception.CustomForbiddenException
import domain.model.sort.ItemSortBy
import infrastructure.entity.Item
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import java.time.OffsetDateTime
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.persistence.EntityNotFoundException
import javax.transaction.Transactional

@ApplicationScoped
@Transactional
class ItemService {

    fun getItemsPageOfCommunities(communityUuids: List<UUID>, pageConfig: PageConfig, sortBy: ItemSortBy?, sortDirection: Sort.Direction?): PageModel<ItemModel> {
        val sortByValue = sortBy?.name ?: ItemSortBy.NAME.name
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Item
            .find("communityUuid IN ?1", sort = Sort.by(sortByValue, sortDirectionValue), communityUuids)
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

    private fun getItemByUuid(uuid: UUID): Item? {
        return Item.find("uuid", uuid).firstResult()
    }

    private fun checkUserRight(userUuid: UUID, item: Item) {
        if (item.userUuid != userUuid) throw CustomForbiddenException("User has no right to manipulate item: ${item.uuid}")
    }
}