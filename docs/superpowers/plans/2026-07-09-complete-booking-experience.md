# Complete Booking Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close four gaps in the booking flow — owner UI for incoming requests, fix hardcoded `isOwner`, availability calendar on item detail, and RETURNED status lifecycle.

**Architecture:** Backend-first: DB migration + new endpoints + updated DTO, then Next.js pages/components, then Flutter screens. The new `isItemOwner` flag on `ItemBookingDto` is computed server-side so no extra roundtrips are needed on either frontend.

**Tech Stack:** Quarkus 3 / Kotlin / Panache / Liquibase (backend); Next.js Pages Router / react-datepicker / next-i18next (web frontend); Flutter / flutter_bloc / AppLocalizations ARB (mobile frontend).

## Global Constraints

- All backend Kotlin sources live under `src/main/kotlin/`, tests under `src/test/kotlin/`
- Migration files go in `src/main/resources/db/changelog/` — auto-included by `changelog-root.xml`'s `<includeAll>`
- Backend test runner: `./mvnw test` from `/Users/mario/Projekte/Private/backend-quarkus`
- Next.js test runner: `npm test` from `/Users/mario/Projekte/Private/frontend-nextjs`
- Flutter test runner: `flutter test` from `/Users/mario/Projekte/Private/frontend_flutter`
- Do NOT add `Co-Authored-By: Claude` or similar AI attribution to commits
- Do NOT modify `.gitconfig`

---

## File Map

**backend-quarkus — create:**
- `src/main/resources/db/changelog/changelog-3.0.sql`
- `src/main/kotlin/api/dto/ItemBookingDateRangeDto.kt`

**backend-quarkus — modify:**
- `src/main/kotlin/common/BookingStatus.kt`
- `src/main/kotlin/common/NotificationType.kt`
- `src/main/kotlin/infrastructure/entity/ItemBooking.kt`
- `src/main/kotlin/domain/model/ItemBookingModel.kt`
- `src/main/kotlin/api/dto/ItemBookingDto.kt`
- `src/main/kotlin/domain/model/exception/ErrorCode.kt`
- `src/main/kotlin/domain/service/ItemService.kt`
- `src/main/kotlin/api/controller/ItemController.kt`
- `src/main/kotlin/domain/service/NotificationService.kt`
- `src/test/kotlin/testUtils/EntityUtil.kt`
- `src/test/kotlin/domain/service/ItemServiceTest.kt`
- `src/test/kotlin/domain/service/NotificationServiceTest.kt`
- `src/test/kotlin/api/controller/ItemControllerTest.kt`

**frontend-nextjs — modify:**
- `app/models/booking.ts`
- `app/lib/bookings.ts`
- `pages/bookings/index.tsx`
- `pages/bookings/[uuid].tsx`
- `pages/items/[id].tsx`
- `app/components/subpages/foreignItem.tsx`
- `public/locales/en/common.json`
- `public/locales/de/common.json`
- `__tests__/pages/bookings/index.test.tsx`
- `__tests__/pages/bookings/[uuid].test.tsx`

**frontend_flutter — create:**
- `lib/model/booking_date_range.dart`

**frontend_flutter — modify:**
- `lib/model/item_booking.dart`
- `lib/service/booking_service.dart`
- `lib/widget/booking/booking_cubit.dart`
- `lib/widget/booking/booking_detail_cubit.dart`
- `lib/widget/booking/booking_detail_screen.dart`
- `lib/widget/my_area/my_area_screen.dart`
- `lib/widget/item/item_detail_screen.dart`
- `lib/l10n/app_en.arb`
- `lib/l10n/app_de.arb`
- `test/widget/booking_cubit_test.dart`
- `test/widget/booking_detail_cubit_test.dart`
- `test/widget/booking_detail_screen_test.dart`

---

## Task 1: DB migration + extend BookingStatus and NotificationType enums

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.0.sql`
- Modify: `src/main/kotlin/common/BookingStatus.kt`
- Modify: `src/main/kotlin/common/NotificationType.kt`

**Interfaces:**
- Produces: `BookingStatus.RETURNED`, `NotificationType.BOOKING_RETURNED` — used by Tasks 2, 5

- [ ] **Step 1: Add RETURNED to BookingStatus enum**

Replace the entire file `src/main/kotlin/common/BookingStatus.kt`:
```kotlin
package common

enum class BookingStatus { PENDING, CONFIRMED, DECLINED, CANCELLED, RETURNED }
```

- [ ] **Step 2: Add BOOKING_RETURNED to NotificationType enum**

Replace the entire file `src/main/kotlin/common/NotificationType.kt`:
```kotlin
package common

enum class NotificationType { BOOKING_REQUEST, BOOKING_CONFIRMED, BOOKING_DECLINED, BOOKING_CANCELLED, BOOKING_RETURNED }
```

- [ ] **Step 3: Create DB migration**

Create `src/main/resources/db/changelog/changelog-3.0.sql`:
```sql
--liquibase formatted sql

--changeset mario:5 labels:complete-booking context:complete-booking
--comment: Add RETURNED booking status, return tracking columns, BOOKING_RETURNED notification type

ALTER TABLE item_booking ADD COLUMN returned_by VARCHAR(20);
ALTER TABLE item_booking ADD COLUMN returned_at TIMESTAMPTZ;

--rollback ALTER TABLE item_booking DROP COLUMN returned_by; ALTER TABLE item_booking DROP COLUMN returned_at;
```

Note: `booking_status` and `notification_type` are stored as `VARCHAR` in this schema (see `changelog-2.0.sql` — `status VARCHAR(20)`, `type VARCHAR(50)`), so no ALTER TYPE is needed. The Kotlin enum values are persisted as their string name via `@Enumerated(EnumType.STRING)`.

- [ ] **Step 4: Verify migration compiles and applies**

Run: `./mvnw quarkus:dev` from `backend-quarkus/` — Quarkus starts, Liquibase runs changeset `mario:5` without errors. Stop with Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/changelog/changelog-3.0.sql \
        src/main/kotlin/common/BookingStatus.kt \
        src/main/kotlin/common/NotificationType.kt
git commit -m "feat: add RETURNED booking status and BOOKING_RETURNED notification type"
```

---

## Task 2: Extend ItemBooking entity, ItemBookingModel, and ItemBookingDto

**Files:**
- Modify: `src/main/kotlin/infrastructure/entity/ItemBooking.kt`
- Modify: `src/main/kotlin/domain/model/ItemBookingModel.kt`
- Modify: `src/main/kotlin/api/dto/ItemBookingDto.kt`

**Interfaces:**
- Consumes: `BookingStatus.RETURNED` from Task 1; `CancelledBy` (existing — reused as `returnedBy` type)
- Produces: `ItemBookingModel(isItemOwner: Boolean)`, `ItemBookingDto(isItemOwner: Boolean, returnedBy, returnedAt)` — used by Tasks 3, 4, 5, 6, 7

- [ ] **Step 1: Add returnedBy and returnedAt to ItemBooking entity**

Replace `src/main/kotlin/infrastructure/entity/ItemBooking.kt`:
```kotlin
package infrastructure.entity

import common.BookingStatus
import common.CancelledBy
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.*

@Entity
@Cacheable
@Table(name = "item_booking")
class ItemBooking : PanacheEntityBase {
    companion object : PanacheCompanion<ItemBooking>

    @Column @Id @Basic @JdbcTypeCode(SqlTypes.CHAR)
    var uuid: UUID = UUID.randomUUID()

    @Column(name = "item_uuid") @Basic @JdbcTypeCode(SqlTypes.CHAR)
    lateinit var itemUuid: UUID

    @Column(name = "user_uuid") @Basic @JdbcTypeCode(SqlTypes.CHAR)
    lateinit var userUuid: UUID

    @Column(name = "start_at") lateinit var startAt: OffsetDateTime
    @Column(name = "end_at") lateinit var endAt: OffsetDateTime
    @Column(name = "created_at") lateinit var createdAt: OffsetDateTime

    @Enumerated(EnumType.STRING) @Column
    lateinit var status: BookingStatus

    @Enumerated(EnumType.STRING) @Column(name = "cancelled_by")
    var cancelledBy: CancelledBy? = null

    @Column(name = "cancelled_at")
    var cancelledAt: OffsetDateTime? = null

    @Enumerated(EnumType.STRING) @Column(name = "returned_by")
    var returnedBy: CancelledBy? = null

    @Column(name = "returned_at")
    var returnedAt: OffsetDateTime? = null

    constructor(
        itemUuid: UUID,
        userUuid: UUID,
        startAt: OffsetDateTime,
        endAt: OffsetDateTime,
        status: BookingStatus = BookingStatus.CONFIRMED
    ) : this() {
        this.itemUuid = itemUuid
        this.userUuid = userUuid
        this.startAt = startAt
        this.endAt = endAt
        this.createdAt = OffsetDateTime.now()
        this.status = status
    }

    private constructor()
}
```

Note: `returnedBy` reuses the `CancelledBy` enum (`OWNER | REQUESTER`) — same domain values.

- [ ] **Step 2: Add isItemOwner, returnedBy, returnedAt to ItemBookingModel**

Replace `src/main/kotlin/domain/model/ItemBookingModel.kt`:
```kotlin
package domain.model

import common.BookingStatus
import common.CancelledBy
import infrastructure.entity.ItemBooking
import java.time.OffsetDateTime
import java.util.*

data class ItemBookingModel(
    val uuid: UUID,
    val itemUuid: UUID,
    val userUuid: UUID,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val status: BookingStatus,
    val cancelledBy: CancelledBy?,
    val cancelledAt: OffsetDateTime?,
    val returnedBy: CancelledBy?,
    val returnedAt: OffsetDateTime?,
    val isItemOwner: Boolean = false
) {
    constructor(itemBooking: ItemBooking) : this(
        uuid = itemBooking.uuid,
        itemUuid = itemBooking.itemUuid,
        userUuid = itemBooking.userUuid,
        startAt = itemBooking.startAt,
        endAt = itemBooking.endAt,
        createdAt = itemBooking.createdAt,
        status = itemBooking.status,
        cancelledBy = itemBooking.cancelledBy,
        cancelledAt = itemBooking.cancelledAt,
        returnedBy = itemBooking.returnedBy,
        returnedAt = itemBooking.returnedAt,
        isItemOwner = false
    )
}
```

- [ ] **Step 3: Add isItemOwner, returnedBy, returnedAt to ItemBookingDto**

