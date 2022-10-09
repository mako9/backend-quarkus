package api.dto

import domain.model.PageModel

/**
 * The PageDto is a data transfer object for pagination.
 * Contains a content list of elements and further information about the pagination.
 */
data class PageDto<T>(
    var content: List<T>,
    var isFirstPage: Boolean,
    var isLastPage: Boolean,
    var pageNumber: Int,
    var pageSize: Int,
    var totalElements: Long,
    var totalPages: Int
) {
    companion object {
        fun <T> of(page: PageModel<T>) = PageDto(
            page.content,
            page.isFirstPage,
            page.isLastPage,
            page.pageNumber,
            page.pageSize,
            page.totalElements,
            page.totalPages
        )

        fun <T, S> of(page: PageModel<T>, converter: (T) -> S): PageDto<S> {
            return of(page).map(converter)
        }
    }

    fun <S> map(converter: (T) -> S): PageDto<S> {
        val convertedContent = content.map(converter)
        return PageDto(
            convertedContent,
            isFirstPage,
            isLastPage,
            pageNumber,
            pageSize,
            totalElements,
            totalPages
        )
    }
}