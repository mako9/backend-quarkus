# Runbook — running the artifacts that ship

Written 2026-11-23 by Phase 1, Task 8. Every command below was run on this machine that day; *Verified*
holds the decisive lines, *What does not work* is as important as the rest. Phase 5 copies its container
contract from here.

## One-time setup

```bash
cd backend-quarkus
docker compose -f docker/compose.dev.yml up -d
```

PostgreSQL 17 on **5433** and Keycloak 26.5.4 on **8180**, realm `quarkus` imported from
`config/quarkus-realm.json`. 5433 because a host PostgreSQL already owns 5432 on this machine — the
host one holds the developer's own `quarkus` and `quarkus_test` databases.

Realm contents (from the admin API, which needs a master-realm token; `curl -u admin:admin` is refused):

- clients: `backend-service` (plus account, account-console, admin-cli, broker, realm-management,
  security-admin-console)
- realm roles: `user`, `admin`, `confidential` (+ the Keycloak built-ins)
- users: `alice`, `jdoe`, `admin` — passwords equal the username. These come from the realm file, so a
  fresh stack already has logins; no user has to be created for local work.

Tear it down with `docker compose -f docker/compose.dev.yml down` (add `-v` to drop the database volume).

## Backend

```bash
cd backend-quarkus
./mvnw -B -DskipTests package
java -jar target/quarkus-app/quarkus-run.jar
```

No environment is needed for a local run: the default URL is `jdbc:postgresql://localhost:5433/quarkus`, the
database in `docker/compose.dev.yml`. It used to be an unqualified `localhost` — port 5432, the developer's own
postgres — and `quarkus.liquibase.migrate-at-start=true` is not profile-scoped, so an unconfigured run migrated
*that* database. It did, on 2026-11-23. A deployment sets `QUARKUS_DATASOURCE_JDBC_URL`; if it forgets, startup
fails on a refused connection instead of quietly writing somewhere else. Measured after the change, with no
datasource environment at all: boot reached `Profile prod activated`, `POST /api/user/community/` answered 200,
the row was in the 5433 database and absent from the 5432 one, whose `databasechangelog` still had its previous
5 rows and execution timestamp.

One exception to "stay off 5432": **the test suite runs against the host postgres.**
`src/test/resources/application.properties` sets `jdbc:postgresql://localhost/quarkus_test` with
`quarkus.liquibase.clean-at-start=true`, so `./mvnw test` needs a postgres on 5432 and wipes `quarkus_test` on
every run. It never touches the `quarkus` database. CI mirrors this with a `postgres` service container
(`.github/workflows/maven.yml`), which is also why the suite does not use dev services for the datasource.

`quarkus:dev` does not work with the checked-in Maven wrapper (wrapper is 3.8.4, Quarkus 3.33's dev goal
needs ≥ 3.9.0), so the packaged jar is the only way to run this backend locally.

Then:

```bash
curl -s localhost:8080/q/openapi                              # YAML; add -H "Accept: application/json"
curl -s -X POST localhost:8180/realms/quarkus/protocol/openid-connect/token \
  -d "client_id=backend-service&client_secret=secret&grant_type=password&username=alice&password=alice"
```

## Web

```bash
cd frontend-nextjs
cp .env.development .env.local   # the local-stack values; .env.example is for deployments
NEXTAUTH_URL=http://localhost:3000 pnpm build
pnpm start                       # http://localhost:3000
```

`.env.development` is committed and holds the local realm's values (`BACKEND_BASE_URL`, the issuer, the
`backend-service` client and its realm secret). **Do not start from `.env.example` for the local stack** — it
points at `auth.example.org` and `api.example.org`, and only the variables you repeat on the command line get
overridden, so sign-in silently fails against the local realm.

`BACKEND_BASE_URL` is read from the environment, never from `next.config.js`, and it is **inlined into the
browser bundle at build time** — the app calls the API from the browser. Changing the API address for a
deployment therefore means rebuilding, not restarting. Routing those calls through Next instead is an open
question in the spec and is not decided here. Since 2026-11-23 a build that has no `BACKEND_BASE_URL` fails
with a message instead of baking `undefined/user/item/...` into the bundle.

`pnpm build` needs `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` and `NEXTAUTH_SECRET`
present, because collecting page data imports `pages/api/auth/[...nextauth].ts`, which throws without
them. An image build must pass them in even though they are runtime values. `next build` also does **not** read
`.env.development` (it loads `.env`, `.env.production` and `.env.local`), which is why `.github/workflows/node.js.yml`
carries them in an `env` block.

## Mobile

`assets/config/config.yaml` already points at this stack (`backendHost: localhost`, `backendPort: 8080`,
`issuer: http://localhost:8180/realms/quarkus`, redirect `com.flutter-auth-test:/oauth-redirect`), so nothing
in the file needs editing. On an Android emulator `localhost` is the emulator itself, so the phone has to be
told where the stack is:

```bash
adb reverse tcp:8080 tcp:8080 && adb reverse tcp:8180 tcp:8180
```

`adb reverse` rather than `10.0.2.2`, because the token's issuer has to match `issuer` above or the backend
rejects it. The compose stack publishes both ports on `127.0.0.1` only, so a physical device on the LAN cannot
reach it either way; use the emulator or a USB `adb reverse`. It does need a device: see *What does not work*.
(`assets/config/config.yaml` is gitignored by line 56 but tracked anyway, so a fresh checkout does have one.)

**Use the toolchain CI uses: Flutter 3.41.9 / Dart 3.11.5**, which `.github/workflows/dart.yml` now pins. Two
reasons this matters out loud: `/usr/local/bin/dart` on this machine is a standalone Dart 2.17.3 that formats
differently, so `$HOME/development/flutter/bin` has to come first in `PATH`; and the analyzer, not the
formatter, is what drifts — the first CI run on this branch asked for `channel: stable`, got Flutter 3.50.1, and
failed with 51 `deprecated_member_use` infos because `package:flutter/material.dart` is deprecated since v3.47.
The same tree analyzes clean on 3.41.9. Upgrading Flutter therefore means doing the `material_ui` migration
first; it is not a side effect of an unrelated PR.

## What does not work

1. **`GET /q/health` answers 404.** There is no `quarkus-smallrye-health` dependency, so the artifact has
   no health endpoint at all. Phase 5 needs one for container healthchecks, and every deployment tool
   will want it.
2. **`isOwner` is always `false` on `GET /api/user/item/{uuid}`.** `ItemDetailDto.isOwner` defaults to
   false and `ItemController.getItem` builds the DTO from the model alone — it never learns who is asking,
   so it cannot answer. Measured: the item's own owner receives `"isOwner": false`. The mobile item screen
   gates the booking form and the booked-dates list on it, so your own item looks like a stranger's. The
   booking endpoints get this right (`ItemBookingModel.copy(isItemOwner = …)`). Belongs with Phase 2's
   read authorization, which gives those endpoints the requester's identity anyway.
3. **Item and community reads are not gated by community membership** (spec says they are) — unchanged in
   this phase, tracked as Phase 2's read-authorization table.
4. **A web session dies when the access token expires** (5 minutes in this realm): the header still looks
   signed in while every page renders empty. next-auth is not refreshing. Phase 2c.
5. **Email is not sent.** `quarkus.mailer.mock=true` is not profile-scoped, so `%prod` only logs
   "Sending email …". Phase 4.
6. **Mobile was not run for this baseline.** No Android SDK on this machine (`ANDROID_HOME` unset, no
   `sdk.dir` in `android/local.properties`, no `~/Library/Android`), so no APK build and no emulator; iOS
   has Xcode but no signing team. `flutter test` (156 since plan 2c, 149 before) and `flutter analyze` are green, and the wire
   format the app reads is the one verified below over HTTP. Recorded as *not verified*, per the plan.