Replace `src/main/kotlin/api/dto/ItemBookingDto.kt`:
```kotlin
package api.dto

import common.BookingStatus
import common.CancelledBy
import domain.model.ItemBookingModel
import java.time.OffsetDateTime
import java.util.*

data class ItemBookingDto(
    val uuid: UUID,
    val itemUuid: UUID,
    val userUuid: UUID,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val status: BookingStatus,
    val cancelledBy: CancelledBy?,
    val cancelledAt: OffsetDateTime?,
    val returnedBy: CancelledBy?,
    val returnedAt: OffsetDateTime?,
    val isItemOwner: Boolean
) {
    constructor(itemBookingModel: ItemBookingModel) : this(
        uuid = itemBookingModel.uuid,
        itemUuid = itemBookingModel.itemUuid,
        userUuid = itemBookingModel.userUuid,
        startAt = itemBookingModel.startAt,
        endAt = itemBookingModel.endAt,
        createdAt = itemBookingModel.createdAt,
        status = itemBookingModel.status,
        cancelledBy = itemBookingModel.cancelledBy,
        cancelledAt = itemBookingModel.cancelledAt,
        returnedBy = itemBookingModel.returnedBy,
        returnedAt = itemBookingModel.returnedAt,
        isItemOwner = itemBookingModel.isItemOwner
    )
}
```

- [ ] **Step 4: Run compilation check**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS (no errors).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/infrastructure/entity/ItemBooking.kt \
        src/main/kotlin/domain/model/ItemBookingModel.kt \
        src/main/kotlin/api/dto/ItemBookingDto.kt
git commit -m "feat: add returnedBy, returnedAt, isItemOwner to booking model and DTO"
```

---

## Task 3: getBookingDates endpoint

**Files:**
- Create: `src/main/kotlin/api/dto/ItemBookingDateRangeDto.kt`
- Modify: `src/main/kotlin/domain/service/ItemService.kt` (add `getItemBookingDates`)
- Modify: `src/main/kotlin/api/controller/ItemController.kt` (add `GET /{uuid}/booking/dates`)

**Interfaces:**
- Consumes: `ItemBooking` entity, `User`/`Community`/`UserCommunityRelation` for membership check
- Produces: `GET /user/item/{uuid}/booking/dates` → `List<ItemBookingDateRangeDto>` — used by Tasks 8, 13

- [ ] **Step 1: Create ItemBookingDateRangeDto**

Create `src/main/kotlin/api/dto/ItemBookingDateRangeDto.kt`:
```kotlin
package api.dto

import java.time.OffsetDateTime

data class ItemBookingDateRangeDto(
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime
)
```

- [ ] **Step 2: Add getItemBookingDates to ItemService**

Add the following method inside the `ItemService` class (after `getItemBookingsPageOfItem`):
```kotlin
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
```

Also add the `UserCommunityRelation` import at the top of `ItemService.kt`:
```kotlin
import infrastructure.entity.UserCommunityRelation
```

- [ ] **Step 3: Add GET /{uuid}/booking/dates endpoint to ItemController**

Add the following method inside `ItemController` (after `getItemBookingsOfItem`):
```kotlin
@GET
@Path("/{uuid}/booking/dates")
fun getItemBookingDates(
    @RestPath uuid: UUID
): List<ItemBookingDateRangeDto> {
    val userUuid = getUserUuid()
    return itemService.getItemBookingDates(uuid, userUuid)
        .map { (startAt, endAt) -> ItemBookingDateRangeDto(startAt, endAt) }
}
```

Also add `ItemBookingDateRangeDto` to the import in `ItemController.kt` (it imports `api.dto.*` already, so no change needed).

- [ ] **Step 4: Compile**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/api/dto/ItemBookingDateRangeDto.kt \
        src/main/kotlin/domain/service/ItemService.kt \
        src/main/kotlin/api/controller/ItemController.kt
git commit -m "feat: add GET /item/{uuid}/booking/dates endpoint for availability calendar"
```

---

## Task 4: getReceivedBookings endpoint + isItemOwner on existing endpoints

**Files:**
- Modify: `src/main/kotlin/domain/service/ItemService.kt` (add `getReceivedItemBookingsPage`, update `isItemOwner` on existing methods)
- Modify: `src/main/kotlin/api/controller/ItemController.kt` (add `GET /booking/received`, propagate `isItemOwner`)

**Interfaces:**
- Produces: `GET /user/item/booking/received` → `PageDto<ItemBookingDto>` with `isItemOwner = true`; all existing booking endpoints now return correct `isItemOwner`

- [ ] **Step 1: Add getReceivedItemBookingsPage to ItemService**

Add after `getItemBookingsPageOfItem` in `ItemService.kt`:
```kotlin
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
```

- [ ] **Step 2: Add PageModel.empty() extension** (if not already present)

Check `src/main/kotlin/domain/model/PageModel.kt`. If there is no `empty()` companion function, add it. Open the file and append inside the companion/object block:
```kotlin
fun <T> empty(): PageModel<T> = PageModel(
    content = emptyList(),
    isFirstPage = true,
    isLastPage = true,
    pageNumber = 0,
    pageSize = 0,
    totalElements = 0,
    totalPages = 0
)
```
If `PageModel` is a data class without a companion, wrap it: add `companion object { fun <T> empty() ... }`.

- [ ] **Step 3: Update isItemOwner in existing ItemService methods**

In `getItemBookingsPageOfItem` (owner-only view), update the `PageModel.of` call:
```kotlin
return PageModel.of(query) { ItemBookingModel(it).copy(isItemOwner = true) }
```

In `getItemBooking`, update the return to set `isItemOwner`:
```kotlin
return ItemBookingModel(itemBooking).copy(isItemOwner = isOwner)
```

In `bookItem`, `approveBooking`, `declineBooking`, `cancelBooking`: after the mutation and before the `return ItemBookingModel(itemBooking)` line, compute `isOwner` and return with it set. For `bookItem`: the caller is the requester, so `isItemOwner = false`. For `approveBooking`/`declineBooking`: the caller is the owner, so `isItemOwner = true`. For `cancelBooking`: reuse the existing `isOwner` local variable.

Update `bookItem` return:
```kotlin
return ItemBookingModel(itemBooking)  // requester is calling, isItemOwner = false (default)
```
(No change needed — default is false.)

Update `approveBooking` return:
```kotlin
return ItemBookingModel(itemBooking).copy(isItemOwner = true)
```

Update `declineBooking` return:
```kotlin
return ItemBookingModel(itemBooking).copy(isItemOwner = true)
```

Update `cancelBooking` return:
```kotlin
return ItemBookingModel(itemBooking).copy(isItemOwner = isOwner)
```

- [ ] **Step 4: Add GET /booking/received endpoint to ItemController**

Add after `getMyItemBookings` in `ItemController.kt`:
```kotlin
@GET
@Path("/booking/received")
fun getReceivedItemBookings(
    @QueryParam("pageNumber") @Min(0) pageNumber: Int?,
    @QueryParam("pageSize") @Range(min = 1, max = 100) pageSize: Int?,
    @QueryParam("sortBy") sortBy: ItemBookingSortBy?,
    @QueryParam("sortDirection") sortDirection: Sort.Direction?,
): PageDto<ItemBookingDto> {
    val userUuid = getUserUuid()
    val page = itemService.getReceivedItemBookingsPage(
        userUuid,
        PageConfig(pageNumber, pageSize),
        sortBy,
        sortDirection
    )
    return PageDto.of(page, ::ItemBookingDto)
}
```

- [ ] **Step 5: Compile**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/domain/service/ItemService.kt \
        src/main/kotlin/domain/model/PageModel.kt \
        src/main/kotlin/api/controller/ItemController.kt
git commit -m "feat: add GET /booking/received endpoint and isItemOwner flag on all booking responses"
```

---

## Task 5: returnBooking endpoint + notification

**Files:**
- Modify: `src/main/kotlin/domain/model/exception/ErrorCode.kt`
- Modify: `src/main/kotlin/domain/service/ItemService.kt`
- Modify: `src/main/kotlin/api/controller/ItemController.kt`
- Modify: `src/main/kotlin/domain/service/NotificationService.kt`

**Interfaces:**
- Produces: `PATCH /user/item/booking/{uuid}/return` → `ItemBookingDto` with `status = RETURNED`; `BOOKING_RETURNED` notification email

- [ ] **Step 1: Add BookingNotReturnable error code**

In `src/main/kotlin/domain/model/exception/ErrorCode.kt`, add a new entry:
```kotlin
BookingNotReturnable("BOOKING_NOT_RETURNABLE", "booking cannot be returned (must be CONFIRMED)")
```

- [ ] **Step 2: Add returnBooking to ItemService**

Add after `cancelBooking` in `ItemService.kt`:
```kotlin
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
```

- [ ] **Step 3: Add PATCH /booking/{uuid}/return endpoint to ItemController**

Add after `cancelBooking` in `ItemController.kt`:
```kotlin
@PATCH
@Path("/booking/{uuid}/return")
fun returnBooking(
    @RestPath uuid: UUID
): ItemBookingDto {
    val userUuid = getUserUuid()
    val itemBookingModel = itemService.returnBooking(uuid, userUuid)
    return ItemBookingDto(itemBookingModel)
}
```

- [ ] **Step 4: Handle BOOKING_RETURNED in NotificationService**

In `NotificationService.kt`, update the `emailSubject` and `emailBody` when-expressions to add the new case:

In `emailSubject`:
```kotlin
NotificationType.BOOKING_RETURNED -> "An item has been returned"
```

In `emailBody`:
```kotlin
NotificationType.BOOKING_RETURNED ->
    "The item \"${event.itemName}\" has been marked as returned."
```

- [ ] **Step 5: Compile and run full test suite**

```bash
./mvnw test
```
Expected: All existing tests pass (no regressions from the enum additions).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/domain/model/exception/ErrorCode.kt \
        src/main/kotlin/domain/service/ItemService.kt \
        src/main/kotlin/api/controller/ItemController.kt \
        src/main/kotlin/domain/service/NotificationService.kt
git commit -m "feat: add PATCH /booking/{uuid}/return endpoint with BOOKING_RETURNED notification"
```

---

## Task 6: Backend service tests

**Files:**
- Modify: `src/test/kotlin/testUtils/EntityUtil.kt`
- Modify: `src/test/kotlin/domain/service/ItemServiceTest.kt`
- Modify: `src/test/kotlin/domain/service/NotificationServiceTest.kt`

**Interfaces:**
- Consumes: all new service methods from Tasks 3–5

- [ ] **Step 1: Add setupItemBookingReturned helper to EntityUtil** (optional convenience — skip if `setupItemBooking` already covers this via intercept)

In `EntityUtil.kt`, the existing `setupItemBooking` has an `intercept` lambda. No new helper needed — use `setupItemBooking { it.status = BookingStatus.RETURNED }` in tests when needed.

- [ ] **Step 2: Write failing tests for returnBooking in ItemServiceTest**

