package api.dto

import domain.model.CommunityModel
import org.hibernate.validator.constraints.Range
import java.util.*
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

data class CommunityDto(
    val uuid: UUID,
    val name: String,
    var radius: Int,
    var latitude: Double,
    var longitude: Double
) {
    constructor(communityModel: CommunityModel): this(
        uuid = communityModel.uuid,
        name = communityModel.name,
        radius = communityModel.radius,
        latitude = communityModel.latitude,
        longitude = communityModel.longitude
    )
}

data class CommunityDetailDto(
    val uuid: UUID,
    val name: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    var isAdmin: Boolean = false,
    var radius: Int,
    var latitude: Double,
    var longitude: Double,
    var adminUuid: UUID,
    var adminFirstName: String? = null,
    var adminLastName: String? = null,
    var canBeJoined: Boolean,
    var isMember: Boolean = false
) {

    companion object {
        fun createWithIsAdmin(communityModel: CommunityModel, userUuid: UUID): CommunityDetailDto {
            val dto = CommunityDetailDto(communityModel)
            dto.isAdmin = communityModel.adminUuid == userUuid
            return dto
        }
    }

    constructor(communityModel: CommunityModel): this(
        uuid = communityModel.uuid,
        name = communityModel.name,
        street = communityModel.street,
        houseNumber = communityModel.houseNumber,
        postalCode = communityModel.postalCode,
        city = communityModel.city,
        radius = communityModel.radius,
        latitude = communityModel.latitude,
        longitude = communityModel.longitude,
        adminUuid = communityModel.adminUuid,
        canBeJoined = communityModel.canBeJoined
    )
}

data class CommunityRequestDto(
    @NotBlank
    val name: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    @Min(1, message = "radius must be greater 1")
    var radius: Int,
    @Range(min = -90, max = 90)
    var latitude: Double,
    @Range(min = -180, max = 180)
    var longitude: Double,
    var canBeJoined: Boolean
)