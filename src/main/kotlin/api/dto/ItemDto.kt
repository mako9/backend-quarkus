package api.dto

import common.ItemCategory
import domain.model.ItemModel
import domain.model.TimeIntervalModel
import utils.DateTimeUtils.getMinutesOfOffsetTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.util.*

data class ItemDto(
    val uuid: UUID,
    val name: String,
    var categories: List<ItemCategory>,
    var communityUuid: UUID,
    var communityName: String?
) {
    constructor(itemModel: ItemModel) : this(
        uuid = itemModel.uuid,
        name = itemModel.name,
        categories = itemModel.categories,
        communityUuid = itemModel.communityUuid,
        communityName = itemModel.communityModel?.name
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
    var availability: List<ItemTimeIntervalDto>,
    var availableUntil: OffsetDateTime?,
    var description: String?,
    var isOwner: Boolean = false,
    var communityName: String?
) {

    constructor(itemModel: ItemModel) : this(
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
        availability = itemModel.availability.map { ItemTimeIntervalDto(it) },
        availableUntil = itemModel.availableUntil,
        description = itemModel.description,
        communityName = itemModel.communityModel?.name
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
    var availability: List<ItemTimeIntervalDto> = emptyList(),
    var availableUntil: OffsetDateTime? = null,
    var description: String? = null
)

data class ItemBookingRequestDto(
    var startAt: OffsetDateTime,
    var endAt: OffsetDateTime
)

data class ItemTimeIntervalDto(
    val startDayOfWeek: Int,
    val endDayOfWeek: Int,
    val startTimeAt: OffsetTime,
    val endTimeAt: OffsetTime
) {
    constructor(timeIntervalModel: TimeIntervalModel) : this(
        startDayOfWeek = timeIntervalModel.startDayOfWeek.value,
        endDayOfWeek = timeIntervalModel.endDayOfWeek.value,
        startTimeAt = timeIntervalModel.startTime,
        endTimeAt = timeIntervalModel.endTime,
    )

    fun toTimeIntervalModel(): TimeIntervalModel = TimeIntervalModel(
        startDayOfWeekInt = this.startDayOfWeek,
        endDayOfWeekInt = this.endDayOfWeek,
        startTimeAtInMinute = this.startTimeAt.getMinutesOfOffsetTime(),
        endTimeAtInMinute = this.endTimeAt.getMinutesOfOffsetTime(),
    )
}