Add the following tests to `ItemServiceTest` (inside the class, alongside existing booking tests):

```kotlin
@Test
fun `when returning booking as requester, then status becomes RETURNED and returnedBy is REQUESTER`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.status = BookingStatus.CONFIRMED
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val result = itemService.returnBooking(booking.uuid, requestingUser.uuid)
    assertEquals(BookingStatus.RETURNED, result.status)
    assertEquals(CancelledBy.REQUESTER, result.returnedBy)
    assertNotNull(result.returnedAt)
    assertFalse(result.isItemOwner)
}

@Test
fun `when returning booking as owner, then status becomes RETURNED and returnedBy is OWNER`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.status = BookingStatus.CONFIRMED
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val result = itemService.returnBooking(booking.uuid, user.uuid)
    assertEquals(BookingStatus.RETURNED, result.status)
    assertEquals(CancelledBy.OWNER, result.returnedBy)
    assertTrue(result.isItemOwner)
}

@Test
fun `when returning booking that is not CONFIRMED, then exception is thrown`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.status = BookingStatus.CANCELLED
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    assertThrows<CustomBadRequestException> {
        itemService.returnBooking(booking.uuid, requestingUser.uuid)
    }
}

@Test
fun `when returning booking as unrelated user, then exception is thrown`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.status = BookingStatus.CONFIRMED
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    assertThrows<CustomBadRequestException> {
        itemService.returnBooking(booking.uuid, UUID.randomUUID())
    }
}
```

- [ ] **Step 3: Write failing tests for getReceivedItemBookingsPage**

```kotlin
@Test
fun `when getting received bookings page as item owner, then only bookings for owned items are returned`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val result = itemService.getReceivedItemBookingsPage(
        user.uuid, PageConfig(0, 10), null, null
    )
    assertTrue(result.content.any { it.uuid == booking.uuid })
    assertTrue(result.content.all { it.isItemOwner })
}

@Test
fun `when getting received bookings page as non-owner, then empty page is returned`() {
    entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val result = itemService.getReceivedItemBookingsPage(
        requestingUser.uuid, PageConfig(0, 10), null, null
    )
    assertTrue(result.content.isEmpty())
}
```

- [ ] **Step 4: Write failing test for getItemBookingDates**

```kotlin
@Test
fun `when getting booking dates for item, then confirmed booking date ranges are returned`() {
    entityUtil.setupUserCommunityRelation { it.userUuid = requestingUser.uuid; it.communityUuid = community.uuid }
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.status = BookingStatus.CONFIRMED
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(3)
    }
    // requestingUser is a community member; itemOne.communityUuid == community.uuid
    val result = itemService.getItemBookingDates(itemOne.uuid, requestingUser.uuid)
    assertEquals(1, result.size)
    assertOffsetDateTimeEquals(booking.startAt, result.first().first)
}
```

Note: `itemOne` in `ItemServiceTest` is set up in `@BeforeEach`. Check the setup — `itemOne` uses `user.uuid` as owner and `community.uuid` as communityUuid. `requestingUser` needs to be a member of that community for this test. Add the `setupUserCommunityRelation` call as shown.

- [ ] **Step 5: Write test for isItemOwner in getItemBooking**

```kotlin
@Test
fun `when getting booking as item owner, then isItemOwner is true`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val result = itemService.getItemBooking(user.uuid, booking.uuid)
    assertTrue(result.isItemOwner)
}

@Test
fun `when getting booking as requester, then isItemOwner is false`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid
        it.userUuid = requestingUser.uuid
        it.startAt = OffsetDateTime.now().plusDays(1)
        it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val result = itemService.getItemBooking(requestingUser.uuid, booking.uuid)
    assertFalse(result.isItemOwner)
}
```

- [ ] **Step 6: Add BOOKING_RETURNED test to NotificationServiceTest**

In `NotificationServiceTest`, add:
```kotlin
@Test
fun `when booking returned event fires, then notification is persisted with BOOKING_RETURNED type`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = item.uuid
        it.userUuid = user.uuid
        it.status = BookingStatus.RETURNED
        it.startAt = OffsetDateTime.now().minusDays(2)
        it.endAt = OffsetDateTime.now().minusDays(1)
    }
    val event = BookingEvent(
        notificationType = NotificationType.BOOKING_RETURNED,
        recipientUserUuid = user.uuid,
        bookingUuid = booking.uuid,
        itemName = "Drill",
        requesterName = "Alice"
    )
    notificationService.onBookingEvent(event)
    val notification = Notification.find("userUuid", user.uuid).firstResult()
    assertNotNull(notification)
    assertEquals(NotificationType.BOOKING_RETURNED, notification!!.type)
}
```

- [ ] **Step 7: Run service tests**

```bash
./mvnw test -Dtest="ItemServiceTest,NotificationServiceTest" -q
```
Expected: All tests pass (including the new ones).

- [ ] **Step 8: Commit**

```bash
git add src/test/kotlin/domain/service/ItemServiceTest.kt \
        src/test/kotlin/domain/service/NotificationServiceTest.kt \
        src/test/kotlin/testUtils/EntityUtil.kt
git commit -m "test: add service tests for returnBooking, getReceivedBookings, getBookingDates, isItemOwner"
```

---

## Task 7: Backend controller tests

**Files:**
- Modify: `src/test/kotlin/api/controller/ItemControllerTest.kt`

- [ ] **Step 1: Write tests for the three new endpoints**

Add the following tests to `ItemControllerTest`:

```kotlin
@Test
fun `when returning booking as requester, then status 200 and RETURNED status is returned`() {
    // itemBooking in @BeforeEach: userUuid = user.uuid (alice is requester), status = CONFIRMED
    val jsonPath = RestAssured.given().auth().oauth2(accessToken)
        .contentType(ContentType.JSON)
        .patch("/api/user/item/booking/${itemBooking.uuid}/return")
        .then()
        .statusCode(200)
        .extract().response().jsonPath()
    assertEquals("RETURNED", jsonPath.getString("status"))
    assertEquals("REQUESTER", jsonPath.getString("returnedBy"))
    assertTrue(jsonPath.getBoolean("isItemOwner").not())
}

@Test
fun `when returning booking that is not CONFIRMED, then status 400 is returned`() {
    val pendingBooking = entityUtil.setupItemBooking {
        it.itemUuid = item.uuid; it.userUuid = user.uuid; it.status = BookingStatus.PENDING
        it.startAt = OffsetDateTime.now().plusDays(1); it.endAt = OffsetDateTime.now().plusDays(2)
    }
    RestAssured.given().auth().oauth2(accessToken)
        .contentType(ContentType.JSON)
        .patch("/api/user/item/booking/${pendingBooking.uuid}/return")
        .then()
        .statusCode(400)
}

@Test
fun `when getting received bookings as item owner, then status 200 and page is returned`() {
    val ownedItem = entityUtil.setupItem { it.userUuid = user.uuid; it.communityUuid = community.uuid }
    val requester = entityUtil.setupUser()
    entityUtil.setupItemBooking {
        it.itemUuid = ownedItem.uuid; it.userUuid = requester.uuid
        it.startAt = OffsetDateTime.now().plusDays(1); it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val jsonPath = RestAssured.given().auth().oauth2(accessToken)
        .contentType(ContentType.JSON)
        .get("/api/user/item/booking/received")
        .then()
        .statusCode(200)
        .extract().response().jsonPath()
    assertTrue(jsonPath.getInt("totalElements") >= 1)
    assertTrue(jsonPath.getBoolean("content[0].isItemOwner"))
}

@Test
fun `when getting booking dates for item, then status 200 and date ranges are returned`() {
    // item is in communityOne where alice (user) is a member
    val jsonPath = RestAssured.given().auth().oauth2(accessToken)
        .contentType(ContentType.JSON)
        .get("/api/user/item/${item.uuid}/booking/dates")
        .then()
        .statusCode(200)
        .extract().response().jsonPath()
    // itemBooking is CONFIRMED (default) for item.uuid — one date range expected
    assertEquals(1, jsonPath.getList<Any>("").size)
    assertNotNull(jsonPath.getString("[0].startAt"))
}
```

- [ ] **Step 2: Run controller tests**

```bash
./mvnw test -Dtest="ItemControllerTest" -q
```
Expected: All tests pass.

- [ ] **Step 3: Run full test suite to confirm no regressions**

```bash
./mvnw test -q
```
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/api/controller/ItemControllerTest.kt
git commit -m "test: add controller tests for return, received bookings, and booking dates endpoints"
```

---

## Task 8: Next.js — models and lib functions

**Files:**
- Modify: `app/models/booking.ts`
- Modify: `app/lib/bookings.ts`

**Interfaces:**
- Produces: `BookingStatus 'RETURNED'`, `ItemBooking.isItemOwner`, `BookingDateRange` type, three new lib functions — used by Tasks 9, 10, 11, 12

- [ ] **Step 1: Update booking model**

Replace `app/models/booking.ts`:
```typescript
export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED' | 'RETURNED'
export type CancelledBy = 'OWNER' | 'REQUESTER'

export type ItemBooking = {
    uuid: string
    itemUuid: string
    userUuid: string
    startAt: string
    endAt: string
    createdAt: string
    status: BookingStatus
    cancelledBy?: CancelledBy
    cancelledAt?: string
    returnedBy?: CancelledBy
    returnedAt?: string
    isItemOwner: boolean
}

export type BookingDateRange = {
    startAt: string
    endAt: string
}
```

- [ ] **Step 2: Add three new lib functions**

Replace `app/lib/bookings.ts`:
```typescript
import { BookingDateRange, ItemBooking } from '../models/booking'
import { Page } from '../models/pageModel'
import { HttpMethod, request } from '../utils/request'

const bookingPath = (p: string) => `${process.env.BACKEND_BASE_URL}/user/item${p}`

export async function getMyBookings(session?): Promise<Page<ItemBooking> | null> {
    const result = await request<Page<ItemBooking>>(bookingPath('/booking'), session)
    return result.data
}

export async function getReceivedBookings(session?): Promise<Page<ItemBooking> | null> {
    const result = await request<Page<ItemBooking>>(bookingPath('/booking/received'), session)
    return result.data
}

export async function getBookingsForItem(itemUuid: string, session?): Promise<Page<ItemBooking> | null> {
    const result = await request<Page<ItemBooking>>(bookingPath(`/${itemUuid}/booking`), session)
    return result.data
}

export async function getBooking(bookingUuid: string, session?): Promise<ItemBooking | null> {
    const result = await request<ItemBooking>(bookingPath(`/booking/${bookingUuid}`), session)
    return result.data
}

