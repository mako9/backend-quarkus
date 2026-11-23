package domain.service

import com.fasterxml.jackson.databind.ObjectMapper
import common.BookingStatus
import common.CancelledBy
import common.NotificationType
import common.PageConfig
import domain.event.BookingEvent
import domain.model.ItemBookingModel
import domain.model.ItemModel
import domain.model.PageModel
import domain.model.containsDates
import domain.model.exception.CustomBadRequestException
import domain.model.exception.CustomForbiddenException
import domain.model.exception.ErrorCode
import domain.model.sort.ItemBookingSortBy
import domain.model.sort.ItemSortBy
import infrastructure.entity.Item
import infrastructure.entity.ItemBooking
import infrastructure.entity.ItemImage
import infrastructure.entity.User
import infrastructure.entity.UserCommunityRelation
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Event
import jakarta.inject.Inject
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.pathString

@ApplicationScoped
@Transactional
class ItemService {
    @ConfigProperty(name = "app.storage.item-image.path")
    private lateinit var imagePath: String

    @Inject
    lateinit var bookingEventSource: Event<BookingEvent>

    fun getItemsPageOfCommunities(
        communityUuids: List<UUID>,
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemModel> {
        val sortByValue = sortBy?.value ?: ItemSortBy.NAME.value
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Item
            .find(
                "communityUuid IN ?1 AND userUuid <> ?2 AND isActive = true",
                sort = Sort.by(sortByValue, sortDirectionValue),
                communityUuids,
                userUuid
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemModel)
    }

    fun getItemsPageOfUser(
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemModel> {
        val sortByValue = sortBy?.value ?: ItemSortBy.NAME.value
        val sortDirectionValue = sortDirection ?: Sort.Direction.Ascending
        val query = Item
            .find("userUuid", sort = Sort.by(sortByValue, sortDirectionValue), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemModel)
    }

    fun getItem(uuid: UUID): ItemModel? {
        val item = getItemByUuid(uuid) ?: return null
        return ItemModel(item)
    }

    fun insertItem(itemModel: ItemModel): ItemModel {
        val item = itemModel.toItem()
        item.persist()
        return ItemModel(item)
    }

    fun updateItem(itemModel: ItemModel): ItemModel {
        val item = getItemByUuid(itemModel.uuid) ?: throw EntityNotFoundException("No item for UUID: ${itemModel.uuid}")
        checkUserRight(itemModel.userUuid, item)
        item.name = itemModel.name
        item.categories = itemModel.categories.toTypedArray()
        item.street = itemModel.street
        item.houseNumber = itemModel.houseNumber
        item.postalCode = itemModel.postalCode
        item.city = itemModel.city
        item.communityUuid = itemModel.communityUuid
        item.isActive = itemModel.isActive
        item.availability = ObjectMapper().writeValueAsString(itemModel.availability)
        item.description = itemModel.description
        item.requiresApproval = itemModel.requiresApproval
        item.updatedAt = OffsetDateTime.now()
        item.persist()
        return ItemModel(item)
    }

    @Transactional
    fun deleteItem(uuid: UUID, userUuid: UUID) {
        val item = getItemByUuid(uuid) ?: throw EntityNotFoundException("No item for UUID: $uuid")
        checkUserRight(userUuid, item)
        val imageUuids = getImageUuids(uuid)
        ItemImage.delete("uuid IN ?1", imageUuids)
        item.delete()
        val itemImages = ItemImage.find("uuid IN ?1", imageUuids).list()
        itemImages.forEach {
            Files.deleteIfExists(Path(it.path))
        }
    }

    fun getImageUuids(itemUuid: UUID): List<UUID> {
        val itemImages = ItemImage
            .find("itemUuid", itemUuid)
            .list()
        return itemImages.map { it.uuid }
    }

    fun getNewestItemImages(itemUuids: List<UUID>): List<Pair<UUID, UUID>> {
        val itemImages = ItemImage.find(
            "FROM ItemImage i WHERE i.itemUuid IN ?1 AND i.createdAt = (SELECT MAX(createdAt) FROM ItemImage WHERE itemUuid = i.itemUuid)",
            itemUuids
        ).list()
        return itemImages.map { Pair(it.itemUuid, it.uuid) }
    }

    fun saveItemImage(itemUuid: UUID, fileUpload: FileUpload, userUuid: UUID): UUID {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        checkUserRight(userUuid, item)
        val path = createImagePath(itemUuid, fileUpload.fileName())
        Files.copy(fileUpload.uploadedFile(), path)
        val itemImage = ItemImage(
            itemUuid,
            path.pathString
        )
        itemImage.persist()
        return itemImage.uuid
    }

    fun getItemImageFile(uuid: UUID): File {
        val itemImage = getItemImage(uuid)
        return File(itemImage.path)
    }

    fun deleteItemImage(uuid: UUID, userUuid: UUID) {
        val itemImage = getItemImage(uuid)
        val item = getItemByUuid(itemImage.itemUuid) ?: throw EntityNotFoundException("No item for UUID: $uuid")
        checkUserRight(userUuid, item)
        Files.deleteIfExists(Path(itemImage.path))
        itemImage.delete()
    }

    fun getItemBookingsPageOfUser(
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemBookingSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemBookingModel> {
        val sortByValue = sortBy?.value ?: ItemBookingSortBy.END_AT.value
        val sortDirectionValue = sortDirection ?: Sort.Direction.Descending
        val query = ItemBooking
            .find("userUuid", sort = Sort.by(sortByValue, sortDirectionValue), userUuid)
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query, ::ItemBookingModel)
    }

    fun getItemBookingsPageOfItem(
        userUuid: UUID,
        itemUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemBookingSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemBookingModel> {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        if (item.userUuid != userUuid) throw CustomForbiddenException("User is not the owner of item: $itemUuid")
        val sortByValue = sortBy?.value ?: ItemBookingSortBy.END_AT.value
        val sortDirectionValue = sortDirection ?: Sort.Direction.Descending
        val query = ItemBooking
            .find(
                "itemUuid = ?1",
                sort = Sort.by(sortByValue, sortDirectionValue),
                itemUuid
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query) { ItemBookingModel(it).copy(isItemOwner = true) }
    }

    fun getItemBooking(
        userUuid: UUID,
        uuid: UUID
    ): ItemBookingModel {
        val itemBooking = ItemBooking
            .find("uuid", uuid)
            .firstResult() ?: throw EntityNotFoundException("No item booking for UUID: $uuid")
        val item = getItemByUuid(itemBooking.itemUuid)
        val isRequester = itemBooking.userUuid == userUuid
        val isOwner = item?.userUuid == userUuid
        if (!isRequester && !isOwner) throw CustomBadRequestException(code = ErrorCode.BookingAccessDenied)
        return ItemBookingModel(itemBooking).copy(isItemOwner = isOwner)
    }

    fun bookItem(itemUuid: UUID, userUuid: UUID, startAt: OffsetDateTime, endAt: OffsetDateTime): ItemBookingModel {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        if (item.userUuid == userUuid) throw CustomBadRequestException(message = "Can not book owned item")
        checkInputDate(startAt, endAt)
        checkItemAvailability(item, startAt, endAt)
        val status = if (item.requiresApproval) BookingStatus.PENDING else BookingStatus.CONFIRMED
        val itemBooking = ItemBooking(
            itemUuid = itemUuid,
            userUuid = userUuid,
            startAt = startAt,
            endAt = endAt,
            status = status
        )
        itemBooking.persist()

        val requester = User.find("uuid", userUuid).firstResult()
        val requesterName = "${requester?.firstName ?: ""} ${requester?.lastName ?: ""}".trim()
        val notificationType = if (status == BookingStatus.PENDING) NotificationType.BOOKING_REQUEST else NotificationType.BOOKING_CONFIRMED
        val recipientUuid = if (status == BookingStatus.PENDING) item.userUuid else userUuid
        bookingEventSource.fireAsync(
            BookingEvent(
                notificationType = notificationType,
                recipientUserUuid = recipientUuid,
                bookingUuid = itemBooking.uuid,
                itemName = item.name,
                requesterName = requesterName
            )
        )

        // The event above reaches exactly one of the two parties. For an item that needs no approval it
        // confirms the booking to the requester, which leaves the owner with no idea that their item is
        // spoken for - so tell them too.
        if (status == BookingStatus.CONFIRMED) {
            bookingEventSource.fireAsync(
                BookingEvent(
                    notificationType = NotificationType.ITEM_BOOKED,
                    recipientUserUuid = item.userUuid,
                    bookingUuid = itemBooking.uuid,
                    itemName = item.name,
                    requesterName = requesterName
                )
            )
        }

        return ItemBookingModel(itemBooking)
    }

    fun approveBooking(bookingUuid: UUID, userUuid: UUID): ItemBookingModel {
        val itemBooking = ItemBooking.find("uuid", bookingUuid).firstResult()
            ?: throw EntityNotFoundException("No item booking for UUID: $bookingUuid")
        val item = getItemByUuid(itemBooking.itemUuid)
            ?: throw EntityNotFoundException("No item for UUID: ${itemBooking.itemUuid}")
        if (item.userUuid != userUuid) throw CustomForbiddenException("User is not the owner of item: ${item.uuid}")
        if (itemBooking.status != BookingStatus.PENDING) throw CustomBadRequestException(code = ErrorCode.BookingNotPending)
        assertNoConfirmedOverlap(itemBooking.itemUuid, itemBooking.startAt, itemBooking.endAt, itemBooking.uuid)

        itemBooking.status = BookingStatus.CONFIRMED
        itemBooking.persist()

        val requester = User.find("uuid", itemBooking.userUuid).firstResult()
        val requesterName = "${requester?.firstName ?: ""} ${requester?.lastName ?: ""}".trim()
        bookingEventSource.fireAsync(
            BookingEvent(
                notificationType = NotificationType.BOOKING_CONFIRMED,
                recipientUserUuid = itemBooking.userUuid,
                bookingUuid = itemBooking.uuid,
                itemName = item.name,
                requesterName = requesterName
            )
        )

        return ItemBookingModel(itemBooking).copy(isItemOwner = true)
    }

    fun declineBooking(bookingUuid: UUID, userUuid: UUID): ItemBookingModel {
        val itemBooking = ItemBooking.find("uuid", bookingUuid).firstResult()
            ?: throw EntityNotFoundException("No item booking for UUID: $bookingUuid")
        val item = getItemByUuid(itemBooking.itemUuid)
            ?: throw EntityNotFoundException("No item for UUID: ${itemBooking.itemUuid}")
        if (item.userUuid != userUuid) throw CustomForbiddenException("User is not the owner of item: ${item.uuid}")
        if (itemBooking.status != BookingStatus.PENDING) throw CustomBadRequestException(code = ErrorCode.BookingNotPending)

        itemBooking.status = BookingStatus.DECLINED
        itemBooking.persist()

        val requester = User.find("uuid", itemBooking.userUuid).firstResult()
        val requesterName = "${requester?.firstName ?: ""} ${requester?.lastName ?: ""}".trim()
        bookingEventSource.fireAsync(
            BookingEvent(
                notificationType = NotificationType.BOOKING_DECLINED,
                recipientUserUuid = itemBooking.userUuid,
                bookingUuid = itemBooking.uuid,
                itemName = item.name,
                requesterName = requesterName
            )
        )

        return ItemBookingModel(itemBooking).copy(isItemOwner = true)
    }

    fun cancelBooking(bookingUuid: UUID, userUuid: UUID): ItemBookingModel {
        val itemBooking = ItemBooking.find("uuid", bookingUuid).firstResult()
            ?: throw EntityNotFoundException("No item booking for UUID: $bookingUuid")
        val item = getItemByUuid(itemBooking.itemUuid)
            ?: throw EntityNotFoundException("No item for UUID: ${itemBooking.itemUuid}")

        val isRequester = itemBooking.userUuid == userUuid
        val isOwner = item.userUuid == userUuid
        if (!isRequester && !isOwner) throw CustomBadRequestException(code = ErrorCode.BookingAccessDenied)
        if (itemBooking.status != BookingStatus.PENDING && itemBooking.status != BookingStatus.CONFIRMED) {
            throw CustomBadRequestException(code = ErrorCode.BookingNotCancellable)
        }

        itemBooking.status = BookingStatus.CANCELLED
        itemBooking.cancelledBy = if (isOwner) CancelledBy.OWNER else CancelledBy.REQUESTER
        itemBooking.cancelledAt = OffsetDateTime.now()
        itemBooking.persist()

        val requester = User.find("uuid", itemBooking.userUuid).firstResult()
        val requesterName = "${requester?.firstName ?: ""} ${requester?.lastName ?: ""}".trim()
        val recipientUuid = if (isOwner) itemBooking.userUuid else item.userUuid
        bookingEventSource.fireAsync(
            BookingEvent(
                notificationType = NotificationType.BOOKING_CANCELLED,
                recipientUserUuid = recipientUuid,
                bookingUuid = itemBooking.uuid,
                itemName = item.name,
                requesterName = requesterName
            )
        )

        return ItemBookingModel(itemBooking).copy(isItemOwner = isOwner)
    }

    fun getItemBookingDates(itemUuid: UUID, userUuid: UUID): List<Pair<OffsetDateTime, OffsetDateTime>> {
        val item = getItemByUuid(itemUuid) ?: throw EntityNotFoundException("No item for UUID: $itemUuid")
        val isMember = UserCommunityRelation
            .find("userUuid = ?1 AND communityUuid = ?2", userUuid, item.communityUuid)
            .count() > 0
        val isOwner = item.userUuid == userUuid
        if (!isMember && !isOwner) throw CustomForbiddenException("User is not a member of the item's community")
        return ItemBooking
            .find("itemUuid = ?1 AND status = 'CONFIRMED'", itemUuid)
            .list()
            .map { Pair(it.startAt, it.endAt) }
    }

    fun getReceivedItemBookingsPage(
        userUuid: UUID,
        pageConfig: PageConfig,
        sortBy: ItemBookingSortBy?,
        sortDirection: Sort.Direction?
    ): PageModel<ItemBookingModel> {
        val sortByValue = sortBy?.value ?: ItemBookingSortBy.END_AT.value
        val sortDirectionValue = sortDirection ?: Sort.Direction.Descending
        val ownedItemUuids = Item.find("userUuid", userUuid).list().map { it.uuid }
        if (ownedItemUuids.isEmpty()) return PageModel.empty()
        val query = ItemBooking
            .find(
                "itemUuid IN ?1",
                sort = Sort.by(sortByValue, sortDirectionValue),
                ownedItemUuids
            )
            .page(Page.of(pageConfig.pageNumber, pageConfig.pageSize))
        return PageModel.of(query) { ItemBookingModel(it).copy(isItemOwner = true) }
    }

    fun returnBooking(bookingUuid: UUID, userUuid: UUID): ItemBookingModel {
        val itemBooking = ItemBooking.find("uuid", bookingUuid).firstResult()
            ?: throw EntityNotFoundException("No item booking for UUID: $bookingUuid")
        val item = getItemByUuid(itemBooking.itemUuid)
            ?: throw EntityNotFoundException("No item for UUID: ${itemBooking.itemUuid}")

        val isRequester = itemBooking.userUuid == userUuid
        val isOwner = item.userUuid == userUuid
        if (!isRequester && !isOwner) throw CustomBadRequestException(code = ErrorCode.BookingAccessDenied)
        if (itemBooking.status != BookingStatus.CONFIRMED) {
            throw CustomBadRequestException(code = ErrorCode.BookingNotReturnable)
        }

        itemBooking.status = BookingStatus.RETURNED
        itemBooking.returnedBy = if (isOwner) CancelledBy.OWNER else CancelledBy.REQUESTER
        itemBooking.returnedAt = OffsetDateTime.now()
        itemBooking.persist()

        val requester = User.find("uuid", itemBooking.userUuid).firstResult()
        val requesterName = "${requester?.firstName ?: ""} ${requester?.lastName ?: ""}".trim()
        val recipientUuid = if (isOwner) itemBooking.userUuid else item.userUuid
        bookingEventSource.fireAsync(
            BookingEvent(
                notificationType = NotificationType.BOOKING_RETURNED,
                recipientUserUuid = recipientUuid,
                bookingUuid = itemBooking.uuid,
                itemName = item.name,
                requesterName = requesterName
            )
        )

        return ItemBookingModel(itemBooking).copy(isItemOwner = isOwner)
    }

    private fun getItemByUuid(uuid: UUID): Item? {
        return Item.find("uuid", uuid).firstResult()
    }

    private fun getItemImage(uuid: UUID): ItemImage {
        return ItemImage
            .find("uuid", uuid)
            .firstResult() ?: throw EntityNotFoundException("No item image for UUID: $uuid")
    }

    private fun checkUserRight(userUuid: UUID, item: Item) {
        if (item.userUuid != userUuid) throw CustomForbiddenException("User has no right to manipulate item: ${item.uuid}")
    }

    private fun createImagePath(itemUuid: UUID, fileName: String): Path {
        val extension = fileName.substringAfterLast(".")
        val dateTime = OffsetDateTime.now().toString()
        val imageName = "${itemUuid}_${dateTime}_${UUID.randomUUID()}.${extension}"
        return Paths.get("${imagePath}/${imageName}")
    }

    private fun checkInputDate(startAt: OffsetDateTime, endAt: OffsetDateTime) {
        if (endAt <= startAt) throw CustomBadRequestException(
            code = ErrorCode.InvalidInputParam
        )
    }

    /**
     * Approving is the moment a request stops being a wish and becomes a promise, so it has to re-check
     * the exclusivity that [checkItemAvailability] only enforced when the request was made. Without it
     * two overlapping requests can both be approved and the owner has promised the same item to two
     * neighbours for the same days.
     *
     * Only CONFIRMED rows count. Still-pending requests must not: nothing prevents several neighbours
     * from asking for the same slot, and approving one of them is how the owner chooses - if a pending
     * rival blocked approval, an owner facing two overlapping requests could accept neither.
     */
    private fun assertNoConfirmedOverlap(
        itemUuid: UUID,
        startAt: OffsetDateTime,
        endAt: OffsetDateTime,
        exceptBookingUuid: UUID
    ) {
        if (ItemBooking.find(
                "itemUuid = ?1 AND uuid <> ?2 AND status = 'CONFIRMED' AND startAt < ?4 AND endAt > ?3",
                itemUuid, exceptBookingUuid, startAt, endAt
            ).count() > 0
        ) throw CustomBadRequestException(code = ErrorCode.ItemReserved)
    }

    private fun checkItemAvailability(item: Item, startAt: OffsetDateTime, endAt: OffsetDateTime) {
        val itemModel = ItemModel(item)
        if (itemModel.availableUntil != null && endAt > itemModel.availableUntil) throw CustomBadRequestException(
            code = ErrorCode.DateExceedsAvailableUntil
        )

        if (itemModel.availability.isNotEmpty() && !itemModel.availability.containsDates(
                startAt,
                endAt
            )
        ) throw CustomBadRequestException(
            code = ErrorCode.DatesNotInInterval
        )

        if (ItemBooking.find(
                "itemUuid = ?1 AND status = 'CONFIRMED' AND startAt < ?3 AND endAt > ?2",
                item.uuid, startAt, endAt
            ).count() > 0
        ) throw CustomBadRequestException(
            code = ErrorCode.ItemReserved
        )
    }
}