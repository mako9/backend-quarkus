package api.dto

import common.ItemCategory
import domain.model.ItemModel
import java.util.*

data class ItemDto(
    val uuid: UUID,
    val name: String,
    var categories: List<ItemCategory>,
    var communityUuid: UUID,
    var communityName: String
) {
    constructor(itemModel: ItemModel): this(
        uuid = itemModel.uuid,
        name = itemModel.name,
        categories = itemModel.categories,
        communityUuid = itemModel.communityUuid,
        communityName = "Unknown"
    )
}

data class ItemDetailDto(
    val uuid: UUID,
    val name: String,
    var categories: List<ItemCategory>,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    var isActive: Boolean = false,
    var communityUuid: UUID,
    var userUuid: UUID,
    var availability: String?,
    var description: String?,
    var isOwner: Boolean = false,
    var communityName: String
) {

    constructor(itemModel: ItemModel): this(
        uuid = itemModel.uuid,
        name = itemModel.name,
        categories = itemModel.categories,
        street = itemModel.street,
        houseNumber = itemModel.houseNumber,
        postalCode = itemModel.postalCode,
        city = itemModel.city,
        isActive = itemModel.isActive,
        communityUuid = itemModel.communityUuid,
        userUuid = itemModel.userUuid,
        availability = itemModel.availability,
        description = itemModel.description,
        communityName = "Unknown"
    )
}

data class ItemRequestDto(
    val name: String,
    var categories: List<ItemCategory>,
    val street: String? = null,
    val houseNumber: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    var isActive: Boolean = false,
    var communityUuid: UUID,
    var availability: String? = null,
    var description: String? = null
)