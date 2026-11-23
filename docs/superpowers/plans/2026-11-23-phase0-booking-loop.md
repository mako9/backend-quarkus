# Phase 0 — Booking Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the booking work abandoned in the working tree on 2026-07-09, and close the two
holes that stop the booking loop from working — the owner never hearing about a booking, and two
neighbours being approvable for the same drill.

**Architecture:** Backend first (the three new endpoints and the `RETURNED` lifecycle are already
written but uncommitted; they go in after re-reading, then two new behaviours on top), then
Next.js pages and lib functions, then Flutter models, service and screens. `isItemOwner` is
computed server-side on every booking response so neither client needs an extra roundtrip.

**Tech Stack:** Quarkus 3.33 / Kotlin / Hibernate-Panache / Liquibase / JUnit + RestAssured
(backend); Next.js 16 Pages Router / react-datepicker / next-i18next / jest + Testing Library
(web); Flutter 3.41 / flutter_bloc / AppLocalizations ARB (mobile).

**Spec:** `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — read *Phase 0*,
*Goals* G3/G4/G5/G11, *Finding 1* and *Finding 6* before starting. This plan implements those and
nothing else from that document.

## Global Constraints

- Repos and their roots, all under `/Users/mario/Projekte/Private/`: `backend-quarkus`,
  `frontend-nextjs`, `frontend_flutter`. Each is a separate git repo — **commit per repo**, never
  one commit spanning two.
- Toolchain is not on the default `PATH` on this machine. Prefix shell steps with
  `export PATH="/usr/local/bin:/opt/homebrew/bin:$HOME/development/flutter/bin:$PATH"`.
- Backend layout: Kotlin in `src/main/kotlin/`, tests in `src/test/kotlin/`. Liquibase changelogs
  in `src/main/resources/db/changelog/`, auto-included by `changelog-root.xml`'s `<includeAll>` —
  a new file is picked up without registration. Existing changeset ids run `mario:1`–`mario:5`;
  this plan adds `mario:6` (the column default) and, after the review round, `mario:7` (the items that
  predate the decision).
- Backend test command: `./mvnw test -Dtest="<Class>"`. **`./mvnw test` (full) cannot pass on
  this machine**: `PolicyEnforcerTest` and the controller tests boot a Keycloak container and the
  Docker VM cannot pull images (see spec *Current state*). Local verification is the service
  tests. Controller tests added here must be written to the same conventions as the existing ones
  and are verified in CI.
- Next.js test command: `npx jest --ci`. **`npx tsc --noEmit` currently reports 17 pre-existing
  errors and `pnpm build` fails on a CSS `@import`** — both belong to Phase 1. Do not add type
  errors; do not fix the existing ones; do not add `tsc` or `build` to any step here.
- Flutter test command: `flutter test`; analysis `flutter analyze` (must stay clean).
- Localisation is mandatory for every user-facing string, in the same task that adds it: Next.js
  `public/locales/en/common.json` **and** `public/locales/de/common.json`; Flutter
  `lib/l10n/app_en.arb` **and** `lib/l10n/app_de.arb`, followed by `flutter gen-l10n` (writes
  `lib/gen_l10n/`, which is committed).
- JSON key names for **new** fields use the dev/test Jackson form — `isItemOwner`, not
  `itemOwner`. The packaged backend drops `jackson-module-kotlin` and renames `is*` properties;
  that mismatch is spec *Finding 0* and is fixed in Phase 1, Task 6 — which makes `is*` correct in
  every mode. Until then it is correct in dev and tests only. Do not "fix" it early in a client.
- No money anywhere. No price, deposit or settlement field (spec: decided 2026-11-23).
- Commits: Conventional Commits, imperative, no `Co-Authored-By` or any AI attribution. Never
  modify `~/.gitconfig` or the repo's `.gitconfig`.
- Do not touch `.mvn/wrapper/` unless Task 1 says to.

## Review Focus

Failure modes this phase's spec text implies but no single task states. Each has a pinned test in
the task that owns the code.

1. **A CONFIRMED booking overlapping a second request must not be approvable.** Today
   `approveBooking` re-checks nothing, so an owner can confirm two people for one item and both
   get a "confirmed" email. Expected: the second approval fails with `ITEM_RESERVED`. Task 3.
2. **`RETURNED` must not render as `Confirmed`.** Flutter's
   `BookingStatus.fromJson` returns `confirmed` for any value it does not recognise, so an
   unknown status silently becomes a live booking. Expected: `returned` parses, and a returned
   booking offers no action buttons. Tasks 9, 11.
3. **Owner-only actions must be absent for the borrower.** `isItemOwner` defaults to `false` in
   the model; a client that ignores the flag shows Approve/Decline to the person who asked.
   Expected: a non-owner sees neither button, on web and mobile. Tasks 6, 7, 11, 12.
4. **The approval default is a default, not enforcement.** After the flip, a client that still
   sends `requiresApproval: false` gets a silently auto-confirmed booking. Expected: existing rows
   flip, both clients' forms start checked, and an explicit `false` still works (so the owner's
   choice is honoured). Task 4, Task 9, Task 13.
5. **A date range that is already booked must be visibly unavailable before booking, and still
   bookable outside it.** Expected: excluded intervals on web, listed ranges on mobile, and a
   request on a free range still succeeding the same day. Tasks 8, 12.

---

## File Structure

**backend-quarkus — modify** `common/BookingStatus.kt`, `common/NotificationType.kt`,
`infrastructure/entity/ItemBooking.kt`, `domain/model/{ItemBookingModel,ItemModel,PageModel}.kt`,
`api/dto/{ItemBookingDto,ItemBookingDateRangeDto,ItemDto}.kt`,
`domain/service/{ItemService,NotificationService}.kt`, `api/controller/ItemController.kt`,
`src/test/kotlin/testUtils/EntityUtil.kt`,
`src/test/kotlin/{domain/service/ItemServiceTest,domain/service/NotificationServiceTest,api/controller/ItemControllerTest}.kt`.
**create** `src/main/resources/db/changelog/changelog-3.1.sql`.

**frontend-nextjs — modify** `app/models/booking.ts`, `app/lib/bookings.ts`,
`app/lib/bookings.test.ts`, `app/components/datePicker.tsx`, `app/components/datePicker.test.tsx`,
`app/components/subpages/foreignItem.tsx`, `app/components/subpages/foreignItem.test.tsx`,
`pages/bookings/index.tsx`, `pages/bookings/[uuid].tsx`, `pages/items/[id].tsx`,
`pages/items/create.tsx`, `public/locales/{en,de}/common.json`,
`__tests__/pages/bookings/{index,[uuid]}.test.tsx`, `__tests__/pages/items/[id].test.tsx`,
`__tests__/pages/items/create.test.tsx`.

**frontend_flutter — modify** `lib/model/item_booking.dart`, `lib/service/booking_service.dart`,
`lib/widget/booking/{booking_cubit,booking_detail_cubit,booking_detail_screen}.dart`,
`lib/widget/my_area/my_area_screen.dart`, `lib/widget/item/{item_detail_screen,item_edit_screen}.dart`,
`lib/l10n/{app_en,app_de}.arb`, `lib/gen_l10n/*` (generated),
`test/service/booking_service_test.dart`, `test/widget/{booking_cubit,booking_detail_cubit,booking_detail_screen,item_edit_cubit}_test.dart`.
**create** `lib/model/booking_date_range.dart`.

---

## Task 1: Land the abandoned backend booking work

The working tree already contains Tasks 1–7 of `2026-07-09-complete-booking-experience.md`: the
`RETURNED` status, `returned_by`/`returned_at`, `isItemOwner`, `GET /booking/received`,
`GET /{uuid}/booking/dates`, `PATCH /booking/{uuid}/return` and their service tests. It compiles
and `ItemServiceTest` is green. This task reviews it, discards unrelated noise, and commits it in
two reviewable pieces.

**Files:**
- Read: `git -C backend-quarkus diff` (13 modified files, +352/−19) plus untracked
  `src/main/kotlin/api/dto/ItemBookingDateRangeDto.kt` and
  `src/main/resources/db/changelog/changelog-3.0.sql`
- Discard: `.mvn/wrapper/maven-wrapper.properties`

**Interfaces:**
- Produces: `BookingStatus.RETURNED`; `NotificationType.BOOKING_RETURNED`;
  `ItemBookingModel(uuid, itemUuid, userUuid, startAt, endAt, createdAt, status, cancelledBy,
  cancelledAt, returnedBy, returnedAt, isItemOwner)`; `ItemBookingDto` with the same field names;
  `ItemBookingDateRangeDto(startAt: OffsetDateTime, endAt: OffsetDateTime)`;
  `ItemService.getItemBookingDates(itemUuid: UUID, userUuid: UUID): List<Pair<OffsetDateTime, OffsetDateTime>>`;
  `ItemService.getReceivedItemBookingsPage(userUuid: UUID, pageConfig: PageConfig,
  sortBy: ItemBookingSortBy?, sortDirection: Sort.Direction?): PageModel<ItemBookingModel>`;
  `ItemService.returnBooking(bookingUuid: UUID, userUuid: UUID): ItemBookingModel`;
  `PageModel.empty()`; endpoints `GET /api/user/item/booking/received`,
  `GET /api/user/item/{uuid}/booking/dates`, `PATCH /api/user/item/booking/{uuid}/return`.
  Tasks 2–4 and 5–8 consume these.

- [x] **Step 1: Discard the wrapper noise**

```bash
cd backend-quarkus && git diff .mvn/wrapper/maven-wrapper.properties
```
If the diff only changes the wrapper distribution URL version, discard it — a wrapper bump
belongs in a dependency-upgrade commit:
```bash
git checkout -- .mvn/wrapper/maven-wrapper.properties
```

- [x] **Step 2: Read the whole diff and confirm three things**

Run `git diff` and confirm: `getItemBookingDates` refuses callers who are neither community member
nor item owner; `returnBooking` only accepts a `CONFIRMED` booking and only for requester or owner;
`getReceivedItemBookingsPage` returns `PageModel.empty()` when the user owns no items rather than
querying with an empty `IN` list. If any of the three is missing, stop and record it — it is a
review finding, not this task's job to fix silently.

- [x] **Step 3: Run the service tests**

```bash
./mvnw test -Dtest='ItemServiceTest,NotificationServiceTest,CommunityServiceTest,UserServiceTest,TimeIntervalModelTest'
```
Expected: `BUILD SUCCESS`, zero failures.

- [x] **Step 4: Commit schema + data layer**

```bash
git add src/main/resources/db/changelog/changelog-3.0.sql src/main/kotlin/common/BookingStatus.kt \
        src/main/kotlin/common/NotificationType.kt src/main/kotlin/infrastructure/entity/ItemBooking.kt \
        src/main/kotlin/domain/model/ItemBookingModel.kt src/main/kotlin/domain/model/PageModel.kt \
        src/main/kotlin/api/dto/ItemBookingDto.kt src/main/kotlin/api/dto/ItemBookingDateRangeDto.kt
git commit -m "feat: add RETURNED booking status with return tracking columns"
```

- [x] **Step 5: Commit endpoints**

```bash
git add src/main/kotlin/api/controller/ItemController.kt src/main/kotlin/domain/service/ItemService.kt \
        src/main/kotlin/domain/service/NotificationService.kt src/main/kotlin/domain/model/exception/ErrorCode.kt \
        src/test/kotlin/domain/service/ItemServiceTest.kt src/test/kotlin/domain/service/NotificationServiceTest.kt \
        src/test/kotlin/api/controller/ItemControllerTest.kt
git commit -m "feat: add return, received-bookings and booking-dates endpoints with isItemOwner"
```

---

## Task 2: Tell the owner when their item is booked

`ItemService.bookItem` fires exactly one event, and for an item that does not require approval the
recipient is the requester — the owner learns nothing. Add a distinct notification type for the
owner side of that path.

**Files:**
- Modify: `src/main/kotlin/common/NotificationType.kt`, `src/main/kotlin/domain/service/ItemService.kt` (`bookItem`), `src/main/kotlin/domain/service/NotificationService.kt` (`emailSubject`, `emailBody`)
- Test: `src/test/kotlin/domain/service/ItemServiceTest.kt`, `src/test/kotlin/domain/service/NotificationServiceTest.kt`

**Interfaces:**
- Consumes: `BookingEvent(notificationType, recipientUserUuid, bookingUuid, itemName, requesterName)`, `bookingEventSource.fireAsync(...)`
- Produces: `NotificationType.ITEM_BOOKED`, sent to `item.userUuid` whenever `bookItem` creates a
  `CONFIRMED` booking. Task 3 and Phase 4's email templates use it.

- [x] **Step 1: Write the failing service test**

In `ItemServiceTest`, alongside the existing booking tests:

```kotlin
@Test
fun `when booking an item that needs no approval, then the owner is notified with ITEM_BOOKED`() {
    val before = Notification.count("userUuid = ?1 AND type = 'ITEM_BOOKED'", user.uuid)
    itemService.bookItem(itemOne.uuid, requestingUser.uuid,
        OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(2))
    assertTrue(Notification.count("userUuid = ?1 AND type = 'ITEM_BOOKED'", user.uuid) > before)
}
```

(`itemOne` is owned by `user`; `requestingUser` books it. Assert on the count delta, not on an
absolute, because `@BeforeEach` fixtures already create notifications.)

- [x] **Step 2: Run it to verify it fails**

```bash
./mvnw test -Dtest='ItemServiceTest#when booking an item that needs no approval, then the owner is notified with ITEM_BOOKED'
```
Expected: FAIL — compile error, `ITEM_BOOKED` unresolved.

- [x] **Step 3: Add the enum value, the second event, and the email copy**

Append `ITEM_BOOKED` to `NotificationType`. In `bookItem`, after the existing `fireAsync`, fire a
second event when `status == BookingStatus.CONFIRMED` with
`notificationType = NotificationType.ITEM_BOOKED` and `recipientUserUuid = item.userUuid`. In
`NotificationService`, add to both `when` expressions:
`NotificationType.ITEM_BOOKED -> "Your item was booked"` and
`NotificationType.ITEM_BOOKED -> "The item \"${event.itemName}\" has been booked by ${event.requesterName}."`

- [x] **Step 4: Add the notification-service test**

In `NotificationServiceTest`, mirroring the existing `onBookingEvent` tests: an `ITEM_BOOKED`
event persists a `Notification` row of type `ITEM_BOOKED` for the recipient.

- [x] **Step 5: Run both suites**

```bash
./mvnw test -Dtest='ItemServiceTest,NotificationServiceTest'
```
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add src/main/kotlin/common/NotificationType.kt src/main/kotlin/domain/service/ItemService.kt \
        src/main/kotlin/domain/service/NotificationService.kt src/test/kotlin/domain/service/ItemServiceTest.kt \
        src/test/kotlin/domain/service/NotificationServiceTest.kt
git commit -m "feat: notify the item owner when their item is auto-confirmed"
```

---

## Task 3: Refuse an approval that overlaps another booking

`approveBooking` checks only `status == PENDING`. Two overlapping `PENDING` requests can both be
approved because `checkItemAvailability` counts `CONFIRMED` rows only.

**Files:**
- Modify: `src/main/kotlin/domain/service/ItemService.kt` (`approveBooking`, new private helper)
- Test: `src/test/kotlin/domain/service/ItemServiceTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.ItemReserved` (exists), `CustomBadRequestException(code = ...)`
- Produces: `private fun ItemService.assertNoConfirmedOverlap(itemUuid: UUID, startAt: OffsetDateTime,
  endAt: OffsetDateTime, exceptBookingUuid: UUID)` — throws `CustomBadRequestException(code =
  ErrorCode.ItemReserved)` when any *other* `CONFIRMED` booking for the item overlaps
  `[startAt, endAt)`. Called from `approveBooking` before the status change.
  **Corrected while executing (2026-11-23): `PENDING` must not count.** The first draft of this task
  required `status IN ('CONFIRMED','PENDING')`, which makes approval impossible in the exact case it
  exists for: several neighbours ask for the same days, and approving one of them is how the owner
  chooses. With pending rivals counted, the owner could accept *neither*. Overlapping pending requests
  are therefore allowed by design, and the rival of an already-confirmed booking is the thing to refuse.
  Pinned by `when approving one of two overlapping pending requests, then the other is refused`.

- [x] **Step 1: Write the failing tests**

```kotlin
@Test
fun `when approving a booking whose slot was already confirmed, then ITEM_RESERVED is thrown`() {
    // first: PENDING -> approve -> CONFIRMED
    val first = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid; it.userUuid = requestingUser.uuid; it.status = BookingStatus.PENDING
        it.startAt = OffsetDateTime.now().plusDays(1); it.endAt = OffsetDateTime.now().plusDays(2)
    }
    val second = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid; it.userUuid = otherUser.uuid; it.status = BookingStatus.PENDING
        it.startAt = OffsetDateTime.now().plusDays(1); it.endAt = OffsetDateTime.now().plusDays(2)
    }
    itemService.approveBooking(first.uuid, user.uuid)
    assertThrows<CustomBadRequestException> { itemService.approveBooking(second.uuid, user.uuid) }
    assertEquals(BookingStatus.PENDING, ItemBooking.find("uuid", second.uuid).firstResult().status)
}

@Test
fun `when approving a booking with no overlap, then it becomes CONFIRMED`() {
    val booking = entityUtil.setupItemBooking {
        it.itemUuid = itemOne.uuid; it.userUuid = requestingUser.uuid; it.status = BookingStatus.PENDING
        it.startAt = OffsetDateTime.now().plusDays(30); it.endAt = OffsetDateTime.now().plusDays(31)
    }
    assertEquals(BookingStatus.CONFIRMED, itemService.approveBooking(booking.uuid, user.uuid).status)
}
```
If `otherUser` does not exist in the fixture, add it to `EntityUtil` / `@BeforeEach` following the
`requestingUser` pattern.

- [x] **Step 2: Run to verify the first test fails**

```bash
./mvnw test -Dtest='ItemServiceTest#when approving a booking whose slot was already confirmed, then ITEM_RESERVED is thrown'
```
Expected: FAIL — no exception thrown (the second approval succeeds).

- [x] **Step 3: Implement `assertNoConflictingBooking`**

A Panache count over `itemUuid = ?1 AND uuid <> ?2 AND status = 'CONFIRMED' AND startAt < ?4 AND
endAt > ?3` with the candidate's `startAt`/`endAt`; throw when the count is above zero. Call it from
`approveBooking` after the ownership and `PENDING` checks and before the status assignment. (See the
interface note for why pending rows are deliberately excluded.)

- [x] **Step 4: Run both tests to verify they pass**

Run: `./mvnw test -Dtest='ItemServiceTest'` — Expected: PASS, all cases including the pre-existing
approval tests (which use non-overlapping fixture dates; if one now fails, it was relying on the
missing check and its dates must be moved, not the rule relaxed).

- [x] **Step 5: Commit**

```bash
git add src/main/kotlin/domain/service/ItemService.kt src/test/kotlin/domain/service/ItemServiceTest.kt
git commit -m "fix: refuse booking approval that overlaps a pending or confirmed booking"
```

---

## Task 4: Require approval by default

Decided 2026-11-23: approval required unless the owner switches it off. The per-item switch stays.
`changelog-2.0.sql:6` defaulted `requires_approval` to `FALSE`; three Kotlin defaults repeat it.

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.1.sql`
- Modify: `src/main/kotlin/api/dto/ItemDto.kt:44` and `:78`, `src/main/kotlin/domain/model/ItemModel.kt:24`
- Test: `src/test/kotlin/domain/service/ItemServiceTest.kt`

**Interfaces:**
- Consumes: nothing new
- Produces: `requiresApproval` defaults to `true` in `ItemModel` and in both `ItemDto` request
  shapes, and `item.requires_approval` defaults to `TRUE` with all existing rows set to `TRUE`.
  Task 13 (Flutter) and Task 9 (Next.js) mirror the client-side defaults.

- [x] **Step 1: Write the failing test**

```kotlin
@Test
fun `when inserting an item without requiresApproval, then it defaults to true`() {
    val model = entityUtil.setupItemModel(requiresApproval = null) // or construct without the arg
    val created = itemService.insertCommunityItem(model) // use the existing insert path under test
    assertTrue(created.requiresApproval)
}
```
Use whatever the file already uses to create an item through the service (see the existing
`insertItem` tests) — the assertion that matters is that an omitted flag yields `true`.

- [x] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest='ItemServiceTest'` — Expected: FAIL, `false`.

- [x] **Step 3: Flip the three Kotlin defaults and add changeset `mario:6`**

`ItemModel.kt:24`, `ItemDto.kt:44`, `ItemDto.kt:78`: `= true`. Create `changelog-3.1.sql`:

```sql
--liquibase formatted sql

--changeset mario:6 labels:approval-default context:approval-default
--comment: Require booking approval by default

ALTER TABLE item ALTER COLUMN requires_approval SET DEFAULT TRUE;
UPDATE item SET requires_approval = TRUE WHERE requires_approval = FALSE;

--rollback ALTER TABLE item ALTER COLUMN requires_approval SET DEFAULT FALSE;
```
The `UPDATE` is deliberate and destructive to prior owners' choice; it is recorded in the spec
(G11) and is the point of the task.

> Corrected in the review round: G11 says only that a new item starts with approval required, so it covers
> the `SET DEFAULT` and not this `UPDATE`. The data change was not authorised by the spec. It now lives in
> its own changeset (`mario:7`), commented as a retroactive policy change with no rollback, and is listed
> as needing an explicit yes before it runs against data anybody cares about. It is harmless on an empty or
> throwaway database, which is all there is so far.

- [x] **Step 4: Run the suite to verify it passes**

Run: `./mvnw test -Dtest='ItemServiceTest,CommunityServiceTest'` — Expected: PASS. A green
`ItemServiceTest` may now book against an approval-requiring item and see `PENDING` instead of
`CONFIRMED`; update those fixtures to set `requiresApproval = false` explicitly rather than
weakening the assertions.

- [x] **Step 5: Commit**

```bash
git add src/main/resources/db/changelog/changelog-3.1.sql src/main/kotlin/domain/model/ItemModel.kt \
        src/main/kotlin/api/dto/ItemDto.kt src/test/kotlin/domain/service/ItemServiceTest.kt
git commit -m "feat: require booking approval by default"
```

---

## Task 5: Next.js booking client — models, lib functions, i18n

**Files:**
- Modify: `app/models/booking.ts` (already modified in the working tree — verify, then commit),
  `app/lib/bookings.ts`, `app/lib/bookings.test.ts`, `public/locales/en/common.json`,
  `public/locales/de/common.json`

**Interfaces:**
- Consumes: backend `GET /user/item/booking/received`, `PATCH /user/item/booking/{uuid}/return`,
  `GET /user/item/{uuid}/booking/dates` (Task 1)
- Produces: `getReceivedBookings(session?): Promise<Page<ItemBooking> | null>`;
  `returnBooking(bookingUuid: string, session?): Promise<void>`;
  `getBookingDates(itemUuid: string, session?): Promise<BookingDateRange[]>` (never throws, `[]`
  on failure); types `ItemBooking.isItemOwner: boolean`, `BookingDateRange { startAt, endAt }`.
  Consumed by Tasks 6, 7, 8.

- [x] **Step 1: Write the failing lib tests**

In `app/lib/bookings.test.ts`, following the existing `mockFetch` style in that file:

```ts
test('getReceivedBookings requests /user/item/booking/received', async () => { /* expect url */ })
test('returnBooking patches /user/item/booking/b-1/return', async () => { /* expect PATCH */ })
test('getBookingDates returns [] when the request fails', async () => { /* expect [] not null */ })
```

- [x] **Step 2: Run to verify they fail**

Run: `npx jest app/lib/bookings --ci` — Expected: FAIL, three functions not exported.

- [x] **Step 3: Implement the three functions and commit the model change**

In `app/lib/bookings.ts`, next to the existing functions, using the existing `bookingPath()`
helper and `request` / `HttpMethod.Patch` pattern. `getBookingDates` returns `result.data ?? []`.
Confirm `app/models/booking.ts` matches the backend names exactly: `isItemOwner`, `returnedBy`,
`returnedAt`, `BookingDateRange`.

- [x] **Step 4: Add the copy**

`en`: `booking.status.returned` = `"Returned"`, `booking.return` = `"Mark as returned"`,
`bookings.myRequests` = `"My requests"`, `bookings.received` = `"Requests for my items"`,
`notification.type.BOOKING_RETURNED` = `"Item returned"`,
`notification.type.ITEM_BOOKED` = `"Your item was booked"`.
`de`: `"Zurückgegeben"`, `"Als zurückgegeben markieren"`, `"Meine Anfragen"`,
`"Anfragen für meine Gegenstände"`, `"Gegenstand zurückgegeben"`, `"Dein Gegenstand wurde gebucht"`.

- [x] **Step 5: Run to verify they pass**

Run: `npx jest app/lib/bookings --ci` — Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add app/models/booking.ts app/lib/bookings.ts app/lib/bookings.test.ts public/locales/en/common.json public/locales/de/common.json
git commit -m "feat: add received-booking, return and booking-dates client functions"
```