export async function approveBooking(bookingUuid: string, session?) {
    await request(bookingPath(`/booking/${bookingUuid}/approve`), session, HttpMethod.Patch)
}

export async function declineBooking(bookingUuid: string, session?) {
    await request(bookingPath(`/booking/${bookingUuid}/decline`), session, HttpMethod.Patch)
}

export async function cancelBooking(bookingUuid: string, session?) {
    await request(bookingPath(`/booking/${bookingUuid}/cancel`), session, HttpMethod.Patch)
}

export async function returnBooking(bookingUuid: string, session?) {
    await request(bookingPath(`/booking/${bookingUuid}/return`), session, HttpMethod.Patch)
}

export async function getBookingDates(itemUuid: string, session?): Promise<BookingDateRange[]> {
    const result = await request<BookingDateRange[]>(bookingPath(`/${itemUuid}/booking/dates`), session)
    return result.data ?? []
}
```

- [ ] **Step 3: Run type-check**

```bash
npx tsc --noEmit
```
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add app/models/booking.ts app/lib/bookings.ts
git commit -m "feat: add RETURNED status, isItemOwner, BookingDateRange to models and three new lib functions"
```

---

## Task 9: Next.js — bookings index page toggle

**Files:**
- Modify: `pages/bookings/index.tsx`

**Interfaces:**
- Consumes: `getMyBookings`, `getReceivedBookings` from Task 8; `ToggleButton` component (existing at `app/components/toggleButton`)

- [ ] **Step 1: Rewrite pages/bookings/index.tsx**

Replace `pages/bookings/index.tsx`:
```tsx
import { GetServerSideProps } from 'next'
import { getSession } from 'next-auth/react'
import { useTranslation } from 'next-i18next/pages'
import { serverSideTranslations } from 'next-i18next/pages/serverSideTranslations'
import Head from 'next/head'
import Link from 'next/link'
import { useState } from 'react'
import { getMyBookings, getReceivedBookings } from '../../app/lib/bookings'
import { ItemBooking, BookingStatus } from '../../app/models/booking'
import ToggleButton from '../../app/components/toggleButton'
import styles from '../../app/styles/utils.module.css'

function statusColor(status: BookingStatus): string {
    switch (status) {
        case 'PENDING': return 'orange'
        case 'CONFIRMED': return 'green'
        case 'DECLINED': return 'red'
        case 'CANCELLED': return 'gray'
        case 'RETURNED': return 'blue'
    }
}

function BookingList({ bookings, noBookingsKey }: { bookings: ItemBooking[], noBookingsKey: string }) {
    const { t } = useTranslation('common')
    if (bookings.length === 0) return <p>{t(noBookingsKey)}</p>
    return (
        <ul>
            {bookings.map(b => (
                <li key={b.uuid} style={{ marginBottom: '12px' }}>
                    <Link href={`/bookings/${b.uuid}`}>
                        <span style={{ color: statusColor(b.status), fontWeight: 'bold', marginRight: '8px' }}>
                            [{t(`booking.status.${b.status.toLowerCase()}`)}]
                        </span>
                        {new Date(b.startAt).toLocaleDateString()} – {new Date(b.endAt).toLocaleDateString()}
                    </Link>
                </li>
            ))}
        </ul>
    )
}

export default function BookingsPage({
    myBookings,
    receivedBookings,
}: {
    myBookings: ItemBooking[]
    receivedBookings: ItemBooking[]
}) {
    const { t } = useTranslation('common')
    const [tabIndex, setTabIndex] = useState(0)

    return (
        <>
            <Head><title>{t('bookings.title')}</title></Head>
            <h1 className={styles.headingXl}>{t('bookings.title')}</h1>
            <ToggleButton
                titleOne={t('bookings.myRequests')}
                titleTwo={t('bookings.receivedBookings')}
                selectedIndex={tabIndex}
                setSelectedIndex={setTabIndex}
            />
            <br />
            {tabIndex === 0
                ? <BookingList bookings={myBookings} noBookingsKey="bookings.noBookings" />
                : <BookingList bookings={receivedBookings} noBookingsKey="bookings.noBookings" />
            }
        </>
    )
}

export const getServerSideProps: GetServerSideProps = async (context) => {
    const session = await getSession(context)
    if (!session) return { redirect: { destination: '/login', permanent: false } }
    const [myPage, receivedPage] = await Promise.all([
        getMyBookings(session),
        getReceivedBookings(session),
    ])
    return {
        props: {
            ...(await serverSideTranslations(context.locale || 'en', ['common'])),
            myBookings: myPage?.content ?? [],
            receivedBookings: receivedPage?.content ?? [],
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add pages/bookings/index.tsx
git commit -m "feat: add owner/requester toggle to bookings list page"
```

---

## Task 10: Next.js — booking detail page fix and return button

**Files:**
- Modify: `pages/bookings/[uuid].tsx`

**Interfaces:**
- Consumes: `booking.isItemOwner` (Task 8); `returnBooking` lib function (Task 8)

- [ ] **Step 1: Rewrite pages/bookings/[uuid].tsx**

Replace `pages/bookings/[uuid].tsx`:
```tsx
import { GetServerSideProps } from 'next'
import { getSession, useSession } from 'next-auth/react'
import { useTranslation } from 'next-i18next/pages'
import { serverSideTranslations } from 'next-i18next/pages/serverSideTranslations'
import Head from 'next/head'
import { useRouter } from 'next/router'
import { ItemBooking, BookingStatus } from '../../app/models/booking'
import { getBooking, approveBooking, declineBooking, cancelBooking, returnBooking } from '../../app/lib/bookings'
import Button from '../../app/components/button'
import styles from '../../app/styles/utils.module.css'

function statusColor(status: BookingStatus): string {
    switch (status) {
        case 'PENDING': return 'orange'
        case 'CONFIRMED': return 'green'
        case 'DECLINED': return 'red'
        case 'CANCELLED': return 'gray'
        case 'RETURNED': return 'blue'
    }
}

export default function BookingDetailPage({ booking }: { booking: ItemBooking | null }) {
    const { t } = useTranslation('common')
    const { data: session } = useSession()
    const router = useRouter()

    if (!booking) return <p>{t('bookings.notFound')}</p>

    const isOwner = booking.isItemOwner
    const canCancel = (booking.status === 'PENDING' || booking.status === 'CONFIRMED')
        && new Date(booking.startAt) > new Date()
    const canReturn = booking.status === 'CONFIRMED'

    return (
        <>
            <Head><title>{t('bookings.detail')}</title></Head>
            <h1 className={styles.headingXl}>{t('bookings.detail')}</h1>
            <p>
                <strong>{t('booking.status.label')}:</strong>{' '}
                <span style={{ color: statusColor(booking.status) }}>
                    {t(`booking.status.${booking.status.toLowerCase()}`)}
                </span>
            </p>
            <p><strong>{t('booking.from')}:</strong> {new Date(booking.startAt).toLocaleDateString()}</p>
            <p><strong>{t('booking.to')}:</strong> {new Date(booking.endAt).toLocaleDateString()}</p>
            <br />
            {booking.status === 'PENDING' && isOwner && (
                <>
                    <Button title={t('booking.approve')} onClick={async () => {
                        await approveBooking(booking.uuid, session)
                        router.push('/bookings')
                    }} isDisabled={false} type="button" />
                    <Button title={t('booking.decline')} onClick={async () => {
                        await declineBooking(booking.uuid, session)
                        router.push('/bookings')
                    }} isDisabled={false} type="button" />
                </>
            )}
            {canCancel && (
                <Button title={t('booking.cancel')} onClick={async () => {
                    await cancelBooking(booking.uuid, session)
                    router.push('/bookings')
                }} isDisabled={false} type="button" />
            )}
            {canReturn && (
                <Button title={t('booking.return')} onClick={async () => {
                    await returnBooking(booking.uuid, session)
                    router.push('/bookings')
                }} isDisabled={false} type="button" />
            )}
        </>
    )
}

export const getServerSideProps: GetServerSideProps = async (context) => {
    const session = await getSession(context)
    if (!session) return { redirect: { destination: '/login', permanent: false } }
    const { uuid } = context.params!
    const booking = await getBooking(uuid as string, session)
    if (!booking) return { notFound: true }
    return {
        props: {
            ...(await serverSideTranslations(context.locale || 'en', ['common'])),
            booking,
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add pages/bookings/[uuid].tsx
git commit -m "fix: use isItemOwner from DTO, add Return button to booking detail page"
```

---

## Task 11: Next.js — item availability calendar

**Files:**
- Modify: `pages/items/[id].tsx`
- Modify: `app/components/subpages/foreignItem.tsx`

**Interfaces:**
- Consumes: `getBookingDates` lib function (Task 8); `BookingDateRange` type (Task 8)
- `react-datepicker`'s `excludeDateIntervals` prop accepts `Array<{ start: Date, end: Date }>`

- [ ] **Step 1: Fetch booking dates in getServerSideProps of pages/items/[id].tsx**

Replace `pages/items/[id].tsx`:
```tsx
import { getItem, getItemImageUuids } from '../../app/lib/items'
import { serverSideTranslations } from 'next-i18next/pages/serverSideTranslations'
import { getServerSideSession } from '../../app/utils/session'
import ownedItem from '../../app/components/subpages/ownedItem'
import foreignItem from '../../app/components/subpages/foreignItem'
import { useSession } from 'next-auth/react'
import { useTranslation } from 'next-i18next/pages'
import { getMyCommunities } from '../../app/lib/communities'
import { getBookingDates } from '../../app/lib/bookings'
import { BookingDateRange } from '../../app/models/booking'

export async function getServerSideProps(context) {
    const session = await getServerSideSession(context)
    const { id } = context.params
    const isOwned = context.query.isOwned ?? 'false'
    const itemData = await getItem(id, session)
    const itemImageData = await getItemImageUuids(id, session)
    const myCommunities = await getMyCommunities(session)
    const bookedDates: BookingDateRange[] = isOwned === 'true' ? [] : await getBookingDates(id, session)

    return {
        props: {
            session,
            itemData,
            itemImageData,
            myCommunities,
            isOwned,
            bookedDates,
            ...(await serverSideTranslations(context.locale ?? 'en', ['common'])),
        },
    }
}

export default function Item({
    itemData,
    itemImageData,
    myCommunities,
    isOwned = 'false',
    bookedDates = [],
}) {
    const { t } = useTranslation('common')
    const session = useSession().data

    if (!itemData) return null

    return isOwned === 'true'
        ? ownedItem(session, itemData, itemImageData, myCommunities.content, t)
        : foreignItem(session, itemData, itemImageData, bookedDates, t)
}
```

- [ ] **Step 2: Accept bookedDates in foreignItem and pass to DatePicker**

