# ComShare — Pilot Readiness Design

**Date:** 2026-11-22
**Status:** **Approved 2026-11-23.** All product decisions closed; questions 4 and 7–10
(infrastructure and privacy detail) are answered during Phases 5 and 6, where they gate.
**Scope:** all three repos — `backend-quarkus`, `frontend-nextjs`, `frontend_flutter`
**Supersedes:** sections of `2026-07-09-platform-roadmap.md` (see *Deviation from the roadmap*)

---

## Intent

ComShare lets neighbours form a community bounded by physical proximity and lend each
other things that sit idle most of the week — a drill, a ladder, a pressure washer, a
folding table. The street owns one; nobody stores it.

Success is not a longer feature list. Success is a real community in which a booking
actually happens: someone posts a pressure washer, a neighbour three doors down asks for
Saturday, the owner sees the request on their phone and says yes, and it comes back.

**No money changes hands, decided 2026-11-23.** Where this platform is described as "renting", it
means borrowing: free, between neighbours, for a period of time.

Everything in this document is in service of that one loop being **usable by people who
were not here when it was built**, on a machine that is not this laptop.

### Working assumption

Approach A was chosen: pilot slice, one real neighbourhood, mobile included. That implies
the pilot is for **real neighbours**, not a recruiter demo. If that is wrong, see
*If the answer is portfolio* at the end — a third of this changes.

---

## Current state (verified 2026-11-22)

| Check | Result |
|---|---|
| `mvnw test -Dtest=ItemServiceTest` | 44 pass |
| `mvnw test` (full) — cold image cache, three runs | 140 run, 1 error, **58 skipped**, ~414 s burned on container-fetch retries |
| `mvnw test` (full) — same command, images cached | **140 run, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS in 50 s** |
| `docker pull quay.io/keycloak/keycloak:26.5.4` | failed three times with `Head "https://quay.io/v2/keycloak/keycloak/manifests/26.5.4": EOF`; **succeeds now** |
| `docker.io/keycloak/keycloak:26.5.4` | pulls first time. Same digest as the quay.io image |
| Keycloak 26.5.4 importing `config/quarkus-realm.json` | **still not verified** — the committed export is from Keycloak 20.0.3. Phase 1 Task 8 |
| `jest --ci` (Next.js) | 180 pass, 43 suites |
| `tsc --noEmit` (Next.js) | **17 errors**, all in test files — never run by CI |
| `pnpm build` (Next.js) | **fails** — see finding 0 |
| `flutter analyze` | no issues |
| `flutter test` | 119 pass |
| `mvnw -DskipTests package` | **BUILD SUCCESS**, `target/quarkus-app` 69 MB — first time it has existed |
| `java -jar target/quarkus-app/quarkus-run.jar` | **starts in 2.4 s, profile `prod`**, runs Liquibase. Then every route, including `/q/health` and `/q/openapi`, answers **500** — see finding 0. *Measured again 2026-11-23 with the realm running: `/q/openapi` answers 200 and `/q/health` answers **404 always** — there is no `quarkus-smallrye-health` dependency, so no health endpoint exists; finding 0's correction is the current statement* |
| Docker daemon | up (29.9.3, Docker Desktop, `HTTP Proxy: http.docker.internal:3128`); pulls work |
| Local PostgreSQL on :5432 | up, host process (not containerised); databases `quarkus` (dev/prod-local) and `quarkus_test` (tests) |

Two corrections to the first draft of this document, and two more since.

**An earlier revision claimed the full suite passed with 143 green tests after a successful
retry.** It did not; that result was reported before it existed. A later revision claimed every
actual run failed identically. That was true when written and is now superseded.

**The 58 skipped tests were an image-pull failure, and it is not permanent.** Docker Desktop
proxies registry traffic through `http.docker.internal:3128`; a cold pull from `quay.io` through it
failed with `EOF`, Quarkus turned that into a test *assumption*, and surefire reported 58 skipped
next to "BUILD SUCCESS". Once the image was cached, the identical command went fully green. So the
environment is not broken — but three things survive that correction, and they are the finding:

- **Nothing fails when the API layer does not run.** A missing daemon, a cold proxy or a laptop with
  Docker switched off produces a green build with 58 tests skipped. That silence is a defect in the
  build, independent of any registry, and it is Phase 1's to close.
- `docker.io/keycloak/keycloak:26.5.4` is the same image by digest and pulls reliably. The test
  profile pins the flaky name.
- Those 58 tests cover every controller, the OpenAPI surface and the policy enforcer. They had run
  **zero** times before 2026-11-23, and everything this document says about the HTTP layer is now
  backed by an actual run rather than by reading.

42 backend endpoints. Community → item → booking → notification runs end to end in all
three stacks **in dev mode**. This is a healthy codebase with a half-finished feature and no
deployment path — but see finding 0 before believing that sentence.

---

### 0. Nothing has ever been built for production

Every green number above comes from dev mode, unit tests, or a test-mode type check. The
production artifacts have not been produced, and where they were attempted they differ from
what was tested.

- **The web frontend does not build.** `pnpm build` fails immediately: `app/styles/globals.css:66`
  has an `@import url(fonts.googleapis.com…)` *after* rules, which Turbopack rejects. Nothing
  else in the build is known until that is fixed, so "add `pnpm build` to CI and fix the type
  errors" is not one task, it is an unbounded one. Next 16 separately warns that `i18n` in
  `next.config.js` is unsupported with the App Router — a migration hazard parked behind the
  CSS error.
- **The backend's production JSON is not the JSON the tests assert.** `jackson-module-kotlin`
  is `<scope>test</scope>` (`pom.xml:113-117`) and is absent from
  `target/quarkus-app/lib/main/`. Without the Kotlin module, Jackson derives names from the
  boolean getters, so `isAdmin`/`isMember`/`isOwner`/`isActive` serialize as
  `admin`/`member`/`owner`/`active`; and `"latitude": null` decodes to `0.0` instead of
  failing. Under `@QuarkusTest` and `quarkus:dev` — where the module *is* present — the same
  code emits `isAdmin`, `isOwner`, `isActive`. **Two different wire formats from one codebase,
  and every test that runs — 82 locally, 140 in CI — only ever sees the one that never ships.**
- **Both clients are written against the production format**, which is why nobody noticed:
  `item.dart:56` reads `json['active']`, `:62` reads `json['owner']`, `community.dart:51` reads
  `json['admin']`; `app/models/item.ts:11` is `active: boolean`. Against a dev backend those
  reads yield `null`. So the earlier note "pick one name" was the wrong instruction — the fix
  is to put `jackson-module-kotlin` back on the runtime classpath *and* rename the keys in both
  clients in the same change, then re-check every `is*` field including `Community.isAdmin` and
  `Community.isMember`.
- **The availability window is mistyped in both clients.** The backend sends
  `List<ItemTimeIntervalDto>`; `item.dart:18` declares `String? availability` and `fromJson`
  assigns the array to it (`:59`), which is a `TypeError` on any item that has a weekly window;
  `item.ts:12` declares `availability: string`. Mobile item detail is therefore not to be
  trusted for any item with declared availability, and the weekly-availability feature cannot be
  exercised from either client.
- **Confirmed against the artifact, not inferred.** `ls target/quarkus-app/lib/main/` lists
  `jackson-databind`, `jackson-core`, `jackson-annotations`, `jackson-datatype-jsr310`,
  `jackson-module-parameter-names` and three Quarkus jars — **no `jackson-module-kotlin`**. The
  two-format claim is now a fact about the shipped artifact.
- **The production jar boots, and Keycloak is a hard dependency of every request.**
  `java -jar target/quarkus-app/quarkus-run.jar` reaches "started in 2.404 s, Profile prod
  activated" against the local database and runs Liquibase. Then `/q/health`, `/q/openapi` and
  `GET /api/item` all return **500** with
  `Could not obtain configuration from server [http://localhost:8180/realms/quarkus/.well-known/uma2-configuration]`.
  The Keycloak **policy enforcer** fetches its configuration per request and has no tolerance for an
  unreachable server, so Keycloak downtime is a total outage, not an outage of authenticated routes,
  and `/q/health` is not a usable container healthcheck while Keycloak is down. Phase 2's decision to
  replace the policy enforcer with `@RolesAllowed` + service-layer checks removes this; until then
  Phase 5's healthcheck design has to live with it.
  - **Corrected 2026-11-23 by Phase 1, Task 8, running the artifact with Keycloak up**
    (`docker/compose.dev.yml`, realm imported): `/q/openapi` answers 200 (YAML unless
    `Accept: application/json`), and `/q/health` answers **404 always** — there is no
    `quarkus-smallrye-health` dependency in `pom.xml`, so the artifact has no health endpoint at all,
    Keycloak up or down. That is worse than "not usable while Keycloak is down": Phase 5 has to *add*
    the dependency before it can write a healthcheck, and the policy-enforcer coupling above still
    stands as the reason a liveness probe must not be a readiness probe.
- **Availability window is mistyped in both clients.** The backend sends
  `List<ItemTimeIntervalDto>`; `item.dart:18` declares `String? availability` and `fromJson`
  assigns the array to it (`:59`), which is a `TypeError` on any item that has a weekly window;
  `item.ts:12` declares `availability: string`, and five test fixtures already pass `[]` — which is
  why they are among the 17 `tsc` errors. Mobile item detail is therefore not to be trusted for any
  item with declared availability.