---

## Task 6: Next.js bookings list — owner and requester tabs

`pages/bookings/index.tsx` fetches only `getMyBookings` and its `statusColor` has no `RETURNED`
branch (the one real type error in the app).

**Files:**
- Modify: `pages/bookings/index.tsx`, `__tests__/pages/bookings/index.test.tsx`

**Interfaces:**
- Consumes: `getMyBookings`, `getReceivedBookings` (Task 5), existing `ToggleButton`
  (`app/components/toggleButton`, props `titleOne`, `titleTwo`, `selectedIndex`, `setSelectedIndex`)
- Produces: server props `{ myBookings: ItemBooking[], receivedBookings: ItemBooking[] }`.

- [x] **Step 1: Write the failing tests**

Extend the existing suite (it already mocks `next-auth/react` and `app/lib/bookings` — add
`getReceivedBookings: jest.fn()` to the `jest.mock('app/lib/bookings', …)` object):

```tsx
test('getServerSideProps returns both booking pages', async () => { /* myBookings + receivedBookings in props */ })
test('a RETURNED booking renders the Returned label, not a blank colour', () => { /* render with status RETURNED, expect text 'Returned' */ })
test('a non-owner booking from the received list is still listed', () => { /* receivedBookings content length 1 -> one link */ })
```