7. **The scripted login used for this baseline failed.** Keycloak 26 answered the form-encoded credential
   POST with 400 and the login page again. That is a limitation of the scripted client, not of the app:
   the same realm and client authenticated from a real browser on 2026-11-23 (Phase 0's walk). A human
   pass is still owed: load `/`, sign in, list items, open an item, create an item, book one.
8. **Mobile could not open an item at all — fixed 2026-11-24 (plan 2c).** `Item.availability` was declared
   `String?` and `fromJson` assigned the JSON array to it, while every item response carries `availability` as
   an array — `[]` when the item has no window. The parse died with
   `type 'List<Map<String, Object>>' is not a subtype of type 'String?'`. Pre-existing, not from Phase 1: the
   pre-Phase-1 artifact sends an array here too. It is now `List<ItemTimeInterval>`, carrying the four wire
   fields verbatim (see *Verified* for the round trip on the artifact). Still not done on mobile: there is no
   availability editor, so a phone can read and preserve a window but cannot set one. Plan 2d.
9. **Mobile could not create or update an item either — fixed 2026-11-24 (plan 2c).** Nothing in the app ever
   set `Item.availability`, so `toJson()` sent `"availability": null`, and `ItemRequestDto.availability` is a
   non-null `List`. Measured again on the current artifact on 2026-11-24: null → **400**; `[]` → 200, and the
   `GET` answers `[]`; a four-field window → 200 and stored; **the key omitted → 200 and the windows are
   gone**. That last row is why the edit screen now carries the windows across by hand, and why the app always
   sends a list. The pre-Phase-1 artifact 400s the null request too — and 400s the omission as well, so Phase 1
   inherited this and improved one case. The web was never affected: create sends `[]` and the update echoes
   back what the server sent.
10. **The booking × availability interaction is verified in code, not over HTTP.** `bookItem` refuses the item's
    own owner (`ItemService.kt:219`, "Can not book owned item") *before* it looks at the window (`:221`), and
    the seeded realm users expose no usable password — `config/quarkus-realm.json` carries no credential values,
    only the one the token recipe above happens to work with. So "an item with no window is bookable on any
    date" rests on `checkItemAvailability`'s `availability.isNotEmpty() &&` guard (`ItemService.kt:485`) and not
    on a booking that answered 200 for a Friday. A second seeded user with a known password belongs with Phase
    6's demo seed; until then any two-person flow needs a user created by hand in the admin console.

## Verified

2026-11-23, this machine, against the packaged artifacts (not dev mode, not test scope).

Stack: `docker compose -f docker/compose.dev.yml up -d` → keycloak + postgres up; Keycloak log:
`Realm 'quarkus' imported`; `GET /realms/quarkus/.well-known/openid-configuration` resolves with
`"issuer":"http://localhost:8180/realms/quarkus"`.

Backend: `started in 2.248s`, `Profile prod activated`, Liquibase created `public.databasechangelog` and
applied `changelog-1.0/2.0/3.0/3.1.sql` against the 5433 database. `GET /q/openapi` 200.
`POST …/token` as alice → access token.

The point of Task 6, measured on the artifact:

| Request | Answer |
|---|---|
| `GET /api/user/community/?pageNumber=0&pageSize=5` | `isFirstPage: true`, `isLastPage: true` (previously `firstPage`/`lastPage`) |
| `GET /api/user/community/{uuid}` | `isAdmin: true`, `isMember: true` |
| `POST /api/user/item/` with `"isActive": true` → `GET /api/user/item/{uuid}` | `isActive: true` — the **request** direction works, which no test could show before |
| same answer, other fields | `requiresApproval: true`, `availability: []`, `isOwner: false` (see defect 2) |

Web: `pnpm build` compiled and printed 17 routes; `pnpm start` served `/` 200 and its server render called
`/api/user/community` and `/api/user/item/my` against the backend. The built client bundle contains the
inlined backend address (18 chunks) and reads `isActive` (4 chunks), so the browser sends and expects the
same names as the artifact. `/en/about`, `/en/contact` render their content; the pages that require a
session render the logged-out shell, which is correct for an unauthenticated walk.

Mobile: `flutter test` 149 passed, `flutter analyze` clean. Runtime: not verified, reason in *What does
not work* 6.

Re-verified on 2026-11-23 after the review round, all on the packaged artifacts:

- `java -jar target/quarkus-app/quarkus-run.jar` with **no** datasource environment boots and writes to the
  5433 database (`select count(*) … = 1`) and not to 5432 (`= 0`), whose `databasechangelog` is unchanged.
- The availability probes behind defects 8 and 9, including the same requests against the **pre-Phase-1**
  artifact built in a throwaway worktree at `0d9aea6`: that one answers `admin`/`member`/`active`/`owner` and
  returns 400 for both an omitted and a null `availability`. That comparison is what separates what Phase 1
  changed from what it inherited.
- `docker port` reports `127.0.0.1:5433` and `127.0.0.1:8180` after `--force-recreate`, and OIDC discovery and
  `psql -h 127.0.0.1 -p 5433` still work.
- `pnpm build` without `BACKEND_BASE_URL` exits 1 with the config's message; with the CI-shaped environment it
  exits 0 and puts the address in 9 client chunks; `pnpm dev` on `.env.development` alone serves
  `/en/about` 200.
- Backend suite after the datasource change: 149 tests, 0 skipped.

### 2026-11-24, plan 2c: the availability shape, against the shipping artifact

Packaged jar of this tree (backend source is unchanged since Phase 1 verified it — the commits on top are docs),
compose stack, `Profile prod activated` with no datasource environment, token as alice. The bodies are exactly
what the Flutter model now serializes; the item lives in a fresh community.

| Request | Answer |
|---|---|
| `POST /api/user/item/` with `"availability": []` | 200, and the `GET` answers `"availability": []` |
| `PATCH` with `[{"startDayOfWeek":1,"endDayOfWeek":3,"startTimeAt":"09:00Z","endTimeAt":"17:00Z"}]` | 200, and the `GET` answers the same four keys with the day numbers unchanged |
| `PATCH` with the artifact's own answer echoed back | 200, unchanged — this is what makes a verbatim pass-through safe rather than lucky |
| `PATCH` with the `availability` key **omitted** | 200 — and the window is **gone**, the `GET` answers `[]` |
| `PATCH` with `"availability": null` | **400** — what the mobile app sent before the fix |
| `POST /api/user/item/{uuid}/booking` by the item's owner | 400 `Can not book owned item`, before any date or interval check |

Mobile suite with the new model: `flutter test` **156 passed** (149 before), `flutter analyze` clean,
`dart format --set-exit-if-changed` clean. Still no runtime verification — see *What does not work* 6.
