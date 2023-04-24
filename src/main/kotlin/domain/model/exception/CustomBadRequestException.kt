package domain.model.exception

sealed class CustomException(
    val codes: List<ErrorCode>? = null,
    message: String? = null
) : Exception(message)

class CustomBadRequestException(codes: List<ErrorCode>? = null, message: String? = null) : CustomException(
    codes = codes,
    message = message
) {
    constructor(code: ErrorCode, message: String? = null) : this(
        codes = listOf(code),
        message = message
    )
}

class CustomForbiddenException(message: String? = null) : CustomException(message = message)