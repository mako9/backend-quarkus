package domain.model

import common.ItemCategory
import infrastructure.entity.Item
import java.time.OffsetDateTime
import java.util.*

data class ItemModel(
    var uuid: UUID,
    var name: String,
    var categories: List<ItemCategory>,
    var street: String? = null,
    var houseNumber: String? = null,
    var postalCode: String? = null,
    var city: String? = null,
    var communityUuid: UUID,
    var userUuid: UUID,
    var isActive: Boolean = true,
    var availability: String?,
    var availableUntil: OffsetDateTime? = null,
    var description: String?
) {

    constructor(item: Item) : this(
        uuid = item.uuid,
        name = item.name,
        categories = item.categories.toList(),
        street = item.street,
        houseNumber = item.houseNumber,
        postalCode = item.postalCode,
        city = item.city,
        communityUuid = item.communityUuid,
        userUuid = item.userUuid,
        isActive = item.isActive,
        availability = item.availability,
        availableUntil = item.availableUntil,
        description = item.description
    )

    fun toItem(): Item {
        return Item(
            uuid,
            name,
            categories,
            street,
            houseNumber,
            postalCode,
            city,
            communityUuid,
            userUuid,
            isActive,
            availability,
            availableUntil,
            description
        )
    }
}