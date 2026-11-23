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
   has Xcode but no signing team. `flutter test` (149) and `flutter analyze` are green, and the wire
   format the app reads is the one verified below over HTTP. Recorded as *not verified*, per the plan.
7. **The scripted login used for this baseline failed.** Keycloak 26 answered the form-encoded credential
   POST with 400 and the login page again. That is a limitation of the scripted client, not of the app:
   the same realm and client authenticated from a real browser on 2026-11-23 (Phase 0's walk). A human
   pass is still owed: load `/`, sign in, list items, open an item, create an item, book one.
8. **Mobile cannot open an item at all.** `Item.availability` is declared `String?` (`item.dart:19`) and
   `fromJson` assigns the JSON array to it (`:63`), while every item response carries `availability` as an
   array — `[]` when the item has no window (measured on the artifact). A throwaway test that parsed a real
   response body failed with `type 'List<dynamic>' is not a subtype of type 'String?'` at `item.dart:63`.
   Pre-existing, not from Phase 1: the pre-Phase-1 artifact sends an array here too. Plan 2c's first item.
9. **Mobile cannot create or update an item either.** Nothing in the app ever sets `Item.availability`, so
   `toJson()` sends `"availability": null`, and `ItemRequestDto.availability` is a non-null `List` with a
   default. Measured on the current artifact: `POST /api/user/item/` with `"availability": null` → **HTTP 400**;
   with `[]` → 200; with the key omitted → 200; with an interval whose keys the DTO does not know → 400. The
   pre-Phase-1 artifact returns 400 for the same null request too, so Phase 1's Jackson change did not cause
   this (it actually improved one case: the old artifact returned 400 when the key was *omitted*, the current
   one accepts that). The web is unaffected — create sends `[]` and the update echoes back what the server
   sent. Plan 2c.

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
