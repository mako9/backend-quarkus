# Phase 4b — Handover and Conversation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A booking that says *when* also says *how you actually get it*, and the two people can talk
about it inside the booking. Today the only channel is an in-app row and an email whose reply-to is a
`noreply` mailbox, so "can I have it Saturday instead?" has nowhere to go.

**Architecture:** Handover is two free-text columns on the **item** (visible before booking, pinned on
the booking after). Conversation is a new `booking_message` table scoped to exactly one booking, readable
and writable by exactly two people — the requester and the owner snapshotted **at booking time**, not the
item's current owner. No editing, no deleting, no rich text, no new transport: polling while the booking
screen is open.

**Tech Stack:** Quarkus 3.33 / Kotlin / Panache / Liquibase; Qute (Phase 4) for the one new mail;
Next.js 16 Pages Router + jest/Testing Library; Flutter 3.x / flutter_bloc / ARB localisation.

**Spec:** `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — read *Phase 4b* in full,
including the *Ripples* list, plus the read-authorization table in *Phase 2* (row:
*Conversation → requester and item owner only*).

**Depends on:** Phase 0 (`RETURNED`, `isItemOwner`, `BookingStatus.returned` on mobile), Phase 2 Tasks 8–10
(object-level authorization in the service layer — this plan adds a third consumer of that pattern, not a
new mechanism), Phase 4 (Qute templates, `Reply-To` convention, the throttle's mail plumbing). Do not
start before Phase 4 Task 3 has templates working.

## Global Constraints

- Repos under `/Users/mario/Projekte/Private/`. Commit per repo, never across two.
- `export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"` (plus `$HOME/development/flutter/bin`).
- Verification stack as in Phases 1–3: compose (Postgres 5433, Keycloak 8180, Mailpit 1025/8025),
  backend packaged with `KEYCLOAK_ISSUER` + `QUARKUS_DATASOURCE_JDBC_URL=…5433…`. **Never 5432.**
  `export APP_MAIL_LANGUAGE=en` (or `de`) for the mail tasks.
- `./mvnw test && ./scripts/ci-check-tests.sh` after every backend task; `npm run typecheck && npx jest
  --ci` for web (and `pnpm build` at the end of each web task); `flutter analyze && flutter test` for
  mobile.
- Liquibase ids: `mario:8`–`mario:11` are Phase 3, `mario:12` is Phase 4. **This plan uses `mario:13`,
  `mario:14`, `mario:15`** in that order, each with a `--rollback`.
- **Message bodies are user-authored text rendered to another person.** No HTML, no markdown, no link
  rendering, no sanitiser library — React escapes by default and Flutter's `Text` is literal; the task
  pins both with a test that carries `<script>alert(1)</script>` through and asserts it appears as
  typed. Do not add `dangerouslySetInnerHTML` anywhere in this plan.
- Immutable: no PATCH, no DELETE on messages. Not "not yet" — the spec makes immutability the trust
  property. If you find yourself adding an edit affordance, stop.
- Length cap 1000 characters, checked in **both** the DTO (`@Size`) and the service (a body that is
  1000 one-character graphemes is not the same as 1000 chars — assert the behaviour you implement, and
  say which you chose).
- Every user-facing string in EN and DE in the same task: web `public/locales/{en,de}/common.json`,
  mobile `lib/l10n/app_{en,de}.arb` + `flutter gen-l10n`.
- Conventional Commits, imperative, **no `Co-Authored-By`, no AI attribution.** Never touch `.gitconfig`.

## Review Focus

1. **Two people, and only those two.** `GET` and `POST …/messages` are authorized by
   `booking.userUuid == caller || booking.itemOwnerUuid == caller`. A community admin, a co-member, or
   the item's *new* owner after a transfer gets 403. The owner-transfer case is the one a reviewer must
   read in the test file, not assume. Task 3, Task 4.
2. **The snapshot, not the current owner.** `booking_message` authorization reads
   `item_booking.item_owner_uuid`, a column this plan adds and backfills. Reading `item.user_uuid`
   instead means selling or transferring an item silently hands a stranger the old conversation. Task 1
   adds the column; Task 4 forbids the other read with a test.
3. **Read-only after `RETURNED`/`CANCELLED`, empty before `CONFIRMED`.** The thread opens when the
   booking is accepted. Listing a `PENDING` booking's messages is 403 (there are none, and the difference
   leaks nothing), posting to a closed booking is refused. Three states, four behaviours, all pinned.
   Task 4.
