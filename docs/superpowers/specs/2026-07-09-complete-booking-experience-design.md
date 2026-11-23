# Sub-Project B — Complete Booking Experience

**Date:** 2026-07-09  
**Repos affected:** backend-quarkus, frontend-nextjs, frontend_flutter  
**Depends on:** Existing booking flow (implemented in PR #14 / feature/implement-item-booking)

---

## Goal

Close the gaps in the booking flow that were left after the initial implementation:
- Item owners have no UI to see and act on incoming booking requests
- The approve/decline buttons in the Next.js booking detail page are unreachable (`isOwner` hardcoded `false`)
- Users pick booking dates without seeing which dates are already taken
- There is no way to confirm that an item has been returned after a booking

---

## Backend Changes

### 1. New booking status: `RETURNED`

Add `RETURNED` to the `BookingStatus` enum (Kotlin + DB).

Valid transition: `CONFIRMED → RETURNED` only. Either the item owner or the requester may trigger it (same pattern as `CANCEL`).

Track who triggered it with two new nullable columns on `item_booking`:
- `returned_by` — enum `OWNER | REQUESTER` (same enum as `cancelled_by`)
- `returned_at` — `OffsetDateTime`

A `BOOKING_RETURNED` value is added to the `NotificationType` enum and the `notification` table enum column.

### 2. DB migration — `changelog-3.0.sql`

```sql
-- extend booking_status enum
ALTER TYPE booking_status ADD VALUE 'RETURNED';

-- extend notification_type enum  
ALTER TYPE notification_type ADD VALUE 'BOOKING_RETURNED';

-- new columns on item_booking
ALTER TABLE item_booking ADD COLUMN returned_by TEXT;
ALTER TABLE item_booking ADD COLUMN returned_at TIMESTAMPTZ;
```

### 3. New endpoint: `PATCH /user/item/booking/{uuid}/return`

- **Auth:** JWT (requester or item owner)
- **Preconditions:** status is `CONFIRMED`; caller is either the requester (`booking.userUuid`) or the item owner (`item.userUuid`)
- **Effect:** sets status = `RETURNED`, records `returned_by` and `returned_at`, fires `BookingEvent(RETURNED)`
- **Response:** 200 + updated `ItemBookingDto`
- **Errors:** 403 if caller is neither party; 409 if status is not `CONFIRMED`

### 4. New endpoint: `GET /user/item/{uuid}/booking/dates`

- **Auth:** JWT; caller must be a member of the community the item belongs to (same membership check as `GET /user/item/{uuid}`)
- **Response:** `[{ startAt: OffsetDateTime, endAt: OffsetDateTime }]` — date ranges of all `CONFIRMED` bookings for this item
- **Purpose:** feeds availability calendars on both frontends without exposing full booking details to non-owners

### 5. New endpoint: `GET /user/item/booking/received`

- **Auth:** JWT
- **Query params:** `pageNumber`, `pageSize`, `sortBy`, `sortDirection` (same pattern as existing booking list)
- **Response:** `Page<ItemBookingDto>` — all bookings for items owned by the calling user, newest first
- **Purpose:** owner-side counterpart to the existing `GET /user/item/booking` (requester view)

### 6. `ItemBookingDto` — add `isItemOwner` flag

The backend computes `isItemOwner = (item.userUuid == callingUserUuid)` and includes it in every `ItemBookingDto` response. Eliminates the need for an extra `/user/me` roundtrip on the frontends to determine ownership.

### 7. `NotificationService` — handle `BOOKING_RETURNED` event

On `BookingEvent(RETURNED)`:
- Notify the *other* party (if requester returned → notify owner, and vice versa)
- Notification type: `BOOKING_RETURNED`
- Email subject/body updated accordingly

---

## Next.js Changes

### New lib functions (`app/lib/bookings.ts`)

| Function | HTTP | Endpoint |
|----------|------|----------|
| `getReceivedBookings(session)` | GET | `/user/item/booking/received` |
| `returnBooking(uuid, session)` | PATCH | `/user/item/booking/:uuid/return` |
| `getBookingDates(itemUuid, session)` | GET | `/user/item/:uuid/booking/dates` |

### `pages/bookings/index.tsx` — owner/requester toggle

Add a `ToggleButton` (existing component) to switch between:
- **My Requests** — existing behavior, calls `getMyBookings`
- **My Items' Bookings** — new, calls `getReceivedBookings`

`getServerSideProps` fetches both lists; the active toggle controls which is displayed. Both lists render the same colored-status-badge + date-range rows, each linking to `/bookings/:uuid`.

### `pages/bookings/[uuid].tsx` — fix `isOwner`, add Return button

- `isOwner` reads from `booking.isItemOwner` (no longer hardcoded)
- Approve / Decline buttons are shown when `isOwner && status === 'PENDING'`
- Return button shown when `status === 'CONFIRMED'` (for both owner and requester), calls `returnBooking`
- Status display updated to handle `'RETURNED'` (color: blue)

### `pages/items/[id].tsx` + `app/components/subpages/foreignItem.tsx` — availability calendar

In `foreignItem.tsx`:
- On render, call `getBookingDates(item.uuid, session)` (passed in from `getServerSideProps`)
- Pass resulting intervals to both `DatePicker` components via `react-datepicker`'s `excludeDateIntervals` prop
- Dates that fall within a booked range are unselectable

`getServerSideProps` in `pages/items/[id].tsx` fetches booking dates alongside the item when `!isOwned`.

### Models (`app/models/booking.ts`)

- Add `'RETURNED'` to `BookingStatus` union type
- Add `isItemOwner: boolean` to `ItemBooking`
- Add `BookingDateRange: { startAt: string, endAt: string }` type

---

## Flutter Changes

### New service methods (`lib/service/booking_service.dart`)

| Method | HTTP | Endpoint |
|--------|------|----------|
| `returnBooking(bookingUuid)` | PATCH | `user/item/booking/:uuid/return` |
| `getReceivedBookings(pageNumber)` | GET | `user/item/booking/received` |
| `getBookingDates(itemUuid)` | GET | `user/item/:uuid/booking/dates` |

### `BookingCubit` — populate `lenderBookings`

`loadBookings()` calls both `getMyBookings` and `getReceivedBookings` in parallel. Both results are stored in `BookingState` (fields already declared).

### `MyAreaScreen` — "Incoming Requests" sub-view

The existing My Area top-level toggle has three options: My Items / My Communities / My Bookings. The **My Bookings** sub-view gains a secondary `ToggleButton`-style switch between "My Requests" (existing, calls `getMyBookings`) and "Incoming Requests" (new, calls `getReceivedBookings`). Incoming request rows tap into `BookingDetailScreen` with `isOwner: true`.

### `BookingDetailCubit` — `returnBooking()`

New method calling `BookingService.returnBooking(bookingUuid)`, then reloads booking state.

### `BookingDetailScreen` — Return button

When `booking.status == BookingStatus.confirmed`: show a Return button (regardless of `isOwner`). Tapping calls `BookingDetailCubit.returnBooking()`.

### `ItemDetailScreen` — availability calendar

On screen load, call `BookingService.getBookingDates(item.uuid)`. Pass the resulting list of date ranges into the date picker as disabled intervals. Implementation via `selectableDayPredicate` (Flutter's built-in date picker) or equivalent on whichever date picker widget is in use — any date that falls within a confirmed booking range is disabled.

### Models (`lib/model/item_booking.dart`)

- Add `returned` to `BookingStatus` enum (with `apiName: 'RETURNED'`)
- Add `isItemOwner: bool` field to `ItemBooking`
- Add `BookingDateRange` model: `{ startAt: DateTime, endAt: DateTime }`

---

## Notification types summary (after this change)

| Type | Sent to | Trigger |
|------|---------|---------|
| `BOOKING_REQUEST` | Owner | Requester books an item |
| `BOOKING_CONFIRMED` | Requester | Owner approves |
| `BOOKING_DECLINED` | Requester | Owner declines |
| `BOOKING_CANCELLED` | Other party | Either party cancels |
| `BOOKING_RETURNED` | Other party | Either party marks as returned |

---

## Out of Scope

- Two-step return handshake (requester initiates, owner confirms) — single-side confirmation is sufficient for MVP
- Damage reporting — treated as a separate feature (sub-project D or later)
- Real-time notification updates — covered under sub-project E (infrastructure)
