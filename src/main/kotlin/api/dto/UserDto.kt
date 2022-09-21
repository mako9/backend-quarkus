package api.dto

import common.UserRole
import domain.model.UserModel
import java.util.*

data class UserDto(
    val uuid: UUID,
    val firstName: String,
    val lastName: String,
    val mail: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    val roles: List<UserRole>
) {
    constructor(userModel: UserModel): this(
        uuid = userModel.uuid,
        firstName = userModel.firstName,
        lastName = userModel.lastName,
        mail = userModel.mail,
        street = userModel.street,
        houseNumber = userModel.houseNumber,
        postalCode = userModel.postalCode,
        city = userModel.city,
        roles = userModel.roles
    )
}