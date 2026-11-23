# Phase 4 — Notifications That Deliver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A booking event reaches a human who is not looking at the app. Today
`quarkus.mailer.mock=true` (`application.properties:50`) has no profile prefix, so it holds in
`%prod` too: nothing has ever been sent, by any code path, in any environment. `%prod` also points at
`localhost:587`, where no SMTP server exists.

**Architecture:** Config first (mock stops being a prod setting), then Qute templates instead of the
inline Kotlin strings at `NotificationService.kt:77-96`, then **recorded delivery** — the notification
row gains `email_sent_at`/`email_error` and a `@Scheduled` sweep retries what failed, replacing the
catch-and-log at `:46-50`. Verification is a real SMTP sink in the dev compose file, not a mock.

**Tech Stack:** Quarkus 3.33 `quarkus-qute` + `quarkus-scheduler` (neither is in `pom.xml` today —
verified), `quarkus-mailer` (present), Liquibase, JUnit. Web: existing 30 s badge poll
(`app/components/navBar.tsx:41-47`). Mobile: a `Timer.periodic` in a cubit; no push, no new transport.

**Spec:** `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — read *Phase 4*, *Finding 1*,
and *Goals* G5/G10.

**Depends on:** Phase 0 (`ITEM_BOOKED`, `BOOKING_RETURNED` types), Phase 1 (runbook, compose stack,
build gates), Phase 2 Task 7 (so an email link can assume a validating API), Phase 4b reuses this
phase's templates and its `Reply-To` rule, so do 4b after this.

## Global Constraints

- Repos under `/Users/mario/Projekte/Private/`; commit per repo.
- `export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"` (plus `$HOME/development/flutter/bin` for
  Flutter).
- `./mvnw test && ./scripts/ci-check-tests.sh` after every backend task. `%test` keeps the mock mailer
  — Quarkus' `Mailer` mock is the right test double, and `send` failures are simulated by a stub
  `Mailer` alternative where a task needs one.
- **No task in this plan may claim an email was delivered without showing it in the SMTP sink's UI or
  API.** "The code calls send" is not delivery. Task 1 provides the sink for exactly this reason.
- Every new user-facing string in the **apps** needs `public/locales/{en,de}/common.json` and
  `lib/l10n/app_{en,de}.arb` + `flutter gen-l10n`. **Email copy is different** — see Task 3's language
  decision — and is deliberately not duplicated into those files.
- Liquibase changeset ids continue from the tree's last (`mario:8`–`mario:11` land in Phase 3; this plan uses `mario:12`).
- Conventional Commits, imperative, **no `Co-Authored-By`, no AI attribution.** Never touch
  `.gitconfig`.
- Nothing in this phase adds push notifications, in-app sockets, SMS, or a nightly digest.

## Review Focus

1. **Mock leaking into prod is the bug this whole phase exists for.** After Task 1, `%prod` must fail
   loudly when SMTP is unconfigured rather than silently swallowing mail. Pinned by a boot test in
   Task 1 Step 4.
2. **A failed send must be visible and retryable.** Today `NotificationService.kt:48-49` logs and
   forgets, so a user loses a booking confirmation with no trace. Expected: the row says it failed and
   when it will be retried; the sweep retries a bounded number of times and then stops with a recorded
   reason. Task 4.
3. **Templates must be data, not string concatenation.** The item name and the requester's name are
   user-authored text arriving in someone's inbox; string-concatenated HTML is an injection surface in
   the mail client. Qute escapes by default — the task pins that with a template test that includes
   `<script>` in an item name. Task 3.
4. **The email is a pointer, not the record.** Every mail links to the booking in the web app and says
   what it is about. A deep link that 404s is worse than no link, so Task 3 verifies the link target
   resolves against the running stack.
5. **`Reply-To` of the owning user turns a notification into a conversation** — but only if the app
   says plainly that replying to a *noreply* address reaches nobody. Task 5 pairs the two, and 4b's
   copy depends on it.