Replace `app/components/subpages/foreignItem.tsx`:
```tsx
import Head from 'next/head'
import utilStyles from '../../styles/utils.module.css'
import KeyValue from '../keyValue'
import Button from '../button'
import { Item, itemCategories } from '../../models/item'
import { bookItem, getItemImage } from '../../lib/items'
import { useEffect, useState } from 'react'
import ImageGallery from '../imageGallery'
import { Session } from 'next-auth'
import CustomDatePicker from '../datePicker'
import { BookingDateRange } from '../../models/booking'

export default function foreignItem(
    session: Session | null,
    itemData: Item,
    itemImageData: [String],
    bookedDates: BookingDateRange[],
    t
) {
    const [images, setImages] = useState<String[]>([])
    const [currentImageIndex, setCurrentImageIndex] = useState(0)
    const [startDate, setStartDate] = useState(new Date())
    const [endDate, setEndDate] = useState(new Date())
    const [booked, setBooked] = useState(false)
    const categories = itemData.categories
        .map((category) => itemCategories(t).find((e) => e.id === category))
        .filter((c): c is { id: string; label: string } => c !== undefined)

    const excludedIntervals = bookedDates.map(({ startAt, endAt }) => ({
        start: new Date(startAt),
        end: new Date(endAt),
    }))

    useEffect(() => {
        const createdUrls: string[] = []
        async function loadImages() {
            let itemImages: String[] = []
            for (const itemImageUuid of itemImageData) {
                const itemImageBlob: Blob | null = await getItemImage(itemImageUuid, session)
                if (itemImageBlob) {
                    const url = URL.createObjectURL(itemImageBlob)
                    createdUrls.push(url)
                    itemImages.push(url)
                }
            }
            setImages(itemImages)
        }
        loadImages()
        return () => { createdUrls.forEach((url) => URL.revokeObjectURL(url)) }
    }, [])

    return (
        <div>
            <Head><title>{itemData.name}</title></Head>
            <article>
                <h1 className={utilStyles.headingXl}>{itemData.name}</h1>
            </article>
            <div>
                {images.length > 0 && (
                    <div className="horizontal_container">
                        <ImageGallery
                            images={images}
                            currentImageIndex={currentImageIndex}
                            setCurrentImageIndex={setCurrentImageIndex}
                            t={t}
                        />
                    </div>
                )}
            </div>
            <br />
            <KeyValue name={t('item.category.category')} value={categories.map((c) => c.label)} />
            <KeyValue name={t('item.description')} value={itemData.description} />
            <br />
            <KeyValue
                name={t('item.desiredBookingStartDate')}
                value={
                    <CustomDatePicker
                        selectedDate={startDate}
                        setSelectedDate={setStartDate}
                        excludeDateIntervals={excludedIntervals}
                    />
                }
            />
            <br />
            <KeyValue
                name={t('item.desiredBookingEndDate')}
                value={
                    <CustomDatePicker
                        selectedDate={endDate}
                        setSelectedDate={setEndDate}
                        excludeDateIntervals={excludedIntervals}
                    />
                }
            />
            <br />
            {itemData.requiresApproval && (
                <p style={{ color: 'orange' }}>{t('item.approvalRequired')}</p>
            )}
            {booked ? (
                <p style={{ color: 'green' }}>{t('item.bookingRequested')}</p>
            ) : (
                <Button
                    title={t('item.requestItem')}
                    onClick={async () => {
                        await bookItem(itemData.uuid, startDate, endDate, session)
                        setBooked(true)
                    }}
                    isDisabled={false}
                    type="button"
                />
            )}
        </div>
    )
}
```

- [ ] **Step 3: Pass excludeDateIntervals through to react-datepicker in datePicker.tsx**

Modify `app/components/datePicker.tsx` to forward the prop:
```tsx
import DatePicker from 'react-datepicker'
import 'react-datepicker/dist/react-datepicker.css'

export default function CustomDatePicker({ selectedDate, setSelectedDate, excludeDateIntervals = [] }) {
    return (
        <DatePicker
            selected={selectedDate}
            onChange={(date) => setSelectedDate(date)}
            showTimeSelect
            showIcon
            timeFormat="HH:mm"
            timeIntervals={15}
            timeCaption="Time"
            dateFormat="MMMM d, yyyy h:mm aa"
            className="form-control"
            excludeDateIntervals={excludeDateIntervals}
        />
    )
}
```

- [ ] **Step 4: Run type-check**

```bash
npx tsc --noEmit
```
Expected: No errors.

- [ ] **Step 5: Commit**

```bash
git add pages/items/[id].tsx \
        app/components/subpages/foreignItem.tsx \
        app/components/datePicker.tsx
git commit -m "feat: show booked date ranges as excluded intervals on item availability calendar"
```

---

## Task 12: Next.js — i18n keys and test updates

**Files:**
- Modify: `public/locales/en/common.json`
- Modify: `public/locales/de/common.json`
- Modify: `__tests__/pages/bookings/index.test.tsx`
- Modify: `__tests__/pages/bookings/[uuid].test.tsx`

- [ ] **Step 1: Add new i18n keys to EN locale**

In `public/locales/en/common.json`, inside the `"bookings"` object add:
```json
"myRequests": "My Requests",
"receivedBookings": "Received Bookings"
```

Inside the `"booking"` object add:
```json
"return": "Mark as Returned"
```

Inside `"booking.status"` add:
```json
"returned": "Returned"
```

Inside `"notification.type"` add:
```json
"BOOKING_RETURNED": "Item returned"
```

- [ ] **Step 2: Add same keys to DE locale**

In `public/locales/de/common.json`, add the same keys in German:
```json
"bookings": { "myRequests": "Meine Anfragen", "receivedBookings": "Erhaltene Buchungen" }
"booking": { "return": "Als zurückgegeben markieren", "status": { "returned": "Zurückgegeben" } }
"notification.type": { "BOOKING_RETURNED": "Artikel zurückgegeben" }
```

- [ ] **Step 3: Update __tests__/pages/bookings/index.test.tsx**

Replace the file:
```tsx
import React from 'react'
import { render, screen } from '@testing-library/react'
import BookingsPage, { getServerSideProps } from '../../../pages/bookings/index'

jest.mock('next-auth/react', () => ({
    ...jest.requireActual('next-auth/react'),
    getSession: jest.fn(),
    useSession: jest.fn(() => ({ data: { accessToken: 'mock-token' }, status: 'authenticated' })),
}))

jest.mock('app/lib/bookings', () => ({
    getMyBookings: jest.fn(),
    getReceivedBookings: jest.fn(),
}))

const { getSession } = require('next-auth/react')
const { getMyBookings, getReceivedBookings } = require('app/lib/bookings')

const mockBooking = {
    uuid: 'b-1',
    itemUuid: 'i-1',
    userUuid: 'u-1',
    startAt: new Date('2025-03-01').toISOString(),
    endAt: new Date('2025-03-05').toISOString(),
    createdAt: new Date('2025-02-01').toISOString(),
    status: 'PENDING' as const,
    isItemOwner: false,
}

const Page = BookingsPage as React.ComponentType<any>

describe('Bookings index page — getServerSideProps', () => {
    test('fetches both booking lists and returns as props', async () => {
        const mockSession = { accessToken: 'token' }
        getSession.mockResolvedValue(mockSession)
        getMyBookings.mockResolvedValue({ content: [mockBooking] })
        getReceivedBookings.mockResolvedValue({ content: [] })
        const context = { req: {}, res: {}, locale: 'en', params: {} }
        const result = await getServerSideProps(context as any)
        expect('props' in result).toBe(true)
        if ('props' in result) {
            expect(result.props.myBookings).toEqual([mockBooking])
            expect(result.props.receivedBookings).toEqual([])
        }
    })

    test('redirects to login when no session', async () => {
        getSession.mockResolvedValue(null)
        const context = { req: {}, res: {}, locale: 'en', params: {} }
        const result = await getServerSideProps(context as any)
        expect('redirect' in result).toBe(true)
    })
})

describe('Bookings index page — component', () => {
    test('renders page title', () => {
        render(<Page myBookings={[]} receivedBookings={[]} />)
        expect(screen.getByText('bookings.title')).toBeInTheDocument()
    })

    test('renders toggle buttons', () => {
        render(<Page myBookings={[]} receivedBookings={[]} />)
        expect(screen.getByText('bookings.myRequests')).toBeInTheDocument()
        expect(screen.getByText('bookings.receivedBookings')).toBeInTheDocument()
    })

    test('renders empty state when no bookings', () => {
        render(<Page myBookings={[]} receivedBookings={[]} />)
        expect(screen.getByText('bookings.noBookings')).toBeInTheDocument()
    })

    test('renders booking with status badge', () => {
        render(<Page myBookings={[mockBooking]} receivedBookings={[]} />)
        expect(screen.getByText(/booking\.status\.pending/)).toBeInTheDocument()
    })
})
```

- [ ] **Step 4: Update __tests__/pages/bookings/[uuid].test.tsx**