- **The naming split is not cosmetic on the write side either, and one consequence is severe.**
  Flutter's `Item.toJson()` (`item.dart:71`) sends `'isActive'` — the Kotlin-module name — while
  `fromJson` (`:56`) reads `json['active']` — the bean name. The model contradicts itself, so
  exactly one direction can work against any single backend. Against a prod-shaped backend the write
  is the broken one: `ItemRequestDto.isActive` (`ItemDto.kt:73`) never receives the field,
  Quarkus ignores the unknown key, the Kotlin default `false` applies, and `ItemService:61` lists
  only `isActive = true` items in the community feed. **Read literally, an item created from the
  mobile app is created inactive and does not appear in the feed.** Against a dev backend the read is
  broken instead: `json['active']` is absent, `isActive` is `null`, and the edit form's active switch
  starts off. Both directions are wrong in production-shaped builds; nobody has seen either, because
  development runs the other one.
- **Complete inventory of `is*` fields, so the fix can be atomic.** Response side:
  `PageDto.isFirstPage`/`isLastPage`, `CommunityDetailDto.isAdmin`/`isMember`,
  `ItemDetailDto.isActive`/`isOwner`, `ItemBookingDto.isItemOwner`. Request side:
  `ItemRequestDto.isActive`. Client sides: Flutter reads `firstPage`, `lastPage`
  (`data_page.dart:37-38`), `admin`, `member` (`community.dart:51,56`), `active`, `owner`
  (`item.dart:56,62`); Next reads `itemData.active` at exactly one site (`ownedItem.tsx:38`) and
  declares `Page.isFirstPage` (`pageModel.ts:3-4`) without reading it; Next sends `active:` at
  `ownedItem.tsx:242` and `pages/items/create.tsx:100`. Eight client sites, one backend scope change.
- Consequence for the plan: before any new feature, there must be a **run-the-real-artifact
  baseline** — packaged backend jar plus `pnpm build` output plus a release Flutter build —
  smoke-tested against a Keycloak that is not a test fixture. Until that exists, no claim about
  "works end to end" is checkable.

**Resolved 2026-11-23 (Phase 1, Task 6).** One logical change, three commits: backend `d7f5b77`
(`jackson-module-kotlin` moved from `test` to runtime scope, plus `scripts/check-runtime-classpath.sh`
gating CI on its presence in `target/quarkus-app/lib/main/`), Flutter `0c8a93e`, web `29c1a96`. The wire
format is now the Kotlin-module format everywhere, and this is what moved:

| Name | Was | Now | Where |
|---|---|---|---|
| page flags | `firstPage` / `lastPage` (client read) | `isFirstPage` / `isLastPage` | `lib/model/data_page.dart:38,39`; artifact already answered these under `@QuarkusTest`, and now answers them for real |
| community flags | `admin` / `member` (client read) | `isAdmin` / `isMember` | `lib/model/community.dart:51,56` |
| item active | `active` (client read) | `isActive` | `lib/model/item.dart:60` |
| item owner | `owner` (client read) | `isOwner` | `lib/model/item.dart:66` |
| web field name | `active: boolean` | `isActive: boolean` | `app/models/item.ts:11`, read at `ownedItem.tsx:38`, sent at `ownedItem.tsx:242` and `pages/items/create.tsx:102`, plus four test fixtures |
| backend scope | `<scope>test</scope>` | runtime | `pom.xml`, guarded by the CI gate |

(The Flutter line numbers are post-`dart format`: `f2cfaaf` reflowed 85 files after the rename, so the lines
quoted in the finding above are one or two off from the current file.)

Measured on the packaged artifact rather than inferred (runbook "Verified"): pages answer
`isFirstPage`/`isLastPage`, `GET /api/user/community/{uuid}` answers `isAdmin`/`isMember`, and a
`POST /api/user/item/` carrying `"isActive": true` is stored and returned as `isActive: true` — the
request direction, which no test could demonstrate because tests always had the module on the classpath.
`Item.toJson()` was already right and is untouched; the two Flutter directions are now asserted to agree
by a round-trip test that failed before the model changed. No un-prefixed boolean is written or read by
either client any more (grep over `lib`, `app`, `pages`, `__tests__`; `app/models/community.ts` has no
owner/admin/member field at all, and the web reads no ownership flag from the API — which "owned" page you get
is chosen by a URL query parameter, `pages/index.tsx:114` and `my-area.tsx:38` pass `isOwned`, and
`pages/items/[id].tsx:15` decides whether to fetch booked dates from it. That client-supplied value is Phase 2
read-authorization territory, not a serialization question).

**The `is*` names are one format now; `availability` is still two shapes and is not fixed.** The backend sends
`availability` as an array of `{startDayOfWeek, endDayOfWeek, startTimeAt, endTimeAt}` — measured:
`[{"startDayOfWeek":1,"endDayOfWeek":3,"startTimeAt":"09:00Z","endTimeAt":"17:00Z"}]` — while
`item.dart:19` declares it `String?` and `:63` assigns the array to it, so **mobile throws
`type 'List<dynamic>' is not a subtype of type 'String?'` on every item response** (measured with a throwaway
test, stack at `item.dart:63`). On the write side the mobile app always sends `"availability": null`, which the
artifact answers with **HTTP 400** — as does the pre-Phase-1 artifact, so this is inherited, not caused here;
`[]` or omitting the key answers 200. The web's `ItemTimeInterval` was described as `{startAt, endAt}` by Phase
1 Task 4 and now carries the four real fields (`3540200`); an interval with unknown keys is a 400, not ignored,
which is why the type has to be exact. Full measurements: `runbook.md`, "What does not work" 8 and 9.

Phase 2 therefore starts from one format **for the `is*` booleans** — anything it adds must keep the `is*`
spelling on the wire, and `scripts/check-runtime-classpath.sh` is what stops the module from drifting back to
test scope. Three facts Phase 2 inherits: `isOwner` is answered `false` on `GET /api/user/item/{uuid}` even to
the owner because `ItemDetailDto` is built without the requester (`runbook.md` 2); the request direction is
asserted by tests plus that one live POST, not by an artifact-level test that could fail; and the availability
shape above is plan 2c's, now with a measured repro for both directions.

---

## Findings that constrain the design

### 1. Notifications do not reach anyone

- `application.properties:50` — `quarkus.mailer.mock=true` has no profile prefix, so it
  holds in `%prod`. Nothing is ever sent.
- `%prod.quarkus.mailer.host=localhost` (`:52`) — even unmocked, there is no SMTP server.
- There is no `src/main/resources/templates/` directory and no Qute dependency in `pom.xml`.
  The 2026-07-08 booking spec claims "Qute-templated HTML email"; reality is inline Kotlin
  strings (`NotificationService.kt:77-96`) sent with `Mail.withText(...)` (`:47`).
- Send failures are caught and logged only (`NotificationService.kt:48-49`). No retry, no
  record of what was lost.
- The only other channel is a DB row: the web app polls `unread-count` every 30 s
  (`app/components/navBar.tsx:45`); **the Flutter app never polls at all** —
  `NotificationService` is called when the screen opens, so on mobile an unread badge and a
  booking request both wait for the user to open the app themselves. No push anywhere.

Two more, and these survive a working mailer:

- `requires_approval` defaults to **false** everywhere (`changelog-2.0.sql:6`,
  `ItemDto.kt:44,78`, `ItemModel`, both client models). In `bookItem`
  (`ItemService.kt:222-241`) a non-approval item is confirmed immediately and the single
  event that fires goes to the **requester** — `recipientUuid = if (PENDING) item.userUuid
  else userUuid`. So for an item created with defaults, **the owner receives nothing, ever,
  on any channel.**
- `approveBooking` (`ItemService.kt:249-258`) checks only `status == PENDING`. It never
  re-runs the overlap test, and the overlap query in `checkItemAvailability`
  (`:443-449`) counts `status = 'CONFIRMED'` only, so PENDING requests are invisible to one
  another. Two neighbours can request the same Saturday and the owner can approve **both**.

Consequence: today a booking request cannot inform the owner, and once notification
transport works, an owner can still confirm two people for one drill. For this product that
is not a polish item — a double-booked drill is the failure that ends a lending pilot. Both
belong to Phase 0's definition of done, not to Phase 4.

### 2. Proximity is dormant — and partly unwritable

- `changelog-1.0.sql:34-36` created `radius`, `latitude`, `longitude`; `Community.kt:54-60`
  maps them; both response DTOs return them (`CommunityDto.kt:12-14`, `:33-35`).
- **Nothing ever queries them.** No distance filter, no ordering, no map.
- `CommunityController.kt:131-137` copies `name`, `address`, `radius`, `canBeJoined` on
  PATCH but **drops `latitude`/`longitude`** — coordinates are frozen at creation.
- `CommunityService.updateCommunity` (`:83-95`) does not copy `canBeJoined` either, though
  the controller sets it on the model.
- `User` has no coordinates at all, so "distance from me" has no left-hand side.

The differentiator is one query and one PATCH fix away. It is a defect, not a missing
project.

### 3. Security posture is dev-shaped

- `quarkus.oidc.token.issuer=any` (`application.properties:21`) and
  `quarkus.oidc.tls.verification=none` (`:20`) — issuer is not checked.
