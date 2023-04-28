package api.dto

import domain.model.exception.CustomException
import org.eclipse.microprofile.openapi.annotations.media.Schema

data class ErrorDto(
    @field:Schema(description = "List of error codes", example = "[INVALID_FILE_FORMAT, INVALID_INPUT_DATA]")
    val codes: List<String>? = null,
    val message: String? = null
) {
    constructor(exception: CustomException) : this(
        codes = exception.codes?.map { it.code },
        message = exception.message
    )
}