- [x] **Step 2: Run to verify they fail**

Run: `npx jest __tests__/pages/bookings/index --ci` — Expected: FAIL (`receivedBookings` absent;
`Returned` label missing).

- [x] **Step 3: Implement**

Fetch both pages with `Promise.all` in `getServerSideProps`; render `ToggleButton` with
`t('bookings.myRequests')` / `t('bookings.received')` over one shared `BookingList` component
holding the current list markup; add `case 'RETURNED': return 'blue'` to `statusColor` and give
the function a `never`-fallthrough default so a future status is a compile error rather than an
`undefined` colour.

- [x] **Step 4: Run to verify they pass**

Run: `npx jest __tests__/pages/bookings --ci` — Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add pages/bookings/index.tsx __tests__/pages/bookings/index.test.tsx
git commit -m "feat: split bookings into my requests and requests for my items"
```

---

## Task 7: Next.js booking detail — real owner actions and return

`pages/bookings/[uuid].tsx:19` hardcodes `const isOwner = false // We don't have item owner info`,
so approve/decline are unreachable in the web UI.

**Files:**
- Modify: `pages/bookings/[uuid].tsx`, `__tests__/pages/bookings/[uuid].test.tsx`

**Interfaces:**
- Consumes: `ItemBooking.isItemOwner` (Task 5), `returnBooking` (Task 5)
- Produces: nothing consumed by later tasks.

