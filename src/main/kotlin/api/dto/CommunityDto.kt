package api.dto

import domain.model.CommunityModel
import java.util.*

data class CommunityDto(
    val uuid: UUID,
    val name: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    var adminUuid: UUID,
    var radius: Int
) {
    constructor(communityModel: CommunityModel): this(
        uuid = communityModel.uuid,
        name = communityModel.name,
        street = communityModel.street,
        houseNumber = communityModel.houseNumber,
        postalCode = communityModel.postalCode,
        city = communityModel.city,
        adminUuid = communityModel.adminUuid,
        radius = communityModel.radius
    )
}

data class CommunityRequestDto(
    val name: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    var radius: Int
)