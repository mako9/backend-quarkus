package domain.model.exception

enum class ErrorCode(val code: String, val description: String) {
    InvalidInputParam("INVALID_INPUT_PARAM", "at least one input param is invalid"),
    StartNotInInterval("START_NOT_IN_INTERVAL", "the start date is not in an available interval"),
    EndNotInInterval("END_NOT_IN_INTERVAL", "the end date is not in an available interval"),
    ItemReserved("ITEM_RESERVED", "the requested item is already reserved at this time")
}