- [x] **Step 1: Write the failing tests**

```tsx
test('owner of a PENDING booking sees Approve and Decline', () => { /* isItemOwner: true */ })
test('requester of a PENDING booking sees neither Approve nor Decline', () => { /* isItemOwner: false */ })
test('CONFIRMED booking offers Mark as returned', () => { /* status CONFIRMED -> button present */ })
test('RETURNED booking offers no action buttons', () => { /* status RETURNED -> no Approve, no Cancel, no Return */ })
```
Use `screen.queryByRole('button', { name: … })` against the translated labels from
`public/locales/en/common.json`.

- [x] **Step 2: Run to verify they fail**

Run: `npx jest "__tests__/pages/bookings/[uuid]" --ci` — Expected: FAIL on the owner case.

- [x] **Step 3: Implement**

`const isOwner = booking.isItemOwner`. Show Approve/Decline when `status === 'PENDING' && isOwner`
(existing block, unchanged condition). Add a Return button when `status === 'CONFIRMED'`, calling
`returnBooking(booking.uuid, session)` then `router.push('/bookings')`. Leave `canCancel` as is —
a `RETURNED` booking fails its `PENDING | CONFIRMED` test and so offers nothing, which is the
behaviour test 4 asserts.

- [x] **Step 4: Run to verify they pass**