6. **The badge must not say "3" after the user has read the list.** The web polls every 30 s and the
   list screen marks read; the mobile app has no poll at all. Task 6 adds polling without adding a
   race (the mark-all-read-before-fetch bug is plan 2c's; this task does not touch it).

---

## File Structure

**backend-quarkus — create** `src/main/resources/templates/BookingMail/{request,confirmed,declined,cancelled,returned,itemBooked}.html.ftl`
and matching `.txt.ftl`, `src/main/kotlin/domain/service/MailService.kt`,
`src/main/kotlin/infrastructure/MailerConfig.kt` if a `@ConfigMapping` is cleaner than properties,
`src/main/resources/db/changelog/changelog-3.6.sql` (`email_sent_at`, `email_error`,
`email_attempts`), `src/main/kotlin/domain/service/NotificationRetryJob.kt`,
`src/test/kotlin/domain/service/{MailServiceTest,NotificationRetryJobTest}.kt`.
**modify** `pom.xml`, `src/main/resources/application.properties`, `infrastructure/entity/Notification.kt`,
`domain/model/NotificationModel.kt`, `api/dto/NotificationDto.kt`, `domain/service/NotificationService.kt`,
`docker/compose.dev.yml`, `docs/runbook.md`.

**frontend_flutter — create** `lib/widget/notification/notification_badge_cubit.dart` (or extend the
existing `notification_cubit.dart`). **modify** `lib/widget/notification/notification_screen.dart`,
`lib/service/notification_service.dart`, `lib/widget/home/*` (wherever the badge renders), the ARB files.

**frontend-nextjs — modify** `app/components/navBar.tsx` (refresh after mark-read), the notifications
page if it marks read on open.

---

## Task 1: A real SMTP sink, and a mock that stops being a prod setting

Nothing here is verifiable until there is a server that receives mail. This task adds one to the dev
stack and makes `%prod` refuse to start without SMTP configuration.

**Files:**
- Modify: `src/main/resources/application.properties`, `docker/compose.dev.yml`, `docs/runbook.md`
- Test: `src/test/kotlin/ProdMailerConfigTest.kt`

**Interfaces:**
- Produces: Mailpit on `localhost:1025` (SMTP) / `http://localhost:8025` (HTTP API for assertions),
  and the properties `%prod` needs: `QUARKUS_MAILER_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD`,
  `APP_MAIL_FROM`. Task 4's retry job and 4b's wake-up mail assert through Mailpit's API
  (`GET /api/v2/messages`).

- [ ] **Step 1: Add Mailpit to the dev compose file**

```yaml
  mailpit:
    image: docker.io/axllent/mailpit
    ports: ["1025:1025", "8025:8025"]
```
`docker compose -f docker/compose.dev.yml up -d && sleep 3 &&
curl -s localhost:8025/api/v2/messages | head -c 200` — Expected: a JSON envelope with an empty message
list, i.e. the API answers.

- [ ] **Step 2: Scope the mock and make prod env-driven**

Replace `application.properties:50-53` with:
```properties
# mailer — mocked in dev and test only. Prod must be configured or the app will not start.
%dev.quarkus.mailer.mock=false
%dev.quarkus.mailer.host=localhost
%dev.quarkus.mailer.port=1025
%test.quarkus.mailer.mock=true
quarkus.mailer.from=${APP_MAIL_FROM:noreply@comshare.local}
%prod.quarkus.mailer.host=${QUARKUS_MAILER_HOST}
%prod.quarkus.mailer.port=${QUARKUS_MAILER_PORT}
%prod.quarkus.mailer.username=${QUARKUS_MAILER_USERNAME}
%prod.quarkus.mailer.password=${QUARKUS_MAILER_PASSWORD}
%prod.quarkus.mailer.from=${APP_MAIL_FROM}
%prod.quarkus.mailer.ssl=true
%prod.quarkus.mailer.use-starttls=true
```
Note the deliberate `%dev.quarkus.mailer.mock=false` pointing at Mailpit: dev now sends for real, to a
sink. Anyone who does not run the compose stack sees connection errors in dev — that is the point, and
it goes in the runbook.

- [ ] **Step 3: Confirm no unscoped mock line survives**

```bash
grep -n "mailer" src/main/resources/application.properties
```
Expected: every `mock` line carries `%dev` or `%test`. **A bare `quarkus.mailer.mock=` line must not
exist** — that single line is the reason nothing was ever sent.

- [ ] **Step 4: Prove prod refuses an unconfigured mailer**

```bash
./mvnw -B -DskipTests package
java -jar target/quarkus-app/quarkus-run.jar 2>&1 | grep -iE "QUARKUS_MAILER|Failed to start|mailer" | head -5
```
Expected: startup fails on the unresolved expression, like Phase 1/2's env-required pattern. If it
starts, Quarkus resolved to empty; then add an explicit guard in a small `@Startup` observer that
throws in `%prod` when `quarkus.mailer.host` is blank, and write which mechanism you used.

- [ ] **Step 5: Prove dev actually delivers**

With the stack up, run the backend in dev and send one notification through the existing flow (or
`./mvnw quarkus:dev` + the REST call Phase 2 Task 1 used), then:
```bash
curl -s "localhost:8025/api/v2/messages?limit=1" | python3 -m json.tool | head -30
```
Expected: one message with a `From` of `noreply@comshare.local`. Paste the JSON fragment into the
report and into `docs/runbook.md`'s Verified section. **Until this step passes, no later task in this
plan may claim delivery.**

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.properties docker/compose.dev.yml docs/runbook.md src/test/kotlin
git commit -m "fix: scope the mailer mock to dev and test and require smtp settings in production"
```

---

## Task 2: Add Qute and the scheduler

Neither extension is in `pom.xml` (verified). This task adds them with a compile-and-boot check only;
the templates come in Task 3.

**Files:**
- Modify: `pom.xml`, `src/main/resources/application.properties`

**Interfaces:**
- Produces: `io.quarkus.qute.Inject`/`TemplatedInstance` availability for Task 3; `@Scheduled` for
  Task 4. Adding the scheduler changes startup: `quarkus.scheduler.enabled=true` is the default, so
  **every existing test now runs a scheduler thread** — which is why the retry job in Task 4 must be
  `@Scheduled(every = …, enabled = …)`-style switchable via `app.notifications.retry-enabled`, false
  under `%test`.

- [ ] **Step 1: Add the two dependencies**

```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-qute</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-scheduler</artifactId>
        </dependency>
```

- [ ] **Step 2: Prove they resolve into the shipped artifact**

```bash
./mvnw -B -DskipTests package
ls target/quarkus-app/lib/main | grep -iE "qute|scheduler"
./scripts/check-runtime-classpath.sh
```
Expected: `io.quarkus.quarkus-qute-3.33.1.jar`, `…quarkus-scheduler…` (plus their transitive
`quarkus-quartz` if the cron flavour is pulled — the plain `@Scheduled` every-interval form does not
need Quartz; if it is pulled anyway, note it, because it costs ~30 MB and a thread on a small box).

- [ ] **Step 3: Prove the suite is unaffected**

`./mvnw test && ./scripts/ci-check-tests.sh` — Expected: green, 0 skipped, same totals as before.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add the qute and scheduler extensions for notification delivery"
```

---

## Task 3: Templates, names, deep links, and a language decision

`NotificationService.kt:77-96` builds subject and body from `when` expressions with string
concatenation and sends `Mail.withText`. Replace with one HTML + one text template per
`NotificationType` (`BOOKING_REQUEST`, `BOOKING_CONFIRMED`, `BOOKING_DECLINED`, `BOOKING_CANCELLED`,
`BOOKING_RETURNED`, `ITEM_BOOKED` — six types after Phase 0), sent multipart.

**Language decision, made here because the spec left it implicit.** There is no locale on `user`, and
an async event has no `Accept-Language` to read. So the pilot sends **one language per deployment**,
`app.mail.language=en|de` (default `en`), and the two template sets live side by side
(`templates/BookingMail/en/…`, `templates/BookingMail/de/…`). Adding a `locale` column to `user` and a
language picker is the right long-term answer; it is a data-model + onboarding change, so it is written
into the spec's *Open questions* by Step 6 rather than done here. Do not invent a browser-locale sniff
in an observer that has no request.

**Files:**
- Create: `src/main/resources/templates/BookingMail/en/{request,confirmed,declined,cancelled,returned,itemBooked}.{html,txt}.ftl`
  and the `de/` mirrors (24 files), `src/main/kotlin/domain/service/MailService.kt`
- Modify: `domain/service/NotificationService.kt`, `src/main/resources/application.properties`,
  `domain/event/BookingEvent.kt` (it needs the fields the templates want — see Step 2)
- Test: `src/test/kotlin/domain/service/MailServiceTest.kt`

**Interfaces:**
- Consumes: `BookingEvent`, `Mailer`
- Produces:
  ```kotlin
  @ApplicationScoped class MailService {
      fun render(type: NotificationType, model: MailModel): RenderedMail   // subject, text, html
      fun send(recipientEmail: String, recipientName: String, mail: RenderedMail, replyTo: MailAddress? = null)
  }
  data class MailModel(type, recipientFirstName, itemUuid, itemName, bookingUuid, bookingStartAt,
                       bookingEndAt, otherPartyName, handoverPlace: String?, handoverTime: String?,
                       appBaseUrl)
  ```
  Task 4 records the outcome of `send`; Phase 4b adds `CHAT_MESSAGE` to the same table of templates.

- [ ] **Step 1: Write the failing template tests**

```kotlin
@Test fun `every notification type has an en and a de html and text template`()
@Test fun `an item name containing html is escaped in the html part`() {
    val mail = mailService.render(NotificationType.BOOKING_REQUEST,
        model(itemName = "<script>alert(1)</script>"))
    assertFalse(mail.html.contains("<script>alert"))
    assertTrue(mail.html.contains("&lt;script&gt;"))
}
@Test fun `the text part contains no markup and no template placeholders`()
@Test fun `every mail links to the booking`()   // contains "$appBaseUrl/bookings/$bookingUuid"
@Test fun `the request mail names the requester and both dates`()
```
`BookingMailType.values()` (or a list built from `NotificationType`) makes the first test cover new
types automatically — that is the point of it.

- [ ] **Step 2: Run to verify they fail, then extend the event**

`./mvnw test -Dtest=MailServiceTest` — Expected: FAIL, unresolved.
`BookingEvent` currently carries `notificationType, recipientUserUuid, bookingUuid, itemName,
requesterName`. Templates need `bookingStartAt`, `bookingEndAt`, `itemUuid`, and the *recipient's*
first name plus the other party's name; add them, updating every `fireAsync` call site
(`ItemService.bookItem`, `approveBooking`, `declineBooking`, `cancelBooking`, `returnBooking`).
`handoverPlace`/`handoverTime` are Phase 4b's — put them in `MailModel` as nullable now so 4b does not
reshape this interface.

- [ ] **Step 3: Write the templates**

Each HTML template: a greeting with the recipient's first name, one sentence of what happened, the
item name and the date range (or the request's proposed range), a single link with the URL as its own
text, and one line saying where to reply. No images, no CSS beyond inline styles, no external
resources — a mail that fetches a remote pixel is a tracking bug. Each `.txt.ftl` is the same content
without markup. Qute auto-escapes `.html.ftl`; state that in a comment at the top of one file so the
next person does not "fix" it by turning escaping off.
EN copy is written once here and mirrored into DE; keep the DE strings in this commit (not a follow-up),
and use the same wording as the app's ARB/common.json strings for status names so the mail and the UI do
not disagree about what `RETURNED` is called.

- [ ] **Step 4: Send multipart**

```kotlin
val mail = Mail.withHtml(to, subject, rendered.html).setTextBody(rendered.text)
```
and `NotificationService.onBookingEvent` calls `mailService` instead of building strings. Delete
`emailSubject`/`emailBody`.

- [ ] **Step 5: Verify against the sink**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
# stack up, backend in dev: drive one BOOKING_REQUEST, then
curl -s "localhost:8025/api/v2/messages?limit=1" | python3 -m json.tool | head -40
```
Expected: the message's `Subject`, and both `text` and `html` parts present; the deep link's UUID is the
booking's. Then **follow the link**: with the web app running (`pnpm dev`), open
`/bookings/{uuid}` while logged in as the recipient and confirm it renders the booking instead of the
not-found state (Review Focus 4). Record the result.

- [ ] **Step 6: Record the language decision**

Append to the spec's *Open questions*: "Should a user choose their mail language (needs a `locale` on
`user` and an onboarding step), and should the language follow the app UI language or a separate
setting? Phase 4 ships one language per deployment (`app.mail.language`)."

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/kotlin src/main/resources/templates src/main/resources/application.properties src/test/kotlin
git commit -m "feat: render notification mail from qute templates in english and german"
```

---

## Task 4: Record delivery, and retry what failed

`NotificationService.kt:46-50` catches, logs at WARN and drops it. A user whose confirmation mail
failed does not exist as a problem anywhere. This task makes the row carry the outcome and adds a
bounded sweep.

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.6.sql`,
  `src/main/kotlin/domain/service/NotificationRetryJob.kt`,
  `src/test/kotlin/domain/service/NotificationRetryJobTest.kt`
- Modify: `infrastructure/entity/Notification.kt`, `domain/model/NotificationModel.kt`,
  `domain/service/NotificationService.kt`, `api/dto/NotificationDto.kt` (deliberately **not** exposed —
  see Step 4), `application.properties`

**Interfaces:**
- Consumes: `MailService.send` (Task 3)
- Produces: `Notification.emailSentAt: OffsetDateTime?`, `emailError: String?`, `emailAttempts: Int`;
  `NotificationService.deliver(notification)` used by both the event observer and the sweep, so a retry
  renders exactly what the original would have.

- [ ] **Step 1: Migration**

```sql
--changeset mario:12 labels:mail-delivery context:mail-delivery
--comment: Record the outcome of notification email delivery
ALTER TABLE notification ADD COLUMN email_sent_at TIMESTAMPTZ;
ALTER TABLE notification ADD COLUMN email_error VARCHAR(500);
ALTER TABLE notification ADD COLUMN email_attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX notification_unsent ON notification (created_at) WHERE email_sent_at IS NULL;
--rollback ALTER TABLE notification DROP COLUMN email_sent_at; ALTER TABLE notification DROP COLUMN email_error; ALTER TABLE notification DROP COLUMN email_attempts;
```
The partial index is what makes the sweep one cheap query instead of a table scan every few minutes on a
box that also hosts Keycloak.

- [ ] **Step 2: Failing tests for the outcome recording**

```kotlin
@Test fun `when sending succeeds, then email_sent_at is set and attempts is one`()
@Test fun `when sending throws, then email_error holds the reason and email_sent_at stays null`()
@Test fun `a notification whose recipient has no email address is marked skipped, not retried forever`()
@Test fun `the sweep does not retry past app.mail.max-attempts`()
@Test fun `the sweep does not touch a notification already sent`()
@Test fun `a retried send renders the same mail as the original`()   // re-render from stored fields
```
The last one is the reason `deliver()` re-renders from the booking/item rather than storing the rendered
body — and storing the body would be worse anyway: it would freeze user-authored text in a row and put
the item name in the archive that Phase 2's deletion note complains about.

**The deleted-item case (spec: "copy for an event whose subject no longer resolves").** `booking_uuid`
is `ON DELETE SET NULL` (`changelog-2.0.sql:20`), so a retry after the item is gone cannot resolve a
name. Decide and implement explicitly: if the booking or item no longer resolves, mark
`email_error = 'source no longer available'`, set `email_attempts` to the max, and **do not send**. The
in-app row stays (it is the user's history), the mail is abandoned. Write that in a comment; plan 2b may
change the retention model and will want to find this decision written down.

- [ ] **Step 3: Run to verify they fail, then implement `deliver` + the sweep**

`deliver` increments `emailAttempts` before sending, records success or `emailError = t.message
.take(500)`. The job:
```kotlin
@ApplicationScoped
class NotificationRetryJob {
    @Scheduled(every = "${app.mail.retry-every:10m}",
               enabled = "${app.mail.retry-enabled:true}")
    @Transactional fun retry() { … }
}
```
Backoff is expressed by the query, not a scheduler per message: only rows whose `created_at` is older
than `attempts * retry-every` are picked up, so attempts 1→2→3 space themselves out without any extra
state. `application.properties`: `%test.quarkus.scheduler.enabled=false` **or**
`%test.app.mail.retry-enabled=false` — pick one, say which, and prove the suite is deterministic by
running it twice.

- [ ] **Step 4: Keep the outcome out of the API**

`NotificationDto` must **not** expose `emailError`: it can contain an SMTP server's message, and it is
not the user's data. If `NotificationModel` carries it for internal use, map it away in the DTO — and
write a test that the notification list response has no `emailError` key.

- [ ] **Step 5: Prove a retry actually happens, against the sink**

With the stack up: stop Mailpit (`docker compose stop mailpit`), generate a notification (it fails),
start Mailpit, wait one `retry-every`, and read `GET /api/v2/messages` plus the row's
`email_sent_at`. Record both. Then set `app.mail.max-attempts=1`, repeat, and show the row stops with
an error and the sweep leaves it alone.
**This is the task's real acceptance test.** A unit test alone does not prove the scheduler wiring.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin src/main/resources src/test/kotlin
git commit -m "feat: record notification email delivery and retry unsent mail with a bounded backoff"
```

---

## Task 5: Reply-To the person, and say plainly where replies go

`noreply@` as sender with no `Reply-To` means "can I have it Saturday instead?" bounces or lands in a
black hole. The spec wants the owning user as `Reply-To` — but with the app, and 4b, being the actual
channel, the mail has to say so or people will hold a two-message email thread that nobody answers.

**Files:**
- Modify: `domain/service/MailService.kt`, the six templates (en + de), `NotificationService.kt`
- Test: `MailServiceTest.kt`

**Interfaces:**
- Consumes: recipient + counterparty emails from `User`
- Produces: `send(…, replyTo = MailAddress(name, email))` set on `BOOKING_REQUEST` (the owner replies
  to the requester) and `BOOKING_CONFIRMED`/`BOOKING_RETURNED` (the borrower replies to the owner).
  `BOOKING_DECLINED` and `BOOKING_CANCELLED` carry **no** `Reply-To`: a rejection and an ending are not
  invitations to write back. 4b's `CHAT_MESSAGE` mail follows this same table plus the throttle.

- [ ] **Step 1: Decide which types get a Reply-To, then test it**

```kotlin
@Test fun `the booking request mail sets reply-to the requester and names them`()
@Test fun `the confirmed mail sets reply-to the item owner`()
@Test fun `the declined and cancelled mails set no reply-to`()
@Test fun `no template tells the reader to reply by email`()   // asserts the copy says "reply in the app"
```
That last test is the whole task: it fails if any template says "reply to this email" or omits the
where-to-reply line. Write the line in EN as
`"To answer, open the booking in ComShare — replies to this message reach nobody."` and in DE
`"Um zu antworten, öffne die Buchung in ComShare — Antworten auf diese Nachricht erreichen niemanden."`
and put it in a shared fragment (`templates/BookingMail/_replyNote.{html,txt}.ftl`) so the six templates
cannot drift.

- [ ] **Step 2: Implement, and send one of each through the sink**

`Mail.withHtml(...).setReplyTo(name, address)` (verify the exact `Mailer` API in use before writing —
`io.quarkus.mailer.Mail` exposes `setReplyTo(String)`; if only the address-only form exists, the name
is dropped and that is fine, note it). Then generate all six mails once against Mailpit and read them
back, confirming headers.

**Verify the personal-data claim out loud, in the report:** a `Reply-To` of a real address in a mail
that is also delivered to that same person is fine; the risk is the *CC/BCC* case — confirm nothing in
`send` ever puts a third party on a mail. `Mail.withHtml` takes one recipient; assert it in a test so a
future "notify the admins too" change has to delete a test on purpose.

- [ ] **Step 3: Verify, commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/main/resources/templates src/test/kotlin
git commit -m "feat: set reply-to to the counterparty and say where replies actually belong"
```

---

## Task 6: The mobile badge actually refreshes

The web polls `unread-count` every 30 s (`navBar.tsx:41-47`); the Flutter app calls it only when the
notification screen is opened, so on the primary device a booking request is invisible until the user
goes looking. This task adds polling — nothing more.

**Files:**
- Modify: `lib/widget/notification/notification_cubit.dart`, wherever the badge count is held and
  rendered (`grep -rn "getUnreadCount" lib`), `lib/l10n/app_{en,de}.arb` if any string is added
- Test: `test/widget/notification_cubit_test.dart`

**Interfaces:**
- Consumes: `GET /user/notification/unread-count` (existing)
- Produces: a `NotificationBadgeCubit` (or an added method on the existing cubit, whichever the code
  already shapes for) that refreshes on a `Timer.periodic` and is cancelled in `close()`; a refresh
  triggered after the list screen marks everything read, so the badge cannot lag the list.

- [ ] **Step 1: Write the failing tests**

```dart
test('when started, then the count is fetched immediately and again on the interval', …);
test('when closed, then the timer is cancelled', …);          // the leak that would matter
test('when a fetch fails, then the previous count is kept and no exception escapes', …);
test('after marking all read, then the count is refreshed', …);
```
`fakeAsync`/`FakeAsync` or `Timer` injection — the existing test suite has a `test/mock/mock.dart`;
follow whatever it already does rather than adding a package.

- [ ] **Step 2: Run to verify they fail**

`flutter test test/widget/notification_cubit_test.dart` — Expected: FAIL.

- [ ] **Step 3: Implement**

Poll interval `const Duration(seconds: 30)` to match the web. Two details that matter: pause polling
when the app is backgrounded (the widget lifecycle's `AppLifecycleState`, or at minimum stop polling in
`dispose` and restart on screen entry — the pilot does not need a perfect job, it needs no wake-locked
battery drain), and never let a failed poll clear the badge (test 3 covers it, because a 401 during a
token refresh otherwise shows "0" and teaches the user to ignore the badge).

- [ ] **Step 4: Verify and commit**

```bash
flutter analyze && flutter test
git add lib test && git commit -m "feat: poll the unread notification count on mobile"
```

---

## Task 7: Prove the phase end to end

**Files:** Modify `docs/runbook.md`.

- [ ] **Step 1: Full loop with mail observed**

With the compose stack (Postgres, Keycloak, Mailpit), the backend in `%prod`-ish local config pointed at
Mailpit, two probe users: request → owner gets `BOOKING_REQUEST` with the requester's name and both
dates → owner approves → borrower gets `BOOKING_CONFIRMED` with a working deep link → borrower marks
returned → owner gets `BOOKING_RETURNED`. Record for each: subject, `Reply-To`, and the Mailpit message
id. Then kill Mailpit before one of them and show the retry, as in Task 4 Step 5.

- [ ] **Step 2: Both languages once**

Boot with `APP_MAIL_LANGUAGE=de` and send one; confirm the DE templates render and no English leaks
into a DE mail. Paste both.

- [ ] **Step 3: Record, and update the spec**

`docs/runbook.md` Verified section with today's date and the observed ids. In the spec's Finding 1, mark
each bullet resolved or note what remains (push is still out, per the spec).

- [ ] **Step 4: Commit**

```bash
git add docs/runbook.md && git commit -m "docs: record the notification delivery baseline"
```

---

## Out of scope for this phase

- **Push notifications.** The spec keeps polling and excludes push. No FCM/APNs, no certificates, no
  `firebase_messaging`.
- **Nightly booking digest.** Task 4's sweep is where it would live; the spec mentions it, this plan
  does not build it. Add it only with a user asking for the morning summary.
- **In-app sockets / SSE / long polling.** Polling on both clients, as specified.
- **Per-user mail language.** Deferred with a reason and an open question (Task 3 Step 6).
- **The mark-all-read-before-fetch race** on `notification_screen.dart:26-27` — plan 2c.
- **Deliverability engineering**: SPF/DKIM/DMARC, warm-up, bounce handling beyond the recorded error,
  a transactional provider. Task 1's `Reply-To`/`from` choices are what a real provider needs later; the
  pilot sends through whatever SMTP the Phase 5 host provides and observes whether mail arrives at all.

## Known deviations to report

- Dev mode now *really* sends mail — to Mailpit. Anyone who skips the compose stack sees SMTP connection
  errors in the dev log. Say this in the report and in the runbook.
- One mail language per deployment, not per user. Deliberate; recorded as an open question.
- If `io.quarkus.mailer.Mail` cannot set a display name on `Reply-To`, the header is address-only. Report
  which you got.
