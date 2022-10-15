package domain.model

import infrastructure.entity.Community
import java.util.*

data class CommunityModel (
    var uuid: UUID,
    var name: String,
    var street: String?,
    var houseNumber: String?,
    var postalCode: String?,
    var city: String?,
    var adminUuid: UUID,
    var radius: Int
) {
    constructor(community: Community): this(
        community.uuid,
        community.name,
        community.street,
        community.houseNumber,
        community.postalCode,
        community.city,
        community.adminUuid,
        community.radius
    )
}