Run: `npx jest "__tests__/pages/bookings/[uuid]" --ci` — Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add pages/bookings/[uuid].tsx "__tests__/pages/bookings/[uuid].test.tsx"
git commit -m "fix: use the server-provided owner flag and add the return action"
```

---

## Task 8: Next.js item detail — hide already-booked dates

`foreignItem` offers two free `CustomDatePicker`s; a borrower cannot see what is already taken
(G4). `react-datepicker` takes `excludeDateIntervals`.

**Files:**
- Modify: `app/components/datePicker.tsx`, `app/components/subpages/foreignItem.tsx`,
  `pages/items/[id].tsx`, `app/components/datePicker.test.tsx`,
  `app/components/subpages/foreignItem.test.tsx`, `__tests__/pages/items/[id].test.tsx`

**Interfaces:**
- Consumes: `getBookingDates(itemUuid, session?)` → `BookingDateRange[]` (Task 5)
- Produces: `CustomDatePicker({ selectedDate, setSelectedDate, excludeDateIntervals? })` where
  `excludeDateIntervals?: Array<{ start: Date; end: Date }>`; `foreignItem(session, itemData,
  itemImageData, bookedDates, t)` — a new 4th positional argument.

- [x] **Step 1: Write the failing tests**

```tsx
// datePicker.test.tsx
test('passes excludeDateIntervals through to react-datepicker', () => {
    render(<CustomDatePicker selectedDate={new Date()} setSelectedDate={jest.fn()}
                             excludeDateIntervals={[{ start: d1, end: d2 }]} />)
    expect(screen.getByRole('textbox')).toBeInTheDocument() // and no throw; assert via the rendered wrapper class
})
// foreignItem.test.tsx
test('renders the booked-range hint when bookedDates is non-empty', () => {
    /* expect t('item.alreadyBooked') copy present */
})
// __tests__/pages/items/[id].test.tsx
test('getServerSideProps supplies bookedDates for a foreign item', async () => { /* props.bookedDates */ })
test('getServerSideProps supplies an empty bookedDates for an owned item', async () => { /* isOwned=true -> [] */ })
```

- [x] **Step 2: Run to verify they fail**

Run: `npx jest app/components/datePicker app/components/subpages/foreignItem "__tests__/pages/items/[id]" --ci`
Expected: FAIL on the new assertions.

- [x] **Step 3: Implement**

`datePicker.tsx`: add the optional prop and forward it to `<DatePicker excludeDateIntervals={…}>`.
`pages/items/[id].tsx`: in `getServerSideProps`, `const bookedDates = isOwned === 'true' ? [] :
await getBookingDates(id, session)`, pass it through props and as the 4th argument to `foreignItem`.
`foreignItem.tsx`: map `bookedDates` to `{ start: new Date(startAt), end: new Date(endAt) }`, pass
the array to both pickers, and when it is non-empty render a line of `t('item.alreadyBooked')`
above them.

- [x] **Step 4: Add the copy**

`en` `item.alreadyBooked` = `"Some dates are already booked and are not selectable."`
`de` `item.alreadyBooked` = `"Einige Termine sind bereits gebucht und können nicht gewählt werden."`

- [x] **Step 5: Run to verify they pass**

Run the same command as Step 2. Expected: PASS. Then `npx jest --ci` for the whole suite —
Expected: 180+ passing, zero failing.

- [x] **Step 6: Commit**

```bash
git add app/components/datePicker.tsx app/components/datePicker.test.tsx app/components/subpages/foreignItem.tsx \
        app/components/subpages/foreignItem.test.tsx "pages/items/[id].tsx" "__tests__/pages/items/[id].test.tsx" \
        public/locales/en/common.json public/locales/de/common.json
git commit -m "feat: exclude already-booked dates from the booking date pickers"
```

---

## Task 9: Next.js item form defaults to requiring approval

**Files:**
- Modify: `pages/items/create.tsx:55`
- Test: `__tests__/pages/items/create.test.tsx`

**Interfaces:**
- Consumes: Task 4's server default
- Produces: nothing consumed later.

- [x] **Step 1: Write the failing test**

```tsx
test('the requires-approval toggle starts checked', () => {
    render(<Create … />)
    expect(screen.getByRole('checkbox')).toBeChecked()
})
```

- [x] **Step 2: Run to verify it fails**

Run: `npx jest __tests__/pages/items/create --ci` — Expected: FAIL, unchecked.

- [x] **Step 3: Change `useState(false)` to `useState(true)`** at `pages/items/create.tsx:55`, for
the `requiresApproval` state only — `pages/items/create.tsx:106` keeps sending
`availableUntil: null` (that field's defect is Phase 2's, not here).

- [x] **Step 4: Run to verify it passes**

Run: `npx jest __tests__/pages/items/create --ci` — Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add pages/items/create.tsx __tests__/pages/items/create.test.tsx
git commit -m "feat: new items require booking approval by default"
```

---

## Task 10: Flutter models and booking service

**Files:**
- Create: `lib/model/booking_date_range.dart`
- Modify: `lib/model/item_booking.dart`, `lib/service/booking_service.dart`
- Test: `test/service/booking_service_test.dart`

**Interfaces:**
- Produces:
  - `enum BookingStatus { pending, confirmed, declined, cancelled, returned }` with
    `BookingStatus.fromJson('RETURNED') -> returned`.
  - `ItemBooking({…, CancelledBy? returnedBy, DateTime? returnedAt, bool isItemOwner = false})`,
    parsed from `json['returnedBy']`, `json['returnedAt']`, `json['isItemOwner'] ?? false`.
  - `class BookingDateRange { final DateTime startAt; final DateTime endAt;
    factory BookingDateRange.fromJson(Map<String, dynamic> json); }`
  - `Future<DataResponse<DataPage<ItemBooking>>> BookingService.getReceivedBookings({int pageNumber = 0})`
  - `Future<DataResponse<ItemBooking>> BookingService.returnBooking(String bookingUuid)`
  - `Future<DataResponse<List<BookingDateRange>>> BookingService.getBookingDates(String itemUuid)`
  Consumed by Tasks 11, 12, 13.

- [x] **Step 1: Write the failing tests**

In `test/service/booking_service_test.dart`, following the file's existing mocked-`RequestService`
pattern:

```dart
test('when getting received bookings with success, then returns page of bookings', …); // expects path user/item/booking/received
test('when returning booking with success, then returns updated booking', …);           // expects PATCH user/item/booking/<uuid>/return
test('when getting booking dates with success, then returns list of date ranges', …);   // expects user/item/<uuid>/booking/dates, length 1, startAt parsed
test('when parsing a RETURNED booking, then status is returned and isItemOwner is read', …);
```

- [x] **Step 2: Run to verify they fail**

Run: `flutter test test/service/booking_service_test.dart` — Expected: FAIL, undefined names.

- [x] **Step 3: Implement**

Add the enum case + `fromJson` branch (`case 'returned': return BookingStatus.returned;`) —
note this changes the `default:` arm's meaning, which is exactly the bug Review Focus 2 names, so
keep `default: return BookingStatus.confirmed;` but make the new case explicit. Extend
`ItemBooking` and its `fromJson`. Create `booking_date_range.dart` with the same `JsonUtil`
parsing the other models use. Add the three service methods beside their siblings, reusing
`_bookingFromResponse` / `_bookingPageFromResponse`; `getBookingDates` maps
`(json as List).map((e) => BookingDateRange.fromJson(e)).toList()`.

- [x] **Step 4: Run to verify they pass**

```bash
flutter test test/service/booking_service_test.dart && flutter analyze
```
Expected: all pass; "No issues found!".

- [x] **Step 5: Commit**

