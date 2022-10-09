package domain.model

import io.quarkus.hibernate.orm.panache.kotlin.PanacheQuery

/**
 * The PageModel is a data model for pagination.
 * Contains a content list of elements and further information about the pagination.
 */
data class PageModel<T>(
    var content: List<T>,
    var isFirstPage: Boolean,
    var isLastPage: Boolean,
    var pageNumber: Int,
    var pageSize: Int,
    var totalElements: Long,
    var totalPages: Int
) {
    companion object {
        fun <T : Any> of(query: PanacheQuery<T>) = PageModel(
            query.list(),
            !query.hasPreviousPage(),
            !query.hasNextPage(),
            query.page().index,
            query.page().size,
            query.count(),
            query.pageCount()
        )

        fun <T : Any, S> of(query: PanacheQuery<T>, converter: (T) -> S): PageModel<S> {
            return of(query).map(converter)
        }
    }

    fun <S> map(converter: (T) -> S): PageModel<S> {
        val convertedContent = content.map(converter)
        return PageModel(
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