4. **One mail per booking per recipient per ten minutes.** A five-message exchange must produce one
   email, not five — the spec names this as the difference between a notification and a reason to turn
   notifications off. Asserted by counting Mailpit messages, not by reading the code. Task 5.
5. **`unread-count` stays one number.** Chat unread folds into the existing endpoint and the badge that
   already reads it, so no client needs a second poll. If a second counter appears, the design has
   drifted. Task 6.
6. **Deleting an item now destroys two people's text.** `booking_message.booking_uuid` cascades, and
   `notification.booking_uuid` is `SET NULL`. Task 7 documents what is destroyed and writes it into the
   spec's Open question 10 — it does not silently pick a retention policy.

---

## File Structure

**backend-quarkus — create** `db/changelog/changelog-3.7.sql` (item handover columns),
`changelog-3.8.sql` (`item_booking.item_owner_uuid`), `changelog-3.9.sql` (`booking_message`),
`infrastructure/entity/BookingMessage.kt`, `domain/model/BookingMessageModel.kt`,
`domain/service/BookingMessageService.kt`, `api/dto/BookingMessageDto.kt`,
`api/controller/BookingMessageController.kt` (or the methods on `ItemController`, per Task 3 Step 1),
`domain/event/BookingMessageEvent.kt`, matching tests, and the `CHAT_MESSAGE` Qute templates in both
languages.
**modify** `infrastructure/entity/{Item,ItemBooking}.kt`, `domain/model/{ItemModel,ItemBookingModel}.kt`,
`api/dto/{ItemDto,ItemBookingDto}.kt`, `common/NotificationType.kt`, `domain/service/{ItemService,NotificationService}.kt`,
`domain/event/BookingEvent.kt` (or a new event type), `application.properties` (throttle), the templates
index, `ItemServiceTest`/`NotificationServiceTest`/`ItemControllerTest`.

**frontend-nextjs — modify** `app/models/{item,booking}.ts`, `app/lib/{items,bookings}.ts` (+ tests),
`app/components/subpages/{foreignItem,ownedItem}.tsx`, `pages/items/create.tsx`,
`pages/bookings/[uuid].tsx`, `app/components/bookingConversation.tsx` (create), `pages/notifications.tsx`
only if the type label map needs it, `public/locales/{en,de}/common.json`, `__tests__/pages/items/[id]`,
`__tests__/pages/bookings/[uuid]`.

**frontend_flutter — modify** `lib/model/{item,item_booking}.dart`,
`lib/model/booking_message.dart` (create), `lib/service/booking_service.dart`,
`lib/widget/booking/{booking_detail_screen,booking_detail_cubit}.dart`,
`lib/widget/item/{item_detail_screen,item_edit_screen}.dart`,
`lib/widget/booking/booking_conversation.dart` (create), `lib/l10n/app_{en,de}.arb`, `lib/gen_l10n/*`,
`test/service/booking_service_test.dart`, `test/widget/booking_detail_*`, `test/widget/item_*`.

---

## Task 1: Handover, on the item