- Client secret `secret` is committed in `application.properties:19`, in
  `config/quarkus-realm.json`, and in `frontend_flutter/assets/config/config.yaml` — the last
  ships inside the app binary. (`frontend-nextjs/.env.local` holds the same secret but is
  gitignored, contrary to the first draft of this document — no history rewrite needed there.)
- `frontend_flutter/lib/service/storage_service.dart:16` — `debugPrint` writes the raw
  access/refresh token to the log.
- **No `@Valid` anywhere in the backend**, so every `@Min`/`@Range`/`@Size` on the DTOs
  (including the `-90..90` latitude range) is inert.
- No `@RolesAllowed` either: authorization lives entirely in Keycloak policy-enforcer
  paths in `config/quarkus-realm.json`, outside the code, exercised by one test
  (`PolicyEnforcerTest`) that needs Docker.
- `ItemService.kt:416-421` — upload file extension taken unsanitized from the client
  filename.
- `CommunityController.kt:169,180` — `join` and `leave` are `@GET`. Both change state, so
  a prefetch, a link scanner or a browser preload can join or leave a community.
- Item images are served by `GET /user/item/image/{uuid}` with no ownership or membership
  check (`ItemController.kt:185`) — enumerable by anyone with a token.

**And the one that would have surfaced first:** a person who registers themselves tomorrow
probably cannot use the API at all.

- `JwtUserInfo.kt:17` builds the role list with
  `keycloakSecurityContext.roles.mapNotNull { UserRole.valueOf(it.uppercase()) }`, and
  `UserRole` is exactly `{USER, ADMIN}`. `valueOf` throws `IllegalArgumentException` —
  `mapNotNull` does not absorb it. Any realm role outside that set kills the request, and
  `Controller.getUserUuid()` is upstream of the `try` in `UserService.kt:29`, so the caller
  gets a 500, not a 403.
- Keycloak attaches the realm default role `default-roles-quarkus` to every user. In
  `config/quarkus-realm.json` it is a real realm role (compositing `offline_access`,
  `uma_authorization`, and account-client roles), and there is no `defaultRoles` entry and
  no registration mapper granting `user`. The only three humans in the file
  (`admin@`, `alice@`, `jdoe@`) have `user`/`admin` **hand-assigned**, which is exactly why
  `PolicyEnforcerTest` and the 54 controller tests never see the problem: they only ever
  authenticate as hand-seeded users.
- The policy enforcer gates `/api/user/*` on realm role `user`, which a self-registered user
  also does not have → 403 before the 500 even matters.

Mechanism is read from code and the realm export. The precise token contents for a
newly-registered user are **not yet verified** — that needs a live Keycloak. Image pulls now work
(see *Current state*), and Phase 1's `docker/compose.dev.yml` gives one on 8180, so verifying it is the
first task of Phase 2, ahead of every other auth change, because it decides whether the fix is one
default-role setting or a role-mapping client. Either way, `UserRole.valueOf` needs to stop throwing
on roles it does not model.

**Self-registration is open, and nothing around it is configured.** In the realm:
`registrationAllowed: true`, `verifyEmail: false`, `resetPasswordAllowed: false`,
`bruteForceProtected: false`, `smtpServer: {}`, and `backend-service` has
`redirectUris: ['*']` with `webOrigins: ['*']`. So a public, unverified, unthrottled signup
form for what is meant to be one street; a forgotten password is unfixable without you
logging in as them; and a typo'd email means the G2 notification lands in a stranger's
mailbox with nobody the wiser.

**Identity has no lifecycle.** The local `user` row is created on first authenticated call,
keyed on `mail`, which is `UNIQUE` (`changelog-1.0.sql:20`). Consequences: change your email
in Keycloak and you get a **fresh empty ComShare identity**, leaving your items, bookings and
community memberships stranded behind the old row; and there is no delete endpoint at all —
`UserController` is only `GET`/`PATCH /user/me`. In the schema only `notification` cascades;
`item`, `item_booking` and `user_community_relation` are plain FKs and `community.admin_uuid`
is `NOT NULL`, so a departing admin does not just leave a community, they break it. Phase 6's
"delete my account" is therefore a design decision, not a checkbox.

### 4. There is no deployment story

- `app.storage.item-image.path=./item-image` (`:42`) — images on the app's own filesystem,
  relative to CWD.
- `quarkus.http.cors.origins` (`:37`) — four hardcoded localhost origins.
- `frontend-nextjs/next.config.js` — `BACKEND_BASE_URL: 'http://127.0.0.1:8080/api'` baked
  into `env`, so the `.env.local` value is shadowed in production builds.
- `frontend_flutter/assets/config/config.yaml` — every URL is `localhost`, bundled as an
  asset, with **no `--dart-define` path**, so there is no way to build the app against a
  real server without editing a tracked asset. `config.yaml.example` uses different key
  names and is already out of sync.
- `Dockerfile.jvm`/`.native` exist; there is no compose file, no reverse proxy, no TLS, no
  backup, no production realm.

### 5. Nothing gates on "does it still build"

- Next.js CI runs `pnpm run test-ci` only. No `tsc`, no `next build`. **17** type errors have
  accumulated: 6 in `app/utils/request.test.ts`, 4 in `app/lib/items.test.ts`, 2 in
  `foreignItem.test.tsx`, and one each in the `bookings/[uuid]`, `bookings/index`,
  `notifications` and `ownedItem` tests — plus exactly one genuine application defect,
  `pages/bookings/index.tsx:11`, where `statusColor` has no `RETURNED` branch, so a returned
  booking renders with an undefined colour. The other sixteen are test fixtures that drifted
  from the `Session` and `Item` types. All invisible to a green CI, because babel-jest strips
  types.
- Backend: `PolicyEnforcerTest` boots Keycloak dev services through Testcontainers. With no
  image available it fails after ~413 s and **58 tests are reported skipped, not failed**. So on
  this machine the entire HTTP and authorization layer goes unexercised, and a run that
  looks mostly green tested 82 of 140 cases. Even in CI the authorization evidence is thin:
  `PolicyEnforcerTest` has 3 tests and 2 are skipped, so one test asserts anything at all
  about the policy enforcer that guards every endpoint.
- Flutter CI runs `dart analyze` and `flutter test`; formatting is commented out.
- Mobile login is likely broken on devices: `config.yaml` sets the redirect
  `com.flutter-auth-test:/oauth-redirect`, but `ios/Runner/Info.plist` has no
  `CFBundleURLTypes` and `android/app/build.gradle` has no `appAuthRedirectScheme`
  placeholder (`applicationId` is still `com.example.frontend_flutter`). Not verified on a
  device — verify before assuming.

### 6. The booking work is half-landed, uncommitted, four and a half months old

`docs/superpowers/plans/2026-07-09-complete-booking-experience.md`, 14 tasks:

- **Backend Tasks 1–7: done, uncommitted.** `RETURNED` status, `returned_by`/`returned_at`
  columns, `GET /booking/received`, `GET /{uuid}/booking/dates`,
  `PATCH /booking/{uuid}/return`, `isItemOwner` on every booking response, and their
  service tests. Compiles; `ItemServiceTest` green.
- **Next.js: 1 of 7.** `app/models/booking.ts` updated. No lib functions, no owner tab, no
  `isOwner` fix, no availability calendar.
- **Flutter: 0 of 7.** `BookingCubit.lenderBookings` (`booking_cubit.dart:33`) still never
  populated; `my_area_screen.dart:181` still opens detail with `isOwner: false`, so
  owner-side approve/decline is unreachable on mobile.

### 7. The plan for the product is not in the product

`.gitignore` lists `docs/superpowers/`, so the roadmap and every design doc exist only on
this laptop.

---

## Goals

A pilot is ready when each of these is true without anyone at a keyboard:

