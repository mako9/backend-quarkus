package api.dto

import domain.model.CommunityModel
import java.util.*
import javax.validation.constraints.Min

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
    @Min(1, message = "radius must be greater 1")
    var radius: Int
)