Two nullable `VARCHAR(255)` columns, editable in both item forms, shown on item detail **before** booking
(the borrower needs to know they can physically get the thing before asking for dates), and pinned on the
booking view once accepted.

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.7.sql`
- Modify: `infrastructure/entity/Item.kt`, `domain/model/ItemModel.kt`, `api/dto/ItemDto.kt`
  (`ItemDetailDto`, `ItemRequestDto`, `ItemPatchDto`), `domain/service/ItemService.kt` (insert + update
  copy), and `ItemServiceTest`
- Client: `pages/items/create.tsx`, `app/components/subpages/ownedItem.tsx` (edit form),
  `app/components/subpages/foreignItem.tsx` (display), `app/models/item.ts`;
  `lib/model/item.dart` (`fromJson` **and** `toJson` — Phase 1 Task 6 is the lesson),
  `lib/widget/item/item_detail_screen.dart`, `lib/widget/item/item_edit_screen.dart`, ARBs

**Interfaces:**
- Consumes: nothing new
- Produces: `ItemModel.handoverPlace: String?`, `handoverTime: String?`; the same two keys on
  `ItemDetailDto` and `ItemRequestDto`; `Item.handoverPlace/handoverTime` on both clients. Task 2 shows
  them on the booking; Task 5 puts them in the approval mail.

- [ ] **Step 1: Migration**

```sql
--changeset mario:13 labels:item-handover context:item-handover
--comment: Free-text handover instructions, decided 2026-11-23 in place of structured opening hours
ALTER TABLE item ADD COLUMN handover_place VARCHAR(255);
ALTER TABLE item ADD COLUMN handover_time  VARCHAR(255);
--rollback ALTER TABLE item DROP COLUMN handover_place; ALTER TABLE item DROP COLUMN handover_time;
```

- [ ] **Step 2: Failing service tests**

In `ItemServiceTest`: create an item with both fields set and assert they come back from `getItem`;
PATCH one field to a new value and assert the other survives (this is the `updateUser`-style partial
update rule from Phase 2 Task 12 applied here — check how `ItemService`'s update copies fields and fix
it if it clobbers); assert a 256-character value is rejected by validation (with `@Size(max = 255)` on
the DTO, from Phase 2 Task 7's pattern) and that `null` is accepted.

- [ ] **Step 3: Implement, verify, commit**

Entity → model → DTO → service copy. Then:
```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/main/resources/db/changelog/changelog-3.7.sql src/test/kotlin
git commit -m "feat: let an item say where and when it can be picked up"
```

- [ ] **Step 4: Web — forms and display**

`pages/items/create.tsx` and the `ownedItem` edit form gain two labelled text inputs with
`maxLength={255}`; `foreignItem` (the borrow view) and `ownedItem` show a *Handover* block above the date
pickers, rendered only when at least one field is set. Keys (EN / DE):
`item.handover.title` `"Pick-up"` / `"Abholung"`, `item.handover.place` `"Where"` / `"Wo"`,
`item.handover.time` `"When"` / `"Wann"`,
`item.handover.placeHint` `"e.g. courtyard, behind the blue door"` /
`"z. B. Hinterhof, hinter der blauen Tür"`,
`item.handover.timeHint` `"e.g. weekdays after 17:00"` / `"z. B. werktags nach 17:00"`.
Tests: the block is absent when both are null, present with both values when set, and the create form
posts them (assert the request body).

- [ ] **Step 5: Mobile — same, and the round-trip test**

`Item.fromJson` reads both, `Item.toJson` writes the same keys (a test asserts
`Item.fromJson(item.toJson()).handoverPlace == item.handoverPlace` — the Phase 1 asymmetry must not be
reintroduced here). `item_edit_screen.dart` gains two `TextFormField`s; `item_detail_screen.dart` shows
the block above the date rows. ARB: `itemEditScreen_handoverPlace`, `itemEditScreen_handoverTime`,
`itemDetailScreen_handoverTitle`, EN + DE as above, then `flutter gen-l10n`.

- [ ] **Step 6: Verify and commit per repo**

`npm run typecheck && npx jest --ci && pnpm build`; `flutter analyze && flutter test`. Web commit
`feat: show and edit item handover details`; mobile the same.

---

## Task 2: The booking keeps its own copy of who the owner was

`booking_message` authorization needs the owner **as of booking creation**. `ItemBooking` has no such
column; joining through `item.user_uuid` at read time is the bug the spec names. This task adds the
column, backfills it, and makes `isItemOwner` derive from it so the flag and the authorization can never
disagree.

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.8.sql`
- Modify: `infrastructure/entity/ItemBooking.kt`, `domain/model/ItemBookingModel.kt`,
  `api/dto/ItemBookingDto.kt` (do **not** expose the new column — it is authorization data),
  `domain/service/ItemService.kt` (`bookItem` sets it; `getItemBooking*`/`isItemOwner` read it),
  `ItemServiceTest.kt`, `ItemControllerTest.kt`

**Interfaces:**
- Produces: `ItemBooking.itemOwnerUuid: UUID` (NOT NULL after backfill),
  `ItemBookingModel.itemOwnerUuid`, and `ItemService.isParty(booking, userUuid): Boolean` —
  `booking.userUuid == caller || booking.itemOwnerUuid == caller`. Task 4's guard calls `isParty` and
  nothing else.

- [ ] **Step 1: Migration with a backfill**

```sql
--changeset mario:14 labels:booking-owner-snapshot context:booking-owner-snapshot
--comment: Snapshot of the item owner at booking time, for stable conversation authorization
ALTER TABLE item_booking ADD COLUMN item_owner_uuid CHAR(36);
UPDATE item_booking b SET item_owner_uuid = i.user_uuid
    FROM item i WHERE i.uuid = b.item_uuid AND b.item_owner_uuid IS NULL;
ALTER TABLE item_booking ALTER COLUMN item_owner_uuid SET NOT NULL;
--rollback ALTER TABLE item_booking DROP COLUMN item_owner_uuid;
```
The `UPDATE` before `SET NOT NULL` will fail loudly if any booking row has no matching item — that is the
correct outcome (it means the local data is already inconsistent); report it rather than softening the
constraint.