```bash
git add lib/model/booking_date_range.dart lib/model/item_booking.dart lib/service/booking_service.dart \
        test/service/booking_service_test.dart
git commit -m "feat: add return, received bookings and booking dates to the mobile client"
```

---

## Task 11: Flutter — load and list the lender's bookings

`BookingState.lenderBookings` is declared and never populated
(`booking_cubit.dart:9-13`), and `my_area_screen.dart:181` opens every booking with
`isOwner: false`.

**Files:**
- Modify: `lib/widget/booking/booking_cubit.dart`, `lib/widget/my_area/my_area_screen.dart`
- Test: `test/widget/booking_cubit_test.dart`

**Interfaces:**
- Consumes: `BookingService.getReceivedBookings` (Task 10), `ItemBooking.isItemOwner` (Task 10)
- Produces: `BookingCubit.loadLenderBookings({int pageNumber = 0})`, which populates
  `BookingState.lenderBookings` and preserves `myBookings`; `_BookingsList({required bool isLender})`
  in `my_area_screen.dart`, rendering `state.data?.lenderBookings` when `isLender` and passing
  `isOwner: isLender` to `BookingDetailScreen`.

- [x] **Step 1: Write the failing tests**

In `test/widget/booking_cubit_test.dart` (it already mocks `BookingService`):

```dart
test('when loading lender bookings, then lenderBookings is filled and myBookings kept', () async {
  // stub getMyBookings -> page A, getReceivedBookings -> page B
  // cubit.loadLenderBookings(); expect state.data!.lenderBookings == B and myBookings == A
});
test('when lender bookings request fails, then errorMessage is set', …);
```

- [x] **Step 2: Run to verify they fail**

Run: `flutter test test/widget/booking_cubit_test.dart` — Expected: FAIL, method missing.

- [x] **Step 3: Implement**

`loadLenderBookings` mirrors `loadMyBookings` (`:27-36`) but keeps `myBookings` from
`state.data`. Call it from the `BookingCubit` constructor next to `loadMyBookings()`. In
`my_area_screen.dart`, give `_BookingsList` the `isLender` flag, select the matching page from the
state, and pass `isOwner: isLender` into `BookingDetailScreen`. Add a section header using the new
`myAreaScreen_lenderBookings` label (Task 12 adds the ARB keys; if you land this task first, add
the keys here instead and skip them in Task 12 — one or the other, not twice).

- [x] **Step 4: Run to verify they pass**

```bash
flutter test test/widget/booking_cubit_test.dart test/widget/my_area_cubit_test.dart && flutter analyze
```
Expected: PASS, no analysis issues.

- [x] **Step 5: Commit**

```bash
git add lib/widget/booking/booking_cubit.dart lib/widget/my_area/my_area_screen.dart test/widget/booking_cubit_test.dart
git commit -m "feat: load and list incoming booking requests on mobile"
```

---

## Task 12: Flutter booking detail — return action and returned state

**Files:**
- Modify: `lib/widget/booking/booking_detail_cubit.dart`, `lib/widget/booking/booking_detail_screen.dart`,
  `lib/l10n/app_en.arb`, `lib/l10n/app_de.arb`
- Test: `test/widget/booking_detail_cubit_test.dart`, `test/widget/booking_detail_screen_test.dart`

**Interfaces:**
- Consumes: `BookingService.returnBooking` (Task 10)
- Produces: `BookingDetailCubit.markReturned()`; ARB keys `bookingDetailScreen_return`,
  `bookingStatus_returned`, `myAreaScreen_lenderBookings`, `itemDetailScreen_alreadyBooked`,
  `notification_bookingReturned`, `notification_itemBooked` (some may land in Task 11 — see note
  there).

- [x] **Step 1: Write the failing tests**

```dart
// booking_detail_cubit_test.dart
test('when marking returned with success, then status becomes returned', …); // stub returnBooking -> booking with RETURNED
// booking_detail_screen_test.dart
test('a confirmed booking offers the return button to the requester', …);
test('a returned booking offers no buttons at all', …);
test('a returned booking shows the Returned label', …);
```

- [x] **Step 2: Run to verify they fail**

Run: `flutter test test/widget/booking_detail_cubit_test.dart test/widget/booking_detail_screen_test.dart`
Expected: FAIL.

- [x] **Step 3: Implement**

`markReturned()` follows `cancel()` (`booking_detail_cubit.dart:30-35`) calling
`_bookingService.returnBooking(uuid)`. In `_BookingDetailScreenContent`, offer the return button
when `booking?.status == BookingStatus.confirmed` — both parties, matching the backend rule — and
add `BookingStatus.returned -> AppLocalizations…bookingStatus_returned` to every status switch in
the screen and to `_statusLabel` in `my_area_screen.dart`. Add the ARB values (EN: `"Mark as
returned"`, `"Returned"`, `"Requests for my items"`, `"Some dates are already booked"`,
`"Item returned"`, `"Your item was booked"`; DE: `"Als zurückgegeben markieren"`,
`"Zurückgegeben"`, `"Anfragen für meine Gegenstände"`, `"Einige Termine sind bereits gebucht"`,
`"Gegenstand zurückgegeben"`, `"Dein Gegenstand wurde gebucht"`), then:

```bash
flutter gen-l10n
```

- [x] **Step 4: Run to verify they pass**

```bash
flutter test test/widget/booking_detail_cubit_test.dart test/widget/booking_detail_screen_test.dart && flutter analyze
```
Expected: PASS, "No issues found!".

- [x] **Step 5: Commit**

```bash
git add lib/widget/booking/booking_detail_cubit.dart lib/widget/booking/booking_detail_screen.dart \
        lib/l10n/app_en.arb lib/l10n/app_de.arb lib/gen_l10n lib/l10n/app_localizations*.dart \
        test/widget/booking_detail_cubit_test.dart test/widget/booking_detail_screen_test.dart
git commit -m "feat: add the return action and Returned status to the mobile booking detail"
```

---

## Task 13: Flutter — show booked ranges, default new items to approval

**Files:**
- Modify: `lib/widget/item/item_detail_screen.dart` (`_ItemDetailScreenContentState`, `:41-96`),
  `lib/widget/item/item_edit_screen.dart:57` and `:101`
- Test: `test/widget/item_edit_cubit_test.dart`, plus `test/service/booking_service_test.dart`
  (already covers `getBookingDates`)

**Interfaces:**
- Consumes: `BookingService.getBookingDates` (Task 10), `BookingDateRange` (Task 10)
- Produces: nothing consumed later.

- [x] **Step 1: Write the failing test**

```dart
// item_edit_cubit_test.dart
test('when creating an item without touching approval, then requiresApproval is true', () async {
  // build ItemEditCubit with a fresh Item; assert createItem was called with requiresApproval: true
});
```

- [x] **Step 2: Run to verify it fails**

Run: `flutter test test/widget/item_edit_cubit_test.dart` — Expected: FAIL, `false`.

- [x] **Step 3: Implement both changes**

`item_edit_screen.dart:57`: `bool _requiresApproval = true;` and `:101` in edit mode
`_requiresApproval = _item?.requiresApproval ?? true;` (a `null` from an old payload follows the
new server default rather than silently opting out).

In `_ItemDetailScreenContentState`, add `List<BookingDateRange> _bookedRanges = [];` and load it
in `initState` through the `_bookingService` the state already holds (`:42`) — guard with
`if (mounted)` before `setState`. Render, above the two date rows, a column listing each range as
`DateFormat('dd.MM.yyyy').format(startAt) – …endAt` under one
`t.itemDetailScreen_alreadyBooked` heading, and only when `_bookedRanges.isNotEmpty`. Mobile shows
the ranges as text rather than blocking `showDatePicker`: the app uses the Material picker, which
has no `excludeDateRanges`, and adding a date-range package for this is Phase 2+ territory.
**Record this as a deliberate deviation from G4 parity when you report the task.**