- **G1** A neighbour signs up, states an address, and sees the communities within their
  radius ranked by distance; communities outside every radius are not offered for joining
  (an invitation from that community's admin is an explicit exception — see Phase 6).
- **G2** An owner learns about a booking request without opening the app — an email arrives
  within a minute and survives an SMTP failure.
- **G3** An owner can see incoming requests and approve or decline them on **both** clients.
- **G4** A borrower sees what is already booked before requesting dates.
- **G5** The booking loop closes: CONFIRMED → RETURNED is reachable in both clients.
- **G6** No secret, token or credential is committed or bundled.
- **G7** Every DTO annotation actually rejects bad input; no state changes on `GET`.
- **G8** One command starts the whole stack locally; one command deploys it; one command
  backs it up.
- **G9** A green CI means *it compiles and the API tests ran* — on this laptop, not only on
  GitHub's runners.
- **G10** A user can delete their account, and the deployment says plainly what data it
  keeps.
- **G11** Every new community starts with approval required. The owner can turn it off per item;
  the default is not their job to discover.
- **G12** Once a booking is accepted, the two parties can exchange messages about that booking,
  and the agreed handover place and time are visible to both without leaving the booking.
- **G13** Any signed-in user can create a community, becomes its admin, and can invite people
  into it without a third party in the middle.

## Non-goals for the pilot

Deliberately absent. These are the shape of Approach B, which was not chosen:

- **Payments, deposits, pricing, insurance — decided out permanently 2026-11-23.** Lending is
  free; booking stays time-based.
- Ratings, reputation scores, public profile pages.
- Group chat or community-wide discussion. Conversation is scoped to one booking between the
  two parties (Phase 4b); the community announcement board stays out.
- Push notifications (FCM/APNs) — needs developer accounts, store listings, token
  registration. Email covers G2; revisit after real users.
- Real-time delivery (SSE/WebSocket). Polling stays.
- Item search, category filtering, pagination UI. With one neighbourhood's catalogue the
  list is short; fixing the broken pagination controls is in, building search is out.
- Announcements/message boards, item moderation tooling, analytics, admin console.
- Map UI on mobile. A distance-sorted list satisfies G1; `flutter_map` is not yet a
  dependency.

## Anti-goals

- All six roadmap headings delivered shallowly instead of one loop delivered properly.
- A green CI that has not type-checked or run the API tests.
- Any new feature whose notification does not reach the other person.
- Building for a hypothetical thousand-user scale before the tenth user.

---

## Approaches considered

**A — Pilot slice (chosen).** Land the in-flight booking work, fix what blocks real use,
ship proximity, deploy one instance, stop. Trades visible breadth for a working product and
first real users. Chosen because enforced proximity is the only thing distinguishing
ComShare from a Facebook group, and A is the only route that puts it in front of people.

**B — Close the roadmap.** Finish A–F as written: search, pagination, profiles, ratings,
invites, announcements, push, SSE. Ends feature-complete on paper. Rejected: every feature
would land on the mocked mailer, filesystem storage and inert validation from §3–4, and
nothing would ever be used by a neighbour.

**C — Web-first pilot.** Next.js and backend become pilot-ready; Flutter gets only
not-rotting fixes. Cheapest to a live deployment and sidesteps the unverified device-auth
risk. Rejected when A was chosen — which keeps mobile in the pocket, so device auth and
the Flutter booking-owner gap are in scope here.

---

## Design

Seven phases, in order. Each is independently shippable and each leaves the tree committed
and green.

**Implementation plans, written 2026-11-23.** One per phase — the spec spans separable subsystems and a
single plan would be unusable. All in `docs/superpowers/plans/`:

| Plan | Covers | Tasks |
|---|---|---|
| `2026-11-23-phase0-booking-loop.md` | Phase 0 | 14 |
| `2026-11-23-phase1-build-truth.md` | Phase 1 | 10 |
| `2026-11-23-phase2-trust-and-authorization.md` | Phase 2, *Secrets and auth* + read authorization | 12 |
| `2026-11-23-phase3-proximity.md` | Phase 3 (incl. plan 2b's `updateCommunity` coordinate fix) | 10 |
| `2026-11-23-phase4-notifications.md` | Phase 4 | 7 |
| `2026-11-23-phase4b-conversation.md` | Phase 4b | 9 |
| *not yet written* | Plan 2b (backend logic), plan 2c (client defects) | — |
| *not yet written* | Phase 5, Phase 6 | — |

Written in dependency order, and each plan names the phases it depends on. Plans 2b and 2c are
deliberately deferred: they are defect lists whose line numbers move under the phases ahead of them, and
drafting them against code that Phase 1 Task 6 and Phase 2 Tasks 8–10 are about to change would produce
instructions that are wrong on arrival. Phases 5 and 6 are blocked on the *Open questions* they answer
(hosting and RAM, hostname, SMTP relay, off-box backups, account-deletion semantics, web topology) — see
the questions at the end of this document.

### Phase 0 — Land what is in flight, and close the loop

Execute the remaining tasks of the 2026-07-09 plan against the code as it now stands: commit
the backend work (after re-reading it, four months on), then Next.js Tasks 8–11 (lib
functions, owner/requester toggle, `isItemOwner` fix + return button, availability calendar),
then Flutter Tasks 12–14 (`booking_date_range.dart`, lender bookings, owner actions in
detail, calendar, ARB strings).

The Next.js i18n keys for `RETURNED` and the return/approve/decline buttons go in here; they
are already referenced by the plan's code and do not exist yet.

Two fixes belong to this phase rather than Phase 4, because without them the loop the phase
is named for still does not close:

- **The owner must hear about a booking.** In `bookItem`, an auto-confirmed booking currently
  notifies only the requester, so an owner who turns approval off silences themselves. Fire a
  "your item was booked" event to the owner on the auto-confirm path too.
- **Approval becomes the default.** Decided 2026-11-23: approval required unless the owner
  switches it off — the per-item switch stays in both UIs (`item_edit_screen.dart:222-231`,
  `pages/items/create.tsx:179-184`), only the default flips. A changeset sets
  `requires_approval` default `TRUE` (`changelog-2.0.sql:6` set it `FALSE`) and updates existing
  rows; `ItemDto` and `ItemModel` defaults follow, and the two client models stop sending an
  explicit `false` when the user never touched the switch.
- **Approval must not double-book.** `approveBooking` re-runs the overlap check against the item's
  **CONFIRMED** bookings and refuses with `ITEM_RESERVED` when a neighbour was confirmed first. Covered by a
  service test. *Corrected while Phase 0 was executed:* this line asked for overlapping *PENDING* requests to
  block approval too, which makes an item unapprovable — an owner holding two rival requests for the same
  Saturday could accept neither, because the rival they are looking at is itself pending. Pending rivals now
  compete and whoever is approved first takes the slot; the rest are refused on their next approval attempt.

### Phase 1 — Make the repos tell the truth

First, a baseline that runs **the artifacts that actually ship**, because none exists:

- Fix `app/styles/globals.css:66` (move the font `@import` above the rules) until
  `pnpm build` succeeds, and only then count what is behind it.
- Move `jackson-module-kotlin` to runtime scope and land the coordinated key renames from
  Phase 2's serialization bullet, then smoke the packaged `quarkus-app` jar against a Keycloak
  started from the realm file — not from a test fixture.
- **Assert the wire format against the shipped artifact, not against the test classpath.**
  `ItemControllerTest:534` and `:572` assert `isItemOwner` and `CommunityControllerTest:204,223`
  assert `isAdmin`; those four assertions went green on 2026-11-23 for the first time, on a classpath
  that has `jackson-module-kotlin` — which `target/quarkus-app/lib/main/` does not. The controller
  tests therefore describe a format the artifact does not emit. Nothing in either repo can see that
  today. One smoke check that boots `quarkus-run.jar` and reads one `is*` field makes it visible, and
  it is the check that has to go green after the scope fix.
- One CI job per repo that runs `package` / `build` / `release` artifacts, so "green" stops
  meaning "green in dev mode".

Then the gates:

- **Image acquisition: resolved 2026-11-23, and it was not a code defect.** Docker Desktop's daemon
  proxy (`http.docker.internal:3128`) failed cold pulls from `quay.io` with `Head …: EOF`; the same
  image pulls reliably from `docker.io` and the quay.io pull succeeds once the proxy is warm. Pin
  `docker.io/keycloak/keycloak:26.5.4` in `PolicyEnforcerTest.Profile` so the suite does not depend
  on a flaky registry, and verify by removing the cached image and re-running `PolicyEnforcerTest`.
- **Make an unavailable container runtime fail the build.** Today it skips 58 tests and reports
  success. Surefire has no "no skips" switch, so the gate is explicit: after `test`, fail if any
  `target/surefire-reports/*.txt` summary reports a non-zero `Skipped:` count. Green then means
  "the HTTP and authorization layer ran", on any machine.
- `docker/compose.dev.yml`: PostgreSQL + Keycloak with
  `--import-realm config/quarkus-realm.json`, ports as the code and clients already expect —
  noting that a host `postgres` is already listening on 5432, so the compose mapping needs a
  non-conflicting port or the host service stopped. Documented as the one-time local setup. This is
  also what makes the packaged jar answer anything at all: with Keycloak down, every route including
  `/q/health` returns 500 (finding 0). *Corrected while executing: `/q/health` answers 404 whatever Keycloak
  does, because there is no health endpoint at all — Phase 5 has to add `quarkus-smallrye-health` before it can
  write a healthcheck.*
- Backend: keep Testcontainers in CI (runners have working egress). Only if the egress fault
  proves permanent does `mvn test` need to run against the compose stack instead, which takes
  more than `-Dquarkus.keycloak.devservices.enabled=false`: `quarkus.oidc.auth-server-url` is
  set only under `%prod` (`application.properties:23`), so `%test` needs it added too. Do
  **not** rewrite the 54 controller tests onto a test-security profile — a large refactor for
  no pilot benefit. *My call.* Superseded in part: the fault turned out to be a warm-up
  condition, so the "if it proves permanent" branch is closed.
- Next.js CI: add `npx tsc --noEmit` and `pnpm build`. The 17 errors break down as 3 tests that use a
  `getServerSideProps` result without awaiting it, 8 that pass a `{ accessToken }` literal where a
  `Session` is required, 5 that pass `availability: []`, and one real defect
  (`pages/bookings/index.tsx:11` `statusColor` has no `RETURNED` branch and no ending return). The
  five `availability` errors are **the type that is wrong, not the fixtures** — the backend sends an
  array and `pages/items/create.tsx:105` posts one; fixing `item.ts:12` to `Array<string>` is the
  honest repair and it is the client half of the availability defect in finding 0.
- Flutter CI: uncomment `dart format --set-exit-if-changed`.
- Stop gitignoring `docs/superpowers/`.

**Executed 2026-11-23** — `2026-11-23-phase1-build-truth.md`, ten tasks, all done. Three measured corrections,
one gap this phase closed late, and one thing it deliberately left undone:

- The image pin is `quarkus.keycloak.devservices.image-name` in `src/test/resources/application.properties`,
  **not** in `PolicyEnforcerTest.Profile` as written above: the container is started by Quarkus dev services, so
  a `-Dkeycloak.docker.image` system property has no effect on it. Cold pull from docker.io proven by digest;
  the suite is 149 tests, 0 skipped.
- The no-skip gate is `scripts/ci-check-tests.sh` with a floor of **149**, not 140 — Phase 0 grew the suite by
  nine tests after this document was written, and a 140 floor would pass while nine tests went missing. CI runs
  `./mvnw -B clean test`, then the gate.
- **The web CI did not build.** `.github/workflows/node.js.yml` appears in the plan's file structure but no step
  wired it, so the job ran `pnpm run test-ci` and nothing else — no `tsc`, no `next build`, which is exactly how
  the CSS break above survived from 2023 to 2026 with green CI. It now runs `pnpm run typecheck` and
  `pnpm run build` (`cd6f4ee`); the build step needs placeholder Keycloak values in its `env` because collecting
  page data imports `pages/api/auth/[...nextauth].ts`, which throws without them.
- **Still not done, on purpose: nothing in CI boots the artifact.** The wire format is guarded two ways —
  `scripts/check-runtime-classpath.sh` in CI (a classpath fact) and Task 8's live run against `quarkus-run.jar`
  (a wire fact, manual, recorded in `docs/runbook.md`). A CI job that starts the jar and reads one `is*` field is
  the honest version of the bullet above, and it belongs with Phase 5's container job, which is the first place
  something runs the artifact automatically. Flagged here so Phase 5 does not inherit it as a surprise.
- `/q/health` answers 404 because there is **no health endpoint at all**, not 500 because Keycloak was down; the
  bullet above about `/q/health` returning 500 is corrected in finding 0.
- **For Phase 3 and Phase 4b:** `lib/gen_l10n/` is **not** committed — it is generated from `lib/l10n/*.arb`,
  gitignored since 904ff99, and was never tracked. Both plans list `lib/gen_l10n/*` under "Modify", which will
  add nothing. Edit the ARB files and run `flutter gen-l10n`; CI does the same since `baddada`, which is also why
  that step exists.

### Phase 2 — Correctness and trust defects

Mechanical, boring, ordered by blast radius.

**Split into three plans, 2026-11-23.** The list below is one phase but three reviewable units: it mixes
authorization work, backend-logic defects and client defects, and a single plan for all of it would be
unwieldy. **Plan 2** (`docs/superpowers/plans/2026-11-23-phase2-trust-and-authorization.md`) covers
*Secrets and auth* and the read-authorization table, plus `UserService.updateUser` pulled forward
because Phase 3 geocodes what it erases. **Plan 2b** covers the *Backend logic* list. **Plan 2c** covers
the *Flutter* and *Next.js* defect lists. 2b and 2c are written after plan 2 lands, so they are drafted
against the code as it then is; nothing below is dropped, and `updateCommunity`'s coordinate loss is the
one line already pulled into Phase 3 because coordinates that cannot be corrected make that phase
untestable. The serialization/`is*` rename that the Flutter list points at moved **up into Phase 1
Task 6**, where it belongs with the artifact that has to emit it.

**Secrets and auth**
- **First task: make a self-registered user work.** Reproduce the new-user path against a
  live Keycloak (register → token → `GET /api/user/me`), then fix it: grant `user` through the
  realm's default role or a group with a role-mapping mapper, and harden `JwtUserInfo.kt:17`
  so unmapped roles are ignored instead of throwing. Nothing else in this phase is worth doing
  until a person who was not hand-seeded can load the app.
- Every secret read from the environment; delete `keycloak-keystore.jks` and real secrets
  from git history awareness (treat `config/quarkus-realm.json` as a dev-only template).
- `token.issuer` pinned to the pilot realm; `tls.verification` on.
- Flutter: mobile OAuth must not carry a client secret at all — switch the mobile client to
  a **public PKCE client** in the realm and drop `clientSecret` from the bundled asset. Both
  frontends currently authenticate as `backend-service`, a confidential service-account client
  with `redirectUris: ['*']` and `webOrigins: ['*']`; they need their own clients.
- Wire the redirect properly: `CFBundleURLTypes` on iOS, `appAuthRedirectScheme` manifest
  placeholder on Android, and real bundle ids — `PRODUCT_BUNDLE_IDENTIFIER` is still
  `com.example.frontendFlutter` and release builds are signed with **debug keys**
  (`android/app/build.gradle:57-61`). Verify login on a device as part of this.
- Delete the token `debugPrint` (`storage_service.dart:16`).
- `@Valid` on every request DTO body in all four controllers.
- `join`/`leave` become `POST` in the backend and in both clients.
- Item image reads become membership-checked.
- **Read authorization — rule decided 2026-11-23.** Today any valid token can read anything:
  `GET /user/item/{uuid}` returns the owner's street and house number to the whole deployment;
  `/{uuid}/booking`, `/booking/{uuid}` and `/{uuid}/image` have no check;
  `/user/community/{uuid}/member` gives member lists to non-admins; `/user/community/` and
  `/user/item/my` return everything to everyone, and the web home page renders all communities,
  all items and their images for any session. The rule to enforce:

  | What | Who may read |
  |---|---|
  | Community list and detail (name, address, radius, coordinates) | any signed-in user — this is the discovery surface |
  | Item list, item detail, item images, booking dates | **members of that community only** |
  | Booking detail | requester and item owner only |
  | Member list | community admin only |
  | Conversation (Phase 4b) | requester and item owner only |

  Community detail therefore stops returning owner street and house number to outsiders —
  those live on the item, which is already members-only, and the community's own address is
  deliberately public because it is what tells you whether a group is on your street. This is
  enforcement work across six to eight endpoints plus the service layer, not a new feature, and
  both clients lose the ability to deep-link into an item they are not in: the web home page's
  `getAllItemsOfMyCommunities` stays correct, but `/items` no longer wants a public catalogue at
  all (its current redirect to `/my-area` was a half-step toward exactly that).

**Backend logic**
- **Approval's overlap check is not atomic** (found in the Phase 0 review round, after that task shipped).
  `approveBooking` reads "is any CONFIRMED booking in this range" and then writes, with no lock and no
  database constraint between the two, so two approvals arriving together can both read *free* and both
  commit — the double booking the Phase 0 guarantee exists to prevent, reachable from two phones in one
  kitchen. Either a pessimistic lock on the item for the check-and-update, or a Postgres exclusion constraint
  on `(item_uuid, tstimerange(start_at, end_at))` where `status = 'CONFIRMED'` (`btree_gist` for the uuid
  equality); the constraint is the stronger answer because it also holds for paths nobody has thought about.
  Whichever it is, the test is two real threads against the database — nothing in the suite today can see
  this, which is how it survived.
- `CommunityController.updateCommunity` (`:131-137`) never copies `latitude`/`longitude` from
  the request DTO onto the model, while `CommunityService.updateCommunity` (`:82-94`) does
  write both fields back — from the values it loaded. Net effect: coordinates are frozen at
  creation. Fix belongs in the controller. `canBeJoined` is inverted: the controller sets it
  on the model and the service never copies it to the entity, so it can never be changed
  either. Both paths are silently lossy.
- Sanitize upload extensions against an allowlist; store the object under the server-side
  UUID only (`ItemService.kt:416-421` takes the extension straight from the client filename).
- `deleteItem` (`ItemService.kt:115-125`): read file paths before deleting rows, and handle
  items that have bookings (soft-delete, or refuse with a clear error) instead of a raw FK
  failure.
- Deleting an item orients its notification history: `notification.booking_uuid` is
  `ON DELETE SET NULL` while the item name is baked into the stored email body, so the row
  survives with no target and the mail in the archive names an item that no longer exists.
  Decide whether notifications die with the item or keep a resolvable reference.
- `UserService.updateUser` (`:75-78`) assigns `street`, `houseNumber`, `postalCode` and `city`
  unconditionally, so a profile PATCH carrying only a name **erases the stored address** —
  precisely the input Phase 3 is about to geocode. Must be fixed first.
- Replace the catch-all at `UserService.kt:22-31` with the specific failures it expects; as
  written any exception inside `createUser` surfaces as a `CustomForbiddenException`, which is
  how a database problem becomes a 403.
- `PageConfig(pageSize = 1000)` (`ItemController.kt:62`) becomes a real paged query.
- Fix the `Location` header at `ItemController.kt:182`.

**Flutter**
- The `isActive`/`active` mismatch is a symptom of finding 0, not a local bug — handled in **Phase 1
  Task 6**, which moves `jackson-module-kotlin` to runtime scope and renames the eight client read/write
  sites in the same push (plan 1's Review Focus 3 forbids splitting that change).
- **Availability type — plan 2c's first item, with a measured repro for both directions.** `item.dart:19`
  declares `String? availability` and `:63` assigns the JSON array to it, so the mobile app throws
  `type 'List<dynamic>' is not a subtype of type 'String?'` on *every* item response (throwaway test, stack at
  `item.dart:63`); and because nothing in the app ever sets the field, `toJson()` sends `"availability": null`,
  which the artifact rejects with HTTP 400 — measured on the current artifact and on the pre-Phase-1 one, so
  mobile item detail and item create/edit have never worked against a real backend. The real shape, measured on
  the artifact: `[{"startDayOfWeek":1,"endDayOfWeek":3,"startTimeAt":"09:00Z","endTimeAt":"17:00Z"}]`, days
  being `java.time.DayOfWeek` (1 = Monday). The Next.js half is done: `availability: Array<ItemTimeInterval>`
  from Phase 1 Task 4, with `ItemTimeInterval` corrected to the four DTO fields in `3540200` (unknown interval
  keys are a 400, not ignored, so the type must be exact). What is left for 2c is the Flutter model plus the
  fixtures that currently hand it a `String` (`test/service/item_service_test.dart`), and deciding whether the
  app should send no window as `[]` or omit the key — both answer 200 today.
  `frontend-nextjs/app/models/{item,community}.ts` need no further renames beyond Phase 1's.
- Pagination: `custom_paginated_list.dart:71` only renders controls when
  `content.length > pageSize`, which the 10-item server page can never satisfy; and
  previous is disabled on page 2 (`:79`).
- `notification_screen.dart:26-27` marks everything read before the list is fetched.
- `community_edit_screen.dart:156` `int.parse` on an empty radius; `:59-60` shows the
  create title in edit mode.
- `item_edit_screen.dart` never seeds `_selectedDate` from `_item.availableUntil` when opening
  an existing item (`:63` initialises it null, `:80` only sets it on an explicit pick), while
  the button *displays* the stored value (`:272-274`). Saving an edit without touching the
  picker therefore silently clears "available until" (`:305`). Web is the mirror image:
  `pages/items/create.tsx:106` hardcodes `availableUntil: null`, so the web UI cannot set it
  at all.
- `item_edit_screen.dart:303-304` uses the wrong default community index when editing.
- `item_service.dart:64` null cast; `LogoutCubit` leaves tokens behind when `endSession`
  throws; side effect in `build` (`item_detail_screen.dart:114`); `PageController` rebuilt
  per frame (`item_card_view.dart:71`).
- Localize "Logout" (`setting_screen.dart:72`); fix "Elektric device" (`app_en.arb:22`).

**Next.js**
- `/about`, `/contact`, `/settings` are stubs; `/items` index redirects to `/my-area`.

### Phase 3 — Proximity

The feature the platform is named for.

- **Coordinates on `user`.** New columns `latitude`, `longitude`, plus `geocoded_at` — a
  Liquibase changeset, entity, model and DTO.
- **Web has no way to state an address.** Next.js has no profile screen at all — `/settings` is
  a stub and nothing in the web app calls `/user/me`. Only Flutter has an address form. So G1 is
  either mobile-only or Phase 3 grows a small web profile page (address fields, i18n, tests).
  It is small enough that the answer should be *build it*; if not, Phase 0 reorders to put
  Flutter first and the smoke script changes.
- **Coordinates must be sanity-checked, not just stored.** Production writes `0.0` for a null
  payload today, and test/demo data uses 20/−30, so `(0, 0)` is the realistic junk value: a
  guard rejecting `(0, 0)` and an out-of-country bbox as "not geocoded" is required, plus the
  nullable migration above. Decide separately what a *user* with no usable coordinates may do —
  hard enforcement means a neighbour whose address failed to geocode joins nothing, which is why
  the recommendation is: they may browse and request, but the UI says "confirm your address"
  until it geocodes.
- **`latitude`/`longitude` on `community` are `NOT NULL` today** (`changelog-1.0.sql:34-36`),
  which contradicts the fallback below. A changeset making both nullable is a prerequisite, not
  an option.
- **Geocoding** at write time, behind a `GeocodingService` interface so the provider is
  swappable. Default **Nominatim** (no key, respect its usage policy: one request per
  second, identifying User-Agent, cache results); an HTTP provider with an API key is a
  config change, not a code change. Geocode on user profile save and on community
  create/update. Failure must not block the save: store the address, leave coordinates
  null, surface "location not confirmed yet" in the UI, retry on next save.
- **Distance query in SQL, no PostGIS.** Two-step: a bounding-box predicate on `latitude`
  /`longitude` (plain `BETWEEN`, indexable with a new composite index) followed by the
  Haversine expression, filtered by `radius` and ordered by distance. PostGIS would be the
  right answer at scale and the wrong answer for one neighbourhood with a native build to
  keep working. *My call.*
- **Enforcement, not decoration.** Joining a community a user is outside the radius of is
  refused with a distinct error code, for every community that *has* coordinates. A
  community without coordinates stays joinable so the pilot is never locked out by a
  geocoding outage.
- **Endpoints:** `GET /user/community/nearby?lat=&lon=` returning communities plus
  `distanceMeters`; `GET /user/me` gains coordinates.
- **What discovery shows, decided 2026-11-23.** Nearby communities are visible to any signed-in
  user — name, address, radius, distance, and how many members. **Their items are not.** Item
  visibility starts at membership, so the path from "there is a group on my street" to "there is
  a ladder in it" runs exclusively through an invitation from that community's creator. The
  discovery page's action is therefore *see it, then ask someone on your street for an invite* —
  not *join*. Enforcement of this lives in Phase 2's authorization table, not here; Phase 3 only
  has to stop the clients from rendering what they cannot see.
- **Web:** the community list shows distance and sorts by it; the existing Leaflet
  dependency gets a real map panel.
- **Mobile:** the community list shows distance and sorts by it. No map.
- **Accepted risk, stated plainly:** addresses are self-declared. Nothing verifies them.
  The pilot mitigates with visibility — members see each other's street, admins see the
  reported distance — and nothing more. Address proof is a post-pilot decision.
- **Proximity will constrain joining, not creation.** With self-serve communities (Phase 6),
  anyone can set up a competing community two streets over using coordinates nobody checks, and
  invitations bypass the radius entirely. What proximity actually guarantees in the pilot is
  narrower and still worth having: you cannot *silently* end up in a community 40 km away
  through open join, and the distance between you and a community is always visible. Anyone
  expecting the radius to keep the neighbourhood exclusive will be disappointed by the second
  mechanism, not the first.

### Phase 4 — Notifications that deliver

- `quarkus.mailer.mock` scoped to `%dev`/`%test` only; SMTP host/port/user/password from the
  environment.
- Add Qute; one HTML template plus one text template per `NotificationType` (including
  `BOOKING_RETURNED`, which has no copy yet in any language), with a deep link to the
  booking. Send multipart.
- **One language per deployment, not per user.** There is no locale on `user`, and an async event has no
  request to read `Accept-Language` from, so mail renders in `app.mail.language=en|de` for the whole
  instance; template sets for both languages ship together and default to `en`. Per-user language needs a
  column plus an onboarding step — Open question 14.
- **Delivery is recorded.** `notification` gains `email_sent_at` and `email_error`; a
  `@Scheduled` sweep retries unsent mail with a bounded backoff. This replaces the
  catch-and-forget at `NotificationService.kt:48-49` and gives the nightly booking-status
  job from the 2026-07-08 spec a place to live. A mail *suppressed* by Phase 4b's throttle is marked sent
  with reason `throttled`, because a suppressed mail is not a failed one and must never enter the retry
  queue.
- Emails are addressed by name and go to a `noreply@` sender with a **Reply-To of the owning
  user**, so "can I have it Saturday instead?" reaches the person and not a black hole.
- Mobile gets the badge polling the web already has. Today the Flutter app only counts
  unread when the screen is opened, so on the primary device there is no badge until you go
  looking for it.
- Copy for an event whose subject no longer resolves (item deleted) is handled explicitly,
  rather than naming a thing that is gone.
- Polling stays; push stays out.

### Phase 4b — Handover and conversation

Decided 2026-11-23. This is the largest new surface since the booking flow, and it is the one
the pilot's success story actually depends on: a booking that says *when* but never *how you get
it* still ends with two strangers emailing a noreply address.

**Handover, on the item.** Two free-text columns, `handover_place` and `handover_time`
(varchar, ~255 each): "courtyard behind the blue door", "weekdays after 17:00". Free text in the
pilot rather than structured opening hours, because the interesting cases are a gate code and a
neighbour who works nights. Editable in both item forms, shown on item detail **before**
booking — a borrower should know they can physically get the thing before requesting dates — and
pinned on the booking view once accepted. Included in the approval email so it survives never
opening the app.

**Conversation, scoped to one booking.** New `booking_message(uuid, booking_uuid, sender_uuid,
body, created_at, read_at)`. Two endpoints, `GET` and `POST /user/item/booking/{uuid}/messages`,
authorized to exactly the two parties. Snapshot the item owner's uuid onto the booking at
creation time so authorization stays stable if ownership of the item later changes — otherwise a
sold or transferred item silently grants a stranger the old conversation.

Plan revision, 2026-11-23: `read_at` is **dropped** from that sketch. The only read-state consumer is
the badge, which counts `notification` rows, so a per-message flag would be written by no code path and
read by none; it comes back only if read-receipts ever become a feature. The owner snapshot is a real
column (`item_booking.item_owner_uuid`, backfilled then `NOT NULL`), and authorization reads the snapshot
rather than the item's current owner — which also means Phase 2's booking-detail predicate switches to it.

- Opens when the booking reaches `CONFIRMED`; becomes read-only at `RETURNED` or `CANCELLED`.
- No editing, no deleting. Both parties seeing the same immutable thread is the trust property.
- Plain text only, length-capped (~1000 chars), lightly rate-limited. Links are not rendered as
  links. This is an abuse floor, not moderation tooling — see non-goals.
- New `NotificationType.CHAT_MESSAGE`; unread counts fold into the existing `unread-count`
  endpoint and the badge that already reads it.
- **Email is a wake-up call, not a channel.** One email per booking per ten minutes while there
  are unread messages, not one per message, and the copy says plainly that replying to the email
  goes nowhere — reply in the app. Inbound mail is not parsed. Without the throttle a five-message
  exchange becomes five emails and the pilot's first users turn notifications off forever.
- Polling only, both clients, while the booking screen is open. No new transport.
- i18n for every string, EN and DE, on both surfaces.

**Ripples this creates in the rest of this design**, each of which needs the answer it now
depends on:

- `notification.booking_uuid` is `ON DELETE SET NULL`; messages hang off the booking too, so
  deleting an item or a user now destroys free-text personal data belonging to two people.
  *Open question 10* has to cover `booking_message`, not just rows and images.
- The Phase 6 data page must name message content: it is the first genuinely personal,
  author-written data the system keeps, and unlike an item title it cannot be sanitised.
- Message bodies become the first user-authored text rendered back to other users, so both
  clients need escaping verified, not assumed.

Out of scope inside the feature: direct messages outside a booking, group or community threads,
attachments, images, voice, emoji reactions, typing indicators, read receipts beyond the badge,
search, block/report. Reopen any of these only with a neighbour asking for it.

### Phase 5 — Deploy

Six services — PostgreSQL, Keycloak, backend, Next.js, MinIO, Caddy — which is heavy for one
small box: Keycloak alone wants ~1 GB heap and Next.js needs room to build. **Build in CI and
push images**; the box only pulls, which also keeps the native/Graal toolchain off a €5 VM
(there is no `native` profile in `pom.xml` today, so native is not actually configured — that
is a decision, not a default). If the box cannot hold ~4 GB, the two levers are: drop MinIO for
a mounted volume, or move Postgres to a managed instance.

- `compose.prod.yml`: PostgreSQL, Keycloak, backend, Next.js, MinIO, behind Caddy for automatic
  TLS. Keycloak behind a proxy needs proxy-header handling, and the issuer string must match
  what Phase 2 pins `quarkus.oidc.token.issuer` to — so the hostname is decided before the
  backend config is.
- **The realm is code.** One declarative `realm-pilot.json`, imported at container start, that
  carries what the dev realm does not: separate public clients for web and mobile with real
  redirect URIs and PKCE, the `user` role grant from Phase 2, password recovery enabled, and
  whatever the deletion answer in Phase 6 needs. Whether the 20.0.3-era export imports cleanly
  into 26.5.4 is **unverified** — no Keycloak image can be pulled on this machine to try it,
  which is why finding the image-pull path is Phase 1 work and not a Phase 5 surprise. Either
  way the pilot realm should be exported from the deployed version and committed, not
  committed, not hand-edited in the admin console: Keycloak holds the users, so a realm nobody
  can reproduce is a restore nobody can survive. While in there, prefer a realm **access type**
  resource server with audience mapping plus `@RolesAllowed` in Kotlin over path-based policy
  enforcer rules, which currently keep authorization in a JSON file no client can see.
- Images move to S3-compatible storage via **MinIO** in the same compose file, behind
  `app.storage.driver=local|s3`, so local development keeps the filesystem path and the
  deployment stops being stateful-per-container. Chosen over a bare volume because
  container restarts and rebuilds currently orphan uploads.
- Environment-driven config everywhere: `auth-server-url`, CORS origins, mailer, storage,
  and Next.js `BACKEND_BASE_URL` — including removing it from `next.config.js`'s `env` block,
  which is what makes it un-overridable today. **This is a fork, not a chore, and it is
  *Open question 4*.** `app/lib/*.ts` is imported by client components
  (`ownedItem.tsx:91,98,102` for image upload and deletes, `foreignItem.tsx:7` for booking and
  image fetch), not just by `getServerSideProps`. So either (a) the API gets a browser-reachable
  origin — and credentials-mode CORS, the image-endpoint checks and rate limiting become
  pilot-facing work; or (b) every write moves behind Next.js route handlers — and
  "environment-driven config" turns into a refactor of every client component. Removing the
  `env` block without picking one leaves `BACKEND_BASE_URL` undefined in the browser bundle, so
  booking, upload and delete break **only in production**, with a clean local run.
- Flutter gets `--dart-define` for issuer/client/redirect/backend base URL and three
  build configurations (dev / staging / pilot), replacing the localhost asset.
- **Name the toolchain versions in the images, the way CI now does.** `frontend_flutter`'s workflow asked for
  `channel: stable` with no version, so on 2026-11-23 it installed Flutter 3.50.1 — three days old — and
  `flutter analyze` failed with 51 `deprecated_member_use` infos: since v3.47 Flutter deprecates
  `package:flutter/material.dart` (and the generated `Global*Localizations` imports) in favour of
  `package:material_ui`. The workflow is pinned to Flutter 3.41.9 / Dart 3.11.5 now, which is what the code was
  formatted, analyzed and tested with. Consequence for here: **upgrading Flutter is a task with a migration in
  front of it**, not a `flutter upgrade`, and the backend/web/mobile images this phase builds have to name
  versions rather than take `latest` — otherwise the deployment drifts the way the test job just did, except
  against real users.
- Nightly `pg_dump` plus an object-storage sync — **to a destination off the box** (a
  Hetzner Storage Box, B2, rsync.net: whatever the author is willing to pay for and hold
  credentials for), with a **restore drilled once, before the first neighbour signs up**. A dump
  that lives on the machine it protects dies with the disk, and Keycloak's user store rides in
  the same file.
- **Distribution: manual install for the pilot, developer accounts later.** Decided 2026-11-23.
  That still requires HTTPS on a real domain — a LAN address over `http://` is not a pilot,
  because Android blocks cleartext by default (no `usesCleartextTraffic`, no
  `networkSecurityConfig`) and iOS blocks ATS, with no `flutter_insecure_link` dependency to
  work around it. What "manual" buys and costs:
  - **Android**: build a release APK and install over USB or share the file. Needs the
    `appAuthRedirectScheme` placeholder, a real `applicationId` (currently
    `com.example.frontend_flutter`), and a signing config that is not `signingConfigs.debug`
    (`android/app/build.gradle:57-61`). Every reinstall is manual, and every redirect change
    needs a rebuild.
  - **iOS without a paid account**: Xcode with a personal team, which expires after seven days
    and needs a Mac with the source for every reinstall on every device. Realistically that makes
    iOS a developer-only platform for the pilot. Recommend neighbours on Android or on the web,
    with the existing `web/` target as the iPhone path (a home-screen PWA, `callback.html` for the
    OAuth redirect already exists) until a developer account is worth paying for.
- A seeded demo community so a new account lands somewhere with things in it, rather than
  an empty screen.
- A smoke script that walks signup → community → item → booking → approve → email observed.

### Phase 6 — Getting people in

Four things stand between a deployment and a first neighbour:

- **Creation is self-serve; joining is by invitation.** Decided 2026-11-23: the pilot grows by
  each member starting their own community and inviting people in, not by a central admin
  seeding streets. The good news is that half of it already works — `insertCommunity`
  (`CommunityService.kt:73-80`) makes the creator the admin and a member in one transaction, and
  the policy enforcer asks only for a valid user token, so any signed-in user can do it today.
  The work is the invite side:
  - A per-community invite token — an admin mints one, optionally single-use and optionally
    expiring — and a join URL that takes a stranger to signup or login and lands them inside the
    community. Flutter has no deep links at all (`main.dart` registers two named routes and every
    other screen is a `Navigator.push`), so mobile takes a pasted code instead of a link.
  - **An invitation is a vouch, so it bypasses the radius check.** Open join and
    discovery-driven join are radius-enforced (Phase 3); an invite is the admin taking
    responsibility for someone who lives across town, and blocking it would mean an admin cannot
    add their own parent. This exception is why G1 says "explicit exception".
  - `can_be_joined` already defaults `FALSE` (`changelog-1.0.sql:37`), which suits invite-first —
    but it cannot currently be changed at all, because the PATCH path drops it (Phase 2).
    Whether a community may also accept unsolicited join requests becomes a per-community setting
    an admin can actually toggle.
- **Cold start.** Because creation is self-serve, nobody has to wait for an administrator, which
  removes the worst version of this risk. It does not remove the risk: five neighbours on day one
  still find an empty catalogue. Phase 5's seeded demo community is not decoration — decide who
  posts the first dozen real items, and whether the pilot starts with one street or one building.
- **Password recovery.** The realm has `resetPasswordAllowed: false` and an empty `smtpServer`,
  so a forgotten password is a permanent lockout and every one of them becomes a personal favour
  to you. If the pilot is to run "without anyone at a keyboard" (G10), Keycloak's own mailer and
  a reset path in both clients are in scope — that is Phase 4/5 work, listed here so it is not
  mistaken for optional.
- **Legal floor.** `/settings` gains account deletion and a plain-language data page naming
  exactly what is stored: name, email, street address, item list, booking history. ComShare
  stores German street addresses; the pilot cannot pretend otherwise. Drafting the actual privacy
  text is *yours*, not the builder's.
  **Deletion is not a checkbox — it is *Open question 10*.** Users live in two stores: the local
  `user` row and Keycloak, and `getUserByJwtUserInfo` recreates the row from the JWT email on the
  next login, so deleting Postgres rows alone resurrects the account the next time they sign in.
  It also needs decisions the schema currently forecloses: `item`, `item_booking` and
  `user_community_relation` have no cascade, `community.admin_uuid` is `NOT NULL` so a departing
  admin breaks their community, and it is unsettled whether a neighbour's item list and booking
  history vanish for the remaining members or are anonymised and kept. If the answer is "delete
  the Keycloak user too", the backend needs admin credentials — which is in direct tension with
  G6.

---

## Deviation from the roadmap

`2026-07-09-platform-roadmap.md` lists six sub-projects as peers. This design reorders them
on one criterion — does the first neighbour need it:

| Roadmap | Here |
|---|---|
| B — Complete booking experience | Phase 0 |
| A — Geo-proximity | Phase 3 |
| E — Production infrastructure | Phases 1, 5, plus the security half of 2 |
| F — Community features | Invite codes promoted to the primary join path (Phase 6); announcements deferred |
| — (not in the roadmap) | Booking-scoped conversation and handover details (Phase 4b), added 2026-11-23 |
| C — Discovery & search | Mostly deferred; pagination controls in Phase 2, no search |
| D — Trust & reputation | Deferred entirely |

New scope this design adds that the roadmap did not have: the security/correctness defect
list (Phase 2), build gates (Phase 1), notification delivery semantics (Phase 4), invite
links, and the legal floor (Phase 6).

## If the answer is portfolio

Then: Phases 0, 1 and 2 stay as they are (they are exactly what a reviewer looks for).
Phase 5 shrinks to a hosted demo with seeded data and no backups, no TLS ceremony, no
invite system, no legal floor — Phase 6 goes away. Phase 3 stays, because the map is the
demo's centrepiece. Payments and reputation move *in*, because they are more legible in a
README than to a neighbour. Roughly a third less work, and nothing after it is real.

---

## Open questions

Answered 2026-11-23 — recorded here so the phases above stay traceable:

1. **Approval and handover.** Approval required by default, switch stays visible per item
   (Phase 0). Handover place and time become item attributes, and a conversation opens between
   the two parties when a booking is accepted (new Phase 4b). Two consequences carried into the
   open list below: handover is currently specified at item level only (question 12), and chat
   introduces the first user-authored free text the system stores and re-renders (question 10).
2. **Who runs it.** Self-serve: any user creates their own community and invites people in
   (Phase 6). Cold start is still open — see the Phase 6 cold-start bullet, and question 2's
   residue: *who posts the first dozen real items, and is the pilot one street or one building*.
3. **Phones.** Manual install for the pilot, developer accounts later (Phase 5's distribution
   bullet). Consequence to accept knowingly: iOS without a paid account means a seven-day
   provisioning profile and a Mac at each device, so iPhone neighbours realistically use the web
   app while Android is the only true mobile target during the pilot.

11. **Address source.** Answered by implication in 2026-11-23: self-declared, with distance shown
    and invitations as the human check. No address proof in the pilot. Flagged again in Phase 3 as
    a constraint on what proximity can actually promise.

Still open, and the only remaining architectural unknown is 6:

4. **Does the browser call the API directly?** Browser-reachable API origin (more CORS, image
   endpoint checks and rate limiting become user-facing) versus routing all writes through
   Next.js (refactor of every client component). Recommend the browser-reachable origin for the
   pilot, and fixing the image/membership checks in Phase 2 regardless.
5. **Who may read what.** Answered 2026-11-23 — communities visible to all signed-in users,
   items and images members-only, bookings to the two parties, member lists to the admin. Written
   up as an enforcement table in Phase 2.
6. **Money.** Answered 2026-11-23: **no money, permanently.** Lending is free. No price field, no
   deposit, no settlement state after `RETURNED`, no payout story. If this is ever revisited it is
   a new design, not an increment to this one — which is exactly why the answer was needed before
   Phase 0 rather than after it.
7. **Hosting, with RAM.** Willing to run one VPS with Keycloak, Postgres, MinIO and Caddy, at
   roughly €10/month, and does it have ~4 GB? Under that, either MinIO becomes a volume or
   Postgres becomes managed — a different Phase 5.
8. **Domain and SMTP relay, together.** TLS, the issuer URL, the Reply-To and the deep links in
   every email all hang off a hostname, and G2 is unmeetable without a real relay (Mailjet,
   Brevo, Postmark free tier, or an existing provider's server).
9. **Where backups land off the box**, and who holds those credentials. Not optional: Keycloak's
   user store is in the same dump.
10. **What "delete my account" deletes.** Also remove the Keycloak identity (needs admin
    credentials — a G6 tension)? Do the departing neighbour's items disappear for the remaining
    members, and does booking history hard-delete or anonymise? Who becomes admin of a community
    whose admin leaves, given `admin_uuid` is `NOT NULL`?
11. **Address source.** Self-declared with visibility, as Phase 3 assumes, or something stronger
    such as an admin confirming each new member's street by hand? Note that combined with
    open self-registration (Phase 6's invite links being the alternative), this is also the
    answer to "how do wrong people get kept out".

12. **How precise does handover get?** Answered 2026-11-23: item-level place and time only. If a
    particular handover needs agreeing differently, that conversation happens in Phase 4b's thread
    rather than in a second data model. No booking-level confirmation step, no extra state.
13. **What does a stranger see when they find a nearby community?** Answered 2026-11-23: the
    community, never its items. See Phase 3's discovery bullet and Phase 2's table.
14. **Which language does mail go out in?** Answered for the pilot by Phase 4: one language per
    deployment (`app.mail.language=en|de`, both template sets shipped, English the default), because
    `user` has no locale and an async observer has no request to read a language from. Per-user language
    costs a column, a default at first login, and a setting in both clients. Decide before the first
    German-speaking neighbour complains, not before deployment.
15. **Who holds the release-signing keys for Android, and is there an Apple developer account?**
    Raised by Phase 2 Task 5 while wiring the OAuth redirect: `android/app/build.gradle:60` signs release
    builds with `signingConfigs.debug`, there is no upload keystore in the repo and no one is named as its
    custodian; iOS `PRODUCT_BUNDLE_IDENTIFIER` was `com.example.frontendFlutter` and an unsigned personal
    team expires after seven days. The bundle ids and the redirect scheme are now agreed in that plan, but
    the keystore custodian and the Apple decision are not builder decisions. Until they are answered,
    "manual install" (question 3) means "a debug-signed APK that anyone can build", which is fine for a
    pilot and is not fine for anything a neighbour keeps on their phone.
16. **Does the approval default apply to items that already exist?** Phase 0 Task 4 shipped a migration
    that both set the column default to `TRUE` and rewrote every stored `FALSE` to `TRUE`. Only the default
    follows from G11; the rewrite decides something G11 says nothing about — whether an owner who switched
    approval off keeps that choice. In the review round it became its own changeset (`mario:7`, no rollback,
    commented as retroactive) so the decision is at least visible, and it still needs a yes or a no from you.
    Nothing is deployed, so today it changes no real rows; after the pilot starts it either honours owners or
    explains itself to them.

## Decided here, not by you

Flagged so they can be overruled while they are still cheap: no PostGIS (Haversine plus a
bounding box); `@RolesAllowed` in Kotlin over path-based policy enforcer rules; Qute for email
templates rather than inline strings; build images in CI rather than on the box; MinIO for
images rather than a volume; leave the 54 controller tests where they are; a small web profile
page rather than making G1 mobile-only.

From the 2026-11-23 answers, my calls inside the new scope: conversation is scoped to one
booking rather than open direct messages, so there is no way to message a neighbour who owes you
a ladder outside the booking that created the relationship; messages are immutable and
undeletable; email for a new message is throttled to one per booking per ten minutes;
free-text handover fields on the item rather than structured opening hours; **an invitation
bypasses the radius check**, because a rule that stops an admin adding their own parent is a rule
that will be turned off; and during the pilot iPhone users get the web app rather than a
seven-day provisioning profile.

## Left to the builder

Exact endpoint and DTO shapes, Liquibase numbering, test names, ARB and JSON i18n keys, the
Leaflet panel's layout, email copy and language, `GeocodingService` method signatures,
which SMTP library settings, hosting provider, and how the seed data is generated. Phase
boundaries, the defect list's file:line references, and the non-goals are fixed.

## Verification

Per phase: backend `mvn test` (with the compose stack up, so the controller tests actually
run), Next.js `tsc --noEmit && jest --ci && next build`, Flutter `dart format --set-exit-if-changed && flutter analyze && flutter test`.
Phases 3, 5 and 6 additionally need a manual pass on a phone and on a LAN address — device
auth and cross-origin cookies cannot be verified from this laptop.
