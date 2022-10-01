package api.dto

data class UserPatchDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val postalCode: String? = null,
    val city: String? = null
)