Replace the file:
```tsx
import React from 'react'
import { render, screen } from '@testing-library/react'
import BookingDetailPage, { getServerSideProps } from '../../../pages/bookings/[uuid]'

jest.mock('next-auth/react', () => ({
    ...jest.requireActual('next-auth/react'),
    getSession: jest.fn(),
    useSession: jest.fn(() => ({ data: { accessToken: 'mock-token' }, status: 'authenticated' })),
}))

jest.mock('next/router', () => ({ useRouter: () => ({ push: jest.fn() }) }))

jest.mock('app/lib/bookings', () => ({
    getBooking: jest.fn(),
    approveBooking: jest.fn(),
    declineBooking: jest.fn(),
    cancelBooking: jest.fn(),
    returnBooking: jest.fn(),
}))

const { getSession } = require('next-auth/react')
const { getBooking } = require('app/lib/bookings')

const futureBooking = {
    uuid: 'b-1',
    itemUuid: 'i-1',
    userUuid: 'u-1',
    startAt: new Date(Date.now() + 86400000 * 5).toISOString(),
    endAt: new Date(Date.now() + 86400000 * 10).toISOString(),
    createdAt: new Date().toISOString(),
    status: 'CONFIRMED' as const,
    isItemOwner: false,
}

const pendingOwnerBooking = {
    ...futureBooking,
    status: 'PENDING' as const,
    isItemOwner: true,
}

const Page = BookingDetailPage as React.ComponentType<any>

describe('Booking detail page — getServerSideProps', () => {
    test('returns booking as prop when found', async () => {
        getSession.mockResolvedValue({ accessToken: 'token' })
        getBooking.mockResolvedValue(futureBooking)
        const context = { req: {}, res: {}, locale: 'en', params: { uuid: 'b-1' } }
        const result = await getServerSideProps(context as any)
        expect('props' in result).toBe(true)
        if ('props' in result) expect(result.props.booking).toEqual(futureBooking)
    })

    test('returns notFound when booking is null', async () => {
        getSession.mockResolvedValue({ accessToken: 'token' })
        getBooking.mockResolvedValue(null)
        const context = { req: {}, res: {}, locale: 'en', params: { uuid: 'b-1' } }
        const result = await getServerSideProps(context as any)
        expect('notFound' in result).toBe(true)
    })

    test('redirects to login when no session', async () => {
        getSession.mockResolvedValue(null)
        const context = { req: {}, res: {}, locale: 'en', params: { uuid: 'b-1' } }
        const result = await getServerSideProps(context as any)
        expect('redirect' in result).toBe(true)
    })
})

describe('Booking detail page — component', () => {
    test('renders status and dates', () => {
        render(<Page booking={futureBooking} />)
        expect(screen.getByText(/booking\.status\.label/)).toBeInTheDocument()
    })

    test('shows Return button when status is CONFIRMED', () => {
        render(<Page booking={futureBooking} />)
        expect(screen.getByText('booking.return')).toBeInTheDocument()
    })

    test('shows Approve and Decline buttons when isItemOwner and PENDING', () => {
        render(<Page booking={pendingOwnerBooking} />)
        expect(screen.getByText('booking.approve')).toBeInTheDocument()
        expect(screen.getByText('booking.decline')).toBeInTheDocument()
    })

    test('does not show Approve/Decline for non-owner', () => {
        const nonOwnerPending = { ...pendingOwnerBooking, isItemOwner: false }
        render(<Page booking={nonOwnerPending} />)
        expect(screen.queryByText('booking.approve')).not.toBeInTheDocument()
    })

    test('shows not found message when booking is null', () => {
        render(<Page booking={null} />)
        expect(screen.getByText('bookings.notFound')).toBeInTheDocument()
    })
})
```

- [ ] **Step 5: Run Next.js tests**

```bash
npm test -- --testPathPattern="bookings" --watchAll=false
```
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add public/locales/en/common.json \
        public/locales/de/common.json \
        __tests__/pages/bookings/index.test.tsx \
        __tests__/pages/bookings/[uuid].test.tsx
git commit -m "feat: add i18n keys for RETURNED status, return action, and booking tabs; update tests"
```

---

## Task 13: Flutter — models and BookingService

**Files:**
- Modify: `lib/model/item_booking.dart`
- Create: `lib/model/booking_date_range.dart`
- Modify: `lib/service/booking_service.dart`

**Interfaces:**
- Produces: `BookingStatus.returned`, `ItemBooking.isItemOwner`, `BookingDateRange`, three new service methods — used by Tasks 14–17

- [ ] **Step 1: Add returned to BookingStatus and isItemOwner to ItemBooking**

Replace `lib/model/item_booking.dart`:
```dart
import 'package:frontend_flutter/util/json_util.dart';

enum BookingStatus {
  pending,
  confirmed,
  declined,
  cancelled,
  returned;

  factory BookingStatus.fromJson(String? value) {
    switch (value?.toLowerCase()) {
      case 'pending':
        return BookingStatus.pending;
      case 'confirmed':
        return BookingStatus.confirmed;
      case 'declined':
        return BookingStatus.declined;
      case 'cancelled':
        return BookingStatus.cancelled;
      case 'returned':
        return BookingStatus.returned;
      default:
        return BookingStatus.confirmed;
    }
  }
}

enum CancelledBy {
  owner,
  requester;

  factory CancelledBy.fromJson(String? value) {
    switch (value?.toLowerCase()) {
      case 'owner':
        return CancelledBy.owner;
      case 'requester':
        return CancelledBy.requester;
      default:
        return CancelledBy.requester;
    }
  }
}

class ItemBooking {
  final String? uuid;
  final String? itemUuid;
  final String? userUuid;
  final DateTime? startAt;
  final DateTime? endAt;
  final DateTime? createdAt;
  final BookingStatus status;
  final CancelledBy? cancelledBy;
  final DateTime? cancelledAt;
  final CancelledBy? returnedBy;
  final DateTime? returnedAt;
  final bool isItemOwner;

  ItemBooking({
    this.uuid,
    this.itemUuid,
    this.userUuid,
    this.startAt,
    this.endAt,
    this.createdAt,
    this.status = BookingStatus.confirmed,
    this.cancelledBy,
    this.cancelledAt,
    this.returnedBy,
    this.returnedAt,
    this.isItemOwner = false,
  });

  factory ItemBooking.fromJson(Map<String, dynamic> json) {
    return ItemBooking(
      uuid: json['uuid'],
      itemUuid: json['itemUuid'],
      userUuid: json['userUuid'],
      startAt: JsonUtil.parseDateString(json['startAt']),
      endAt: JsonUtil.parseDateString(json['endAt']),
      createdAt: JsonUtil.parseDateString(json['createdAt']),
      status: BookingStatus.fromJson(json['status']),
      cancelledBy: json['cancelledBy'] != null
          ? CancelledBy.fromJson(json['cancelledBy'])
          : null,
      cancelledAt: JsonUtil.parseDateString(json['cancelledAt']),
      returnedBy: json['returnedBy'] != null
          ? CancelledBy.fromJson(json['returnedBy'])
          : null,
      returnedAt: JsonUtil.parseDateString(json['returnedAt']),
      isItemOwner: json['isItemOwner'] ?? false,
    );
  }
}
```

- [ ] **Step 2: Create BookingDateRange model**

Create `lib/model/booking_date_range.dart`:
```dart
import 'package:frontend_flutter/util/json_util.dart';

class BookingDateRange {
  final DateTime? startAt;
  final DateTime? endAt;

  BookingDateRange({this.startAt, this.endAt});

  factory BookingDateRange.fromJson(Map<String, dynamic> json) {
    return BookingDateRange(
      startAt: JsonUtil.parseDateString(json['startAt']),
      endAt: JsonUtil.parseDateString(json['endAt']),
    );
  }
}
```

- [ ] **Step 3: Add three new methods to BookingService**

Replace `lib/service/booking_service.dart`:
```dart
import 'package:frontend_flutter/di/service_locator.dart';
import 'package:frontend_flutter/model/booking_date_range.dart';
import 'package:frontend_flutter/model/data_page.dart';
import 'package:frontend_flutter/model/data_response.dart';
import 'package:frontend_flutter/model/http_json_response.dart';
import 'package:frontend_flutter/model/item_booking.dart';
import 'package:frontend_flutter/service/request_service.dart';
import 'package:frontend_flutter/util/http_helper.dart';

class BookingService {
  final RequestService _requestService = getIt.get<RequestService>();
  final String _itemPath = 'user/item';

  Future<DataResponse<ItemBooking>> bookItem(
      String itemUuid, DateTime startAt, DateTime endAt) async {
    final body = {
      'startAt': startAt.toUtc().toIso8601String(),
      'endAt': endAt.toUtc().toIso8601String(),
    };
    final response = await _requestService.request(
      '$_itemPath/$itemUuid/booking',
      method: HttpMethod.post,
      body: body,
    );
    return _bookingFromResponse(response);
  }

  Future<DataResponse<DataPage<ItemBooking>>> getMyBookings(
      {int pageNumber = 0}) async {
    final response = await _requestService.request(
      '$_itemPath/booking',
      queryParameters: {
        'pageSize': '20',
        'pageNumber': pageNumber.toString()
      },
    );
    return _bookingPageFromResponse(response);
  }

  Future<DataResponse<DataPage<ItemBooking>>> getReceivedBookings(
      {int pageNumber = 0}) async {
    final response = await _requestService.request(
      '$_itemPath/booking/received',
      queryParameters: {
        'pageSize': '20',
        'pageNumber': pageNumber.toString()
      },
    );
    return _bookingPageFromResponse(response);
  }

  Future<DataResponse<DataPage<ItemBooking>>> getBookingsForItem(
      String itemUuid, {int pageNumber = 0}) async {
    final response = await _requestService.request(
      '$_itemPath/$itemUuid/booking',
      queryParameters: {
        'pageSize': '20',
        'pageNumber': pageNumber.toString()
      },
    );
    return _bookingPageFromResponse(response);
  }

  Future<DataResponse<ItemBooking>> getBooking(String bookingUuid) async {
    final response =
        await _requestService.request('$_itemPath/booking/$bookingUuid');
    return _bookingFromResponse(response);
  }

  Future<DataResponse<ItemBooking>> approveBooking(String bookingUuid) async {
    final response = await _requestService.request(
      '$_itemPath/booking/$bookingUuid/approve',
      method: HttpMethod.patch,
    );
    return _bookingFromResponse(response);
  }

  Future<DataResponse<ItemBooking>> declineBooking(String bookingUuid) async {
    final response = await _requestService.request(
      '$_itemPath/booking/$bookingUuid/decline',
      method: HttpMethod.patch,
    );
    return _bookingFromResponse(response);
  }

  Future<DataResponse<ItemBooking>> cancelBooking(String bookingUuid) async {
    final response = await _requestService.request(
      '$_itemPath/booking/$bookingUuid/cancel',
      method: HttpMethod.patch,
    );
    return _bookingFromResponse(response);
  }

  Future<DataResponse<ItemBooking>> returnBooking(String bookingUuid) async {
    final response = await _requestService.request(
      '$_itemPath/booking/$bookingUuid/return',
      method: HttpMethod.patch,
    );
    return _bookingFromResponse(response);
  }

