# Booking Flow Design

**Date:** 2026-07-08  
**Status:** Approved  
**Scope:** Item booking lifecycle with approval workflow, conflict detection, availability validation, and in-app + email notifications

---

## Context

The ComShare platform allows neighbours to share tools and equipment within geographically-restricted communities. The backend already supports creating a booking (`POST /user/item/{uuid}/booking`) but has no status model, no approval step, no conflict detection, and no notification system. This spec covers the complete booking flow as the first post-MVP feature.

---

## Requirements

- Item owners can configure each item as **requires approval** or **auto-approve**
- Booking statuses: `PENDING` → `CONFIRMED` → `DECLINED` / `CANCELLED` / `COMPLETED`
- Conflict detection: a new booking is rejected if any **CONFIRMED** booking overlaps the requested time range (PENDING bookings do not block slots)
- Availability validation: bookings must fall within the item's declared weekly availability windows
- Both the item owner and the requester can cancel a booking in `PENDING` or `CONFIRMED` state, anytime before `start_at`
- Every booking state change triggers an **in-app notification** (DB record) and an **email** to the relevant party
- Notifications are delivered asynchronously — HTTP responses are never blocked by email sending
- Both Flutter (mobile) and Next.js (web) frontends are updated in parallel

---

## Data Model

### `item` table — new column

```sql
requires_approval BOOLEAN NOT NULL DEFAULT FALSE
```

### `item_booking` table — new columns

```sql
status       VARCHAR NOT NULL DEFAULT 'CONFIRMED'
             -- PENDING | CONFIRMED | DECLINED | CANCELLED | COMPLETED
cancelled_by VARCHAR NULL
             -- OWNER | REQUESTER (set only when status = CANCELLED)
cancelled_at TIMESTAMP NULL
```

Existing rows default to `CONFIRMED` to avoid disrupting historical data.

### New `notification` table

```sql
CREATE TABLE notification (
    uuid         UUID PRIMARY KEY,
    user_uuid    UUID NOT NULL REFERENCES "user"(uuid) ON DELETE CASCADE,
    type         VARCHAR NOT NULL,
    -- BOOKING_REQUEST | BOOKING_CONFIRMED | BOOKING_DECLINED | BOOKING_CANCELLED
    booking_uuid UUID REFERENCES item_booking(uuid) ON DELETE SET NULL,
    read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL
);
```

---

## Backend API

### Modified endpoints

| Method | Path | Change |
|--------|------|--------|
| `POST` | `/user/item/{uuid}/booking` | Validates availability windows and slot conflicts; sets `PENDING` or `CONFIRMED` based on `requiresApproval`; fires async notification event |
| `PATCH` | `/user/item/{uuid}` | Accepts and returns `requiresApproval` field |

### New booking action endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `PATCH` | `/user/item/booking/{uuid}/approve` | Item owner only | `PENDING` → `CONFIRMED`; notifies requester |
| `PATCH` | `/user/item/booking/{uuid}/decline` | Item owner only | `PENDING` → `DECLINED`; notifies requester |
| `PATCH` | `/user/item/booking/{uuid}/cancel` | Owner or requester | `PENDING` or `CONFIRMED` → `CANCELLED`; sets `cancelled_by`, `cancelled_at`; notifies the other party |

### New notification endpoints (`/user/notification`)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/user/notification` | Paginated list, newest first |
| `GET` | `/user/notification/unread-count` | Returns `{ count: N }` for badge |
| `PATCH` | `/user/notification/{uuid}/read` | Mark single notification read |
| `PATCH` | `/user/notification/read-all` | Mark all notifications read |

### Booking creation logic (`ItemService`)

1. Load item; verify requesting user is not the owner
2. Check `start_at`/`end_at` falls within a declared weekly availability window — reject `400` if not
3. Query all `CONFIRMED` bookings for the item that overlap the requested range — reject `409` if any exist
4. Create booking with `status = PENDING` (if `requiresApproval`) or `CONFIRMED` (otherwise)
5. Fire async CDI event → `NotificationService` (fire-and-forget)

---

## Notification Service

`NotificationService` lives in the domain layer. It is invoked via async CDI events fired by `ItemService` after each state transition. It is never called on the HTTP request thread.

### Event → recipient mapping

| Trigger | Recipient | Type |
|---------|-----------|------|
| Booking created, `requiresApproval = true` | Item owner | `BOOKING_REQUEST` |
| Booking created, `requiresApproval = false` | Requester | `BOOKING_CONFIRMED` |
| Owner approves booking | Requester | `BOOKING_CONFIRMED` |
| Owner declines booking | Requester | `BOOKING_DECLINED` |
| Owner cancels booking | Requester | `BOOKING_CANCELLED` |
| Requester cancels booking | Item owner | `BOOKING_CANCELLED` |

### For each event, the service:
1. Writes a `notification` row to the DB
2. Sends a Qute-templated HTML email via Quarkus Mailer (SMTP)

### Email templates

Located in `src/main/resources/templates/`. One HTML template per notification type. Template variables: recipient name, item name, booking start/end dates, deep-link URL to the booking.

### COMPLETED status

`COMPLETED` is computed lazily: when a `CONFIRMED` booking's `end_at` is in the past, GET responses return it as `COMPLETED`. A `@Scheduled` nightly job persists the final status to the DB for bookings that have passed.

---

## Frontend Changes

Both frontends implement the same conceptual features, adapted to their stack.

### Flutter (BLoC/Cubit)

**New/updated screens:**
- `ItemDetailScreen` — replaces the existing "rent item" TODO with a date range picker + confirmation dialog; shows "Approval required" notice if `requiresApproval = true`
- `BookingDetailScreen` (new) — status chip, cancel button (if cancellable), item and requester info
- `MyAreaScreen` — new "My Bookings" tab with sub-views "As Borrower" and "As Lender"; lender view shows `PENDING` requests with Approve / Decline actions
- `NotificationScreen` (new) — list of notifications; unread highlighted; tap navigates to relevant booking
- `ItemEditScreen` — adds `requiresApproval` toggle

**New cubits:** `BookingCubit`, `BookingDetailCubit`, `NotificationCubit`  
**New services:** `BookingService`, `NotificationService`  
**Notification badge:** `NotificationCubit` polls `unread-count` on tab focus; badge shown on nav bar icon

### Next.js

**New/updated pages:**
- `/items/[id]` — wire existing date picker to real API; show status feedback and `requiresApproval` notice
- `/bookings` (new) — borrower / lender split
- `/bookings/[id]` (new) — booking detail with approve/decline/cancel
- `NavBar` — notification bell with unread badge; polling `unread-count` via `setInterval` on mount; dropdown panel marks all read on open

**Updated pages:**
- `/items/create`, `/items/[id]/edit` — add `requiresApproval` toggle

**New API lib files:** `bookingApi.ts`, `notificationApi.ts`

### Shared UX rules

- `PENDING` bookings display "Awaiting owner approval" chip
- Approve/Decline actions are shown only to the item owner
- Cancel is shown to whichever party is permitted to act
- Bookings with `end_at` in the past and status `CONFIRMED` are displayed as "Completed"

---

## Out of Scope

The following are intentionally excluded from this spec and tracked as future work:

- Push notifications to Flutter via FCM/APNs (requires a notification service upgrade)
- Real-time WebSocket/SSE delivery to Next.js
- Ratings and reviews post-booking
- Damage reporting
- Deposit / payment handling
- Proximity enforcement for community joining