- [x] **Step 4: Run to verify they pass**

```bash
flutter test && flutter analyze
```
Expected: every test passes (~119 plus the new ones), no analysis issues.

- [x] **Step 5: Commit**

```bash
git add lib/widget/item/item_detail_screen.dart lib/widget/item/item_edit_screen.dart test/widget/item_edit_cubit_test.dart
git commit -m "feat: show booked ranges on item detail and default to requiring approval"
```

---

## Task 14: Cross-stack verification and record

**Files:**
- Modify: this plan file (checkboxes + the deviation note), `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md`
  only if a decision changed.

**Interfaces:** Consumes everything above. Produces: a green baseline for Phase 1.

- [x] **Step 1: Backend**

```bash
cd backend-quarkus && ./mvnw test -Dtest='ItemServiceTest,NotificationServiceTest,CommunityServiceTest,UserServiceTest,TimeIntervalModelTest'
```
Expected: BUILD SUCCESS. Then push and confirm CI's full run (which *does* have Docker) reports
140 tests, zero failures, zero skipped. If CI shows any skipped, that is a Phase 1 defect — record
it, do not work around it here.

- [x] **Step 2: Next.js**

```bash
cd frontend-nextjs && npx jest --ci
```
Expected: all suites pass. Confirm `npx tsc --noEmit` still reports **17** errors and no more, and
that `pnpm build`'s failure is still only the CSS `@import`.

- [x] **Step 3: Flutter**

```bash
cd frontend_flutter && flutter analyze && flutter test
```
Expected: "No issues found!", all tests pass.

- [x] **Step 4: Manual pass against the running stack**

With `./mvnw quarkus:dev`, the Next.js dev server and the Flutter app on the iOS simulator or
`chrome`: post an item as Alice with approval required, request it as Bob, confirm Alice receives a
`BOOKING_REQUEST` in-app, approves, confirms Bob sees `Confirmed` and the conversation-free booking
detail, marks it returned, confirm both see `Returned` and no buttons, and confirm Bob's picker
will not select Alice's booked range. Write the observed result — pass or fail per step — in the
"Deviation" note of this plan. Steps that cannot be run locally (device auth) are listed as
unverified rather than assumed.

- [x] **Step 5: Commit the checked plan**

```bash
git add docs/superpowers/plans/2026-11-23-phase0-booking-loop.md
git commit -m "docs: mark phase 0 booking loop plan complete"
```

---

## Out of scope for this phase

Everything else in the spec, deliberately. Notably: the production-artifact baseline and CI gates
(Phase 1), `jackson-module-kotlin` scope and the `isItemOwner` wire name (Phase 1 Task 6), the self-registered
user's role, `@Valid`, `GET` join/leave (Phase 2), proximity (Phase 3), email delivery and templates
(Phase 4), conversation and handover (Phase 4b), deployment (Phase 5), invites and the legal floor
(Phase 6).

---

## Deviations and findings

Recorded while executing, because several of them changed what a later phase has to do.

### The overlap rule is CONFIRMED-only

The plan said an approval must refuse a request that overlaps any *pending or confirmed* booking. That
makes an item unapprovable: an owner holding two overlapping requests could accept neither, because the
rival they are looking at is itself pending. The rule as shipped (ItemService.assertNoConfirmedOverlap,
`ErrorCode.ItemReserved`) refuses overlap with **confirmed** bookings only; pending rivals compete, and
whoever is approved first takes the slot and the rest are refused on their next approval attempt. Found
by the test in Task 3, which is why the plan text was changed rather than the code.

### Task 6's accessible-name fix was needed for its own test

`pages/items/create.tsx` labelled its two switches with `<text>` elements, so neither checkbox had an
accessible name and `getByRole('checkbox', { name })` could not find one. The labels became real
`<label htmlFor>`s. `ownedItem.tsx` still has the `<text>` pattern (lines 187/197) and its test reaches
the checkboxes by index — Phase 2c.

### The web app could not be run at all, not merely built

`app/styles/globals.css` has a font `@import` at line 66, after other rules. `pnpm build` fails on it, and
so does `pnpm dev`: every page renders an error overlay with an empty `<main>`. Step 4 was walked with the
`@import` moved to line 1 locally, **uncommitted and reverted afterwards**; the one-line move was verified
to be the whole fix. Phase 1 Task 2 is therefore "the web app does not run", not "the production build
fails". Pre-existing: it came in with 5d2b06b (2023-11-14, "Implement item booking").

### Quarkus dev mode cannot be started with the checked-in wrapper

`./mvnw quarkus:dev` refuses: the wrapper is Maven 3.8.4 and Quarkus 3.33's dev goal requires >= 3.9.0
(`./mvnw test` is unaffected). The system Maven is 3.8.6. So the only way to run the backend on this
machine is the packaged artifact, which is also the artifact with the wire-name defects below. Bumping the
wrapper belongs in Phase 1.

### Step 4: what was actually run, and what it showed

Stack: Keycloak 26.5.4 (realm `config/quarkus-realm.json` imported cleanly, users alice/alice and
jdoe/jdoe) on 8180, `postgres:17` on 5433, the packaged backend (`java -jar target/quarkus-app/quarkus-run.jar`)
on 8080, `pnpm dev` on 3000, driven in the in-app browser as both users. Flutter was not run on a device:
no emulator was started, so the mobile UI is unverified; its services, cubits and widgets are covered by
`flutter test` (140) and `flutter analyze`.

Booking loop over HTTP (`walk.py` in the execution ledger folder; 24 checks, all green): community
founded and admin approved a join request; a request on an approval item starts PENDING and the owner sees
it in `booking/received`; approval returns CONFIRMED; `booking/dates` reports exactly the CONFIRMED ranges
and empties again once the booking is RETURNED; an overlapping request is refused with
`{"codes":["ITEM_RESERVED"]}`; an item with approval switched off books itself CONFIRMED; RETURNED is
accepted from either side with `returnedBy` naming who did it, and only the party that did not act is
notified; the freed slot can be requested again.

Same loop in the browser: alice's Create Item form shows both switches checked (Task 9); jdoe's item page
says "Some dates are already booked and is not selectable" and refuses the booked days; the bookings page
switches between My requests and Requests for my items with Returned in blue and Awaiting approval in
orange (Task 6); the booking detail offers the borrower "Mark as returned", and using it moved the row to
Returned (Task 7).

Two defects the browser pass caught in code written in this phase, both fixed in the same phase:

* the picker excluded only the day on which a booking's *midnight* fell — 09:00 to 17:00 two days later
  blocked one day out of three, so Dec 14 was still selectable next to a confirmed 14-15 booking. The
  intervals are now widened to whole days (frontend-nextjs 6a43f13).
* `toLocaleDateString()` with no locale rendered "12/14/2026" from Node and "14.12.2026" in the German
  browser, which React 19 reports as a hydration failure and repairs by throwing the tree away. Dates go
  through `app/utils/dates.ts` with the page language now (6d624d6). `pages/notifications.tsx` had the same
  line and is included.

### Findings that belong to a later phase