  Future<List<BookingDateRange>> getBookingDates(String itemUuid) async {
    final response = await _requestService.request(
      '$_itemPath/$itemUuid/booking/dates',
    );
    final json = response.getData();
    if (json == null || json is! List) return [];
    return (json as List)
        .map((e) => BookingDateRange.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  DataResponse<ItemBooking> _bookingFromResponse(HttpDataResponse response) {
    final json = response.getData();
    ItemBooking? booking;
    if (json != null) booking = ItemBooking.fromJson(json);
    return DataResponse.fromHttpResponse(booking, response);
  }

  DataResponse<DataPage<ItemBooking>> _bookingPageFromResponse(
      HttpDataResponse response) {
    final json = response.getData();
    DataPage<ItemBooking>? page;
    if (json != null) page = DataPage.fromJson(json, ItemBooking.fromJson);
    return DataResponse.fromHttpResponse(page, response);
  }
}
```

- [ ] **Step 4: Run Flutter analysis**

```bash
flutter analyze lib/
```
Expected: No errors.

- [ ] **Step 5: Commit**

```bash
git add lib/model/item_booking.dart \
        lib/model/booking_date_range.dart \
        lib/service/booking_service.dart
git commit -m "feat: add returned status, isItemOwner, BookingDateRange, and three new BookingService methods"
```

---

## Task 14: Flutter — BookingCubit and BookingDetailCubit

**Files:**
- Modify: `lib/widget/booking/booking_cubit.dart`
- Modify: `lib/widget/booking/booking_detail_cubit.dart`

**Interfaces:**
- Produces: `BookingCubit.loadLenderBookings()`, `BookingDetailCubit.returnBooking()` — used by Tasks 15, 16

- [ ] **Step 1: Update BookingCubit to load both lists**

Replace `lib/widget/booking/booking_cubit.dart`:
```dart
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:frontend_flutter/di/service_locator.dart';
import 'package:frontend_flutter/model/data_page.dart';
import 'package:frontend_flutter/model/data_response.dart';
import 'package:frontend_flutter/model/item_booking.dart';
import 'package:frontend_flutter/service/booking_service.dart';

class BookingState {
  final DataPage<ItemBooking>? myBookings;
  final DataPage<ItemBooking>? lenderBookings;
  final String? errorMessage;

  BookingState({this.myBookings, this.lenderBookings, this.errorMessage});
}

class BookingCubit extends Cubit<DataResponse<BookingState>> {
  final BookingService _bookingService = getIt.get<BookingService>();

  BookingCubit(super.initialState) {
    loadAllBookings();
  }

  factory BookingCubit.ofInitialState() {
    return BookingCubit(DataResponse.empty());
  }

  Future<void> loadAllBookings({int pageNumber = 0}) async {
    final results = await Future.wait([
      _bookingService.getMyBookings(pageNumber: pageNumber),
      _bookingService.getReceivedBookings(pageNumber: pageNumber),
    ]);
    final myResponse = results[0] as DataResponse<DataPage<ItemBooking>>;
    final lenderResponse = results[1] as DataResponse<DataPage<ItemBooking>>;
    emit(DataResponse(
      data: BookingState(
        myBookings: myResponse.data,
        lenderBookings: lenderResponse.data,
      ),
      errorMessage: myResponse.errorMessage ?? lenderResponse.errorMessage,
    ));
  }

  Future<void> loadMyBookings({int pageNumber = 0}) async {
    final response = await _bookingService.getMyBookings(pageNumber: pageNumber);
    emit(DataResponse(
      data: BookingState(
        myBookings: response.data,
        lenderBookings: state.data?.lenderBookings,
      ),
      errorMessage: response.errorMessage,
    ));
  }

  Future<void> loadLenderBookings({int pageNumber = 0}) async {
    final response = await _bookingService.getReceivedBookings(pageNumber: pageNumber);
    emit(DataResponse(
      data: BookingState(
        myBookings: state.data?.myBookings,
        lenderBookings: response.data,
      ),
      errorMessage: response.errorMessage,
    ));
  }
}
```

- [ ] **Step 2: Add returnBooking to BookingDetailCubit**

Replace `lib/widget/booking/booking_detail_cubit.dart`:
```dart
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:frontend_flutter/di/service_locator.dart';
import 'package:frontend_flutter/model/data_response.dart';
import 'package:frontend_flutter/model/item_booking.dart';
import 'package:frontend_flutter/service/booking_service.dart';

class BookingDetailCubit extends Cubit<DataResponse<ItemBooking>> {
  final BookingService _bookingService = getIt.get<BookingService>();

  BookingDetailCubit(super.initialState);

  factory BookingDetailCubit.ofInitialState({required ItemBooking booking}) {
    return BookingDetailCubit(DataResponse(data: booking, errorMessage: null));
  }

  Future<void> approve() async {
    final uuid = state.data?.uuid;
    if (uuid == null) return;
    final response = await _bookingService.approveBooking(uuid);
    emit(response);
  }

  Future<void> decline() async {
    final uuid = state.data?.uuid;
    if (uuid == null) return;
    final response = await _bookingService.declineBooking(uuid);
    emit(response);
  }

  Future<void> cancel() async {
    final uuid = state.data?.uuid;
    if (uuid == null) return;
    final response = await _bookingService.cancelBooking(uuid);
    emit(response);
  }

  Future<void> returnItem() async {
    final uuid = state.data?.uuid;
    if (uuid == null) return;
    final response = await _bookingService.returnBooking(uuid);
    emit(response);
  }
}
```

- [ ] **Step 3: Run Flutter analysis**

```bash
flutter analyze lib/widget/booking/
```
Expected: No errors.

- [ ] **Step 4: Commit**

```bash
git add lib/widget/booking/booking_cubit.dart \
        lib/widget/booking/booking_detail_cubit.dart
git commit -m "feat: BookingCubit loads both my and lender bookings; BookingDetailCubit adds returnItem()"
```

---

## Task 15: Flutter — BookingDetailScreen and MyAreaScreen

**Files:**
- Modify: `lib/widget/booking/booking_detail_screen.dart`
- Modify: `lib/widget/my_area/my_area_screen.dart`

**Interfaces:**
- Consumes: `BookingDetailCubit.returnItem()` (Task 14); `BookingCubit.lenderBookings` (Task 14)

- [ ] **Step 1: Add Return button and RETURNED status to BookingDetailScreen**

Replace `lib/widget/booking/booking_detail_screen.dart`:
```dart
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:frontend_flutter/gen_l10n/app_localizations.dart';
import 'package:frontend_flutter/model/data_response.dart';
import 'package:frontend_flutter/model/item_booking.dart';
import 'package:frontend_flutter/widget/booking/booking_detail_cubit.dart';
import 'package:frontend_flutter/widget/element/custom_button.dart';
import 'package:frontend_flutter/widget/element/custom_key_value_column.dart';
import 'package:frontend_flutter/widget/element/loading_overlay.dart';
import 'package:intl/intl.dart';

class BookingDetailScreen extends StatelessWidget {
  final ItemBooking booking;
  final bool isOwner;

  const BookingDetailScreen(
      {super.key, required this.booking, this.isOwner = false});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => BookingDetailCubit.ofInitialState(booking: booking),
      child: LoadingOverlay(child: _BookingDetailScreenContent(isOwner: isOwner)),
    );
  }
}

class _BookingDetailScreenContent extends StatelessWidget {
  final bool isOwner;
  const _BookingDetailScreenContent({required this.isOwner});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
          title: Text(AppLocalizations.of(context)!.bookingDetailScreen_title)),
      body: BlocBuilder<BookingDetailCubit, DataResponse<ItemBooking>>(
        builder: (_, response) {
          final booking = response.data;
          return Container(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _statusChip(context, booking?.status),
                const SizedBox(height: 16),
                CustomKeyValueColumn(
                    AppLocalizations.of(context)!.bookingDetailScreen_from,
                    booking?.startAt != null
                        ? DateFormat('dd.MM.yyyy').format(booking!.startAt!)
                        : '-'),
                const SizedBox(height: 8),
                CustomKeyValueColumn(
                    AppLocalizations.of(context)!.bookingDetailScreen_to,
                    booking?.endAt != null
                        ? DateFormat('dd.MM.yyyy').format(booking!.endAt!)
                        : '-'),
                if (response.errorMessage != null) ...[
                  const SizedBox(height: 16),
                  Text(
                      AppLocalizations.of(context)!
                          .errorMessage(response.errorMessage!),
                      style: const TextStyle(color: Colors.red)),
                ],
                const Spacer(),
                if (isOwner && booking?.status == BookingStatus.pending) ...[
                  CustomButton(
                      AppLocalizations.of(context)!.bookingDetailScreen_approve,
                      Icons.check, () async {
                    LoadingOverlay.of(context).show();
                    await context.read<BookingDetailCubit>().approve();
                    if (context.mounted) {
                      LoadingOverlay.of(context).hide();
                      Navigator.pop(context);
                    }
                  }),
                  const SizedBox(height: 8),
                  CustomButton(
                      AppLocalizations.of(context)!.bookingDetailScreen_decline,
                      Icons.close, () async {
                    LoadingOverlay.of(context).show();
                    await context.read<BookingDetailCubit>().decline();
                    if (context.mounted) {
                      LoadingOverlay.of(context).hide();
                      Navigator.pop(context);
                    }
                  }),
                ],
                if ((booking?.status == BookingStatus.pending ||
                        booking?.status == BookingStatus.confirmed) &&
                    booking?.startAt != null &&
                    booking!.startAt!.isAfter(DateTime.now())) ...[
                  const SizedBox(height: 8),
                  CustomButton(
                      AppLocalizations.of(context)!.bookingDetailScreen_cancel,
                      Icons.cancel, () async {
                    LoadingOverlay.of(context).show();
                    await context.read<BookingDetailCubit>().cancel();
                    if (context.mounted) {
                      LoadingOverlay.of(context).hide();
                      Navigator.pop(context);
                    }
                  }),
                ],
                if (booking?.status == BookingStatus.confirmed) ...[
                  const SizedBox(height: 8),
                  CustomButton(
                      AppLocalizations.of(context)!.bookingDetailScreen_return,
                      Icons.assignment_return, () async {
                    LoadingOverlay.of(context).show();
                    await context.read<BookingDetailCubit>().returnItem();
                    if (context.mounted) {
                      LoadingOverlay.of(context).hide();
                      Navigator.pop(context);
                    }
                  }),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _statusChip(BuildContext context, BookingStatus? status) {
    final label = switch (status) {
      BookingStatus.pending =>
        AppLocalizations.of(context)!.bookingStatus_pending,
      BookingStatus.confirmed =>
        AppLocalizations.of(context)!.bookingStatus_confirmed,
      BookingStatus.declined =>
        AppLocalizations.of(context)!.bookingStatus_declined,
      BookingStatus.cancelled =>
        AppLocalizations.of(context)!.bookingStatus_cancelled,
      BookingStatus.returned =>
        AppLocalizations.of(context)!.bookingStatus_returned,
      null => '-',
    };
    final color = switch (status) {
      BookingStatus.pending => Colors.orange,
      BookingStatus.confirmed => Colors.green,
      BookingStatus.declined => Colors.red,
      BookingStatus.cancelled => Colors.grey,
      BookingStatus.returned => Colors.blue,
      null => Colors.grey,
    };
    return Chip(
        label: Text(label, style: const TextStyle(color: Colors.white)),
        backgroundColor: color);
  }
}
```

- [ ] **Step 2: Add incoming requests sub-toggle to MyAreaScreen**

In `lib/widget/my_area/my_area_screen.dart`, replace the `_BookingsList` class with:
```dart
class _BookingsList extends StatefulWidget {
  @override
  State<_BookingsList> createState() => _BookingsListState();
}

class _BookingsListState extends State<_BookingsList> {
  int _bookingTabIndex = 0;

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<BookingCubit, DataResponse<BookingState>>(
      builder: (_, response) {
        final myBookings = response.data?.myBookings?.content ?? [];
        final lenderBookings = response.data?.lenderBookings?.content ?? [];
        final bookings = _bookingTabIndex == 0 ? myBookings : lenderBookings;

        return Column(
          children: [
            ToggleButtons(
              direction: Axis.horizontal,
              onPressed: (int index) => setState(() => _bookingTabIndex = index),
              constraints: const BoxConstraints(minHeight: 36.0, minWidth: 120.0),
              isSelected: [_bookingTabIndex == 0, _bookingTabIndex == 1],
              children: [
                Text(AppLocalizations.of(context)!.myAreaScreen_myBookings),
                Text(AppLocalizations.of(context)!.myAreaScreen_incomingRequests),
              ],
            ),
            const SizedBox(height: 8),
            if (bookings.isEmpty)
              Center(child: Text(AppLocalizations.of(context)!.emptyList))
            else
              Expanded(
                child: ListView.builder(
                  itemCount: bookings.length,
                  itemBuilder: (context, index) {
                    final booking = bookings[index];
                    return ListTile(
                      title: Text(
                          '${_formatDate(booking.startAt)} - ${_formatDate(booking.endAt)}'),
                      subtitle: Text(_statusLabel(context, booking.status)),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => BookingDetailScreen(
                              booking: booking,
                              isOwner: _bookingTabIndex == 1,
                            ),
                          ),
                        );
                      },
                    );
                  },
                ),
              ),
          ],
        );
      },
    );
  }

  String _formatDate(DateTime? date) {
    if (date == null) return '-';
    return DateFormat('dd.MM.yyyy').format(date);
  }

  String _statusLabel(BuildContext context, BookingStatus status) {
    switch (status) {
      case BookingStatus.pending:
        return AppLocalizations.of(context)!.bookingStatus_pending;
      case BookingStatus.confirmed:
        return AppLocalizations.of(context)!.bookingStatus_confirmed;
      case BookingStatus.declined:
        return AppLocalizations.of(context)!.bookingStatus_declined;
      case BookingStatus.cancelled:
        return AppLocalizations.of(context)!.bookingStatus_cancelled;
      case BookingStatus.returned:
        return AppLocalizations.of(context)!.bookingStatus_returned;
    }
  }
}
```

- [ ] **Step 3: Run Flutter analysis**

```bash
flutter analyze lib/widget/
```
Expected: No errors (except possibly "myAreaScreen_incomingRequests" missing from AppLocalizations — that's fixed in Task 16).

- [ ] **Step 4: Commit**

```bash
git add lib/widget/booking/booking_detail_screen.dart \
        lib/widget/my_area/my_area_screen.dart
