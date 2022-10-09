package common

/**
 * Configuration of a request for a specific page.
 */
data class PageConfig(
    val pageNumber: Int = 0,
    val pageSize: Int = 50
)