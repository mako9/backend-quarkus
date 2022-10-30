package api.dto

import domain.model.UserModel
import java.util.*

data class MinimalUserDto(
    val uuid: UUID,
    val firstName: String,
    val lastName: String
) {
    constructor(userModel: UserModel): this(
        uuid = userModel.uuid,
        firstName = userModel.firstName,
        lastName = userModel.lastName
    )
}