* **Owner controls are dead against the shipped artifact.** As alice, a PENDING booking of her own item
  showed only "Cancel booking" — no Approve/Decline — because the prod jar emits `itemOwner` (and `active`,
  `admin`, `member`) while the clients read `isItemOwner`/`isActive`/`isAdmin`/`isMember`. Task 7's code is
  correct and its tests pass; the wire format is what is wrong. This makes Phase 1 Task 6 a hard
  prerequisite for the pilot, not a cleanup, and it also means **an absent `requiresApproval` in a request
  body becomes `false`** without `jackson-module-kotlin` (a primitive boolean gets Jackson's own default,
  silently defeating Task 4's server-side default), and an absent `availability` fails the whole request
  with HTTP 400 and an empty body. Both observed directly on the prod artifact.
* **Item visibility is not gated by community membership.** Spec: "item visibility starts at community
  membership". `GET /user/community/{uuid}/item`, `GET /user/item/{uuid}` and the community item list answer
  for any signed-in user — jdoe read items in a community he had never joined, and the home page listed
  them. `getItemBookingDates` does check membership, so the guard exists and is simply missing elsewhere.
  Not introduced here; needs a home in a later plan (Phase 2b candidate, or Phase 3 where visibility is
  re-argued).
* **A session dies silently after five minutes.** The realm issues 300-second access tokens and the web app
  has no refresh handling: every request 401s, `request.ts` logs "Missing access token", and the pages
  render an empty `<main>` while the header still looks signed in. Only signing out and back in cures it.
  The pilot cannot ship like this (Phase 2c or Phase 4).
* **`quarkus.mailer.mock=true` is not profile-scoped** — the `%prod.` block sets from/host/port but never
  `mock=false`, so the shipped jar logs "Sending email ... to [alice@test.tld]" and drops it. Phase 4 must
  un-mock it; the subjects and recipients it logged for the four notification types were otherwise right.
* The login page's only button is titled "Go to sign up page" while its handler is `signIn('keycloak')` —
  one control does both jobs under a signup label.
* The item list asks for `/user/item/image/null` when an item has no image (Phase 2c). Separately, a
  programmatic click on a bookings row navigated to `/bookings/...` and lost the `/en` prefix; this
  machine's browser default is German, so the page then served English text under a German route. Cause not
  chased — worth a look when someone is in the routing code, not a claim yet.

### Review round after Task 14

Two fresh-context reviews of the phase's commits (backend + web, and Flutter). Six of their findings were
real and are fixed here; the rest were already known, or are recorded below as declined with the reason.

* **Changeset `mario:6` was doing two things.** Alongside `SET DEFAULT TRUE` it carried
  `UPDATE item SET requires_approval = TRUE`, which rewrites rows the owners had set by hand and cannot be
  undone by the declared rollback. It is now two changesets: `mario:6` the default, `mario:7` the
  retroactive data change, commented as such and deliberately without a rollback (an automatic revert would
  also flip the items whose owners really did choose approval). Later phases' ids move up one: Phase 3
  `mario:8`-`11`, Phase 4 `mario:12`, Phase 4b `mario:13`-`15`.

  The reviewer read the `UPDATE` as an unnoticed destructive change; the plan's Task 4 says it is
  "deliberate ... recorded in the spec (G11)". Checking G11, the reviewer is right and the plan was wrong:
  G11 covers what a *new* item defaults to, nothing more. The `UPDATE` is kept because the stored `FALSE`s
  predate the decision — nobody chose them — but it is an extra policy call, not a default, and it needs an
  explicit yes. With nothing deployed yet it changes no real rows today; that is exactly when to decide it
  rather than after the pilot has neighbours.
* **`returnBooking` accepted a booking whose rental had not started** (cb48d53, web 069261d). "Returned" is
  the statement that the item is back with its owner; before pickup there is nothing to hand back, and the
  booking a party no longer wants is a *cancelled* one — the distinction is what keeps `RETURNED` meaningful
  as a signal. Now `CONFIRMED` **and** `startAt <= now` or `BookingNotReturnable`; the web button follows the
  same rule so it cannot offer an action that only fails. Two of this phase's own tests had fixtures with
  `startAt = now + 1 day`, which is how the gap survived: they described a future rental and asserted success.
* **The overlap question was asked twice**, as two hand-written JPQL strings with different parameter
  numbering (the approval guard and the booking-time check). One implementation now, `exceptBookingUuid`
  optional (cb48d53).
* **One failed booking action used to brick the mobile booking screen** (0f618bb). `approve/decline/cancel/
  markReturned` each emitted the response wholesale, and an error response carries no booking, so `state.data`
  became null — and since each action begins by reading the uuid out of `state.data`, every later tap was a
  silent no-op for the life of the screen. A failed call now keeps the known booking and shows only the error,
  and the screen no longer pops itself on failure: the frame it popped away was the one saying what went wrong.
  The phase's test for this path asserted only `errorMessage`, which is why it passed over the data loss; it
  now asserts the booking survives and that a retry reaches the server.
* **The mobile picker listed booked dates without refusing them** (078f502): the ranges were fetched and
  rendered as text, but `showDatePicker` had no `selectableDayPredicate`, so the borrower learned about the
  clash from the server after submitting. Ranges are compared whole-day (`BookingDateRange.coversDay`, which
  matches the web fix 6a43f13), and the fetch now happens only when the result is displayed — a non-owner
  view. Writing that test is what found the first version of `coversDay` wrong: it floored the interval but
  not the day asked about, so an evening fell outside a booking that started that same morning.
* **The two new My area tabs were gated on the wrong cubit's error** (c98f97f): the items/communities load is
  the first branch of one `if/else if` chain, so a failed community list blanked the booking tabs, which are
  backed by `BookingCubit` and had loaded fine. The error branch is scoped to the two tabs it describes. The
  cubit test that claims to cover interleaving now actually overlaps the two loads (e0edcae) instead of
  awaiting them one after another.

Declined, with the reason:

* **Not fixed, and it needs a home: two approvals can win the same slot.** `assertNoConfirmedOverlap` is a
  read-then-write check with no locking and nothing at the database level behind it, so two owners approving
  two overlapping requests at the same moment can both see "free" and both commit — the double booking Task 3
  exists to prevent. Reproducing it needs two threads against a real database, which is a different test
  shape from anything in the suite today. Pessimistic lock on the item for the check-and-update, or a
  Postgres exclusion constraint on `(item_uuid, tstimerange(start_at, end_at))` where `status = 'CONFIRMED'`
  (the latter also covers bookings created before the lock existed). Suggested home: Phase 2b, with the
  membership checks.

* `Item.requiresApproval`'s **entity** default stays `false` while the DTO, the model and the column default
  say `true`. Every production construction passes the value explicitly; the only code that reaches the
  entity default is test fixtures, several of which rely on it meaning "no approval" to exercise the
  auto-confirm path. Flipping it would silently change what those tests assert, which is the opposite of the
  safety the reviewer wanted.
* `returnedBy` keeps the `CancelledBy` enum. A neutral `BookingParty` is the right name, but it is a rename
  across entity, model, DTO and both clients, and that belongs with the Phase 2 wire-contract work, not here.
* `getBookingDates`'s `(json as List)` is left as it is: it is the only raw cast in that service, but the
  endpoint answers with an array or an error status, and the widget already survives the error path.

Gates after the round: backend `./mvnw test` **149 passed, 0 skipped** (one test added); web `npx jest --ci`
**209 passed**, `tsc --noEmit` still 14; mobile `flutter test` **147 passed**, `flutter analyze` clean.