git commit -m "feat: add Return button and returned chip to BookingDetailScreen; add incoming requests toggle to MyAreaScreen"
```

---

## Task 16: Flutter — ItemDetailScreen availability calendar, i18n, and tests

**Files:**
- Modify: `lib/widget/item/item_detail_screen.dart`
- Modify: `lib/l10n/app_en.arb`
- Modify: `lib/l10n/app_de.arb`
- Modify: `test/widget/booking_cubit_test.dart`
- Modify: `test/widget/booking_detail_cubit_test.dart`
- Modify: `test/widget/booking_detail_screen_test.dart`

- [ ] **Step 1: Fetch and apply booking dates in ItemDetailScreen**

In `lib/widget/item/item_detail_screen.dart`, add `_bookedDates` state and load it in `initState`. The `_ItemDetailScreenContentState` class needs to be converted to use `initState`. Replace the `_ItemDetailScreenContentState` class:

```dart
class _ItemDetailScreenContentState extends State<_ItemDetailScreenContent> {
  final BookingService _bookingService = getIt.get<BookingService>();
  DateTime? _startDate;
  DateTime? _endDate;
  String? _bookingError;
  bool _bookingSuccess = false;
  List<BookingDateRange> _bookedDates = [];

  @override
  void initState() {
    super.initState();
    // item is loaded by the cubit; we listen for it after first build
  }

  Future<void> _loadBookingDates(String itemUuid) async {
    final dates = await _bookingService.getBookingDates(itemUuid);
    if (mounted) setState(() => _bookedDates = dates);
  }

  Future<void> _selectStartDate(BuildContext context) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime(2050),
      selectableDayPredicate: (day) => !_isDateBooked(day),
    );
    if (picked != null) {
      setState(() {
        _startDate = picked;
        if (_endDate != null && _endDate!.isBefore(_startDate!)) _endDate = null;
      });
    }
  }

  Future<void> _selectEndDate(BuildContext context) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _startDate ?? DateTime.now(),
      firstDate: _startDate ?? DateTime.now(),
      lastDate: DateTime(2050),
      selectableDayPredicate: (day) => !_isDateBooked(day),
    );
    if (picked != null) setState(() => _endDate = picked);
  }

  bool _isDateBooked(DateTime day) {
    final d = DateTime(day.year, day.month, day.day);
    for (final range in _bookedDates) {
      if (range.startAt == null || range.endAt == null) continue;
      final start = DateTime(range.startAt!.year, range.startAt!.month, range.startAt!.day);
      final end = DateTime(range.endAt!.year, range.endAt!.month, range.endAt!.day);
      if (!d.isBefore(start) && !d.isAfter(end)) return true;
    }
    return false;
  }

  Future<void> _requestBooking(BuildContext context, String itemUuid) async {
    if (_startDate == null || _endDate == null) return;
    LoadingOverlay.of(context).show();
    final response = await _bookingService.bookItem(itemUuid, _startDate!, _endDate!);
    if (context.mounted) {
      LoadingOverlay.of(context).hide();
      setState(() {
        if (response.errorMessage != null) {
          _bookingError = response.errorMessage;
          _bookingSuccess = false;
        } else {
          _bookingError = null;
          _bookingSuccess = true;
        }
      });
    }
  }
```

In the `build` method's `BlocBuilder`, after `item = dataResponse.data;` add:
```dart
if (item != null && !item!.isOwner && _bookedDates.isEmpty) {
  _loadBookingDates(item!.uuid!);
}
```

Also add the import at the top of the file:
```dart
import 'package:frontend_flutter/model/booking_date_range.dart';
```

- [ ] **Step 2: Add new ARB keys to app_en.arb**

In `lib/l10n/app_en.arb`, add before the closing `}`:
```json
  "bookingStatus_returned": "Returned",
  "bookingDetailScreen_return": "Mark as returned",
  "myAreaScreen_incomingRequests": "Incoming requests",
  "notification_bookingReturned": "Item returned"
```

- [ ] **Step 3: Add same keys to app_de.arb**

In `lib/l10n/app_de.arb`, add before the closing `}`:
```json
  "bookingStatus_returned": "Zurückgegeben",
  "bookingDetailScreen_return": "Als zurückgegeben markieren",
  "myAreaScreen_incomingRequests": "Eingehende Anfragen",
  "notification_bookingReturned": "Artikel zurückgegeben"
```

- [ ] **Step 4: Regenerate localizations**

```bash
flutter gen-l10n
```
Expected: `lib/gen_l10n/app_localizations*.dart` updated with new keys.

- [ ] **Step 5: Update booking_cubit_test.dart**

In `test/widget/booking_cubit_test.dart`, update the mock setup and add a test for `loadLenderBookings`. The existing mock setup likely mocks `getMyBookings`. Also mock `getReceivedBookings`:

```dart
// In the mock setup (setUpAll or setUp), add:
when(() => mockBookingService.getReceivedBookings(pageNumber: any(named: 'pageNumber')))
    .thenAnswer((_) async => DataResponse(data: DataPage(content: [], isFirstPage: true, isLastPage: true, pageNumber: 0, pageSize: 20, totalElements: 0, totalPages: 0), errorMessage: null));

// Add test:
test('loadLenderBookings populates lenderBookings state', () async {
  final cubit = BookingCubit(DataResponse.empty());
  await Future.delayed(Duration.zero); // let constructor complete
  await cubit.loadLenderBookings();
  expect(cubit.state.data?.lenderBookings, isNotNull);
});
```

- [ ] **Step 6: Update booking_detail_cubit_test.dart**

Add a test for `returnItem()`:
```dart
test('returnItem emits returned booking state', () async {
  final booking = ItemBooking(uuid: 'b-1', status: BookingStatus.confirmed);
  final cubit = BookingDetailCubit.ofInitialState(booking: booking);
  when(() => mockBookingService.returnBooking('b-1'))
      .thenAnswer((_) async => DataResponse(
          data: ItemBooking(uuid: 'b-1', status: BookingStatus.returned),
          errorMessage: null));
  await cubit.returnItem();
  expect(cubit.state.data?.status, BookingStatus.returned);
});
```

- [ ] **Step 7: Update booking_detail_screen_test.dart**

Add tests for the Return button and the returned status chip:
```dart
testWidgets('shows Return button when booking is confirmed', (tester) async {
  final booking = ItemBooking(
    uuid: 'b-1',
    status: BookingStatus.confirmed,
    startAt: DateTime.now().add(const Duration(days: 5)),
    endAt: DateTime.now().add(const Duration(days: 10)),
  );
  await tester.pumpWidget(MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: BookingDetailScreen(booking: booking),
  ));
  await tester.pumpAndSettle();
  expect(find.text(AppLocalizations.of(tester.element(find.byType(BookingDetailScreen)))!.bookingDetailScreen_return), findsOneWidget);
});

testWidgets('shows returned status chip for returned booking', (tester) async {
  final booking = ItemBooking(uuid: 'b-1', status: BookingStatus.returned);
  await tester.pumpWidget(MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: BookingDetailScreen(booking: booking),
  ));
  await tester.pumpAndSettle();
  expect(find.text(AppLocalizations.of(tester.element(find.byType(BookingDetailScreen)))!.bookingStatus_returned), findsOneWidget);
});
```

- [ ] **Step 8: Run Flutter tests**

```bash
flutter test test/widget/booking_cubit_test.dart \
             test/widget/booking_detail_cubit_test.dart \
             test/widget/booking_detail_screen_test.dart
```
Expected: All tests pass.

- [ ] **Step 9: Run full Flutter test suite**

```bash
flutter test
```
Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git add lib/widget/item/item_detail_screen.dart \
        lib/l10n/app_en.arb lib/l10n/app_de.arb \
        lib/gen_l10n/ \
        test/widget/booking_cubit_test.dart \
        test/widget/booking_detail_cubit_test.dart \
        test/widget/booking_detail_screen_test.dart
git commit -m "feat: availability calendar on item detail; i18n for returned/return/incoming; cubit and screen tests"
```
