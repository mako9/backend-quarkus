package domain.model.exception

enum class ErrorCode(val code: String, val description: String) {
    InvalidInputParam("INVALID_INPUT_PARAM", "at least one input param is invalid"),
    DatesNotInInterval("DATES_NOT_IN_INTERVAL", "start and/or end date is not in an available interval"),
    DateExceedsAvailableUntil(
        "DATE_EXCEEDS_AVAILABLE_UNTIL",
        "the requested interval exceeds the available until date"
    ),
    ItemReserved("ITEM_RESERVED", "the requested item is already reserved at this time")
}