- [ ] **Step 2: Failing test for `isParty` after a transfer**

```kotlin
@Test
fun `when the item has changed hands, then the original owner is still a party and the new one is not`() {
    // book item as requester; then update item.user_uuid to a third user
    assertTrue(itemService.isParty(booking, originalOwner.uuid))
    assertFalse(itemService.isParty(booking, newOwner.uuid))
    assertTrue(itemService.isParty(booking, requester.uuid))
}
```

- [ ] **Step 3: Implement, and make `isItemOwner` use it**

`bookItem` writes `itemOwnerUuid = item.userUuid`. Wherever `isItemOwner` is computed today (Phase 0)
switch to `booking.itemOwnerUuid == caller` — and run the whole suite: if any Phase 0 test asserted the
old derivation it was asserting a join, and the change in that test is the point of this one.

- [ ] **Step 4: Verify and commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/main/resources/db/changelog/changelog-3.8.sql src/test/kotlin
git commit -m "feat: snapshot the item owner onto the booking for conversation authorization"
```

---

## Task 3: The message table, the guard, and the two endpoints

**Files:**
- Create: `changelog-3.9.sql`, `infrastructure/entity/BookingMessage.kt`,
  `domain/model/BookingMessageModel.kt`, `api/dto/BookingMessageDto.kt`,
  `domain/service/BookingMessageService.kt`, the controller methods,
  `src/test/kotlin/api/controller/BookingMessageControllerTest.kt`
- Modify: `domain/service/ItemService.kt` only if the guard is placed there instead (Step 1 decides)

**Interfaces:**
- Consumes: `ItemService.isParty` (Task 2), `BookingStatus`
- Produces:
  ```
  GET  /api/user/item/booking/{uuid}/messages   → List<BookingMessageDto> (created_at ASC, capped page)
  POST /api/user/item/booking/{uuid}/messages   → BookingMessageDto (201)
  BookingMessageDto(uuid, bookingUuid, senderUuid, senderFirstName, body, createdAt)
  BookingMessageService.listFor(bookingUuid, callerUuid): List<BookingMessageModel>
  BookingMessageService.post(bookingUuid, senderUuid, body): BookingMessageModel
  ```
  Task 5's event and Tasks 6–7's clients use these. `senderFirstName` is denormalised **into the
  response only** (joined at read time) so a name change is reflected; there is no `senderName` column.

- [ ] **Step 1: Decide the controller location and write it down**

`ItemController` is already ~350 lines with the booking routes under `/user/item/booking/…`. Put the two
message routes in a **new** `BookingMessageController` with `@Path("/user/item/booking/{uuid}/messages")`
— same public path, separate class. Record the choice; the alternative (a fourth block of methods on
`ItemController`) is defensible but the file is where it hurts.

- [ ] **Step 2: The table**

```sql
--changeset mario:15 labels:booking-message context:booking-message
--comment: Booking-scoped conversation between the two parties
CREATE TABLE "booking_message" (
    uuid CHAR(36) PRIMARY KEY NOT NULL,
    booking_uuid CHAR(36) NOT NULL,
    sender_uuid CHAR(36) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_booking_message__booking FOREIGN KEY (booking_uuid) REFERENCES item_booking(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_booking_message__sender   FOREIGN KEY (sender_uuid)  REFERENCES "user"(uuid)      ON DELETE CASCADE
);
CREATE INDEX booking_message__booking_created ON booking_message (booking_uuid, created_at);
--rollback DROP TABLE booking_message;
```
No `read_at` column: Task 6 folds unread state into the existing `notification` table, and a per-message
read flag that nothing reads is debt. (The spec's sketch lists one — Task 7 records the deviation and
why.)

- [ ] **Step 3: Failing tests — write all of these before any implementation**

```kotlin
@Test fun `when the requester posts, then 201 and the message is listed`()
@Test fun `when the original owner posts, then 201`()
@Test fun `when a community member who is not a party lists, then 403`()
@Test fun `when a non-party posts, then 403`()
@Test fun `when the item changed hands, then the new owner is refused`()          // Review Focus 2
@Test fun `when the booking is PENDING, then listing is 403 and posting is refused`()
@Test fun `when the booking is CONFIRMED, then posting is accepted`()
@Test fun `when the booking is RETURNED, then listing works and posting is refused`()
@Test fun `when the booking is CANCELLED, then listing works and posting is refused`()
@Test fun `a body over the limit is refused with 400 and nothing is persisted`()
@Test fun `an empty or blank body is refused`()
@Test fun `messages come back oldest first`()
@Test fun `a 1000-character body is accepted`()
@Test fun `a body containing html is stored raw and returned unchanged`()          // escaping is the client's job
```
Expected before implementing: every one fails (404 on the routes).

- [ ] **Step 4: Implement**

`BookingMessageService.post` order of checks: booking exists → `isParty` → status is `CONFIRMED` →
length. Throw the existing `CustomForbiddenException`/`CustomBadRequestException` with the codebase's
`ErrorCode` style; add `ErrorCode.BookingNotOpen("BOOKING_NOT_OPEN", …)` if the code list has no suitable
entry (read `ErrorCode` first — `BookingAccessDenied` already exists and is the right one for the
non-party cases). `listFor` allows `CONFIRMED`, `RETURNED`, `CANCELLED` and 403s `PENDING`/`DECLINED`.
`@Size(max = 1000)` on the request DTO and `@Valid` on the endpoint (Phase 2 Task 7's pattern), plus a
service-side length check so the rule survives a future caller that skips validation.

- [ ] **Step 5: Verify and commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/main/resources/db/changelog/changelog-3.9.sql src/test/kotlin
git commit -m "feat: add booking-scoped messages between requester and item owner"
```

---

## Task 4: Conversation state is derived, never stored

The thread's openness is a function of `booking.status`. Nothing in this plan writes a "thread closed"
flag, because the booking status already says it and a second source of truth would drift.

**Files:**
- Modify: `domain/service/BookingMessageService.kt`, `BookingMessageServiceTest.kt` (or the controller
  test if the service has no test class yet), `ItemService.kt` (if a status transition needs to fire
  something on close — it does not, per this task's tests)
- Test: as above

**Interfaces:**
- Produces: `BookingMessageService.isOpenFor(booking): Boolean` used by `post`, and the documented
  table (below) that both clients mirror in Tasks 6–7. Task 5's notification fires only from an accepted
  `post`, so a closed thread cannot generate mail.

| `booking.status` | list | post | client shows |
|---|---|---|---|
| `PENDING` | 403 | refused | nothing — the conversation does not exist yet |
| `CONFIRMED` | 200 | accepted | the thread and a composer |
| `RETURNED` | 200 | refused | the thread, no composer, read-only note |
| `CANCELLED` | 200 | refused | the thread, no composer, read-only note |
| `DECLINED` | 403 | refused | nothing |

- [ ] **Step 1: Pin the state table in a parameterised test**

One test, five cases, driven by the table above, asserting both verbs. Kotlin's
`@ParameterizedTest @EnumSource(BookingStatus::class)` or the codebase's existing style — read
`ItemServiceTest` and match it.

- [ ] **Step 2: Prove no drift channel exists**

Grep for any write to a thread state: `grep -rn "thread\|conversation" src/main/kotlin` must find only
`BookingMessage*` classes. And assert the *transition* case:
```kotlin
@Test
fun `when a booking is returned, then the existing messages are still listable and posting is refused`()
```
i.e. the state is derived at read time, not copied anywhere.

- [ ] **Step 3: Verify and commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/test/kotlin
git commit -m "refactor: derive conversation state from booking status"
```

---

## Task 5: `CHAT_MESSAGE` notifications, and the ten-minute throttle

**Files:**
- Modify: `common/NotificationType.kt`, `domain/event/BookingMessageEvent.kt` (create),
  `domain/service/NotificationService.kt`, `src/main/resources/application.properties`,
  `domain/service/BookingMessageService.kt` (fire the event), the Qute template set from Phase 4
  (add `chatMessage.{html,txt}.ftl` in `en/` and `de/`), `NotificationServiceTest.kt`
- Test: throttle behaviour, with the clock injected

**Interfaces:**
- Consumes: Phase 4's `MailService`/templates and `Notification` row; `BookingMessageService.post`
- Produces: `NotificationType.CHAT_MESSAGE`; a notification row per message for the **other party**; and
  `app.chat.mail-throttle=10M` governing the mail. `GET /user/notification/unread-count` needs no change
  (it counts all unread rows), which Task 6 verifies rather than assumes.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun `when A posts to a booking B owns, then B gets a CHAT_MESSAGE notification and A does not`()
@Test fun `the first message in a window sends mail; a second within the window does not`()
@Test fun `after the throttle window elapses, mail is sent again`()
@Test fun `a message still creates a notification when the mail is throttled`()   // in-app is never throttled
```
The clock: inject a `() -> OffsetDateTime` or use the `Notification.createdAt` you control, so "after the
window elapses" is a data setup, not a sleep. Do not `Thread.sleep` in a test.

- [ ] **Step 2: The throttle query, and where it lives**

Mail is sent when no `CHAT_MESSAGE` notification **for this booking and this recipient** was created in
the last `app.chat.mail-throttle`. Implement it as a count on the existing table — no new columns:
```kotlin
val recent = Notification.count(
    "userUuid = ?1 AND bookingUuid = ?2 AND type = 'CHAT_MESSAGE' AND createdAt > ?3",
    recipient, bookingUuid, now().minus(throttle))
```
Write the consequence in a comment: the throttle is *per booking per recipient*, so two simultaneous
bookings of the same item mail separately — which is right, they are different conversations.

- [ ] **Step 3: Templates**

`CHAT_MESSAGE` gets subject + body in EN and DE: who wrote, **which item**, the first ~120 characters of
the message as a plain-text excerpt, and the deep link to the booking. Then the sentence the spec makes
mandatory: replying to this email reaches nobody; answer in the app. Use Phase 4's `_replyNote` fragment
so the wording is literally shared, and set `Reply-To` **absent** for this type — a `Reply-To` of the
other party on a chat mail would contradict the sentence in the body. (Phase 4's table assigns
`Reply-To` to `BOOKING_REQUEST`/`CONFIRMED`/`RETURNED`; extend the Phase 4 Task 5 test that asserts which
types carry it, adding `CHAT_MESSAGE` to the "none" group.)

- [ ] **Step 4: Verify with real mail, count it**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
```
Then the stack up: as user A, post five messages to a booking owned by B inside one minute, then
`curl -s localhost:8025/api/v2/messages | python3 -c "import json,sys;print(json.load(sys.stdin)['total'])"`.
**Expected: 1.** Then set `app.chat.mail-throttle=1S`, restart, post again, and expect a second. Paste
both numbers into the report — Review Focus 4 is checked here and nowhere else.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin src/main/resources src/test/kotlin
git commit -m "feat: notify parties about booking messages with a throttled wake-up mail"
```

---

## Task 6: Unread counts, and no second poll

**Files:**
- Modify: `domain/service/NotificationService.kt` (only if a count gap is found),
  `api/controller/NotificationController.kt` (same), tests
- Clients: `app/lib/notifications.ts` + `app/components/navBar.tsx` (label only),
  `pages/notifications.tsx` (the type label map), `lib/widget/notification/*` on mobile, ARB + locale keys

**Interfaces:**
- Consumes: `CHAT_MESSAGE` notification rows (Task 5)
- Produces: the same `UnreadCountDto` shape, now including chat unread, and a notification row that links
  to the booking. No new endpoint. If this task concludes a new endpoint *is* needed, it has failed —
  report why instead.

- [ ] **Step 1: Prove the existing count already includes chat**

`getUnreadCount` counts `userUuid = ?1 AND read = false` with no type filter, so a `CHAT_MESSAGE` row is
already counted. Add a test that asserts exactly that (one booking notification + one chat message → 2)
so a future `type IN (…)` optimisation cannot silently drop chat from the badge.

- [ ] **Step 2: Make the notification navigable**

A `CHAT_MESSAGE` row must carry `bookingUuid` (the entity already has the column) so the client can open
the booking. Test that the notification DTO exposes it for chat rows. On web, `pages/notifications.tsx`
renders a link to `/bookings/{uuid}` for chat rows (and keeps its current behaviour for booking rows);
mobile's `notification_screen.dart` pushes `BookingDetailScreen` for them.
Keys: `notification.type.CHAT_MESSAGE` EN `"New message"` / DE `"Neue Nachricht"`;
mobile `notification_chatMessage` with the same wording.

- [ ] **Step 3: Verify both clients still poll once**

Web: `navBar.tsx`'s single 30 s interval, unchanged. Mobile: the Phase 4 Task 6 badge poll, unchanged.
Confirm by reading the code and by watching one poll cycle in each: exactly one `unread-count` request
per 30 s. Record it.

- [ ] **Step 4: Commit per repo**

backend `test: pin chat messages into the unread count`;
web `feat: link chat notifications to their booking`;
mobile `feat: open the booking from a chat notification`.

---

## Task 7: The web conversation UI

**Files:**
- Create: `app/components/bookingConversation.tsx`, `app/lib/messages.ts`, `app/lib/messages.test.ts`,
  `app/components/bookingConversation.test.tsx`
- Modify: `pages/bookings/[uuid].tsx`, `app/models/booking.ts`,
  `public/locales/{en,de}/common.json`, `__tests__/pages/bookings/[uuid].test.tsx`

**Interfaces:**
- Consumes: `GET`/`POST …/booking/{uuid}/messages` (Task 3), `ItemBooking.status`
- Produces: `getBookingMessages(uuid, session?)`, `postBookingMessage(uuid, body, session?)`, and
  `<BookingConversation bookingUuid status accessToken/>`. Task 8 builds the mobile mirror of the same
  contract.

- [ ] **Step 1: lib tests, then the lib functions**

Path assertions for both, plus: a 403 yields a typed failure the component can act on (not `null`,
matching the project's `request()` result shape). `getBookingMessages` never throws — it returns the
status the caller needs, following `getBookingDates`' convention from Phase 0.

- [ ] **Step 2: Component tests**

```tsx
test('a PENDING booking renders no conversation at all', …)
test('a CONFIRMED booking renders the thread and a composer', …)
test('a RETURNED booking renders the thread with no composer and the read-only note', …)
test('the newest message is at the bottom and the order matches the server', …)   // no client re-sort
test('a message containing <script> renders as text', …)
test('a 1000-character limit is enforced in the textarea and the counter shows it', …)
test('posting clears the composer and appends the message without a refetch', …)
test('a 403 on fetch renders the not-yours state and no composer', …)
```

- [ ] **Step 3: Implement**

Poll `while the tab is visible and status === 'CONFIRMED'`, on the same 30 s cadence as the badge, with
`document.visibilityState` gating so a background tab does not burn requests. `RETURNED`/`CANCELLED` do
not poll (a closed thread cannot gain messages — but see Step 4). Body rendered as a plain string child
inside `<p>` with `whiteSpace: 'pre-wrap'`; **no `dangerouslySetInnerHTML`**, and the test asserts the
`<script>` case renders as text. Composer is a `<textarea rows=3 maxLength=1000>` + the existing submit
button component. Keys (EN / DE):
`booking.conversation.title` `"Messages"` / `"Nachrichten"`,
`booking.conversation.placeholder` `"Write to the other person"` / `"Schreib der anderen Person"`,
`booking.conversation.send` `"Send"` / `"Senden"`,
`booking.conversation.readonly` `"This conversation is closed."` / `"Diese Unterhaltung ist beendet."`,
`booking.conversation.empty` `"No messages yet. Arrange the handover here."` /
`"Noch keine Nachrichten. Vereinbare die Übergabe hier."`,
`booking.conversation.immutability` `"Messages cannot be edited or deleted."` /
`"Nachrichten können nicht bearbeitet oder gelöscht werden."`.
That last line is the trust property stated to the user; do not drop it.

- [ ] **Step 4: The one polling question this task must answer, in writing**

A `RETURNED` booking cannot gain messages *by rule* (Task 4 refuses posts), so not polling it is sound —
unless a message can arrive another way (there is none). Write that one-sentence justification in the
component's comment so the next person does not "fix" it by polling everything.

- [ ] **Step 5: Verify and commit**

```bash
npm run typecheck && npx jest --ci && pnpm build
git add app __tests__ public/locales
git commit -m "feat: add the booking conversation to the web booking page"
```

---

## Task 8: The mobile conversation UI

**Files:**
- Create: `lib/model/booking_message.dart`, `lib/widget/booking/booking_conversation.dart`,
  `lib/widget/booking/booking_conversation_cubit.dart` (or extend `booking_detail_cubit`, per Step 1),
  `test/widget/booking_conversation_test.dart`
- Modify: `lib/service/booking_service.dart`, `lib/model/item_booking.dart`,
  `lib/widget/booking/booking_detail_screen.dart`, `lib/l10n/app_{en,de}.arb`, `lib/gen_l10n/*`,
  `test/service/booking_service_test.dart`

**Interfaces:**
- Consumes: the same two endpoints, `BookingStatus`
- Produces: `BookingService.getBookingMessages(String bookingUuid)`,
  `BookingService.postBookingMessage(String bookingUuid, String body)`,
  `BookingMessage { uuid, senderUuid, senderFirstName, body, createdAt, isMine }`,
  `BookingConversation({required bookingUuid, required status})`.

- [ ] **Step 1: Decide cubit ownership before writing**

`booking_detail_cubit` already owns the booking. The conversation needs its own polling lifetime and its
own send state; make it a separate cubit provided in the same `BlocProvider` scope and read Step 2's
tests against it. Record the choice; do not thread eight new fields through `BookingState`.

- [ ] **Step 2: Tests**

```dart
test('a PENDING booking renders no conversation widget', …);
test('a CONFIRMED booking renders the thread and a composer', …);
test('a RETURNED booking renders the thread without a composer', …);
test('my own messages are distinguishable from the other party', …);
test('sending clears the field and appends without a refetch', …);
test('a body over 1000 characters cannot be submitted', …);
test('the polling timer is cancelled on dispose', …);            // the leak that matters
```

- [ ] **Step 3: Implement**

`ListView` inside a `Column` with the composer pinned below, `ScrollController` scrolling to the bottom
on a new message; polling on `Timer.periodic(const Duration(seconds: 30))` **only** while
`status == BookingStatus.confirmed`, cancelled in `close()`/`dispose` (test asserts it). Render the body
with plain `Text(body)` — no `SelectableText` with a link recognizer, no markdown package; links stay
inert, as on web. The read-only and immutability notes use the ARB keys
`bookingConversation_readonly`, `bookingConversation_empty`, `bookingConversation_immutability`,
`bookingConversation_placeholder`, `bookingConversation_send` (EN/DE strings copied from Task 7 Step 3
so the two surfaces agree verbatim), then `flutter gen-l10n`.

- [ ] **Step 4: Verify and commit**

```bash
export PATH="/usr/local/bin:/opt/homebrew/bin:$HOME/development/flutter/bin:$PATH"
flutter analyze && flutter test
git add lib test && git commit -m "feat: add the booking conversation to the mobile booking detail"
```

---

## Task 9: Prove it, and write down what this made personal

**Files:** Modify `docs/runbook.md`, the spec's *Open questions* (question 10), this plan file.

- [ ] **Step 1: End-to-end with two real users and Mailpit**

Two probe users; one item with handover text; approval on. Walk:
requester asks a question **before** approval (expect the conversation absent on both surfaces) → owner
approves → requester posts "can I get it Saturday morning?" → owner sees a badge and answers → **one**
mail in Mailpit for the exchange, with the deep link → requester marks returned → both surfaces show the
thread read-only and refuse a new post. Record each step with what was observed, including the Mailpit
count.

- [ ] **Step 2: The three privacy checks**

1. A third user (a member of the same community, not a party) requesting the messages gets 403. Record
   the code from the body.
2. The item is transferred to that third user; the old conversation still refuses them (Review Focus 2).
3. Delete the item as its owner, then query `booking_message` for that booking: **write down exactly what
   is gone** — per Task 3's cascade, the messages go with the booking, which goes with the item. Note
   whether the booking itself survives `deleteItem` at all (plan 2b's deletion task owns that decision;
   this task only records the interaction so 2b cannot decide it blindly).

- [ ] **Step 3: Write it into the spec**

Update *Open question 10* with: what `booking_message` adds to the deletion question, the
`ON DELETE CASCADE` reality measured in Step 2, and the fact that message bodies are the first
author-written personal data in the system — which is a Phase 6 data-page sentence, not a Phase 6
encryption problem. If the answer to question 10 is still open when Phase 6 is planned, this plan's
cascade behaviour is the default that Phase 6 must either ratify or migrate.

- [ ] **Step 4: Record and commit**

Append the walk and the three checks to `docs/runbook.md`'s Verified section with today's date, and note
anything from Steps 1–2 that did not happen as not verified with the reason.

```bash
git add docs && git commit -m "docs: record the booking conversation baseline"
```

---

## Out of scope for this phase

Named by the spec and binding: direct messages outside a booking; community or group threads;
attachments, images, voice; emoji reactions; typing indicators; read receipts beyond the existing badge;
search; block/report; message editing or deletion; email reply parsing (inbound mail is not read);
end-to-end encryption; delivery guarantees beyond "the row is in the database"; push. Also out: the
`read_at` column from the spec's sketch (Task 3 explains why it is dropped), and any moderation or
reporting surface — see the spec's non-goals.

## Known deviations to report

- `read_at` is not implemented; the spec's Phase 4b column list was corrected on 2026-11-23 to drop it
  (Task 3 Step 2 explains why the plan and the spec now agree). Report it as a deliberate deviation, not
  an omission.
- New controller class `BookingMessageController` instead of methods on `ItemController` (Task 3 Step 1).
- No `Reply-To` on `CHAT_MESSAGE` mail, which extends Phase 4 Task 5's table. Report the change to that
  task's expectation.
- If `item_booking`'s backfill in Task 1 finds orphan rows, stop and report; do not weaken the constraint.
