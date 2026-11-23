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
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/quarkus \
  java -jar target/quarkus-app/quarkus-run.jar
```

**Check that URL every time.** `application.properties` has no `%prod` datasource, so without the
override the artifact migrates `localhost:5432` — the developer's own database. That happened on
2026-11-23 while this plan was being written, which is why it is in bold here and in the plan.

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
cp .env.example .env.local       # then fill it in; .env.development has the local-stack values
BACKEND_BASE_URL=http://127.0.0.1:8080/api NEXTAUTH_URL=http://localhost:3000 pnpm build
pnpm start                       # http://localhost:3000
```

`BACKEND_BASE_URL` is read from the environment, never from `next.config.js`, and it is **inlined into the
browser bundle at build time** — the app calls the API from the browser. Changing the API address for a
deployment therefore means rebuilding, not restarting. Routing those calls through Next instead is an open
question in the spec and is not decided here.

`pnpm build` needs `KEYCLOAK_ISSUER`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` and `NEXTAUTH_SECRET`
present, because collecting page data imports `pages/api/auth/[...nextauth].ts`, which throws without
them. An image build must pass them in even though they are runtime values.

## Mobile

`assets/config/config.yaml` already points at this stack (`backendHost: localhost`, `backendPort: 8080`,
`issuer: http://localhost:8180/realms/quarkus`, redirect `com.flutter-auth-test:/oauth-redirect`), so
running the app against a local stack needs no edit. It does need a device: see *What does not work*.

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
