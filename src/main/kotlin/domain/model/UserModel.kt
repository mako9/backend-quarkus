package domain.model

import common.UserRole
import infrastructure.entity.User
import java.util.*

data class UserModel(
    val uuid: UUID,
    val firstName: String,
    val lastName: String,
    val mail: String,
    val street: String? = null,
    val houseNumber: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val roles: List<UserRole>
) {
    constructor(user: User): this(
        uuid = user.uuid,
        firstName = user.firstName,
        lastName = user.lastName,
        mail = user.mail,
        street = user.street,
        houseNumber = user.houseNumber,
        postalCode = user.postalCode,
        city = user.city,
        roles = user.roles.toList()
    )
}