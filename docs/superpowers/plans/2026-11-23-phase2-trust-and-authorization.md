# Phase 2 — Trust and Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A person who registers themselves can use the app, and what they can read is decided by the
server instead of by whoever guessed the UUID. Today a self-registered user gets 403 (no `user` role)
or 500 (`UserRole.valueOf` on `default-roles-quarkus`), and any valid token can read any item, any
booking and any member list.

**Architecture:** Realm first (a role a new account actually receives, clients that match how each
frontend is distributed), then request-shape enforcement (`@Valid` + real constraints), then
object-level authorization in the service layer behind a single membership helper. The Keycloak policy
enforcer stays for this phase — it gates `/api/user/*` on the `user` realm role and nothing else, which
is exactly the coarse gate we need once new users have that role. Replacing it with `@RolesAllowed` is
*not* in this plan: with object-level checks in the service layer, the enforcer is no longer load
bearing, and removing it changes the 58 container-backed tests for no pilot benefit.

**Tech Stack:** Quarkus 3.33 / Kotlin / Hibernate-Panache / hibernate-validator / JUnit + RestAssured /
Keycloak 26.5.4 admin REST; Flutter 3.x (`flutter_appauth`, `flutter_secure_storage`); Next.js 16
(next-auth Keycloak provider).

**Spec:** `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — read *Finding 3*, the read
authorization table under *Phase 2*, and *Goals* G6–G9.

**Depends on:** Phases 0 and 1. Phase 1 Task 7 provides `docker/compose.dev.yml` (Keycloak on 8180
with the `quarkus` realm) — every verification step here starts that stack. Phase 1 Task 8 proves the
artifact boots. Do not start this plan before Phase 1 Task 7 is merged.

**Not in this plan:** the *Backend logic* and *Flutter/Next.js defect* lists from spec §Phase 2
(`updateCommunity` coordinate loss, `deleteItem`, `UserService.updateUser` address erasure,
pagination, `item_edit_screen` availability, and the rest). They are independent of authorization and
each is its own reviewable unit; they get plans **2b** and **2c**, written after this one lands, so
they are drafted against the code as it then is. `UserService.updateUser`'s address erasure is the
one item pulled forward, into Task 12, because Phase 3 geocodes what it deletes.

## Global Constraints

- Repos under `/Users/mario/Projekte/Private/`: `backend-quarkus`, `frontend-nextjs`,
  `frontend_flutter`. Commit per repo, never across two.
- Prefix shell steps with
  `export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"` (add `$HOME/development/flutter/bin` for
  Flutter steps).
- Start the stack before any verification:
  `cd backend-quarkus && docker compose -f docker/compose.dev.yml up -d` (Keycloak 8180, Postgres
  5433). Backend runs with
  `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/quarkus java -jar target/quarkus-app/quarkus-run.jar`.
  **Never point a packaged run at 5432** — that is the developer's own database.
- `./mvnw test` must end 0-failures **and** `./scripts/ci-check-tests.sh` must pass. New controller
  tests run against the real Keycloak container, so they are the verification for every authorization
  task here.
- Backend test conventions: `@QuarkusTest`, `@TestProfile(<Controller>.Profile)` via the existing
  `testUtils` base classes, RestAssured, `entityUtil.setup…` fixtures. Copy the shape of an existing
  test in the same file; do not introduce a new test framework or a test-security profile.
- **Realm edits go through the running server and a fresh export**, never by hand-editing
  `config/quarkus-realm.json`. The file is a dev-only template; the export command is in Task 1 Step 6
  and it is the only sanctioned way to change it.
- No secret, token or keystore content in a commit, a log line, or this plan. `config/keycloak-keystore.jks`
  is Task 3's subject.
- Every user-facing string needs `public/locales/{en,de}/common.json` (web) and
  `lib/l10n/app_{en,de}.arb` + `flutter gen-l10n` (mobile), in the task that adds it.
- Conventional Commits, imperative, **no `Co-Authored-By`, no AI attribution.** Never touch
  `.gitconfig`.
- Mobile device verification (Tasks 5–6) needs a real device or simulator. If it cannot be run, write
  `not verified 2026-11-mm-dd, reason: …` in the report — never assume.

## Review Focus

1. **A fix that only works for the three hand-seeded users is not a fix.** `admin@`, `alice@`, `jdoe@`
   have `user`/`admin` assigned by hand, which is why 57 tests never see the self-registration bug.
   Every auth change here is verified with an account created through the registration endpoint or the
   admin API — never with a seeded one. Task 1.
2. **`@Valid` without constraints is theatre.** `@Valid` on a body whose DTO has no `@NotBlank`,
   `@Size` or `@Min` changes nothing at all. Only `CommunityDto.kt` imports `jakarta.validation` today;
   `ItemRequestDto`, `ItemBookingRequestDto`, `UserPatchDto` and `CommunityRequestDto` carry no
   constraints. The task therefore adds constraints *and* `@Valid`, and a test that a bad body is 400.
   Task 7.
3. **A 403 must not become a 200 with an empty body.** The authorization table says who may read.
   "Returns nothing" for a non-member looks like success to a client and hides the check failing.
   Expected: 403 (or 404 where the spec's privacy intent is "does not exist for you"), asserted per
   endpoint. Task 9.
4. **Membership is checked once, in one place.** If `checkMembership` gets copy-pasted into eight
   controller methods, the ninth endpoint forgets it. Expected: one service-layer helper, called by
   the service methods the controllers already delegate to, with the controller tests proving each
   endpoint reaches it. Task 8.
5. **A public client must not carry a secret, and a confidential one must not be shipped.** After
   Task 4, `assets/config/config.yaml` has no `clientSecret`, and the only place a client secret
   exists is the backend's environment and the Next.js server's environment. Task 4.
6. **GET endpoints that change state are a CSRF surface.** `join`/`leave` are `GET` today, so an
   `<img src>` from any page a signed-in user visits can join them to a community. Task 6 changes the
   verb in the backend and both clients in one push; a half-landed version breaks one client silently.

---

## File Structure

**backend-quarkus — modify** `common/JwtUserInfo.kt`, `common/UserRole.kt`,
`src/main/resources/application.properties`, `config/quarkus-realm.json` (by export only),
`api/controller/{Community,Item,ItemBookmark,ItemFavorite,ItemReview,User,Notification}Controller.kt`,
`api/dto/{Item,Community,User}Dto.kt`, `domain/service/{Community,Item,User}Service.kt`,
`.gitignore`. **create** `src/test/kotlin/api/controller/{AuthorizationTest,ItemAccessTest}.kt`,
`docs/security-notes.md`. **delete** `config/keycloak-keystore.jks` (Task 3, after checking refs).

**frontend-nextjs — modify** `app/lib/communities.ts`, `app/lib/items.ts`, `app/lib/bookings.ts`,
`app/utils/request.ts` (only if a call site needs it), the pages and components that call
`joinCommunity`/`leaveCommunity`, `.env.example`, `pages/api/auth/[...nextauth].ts` (client id from
env only — it already is; verify).

**frontend_flutter — modify** `lib/service/community_service.dart`, `lib/service/item_service.dart`,
`lib/service/storage_service.dart`, `assets/config/config.yaml.example` (+ the local
`config.yaml`, which is not committed), `android/app/build.gradle`, `android/app/src/main/AndroidManifest.xml`,
`ios/Runner/Info.plist`, `ios/Runner.xcodeproj/project.pbxproj`, and the cubits/screens that call
join and leave.

---

## Task 1: Make a self-registered user work

`config/quarkus-realm.json` has no `defaultRoles` and no registration mapper granting `user`; the
policy enforcer gates `/api/user/*` on that role. And `JwtUserInfo.kt:17`
(`roles.mapNotNull { UserRole.valueOf(it.uppercase()) }`) throws on `default-roles-quarkus`, which
Keycloak gives every account. This task verifies which of the two bites first, then fixes both.

**Files:**
- Read: a freshly registered user's token
- Modify: the realm (via export), `src/main/kotlin/common/JwtUserInfo.kt`, `src/test/kotlin/common/JwtUserInfoTest.kt` (create)

**Interfaces:**
- Consumes: Phase 1's compose stack, Keycloak admin API (`admin`/`admin`)
- Produces: a realm in which a new account can call `GET /api/user/me`;
  `JwtUserInfo.roles` = the intersection of the token's realm roles with `UserRole`, unknown roles
  ignored. Tasks 2, 9, 10 and every later phase log in as a normal, self-created user.

- [ ] **Step 1: Create a user the way a new neighbour would**

```bash
cd backend-quarkus && docker compose -f docker/compose.dev.yml up -d && sleep 35
TOKEN=$(curl -s -X POST localhost:8180/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli&grant_type=password&username=admin&password=admin" | python3 -c "import json,sys;print(json.load(sys.stdin)['access_token'])")
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  localhost:8180/admin/realms/quarkus/users \
  -d '{"username":"probe1","email":"probe1@example.test","enabled":true,"emailVerified":true,
       "firstName":"Probe","lastName":"One","credentials":[{"type":"password","value":"probe-pass-1","temporary":false}]}'
```
If registration (`POST /realms/quarkus/protocol/openid-connect/register` or the account API) is
reachable, prefer it — it is the real path. Record which one you used.

- [ ] **Step 2: Read the token, and write down the real role list**

```bash
PROBE=$(curl -s -X POST localhost:8180/realms/quarkus/protocol/openid-connect/token \
  -d "client_id=backend-service&client_secret=secret&grant_type=password&username=probe1&password=probe-pass-1" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('access_token',''))")
python3 -c "
import base64,sys
t='$PROBE'.split('.')[1]
p=json.loads(base64.urlsafe_b64decode(t+'==='))
print('realm_access',p['realm_access'])
print('email',p.get('email'),'given_name',p.get('given_name'))
"
curl -s -o /dev/null -w "GET /api/user/me = %{http_code}\n" -H "Authorization: Bearer $PROBE" localhost:8080/api/user/me
```
Expected before the fix: `realm_access.roles` contains `default-roles-quarkus` (and likely
`uma_authorization`) and **not** `user`; the request is 403. Paste both outputs into the report — this
is the measurement the spec called unverified. If `user` *is* present, or the call is 500 rather than
403, the spec's Finding 3 needs correcting before you continue; say so.

- [ ] **Step 3: Write the failing unit test for the role parse**

Create `src/test/kotlin/common/JwtUserInfoTest.kt`. `UserRole` has exactly `{USER, ADMIN}`, so the
requirement is "unknown realm roles are ignored, known ones survive":

```kotlin
class JwtUserInfoTest {
    @Test
    fun `unknown realm roles are ignored and known ones are kept`() {
        val roles = setOf("default-roles-quarkus", "uma_authorization", "user", "confidential")
        val mapped = roles.mapNotNull { UserRole.fromRealmRole(it) }
        assertEquals(listOf(UserRole.USER), mapped)
    }
}
```

- [ ] **Step 4: Add `UserRole.fromRealmRole` and use it**

In `common/UserRole.kt`:
```kotlin
companion object {
    fun fromRealmRole(name: String): UserRole? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
```
Then `JwtUserInfo.kt:17` becomes
`roles = keycloakSecurityContext.roles.mapNotNull(UserRole::fromRealmRole)`.
`entries` needs Kotlin 1.9+ (the project is on 2.3.20 — fine); if `entries` is unavailable on the
enum, use `values()`.

```bash
./mvnw test -Dtest='JwtUserInfoTest'
```
Expected: PASS.

- [ ] **Step 5: Grant `user` at registration, in the server, not in the file**

```bash
USER_ROLE_ID=$(curl -s -H "Authorization: Bearer $TOKEN" localhost:8180/admin/realms/quarkus/roles/user | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  localhost:8180/admin/realms/quarkus/roles-by-id/$USER_ROLE_ID/composites/realm \
  -d '{"name":"default-roles-quarkus","description":"","scopeParamRequired":false}'
```
That makes `user` a composite of the realm default role, so every account — existing and future —
receives it. Verify with Step 2's decode for `probe1` **and** for one brand-new probe user.

> If Keycloak rejects the composite (it refuses cycles and default-role self-composites), the
> fallback is a client-scoped role or a group `members` with a group-mapping mapper set as the realm's
> `defaultGroups`. Try, then record which mechanism the realm ended up using — Phase 6's onboarding
> copies it.

- [ ] **Step 6: Export the realm and commit it**

```bash
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8180/admin/realms/quarkus/export?exportGroups=true" \
  > config/quarkus-realm.json
git diff --stat config/quarkus-realm.json
```
Expected: a diff that contains the new composite/default-role wiring and `keycloakVersion: 26.5.4`.
**Review the diff**: an export of a stack you have poked at can carry probe users. If it does, delete
them through the admin API, re-export, and commit only the role/defaults change.

- [ ] **Step 7: Prove the end-to-end path**

Re-run Step 1–2 for a new user, then
`curl -s -H "Authorization: Bearer $PROBE" localhost:8080/api/user/me`.
Expected: `200` and the probe user's email. Record the status codes before/after.

- [ ] **Step 8: Commit and note it in the spec**

```bash
git add src/main/kotlin/common/UserRole.kt src/main/kotlin/common/JwtUserInfo.kt src/test/kotlin/common/JwtUserInfoTest.kt config/quarkus-realm.json
git commit -m "fix: grant the user role to new accounts and ignore realm roles the app does not model"
```
Append the measured outputs to the spec's Finding 3, replacing the paragraph that says the token
contents are unverified.

---

## Task 2: Pin the issuer and TLS verification for production

`quarkus.oidc.token.issuer=any` and `quarkus.oidc.tls.verification=none`
(`application.properties:20-21`) are set for *all* profiles: any token from any issuer is accepted, and
a MITM Keycloak is trusted. The pilot hostname is not chosen yet, so the pin has to come from the
environment with no default — a deployment that forgets it must fail at boot, not accept foreign
tokens.

**Files:**
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: `KEYCLOAK_ISSUER` (new deployment env var, documented in Task 3's list)
- Produces: prod refuses tokens from another issuer or realm, and refuses to start without an explicit
  issuer. Phase 5's compose file must set `KEYCLOAK_ISSUER` and
  `QUARKUS_OIDC_CREDENTIALS_SECRET`.

- [ ] **Step 1: Replace the two dev-shaped lines with profile-scoped ones**

```properties
# auth — dev/test keep the permissive local settings; prod is strict and env-driven
quarkus.oidc.tls.verification=none
quarkus.oidc.token.issuer=any
%prod.quarkus.oidc.tls.verification=system
%prod.quarkus.oidc.token.issuer=${KEYCLOAK_ISSUER}
```
Leave `%prod.quarkus.oidc.auth-server-url` as it is for now (Phase 5 turns it into an env var too) and
leave the policy-enforcer lines alone.

- [ ] **Step 2: Prove the "no default" actually bites**

```bash
./mvnw -B -DskipTests package
java -jar target/quarkus-app/quarkus-run.jar 2>&1 | grep -iE "KEYCLOAK_ISSUER|Failed to start|issuer" | head -5
```
Expected: the app fails to start (unresolved expression) rather than starting with an empty issuer.
If it starts, Quarkus resolved `${KEYCLOAK_ISSUER}` to `""`; use `${KEYCLOAK_ISSUER:%missing%}`-style
guarding instead — a value that can never match a real issuer — and say which you used.
**Set `KEYCLOAK_ISSUER=http://localhost:8180/realms/quarkus` for the rest of the local steps from now
on**, and say so in the runbook (Task 11).

- [ ] **Step 3: Prove a foreign issuer is refused**

With the stack up and the backend running in prod with
`KEYCLOAK_ISSUER=http://localhost:8180/realms/quarkus`: mint a token from a *different* realm (create
one with `--import-realm` off, or use `master`) and call `GET /api/user/me`.
Expected: 401. Record the code. Then the normal probe token: expected 200.

- [ ] **Step 4: Full suite still green**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
```
Expected: green, 0 skipped. `%test` keeps the permissive settings — that is intentional and is the
reason the container tests are unaffected.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "fix: require a pinned token issuer and system trust in production"
```

---

## Task 3: Nothing secret in the repository

`quarkus.oidc.credentials.secret=secret` (`application.properties:19`) is committed;
`config/keycloak-keystore.jks` is committed; `assets/config/config.yaml.example` ships a
`clientSecret: secret`. The realm file is a dev template and should say so in its own repo.

**Files:**
- Modify: `src/main/resources/application.properties`, `.gitignore`,
  `frontend_flutter/assets/config/config.yaml.example`
- Delete: `config/keycloak-keystore.jks` — **only after Step 1 proves nothing reads it**

**Interfaces:**
- Consumes: `QUARKUS_OIDC_CREDENTIALS_SECRET` (Quarkus' own env-var mapping of the property — no
  custom code)
- Produces: a list of required deployment variables, written into `docs/security-notes.md`, that
  Phase 5 copies verbatim.

- [ ] **Step 1: Check whether the keystore is referenced anywhere**

```bash
cd backend-quarkus && grep -rn "keystore\|jks\|ssl-certificate\|https.port" src/main/resources config/quarkus-realm.json docker 2>/dev/null | grep -v "^Binary"
```
Expected: no reference to `keycloak-keystore.jks`. If something does reference it, stop and record it —
deleting it is then not safe and the task changes to "generate the keystore from an env-supplied
password", which must be written back into the spec before continuing.

- [ ] **Step 2: Make the secret env-only in prod, literal in dev**

```properties
%prod.quarkus.oidc.credentials.secret=${QUARKUS_OIDC_CREDENTIALS_SECRET}
quarkus.oidc.credentials.secret=${QUARKUS_OIDC_CREDENTIALS_SECRET:secret}
```
Dev/test keep working with the realm's literal `secret`; prod fails fast when unset, matching Task 2's
pattern.

- [ ] **Step 3: Remove the keystore and the shipped client secret**

```bash
git rm config/keycloak-keystore.jks
```
In `frontend_flutter/assets/config/config.yaml.example`, delete the `clientSecret` line — Task 4 makes
mobile a public client, so the example must not invite anyone to fill one in. Leave the developer's
local `assets/config/config.yaml` untouched (it is not committed; verify with `git check-ignore`).

- [ ] **Step 4: Write the deployment variable list**

Create `docs/security-notes.md`: every variable prod needs and what breaks without it
(`KEYCLOAK_ISSUER`, `QUARKUS_OIDC_CREDENTIALS_SECRET`, `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_PASSWORD`, and `QUARKUS_HTTP_CORS_ORIGINS` — which Task 10 will add). Below it,
two sentences of git-history awareness: the values `secret` and the keystore contents are recoverable
from history, so **the realm's `backend-service` secret and anything in the keystore are treated as
dev-only and are rotated before any pilot deployment** — the record is what makes the claim true rather
than the deletion.

- [ ] **Step 5: Verify and commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh && ./mvnw -B -DskipTests package && ./scripts/check-runtime-classpath.sh
git add src/main/resources/application.properties .gitignore docs/security-notes.md
git commit -m "fix: read the oidc client secret from the environment and drop the committed keystore"
cd ../frontend_flutter && git add assets/config/config.yaml.example
git commit -m "fix: stop shipping a client secret placeholder in the mobile config template"
```

---

## Task 4: Give each frontend a client that matches how it is distributed

Both frontends authenticate as `backend-service`: a **confidential** client
(`publicClient: false`) whose secret is inside a distributed mobile bundle
(`assets/config/config.yaml`), with `redirectUris: ['*']` and `webOrigins: ['*']`. A secret in a
shipped binary is not a secret; the mobile flow becomes public PKCE.

**Files:**
- Modify (server, via export): `config/quarkus-realm.json`
- Modify: `frontend_flutter/assets/config/config.yaml.example` + the local `config.yaml`; the Flutter
  config loader/model that reads `clientSecret`; `.env.example`

**Interfaces:**
- Consumes: Keycloak admin API
- Produces: `mobile-app` (public, PKCE, redirect `com.flutter-auth-test:/oauth-redirect`),
  `web-app` (confidential, redirect `${NEXTAUTH_URL}/api/auth/callback/keycloak`, webOrigins pinned);
  `Config.clientSecret` gone from the mobile client. Task 5 wires the redirect; Task 6 does not depend
  on this.

- [ ] **Step 1: Find every read of `clientSecret` on mobile**

```bash
cd frontend_flutter && grep -rn "clientSecret" lib assets test
```
Expected: the config model, `assets/config/config.yaml{,.example}`, and the `flutter_appauth`
initialisation. List them; Step 4 removes all of them.

- [ ] **Step 2: Create the two clients on the running server**

```bash
cd ../backend-quarkus && TOKEN=$(curl -s -X POST localhost:8180/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli&grant_type=password&username=admin&password=admin" | python3 -c "import json,sys;print(json.load(sys.stdin)['access_token'])")
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  localhost:8180/admin/realms/quarkus/clients -d '{
    "clientId":"mobile-app","publicClient":true,"standardFlowEnabled":true,
    "directAccessGrantsEnabled":false,"serviceAccountsEnabled":false,
    "redirectUris":["com.flutter-auth-test:/oauth-redirect"],
    "webOrigins":["+"]}'
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  localhost:8180/admin/realms/quarkus/clients -d '{
    "clientId":"web-app","publicClient":false,"standardFlowEnabled":true,
    "directAccessGrantsEnabled":false,
    "redirectUris":["http://localhost:3000/api/auth/callback/keycloak","http://localhost:49430/api/auth/callback/keycloak"],
    "webOrigins":["http://localhost:3000","http://localhost:49430"]}'
```
The `web-app` secret is generated; read it back
(`GET /admin/realms/quarkus/clients/{id}/client-secret`) and put it **only** in the Next.js server's
environment. Never in a file, never in this plan.

Then tighten the API client:
```bash
ID=$(curl -s -H "Authorization: Bearer $TOKEN" "localhost:8180/admin/realms/quarkus/clients?clientId=backend-service" | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
curl -s -X POST -H "Authorization: Bearer $TOKEN" "localhost:8180/admin/realms/quarkus/clients/$ID" \
  -H "Content-Type: application/json" -d "{\"id\":\"$ID\",\"clientId\":\"backend-service\",\"publicClient\":false,\"standardFlowEnabled\":false,\"directAccessGrantsEnabled\":true,\"redirectUris\":[],\"webOrigins\":[]}"
```
`directAccessGrantsEnabled` stays on **only** while local verification needs a password grant; Task 12
turns it off and re-runs the suite. If the suite cannot run without it, record that explicitly — it is
a fact Phase 5 needs about how tests authenticate.

- [ ] **Step 3: Prove PKCE is required for the mobile client**

```bash
curl -s "localhost:8180/realms/quarkus/protocol/openid-connect/auth?client_id=mobile-app&response_type=code&redirect_uri=com.flutter-auth-test:/oauth-redirect&scope=openid" \
  | grep -oiE "sso|login-form|error" | head -3
```
Expected: the login form (a public client is reachable and does not demand a secret). Also confirm a
token request **without** `code_verifier` fails — the exchange cannot be replayed.

- [ ] **Step 4: Drop the secret from the mobile client**

Remove `clientSecret` from `assets/config/config.yaml{,.example}` and from the Dart config model + the
`flutter_appauth` service that passes it. `flutter analyze` will fail on every leftover reference —
that is the checklist.
```bash
export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"; cd ../frontend_flutter
flutter analyze && flutter test
```
Expected: clean. If a test asserted on the secret being sent, update the test: the assertion encoded
the wrong design.

- [ ] **Step 5: Point Next.js at `web-app` and prove login still works**

`pages/api/auth/[...nextauth].ts` already reads `KEYCLOAK_CLIENT_ID`/`_SECRET` from env. Set
`KEYCLOAK_CLIENT_ID=web-app` plus the generated secret in the local environment (and `.env.example`
documents the names, never values), then
`BACKEND_BASE_URL=http://127.0.0.1:8080/api NEXTAUTH_URL=http://localhost:3000 NEXTAUTH_SECRET=dev-only pnpm build && pnpm start`
and log in. Expected: a session, and the callback URL that Keycloak accepts is exactly the one in the
client — a `redirect_uri mismatch` here means the admin POST in Step 2 did not take.

- [ ] **Step 6: Export, review, commit**

Realm export as in Task 1 Step 6. Then:
```bash
cd ../backend-quarkus && git add config/quarkus-realm.json && git commit -m "feat: add per-frontend oauth clients and pin their redirect origins"
cd ../frontend_flutter && git add assets/config/config.yaml.example lib && git commit -m "feat: authenticate the mobile app as a public pkce client"
cd ../frontend-nextjs && git add .env.example && git commit -m "docs: document the web-app oauth client variables"
```

---

## Task 5: Make the mobile redirect actually work

`ios/Runner/Info.plist` has no `CFBundleURLTypes`; `android/app/build.gradle` has no
`appAuthRedirectScheme`; `PRODUCT_BUNDLE_IDENTIFIER` is `com.example.frontendFlutter` and
`applicationId` is `com.example.frontend_flutter`; release builds use `signingConfigs.debug`
(`build.gradle:60`). None of this can survive a real install, and the spec names device auth as the
phase's biggest unverified risk.

**Files:**
- Modify: `frontend_flutter/ios/Runner/Info.plist`, `ios/Runner.xcodeproj/project.pbxproj`,
  `android/app/build.gradle`, `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `mobile-app`'s registered redirect scheme (Task 4)
- Produces: a redirect scheme that is the real application id, in all four places that must agree:
  Keycloak's `redirectUris`, `appAuthRedirectScheme`, iOS URL types, and the `redirectUrl` in
  `assets/config/config.yaml`. Task 6 verifies it on a device.

- [ ] **Step 1: Decide the ids and write them down before editing**

The application id and bundle id are a product decision, not a builder decision. Propose
`app.comshare.mobile` for both, confirm with the user, and record the answer here in the plan file
before proceeding. Do not invent it silently.

- [ ] **Step 2: Android**

In `android/app/build.gradle`: set `applicationId` to the agreed id; add the appauth manifest
placeholder inside `defaultConfig`:
```groovy
manifestPlaceholders = [appAuthRedirectScheme: "app.comshare.mobile"]   // scheme only, no ://
```
Confirm `android/app/src/main/AndroidManifest.xml` needs no manual `RedirectActivity` intent filter —
`flutter_appauth`'s manifest merges one; if it does not, add the filter the package documents and note
that you did.

- [ ] **Step 3: iOS**

Set `PRODUCT_BUNDLE_IDENTIFIER` to the agreed id in all three build configurations in
`project.pbxproj`, and add to `Info.plist`:
```xml
<key>CFBundleURLTypes</key>
<array><dict>
  <key>CFBundleURLName</key><string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
  <key>CFBundleURLSchemes</key><array><string>app.comshare.mobile</string></array>
</dict></array>
```
`keycloak` is a reserved scheme on iOS — do not use it.

- [ ] **Step 4: Make the four places agree, and register the new scheme**

Update `redirectUrl`/`postLogoutRedirectUrl` in `assets/config/config.yaml{,.example}` to
`app.comshare.mobile:/oauth-redirect` / `…:/oauth-logout`, and add both to the `mobile-app` client's
`redirectUris` through the admin API, then re-export the realm.

- [ ] **Step 5: Verify on at least one real target**

`flutter devices`, then run the app against the local stack on a simulator or a connected device and
complete a login as a Task 1-style probe user. Record: device/target, the observed redirect, and the
token's `realm_access.roles`.
**If no device or simulator is available, write `not verified 2026-11-mm-dd, reason: …` and say which
of Steps 2–3 you therefore believe.** Do not mark this task verified from `flutter analyze`.

- [ ] **Step 6: Release signing is a separate decision — record it, do not guess**

`signingConfig signingConfigs.debug` under `buildTypes.release` and a keystore that does not exist in
the repo mean there is no release-signing story. Write the two questions (Android upload keystore
location and who holds it; iOS team id and whether an Apple developer account exists) into the spec's
*Open questions* under Phase 5 and **do not** generate keystores or commit signing config.

- [ ] **Step 7: Commit**

```bash
git add android/app/build.gradle android/app/src/main/AndroidManifest.xml ios/Runner/Info.plist ios/Runner.xcodeproj/project.pbxproj assets/config/config.yaml.example
git commit -m "feat: wire the oauth redirect scheme and real bundle ids for mobile"
```
Realm file in a backend-quarkus commit: `git commit -m "feat: register the mobile redirect scheme"`.

---

## Task 6: Stop logging tokens

`storage_service.dart:16` prints `Writing new accessToken: eyJhb…` through `debugPrint`. In a release
Flutter build `debugPrint` is not automatically stripped, and this lands in the device log where any
adb-connected machine can read it.

**Files:**
- Modify: `frontend_flutter/lib/service/storage_service.dart`

**Interfaces:**
- Produces: no credential material in logs. Reviewed once, applies forever.

- [ ] **Step 1: Find every credential log line, not just the known one**

```bash
cd frontend_flutter && grep -rn "debugPrint\|print(" lib | grep -iE "token|secret|password|authorization"
```
Expected: `storage_service.dart:16` plus any sibling. List all of them.

- [ ] **Step 2: Delete the value, keep the event**

Replace with a line that says something happened and nothing about what:
```dart
debugPrint("Stored ${tokenType.name}");
```
Do the same for the other sites found in Step 1.

- [ ] **Step 3: Prove it**

```bash
export PATH="/usr/local/bin:/opt/homebrew/bin:$HOME/development/flutter/bin:$PATH"
grep -rn "debugPrint\|print(" lib | grep -iE "token|secret|password|authorization"   # expect no matches
flutter analyze && flutter test
```

- [ ] **Step 4: Commit**

```bash
git add lib/service/storage_service.dart && git commit -m "fix: stop logging stored token values"
```

---

## Task 7: Validate request bodies for real

No controller method carries `@Valid`, and only `CommunityDto.kt` imports `jakarta.validation`. A
booking with `endAt` before `startAt`, a one-character item name, and a 10 000-character description
are all accepted today.

**Files:**
- Modify: `api/dto/{Item,Community,User}Dto.kt` and any other request DTO; the four+ controllers'
  body parameters
- Test: `src/test/kotlin/api/controller/ValidationTest.kt` (create)

**Interfaces:**
- Consumes: `quarkus-hibernate-validator` (already an installed feature per the prod boot log)
- Produces: 400 + a violation body for malformed requests. Tasks 8–10 assume a body has passed its
  constraints before reaching the service layer.

- [ ] **Step 1: Inventory the request DTOs**

```bash
cd backend-quarkus && grep -rln "RequestDto\|PatchDto\|Dto(" src/main/kotlin/api/dto | sort
grep -rn "@PATCH\|@POST" src/main/kotlin/api/controller | wc -l
```
List every DTO used as a body parameter and which controller method takes it. That list is the task's
scope; anything not on it is not in scope.

- [ ] **Step 2: Write the failing endpoint tests first**

Create `src/test/kotlin/api/controller/ValidationTest.kt` following the existing controller-test
profile, one case per rule:
```kotlin
@Test fun `when booking with endAt before startAt, then 400`()
@Test fun `when creating an item with a blank name, then 400`()
@Test fun `when creating an item with no category, then 400`()
@Test fun `when creating a community with a negative radius, then 400`()
```
Each posts a body that is well-formed JSON and semantically wrong, and asserts
`statusCode(400)`. Run: `./mvnw test -Dtest=ValidationTest` — Expected: FAIL (all four are 201/204).

- [ ] **Step 3: Add the constraints**

On `ItemRequestDto`: `@field:NotBlank @field:Size(max = 120) val name: String`,
`@field:NotEmpty var categories: List<ItemCategory>`,
`@field:Size(max = 2000) var description: String?`.
On `ItemBookingRequestDto`: `@field:NotNull` both, plus a `@AssertTrue` computed property
`val isChronological: Boolean get() = endAt.isAfter(startAt)` — a cross-field rule cannot be a
per-field annotation.
On `CommunityRequestDto`/`CommunityPatchDto`: `@field:NotBlank @field:Size(max = 120)` name,
`@field:Min(0) @field:Max(5000)` radius, `@field:Size(max = 24)` on each address part.
On `UserPatchDto`: `@field:Size` on every field, `@field:Pattern` free-form is not required.
Kotlin + Bean Validation needs `@field:` use-site targets — without them the annotation lands on the
getter and `@NotNull` on a non-null Kotlin type is silently dropped.

- [ ] **Step 4: Add `@Valid` at the endpoints**

On every body parameter in the controllers, in the existing parameter order:
```kotlin
fun createItem(itemRequestDto: @Valid ItemRequestDto): Response {
```
(`quarkus-rest` + hibernate-validator accept the type-use position; if the codebase prefers a
parameter annotation, `@Valid @BeanParam` is wrong here — the body is not a bean param.)

```bash
./mvnw test -Dtest=ValidationTest && ./mvnw test && ./scripts/ci-check-tests.sh
```
Expected: the four new tests pass, nothing else changed. If an existing controller test now gets a
400, it was posting an invalid body — fix the fixture, do not weaken the constraint.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/api/dto src/main/kotlin/api/controller src/test/kotlin/api/controller/ValidationTest.kt
git commit -m "feat: validate request bodies on every write endpoint"
```

---

## Task 8: One membership check in the service layer

The read-authorization table needs "is this user a member of this community" behind item, image and
booking-date access. `CommunityService` can already answer it, but nothing exposes it as a guard. This
task builds the guard before the endpoints that call it.

**Files:**
- Modify: `src/main/kotlin/domain/service/CommunityService.kt`, `ItemService.kt`
- Test: `src/test/kotlin/domain/service/CommunityServiceTest.kt`

**Interfaces:**
- Consumes: `CommunityMembership` (existing entity/model), `ItemModel.communityUuid`
- Produces:
  `fun CommunityService.isMember(communityUuid: UUID, userUuid: UUID): Boolean`
  `fun CommunityService.requireMembership(communityUuid: UUID, userUuid: UUID)` — throws
  `CustomForbiddenException(code = ErrorCode.Forbidden)` (match the code the codebase already uses for
  403) when not a member;
  `fun CommunityService.requireAdmin(communityUuid: UUID, userUuid: UUID)` — same for the member list.
  Tasks 9–10 call these and nothing else.

- [ ] **Step 1: Write the failing service tests**

In `CommunityServiceTest`, four cases: a member passes; a non-member of an existing community fails; a
pending (not yet approved) membership **fails** — the read table says *members*, not *requesters*;
`requireAdmin` passes for the community's `adminUuid` and fails for a plain member.

- [ ] **Step 2: Run to verify they fail**

`./mvnw test -Dtest=CommunityServiceTest` — Expected: FAIL, unresolved references.

- [ ] **Step 3: Implement all three**

One query, one status test, reused by `requireMembership`/`requireAdmin` — no second membership
helper anywhere else in the codebase. Note the invitation bypass decided in the spec (Phase 6) does
*not* apply here: an invitation creates a real membership row, so `isMember` stays the single rule.

- [ ] **Step 4: Verify, including that nothing else changed**

`./mvnw test && ./scripts/ci-check-tests.sh` — Expected: green.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/domain/service/CommunityService.kt src/test/kotlin/domain/service/CommunityServiceTest.kt
git commit -m "feat: add membership and admin guards to the community service"
```

---

## Task 9: Items and their images are for members only

The table row: *item list, item detail, item images, booking dates → members of that community only*.
Today `GET /user/item/{uuid}` answers any signed-in user with the owner's street and house number.

**Files:**
- Modify: `ItemService.kt` (`getItem`, `getItemImageUuids`, `getItemImage`, and the booking-dates
  service method — which Phase 0 already restricts, so reconcile rather than duplicate),
  `ItemController.kt` (`GET /{uuid}`, `GET /{uuid}/image`, `GET /image/{uuid}`)
- Test: `src/test/kotlin/api/controller/ItemAccessTest.kt` (create), `ItemServiceTest.kt`

**Interfaces:**
- Consumes: `requireMembership` (Task 8)
- Produces: 403 for a non-member on those three reads, and `/items` on the web no longer a catalogue
  (Step 5). Task 10 reuses the same guard for bookings.

- [ ] **Step 1: Write the failing authorization tests**

`ItemAccessTest`, one test per endpoint, using the existing two-communities fixture so that
`otherUser` is in no community with the item:
```kotlin
@Test fun `when a non-member gets an item, then 403`()
@Test fun `when a member gets the same item, then 200`()
@Test fun `when a non-member lists an item image uuid, then 403`()
@Test fun `when a non-member downloads an item image, then 403`()
@Test fun `when a non-member asks for booked dates, then 403`()
@Test fun `when a pending requester asks for any of the above, then 403`()
```
Run: `./mvnw test -Dtest=ItemAccessTest` — Expected: every 403-asserting case FAILs with 200.

- [ ] **Step 2: Put the guard in the service, not the controller**

`getItem` (or the method the controller calls) starts with
`communityService.requireMembership(item.communityUuid, userUuid)`. Same for the image lookups and the
dates. Service-layer placement means a future endpoint cannot forget it by forgetting an annotation.
`getItemsOfMyCommunities` / `/owned` are already scoped by membership and stay as they are.

- [ ] **Step 3: Verify**

`./mvnw test -Dtest=ItemAccessTest,ItemServiceTest && ./mvnw test && ./scripts/ci-check-tests.sh`
Expected: green. Any controller test that fetched a foreign item as a fixture now needs a membership
fixture — add it; do not relax the rule.

- [ ] **Step 4: Web and mobile handle the 403 they now get**

`pages/items/[id].tsx` must render a "you are not a member of this community" state for a 403 rather
than an empty item, and `getItem` in `app/lib/items.ts` must surface the status instead of returning
`null` for every failure. Mobile: `item_service.dart`'s failure path shows the same message.
Keys: `item.notAMember` EN `"This item is only visible to members of its community."` /
DE `"Dieser Gegenstand ist nur für Mitglieder der Gemeinschaft sichtbar."`;
mobile `itemDetailScreen_notAMember`, same wording.
Add a web test that a 403 from `getBookingDates`/`getItem` renders that copy.

- [ ] **Step 5: `/items` stops pretending to be a catalogue**

`pages/items/index.tsx` already redirects to `/my-area`. Confirm the redirect covers the case that
matters: an authenticated non-member cannot reach an item page. Keep the redirect; delete nothing else.

- [ ] **Step 6: Commit per repo**

```bash
# backend-quarkus
git add src/main/kotlin/domain/service/ItemService.kt src/main/kotlin/api/controller/ItemController.kt src/test/kotlin/api/controller/ItemAccessTest.kt src/test/kotlin/domain/service/ItemServiceTest.kt
git commit -m "feat: restrict item, image and booking-date reads to community members"
# frontend-nextjs / frontend_flutter: their own commits, "fix: handle the member-only item response"
```

---

## Task 10: Bookings and member lists see only their own people

Two rows of the table left: *booking detail → requester and item owner only*,
*member list → community admin only*. Both are direct object references by UUID today.

**Files:**
- Modify: `ItemService.getBooking` / `approve|decline|cancel|return` (verify each checks the caller),
  `CommunityService.getCommunityMembers` + `CommunityController.getCommunityMembers`,
  `CommunityController.getCommunityJoinRequests`
- Test: `src/test/kotlin/api/controller/ItemAccessTest.kt`, `CommunityControllerTest.kt`

**Interfaces:**
- Consumes: `requireAdmin` (Task 8)
- Produces: 403 on `/user/item/booking/{uuid}` for a third party, and on
  `/user/community/{uuid}/member` for a non-admin member.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun `when a third party reads a booking, then 403`()
@Test fun `when the requester reads the booking, then 200`()
@Test fun `when the item owner reads the booking, then 200`()
@Test fun `when a plain member lists community members, then 403`()
@Test fun `when the community admin lists community members, then 200`()
@Test fun `when a plain member lists join requests, then 403`()
```
Expected: the four 403 assertions FAIL with 200.

- [ ] **Step 2: Implement in the service layer**

A booking is readable by `booking.userUuid == caller` or by the item's owner
(`item.userUuid == caller`), where `isItemOwner` (Phase 0) already encodes exactly that — reuse the
same predicate so the flag and the authorization can never disagree. Member list and join-request list
take `requireAdmin`.

- [ ] **Step 3: Verify and confirm nothing legitimate broke**

`./mvnw test && ./scripts/ci-check-tests.sh`. Then walk the web booking list and mobile booking detail
as both parties: both still work, and `pages/bookings/[uuid].tsx` shows the not-found state for a
booking that is not yours (the web page already handles `null`).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/domain/service/ItemService.kt src/main/kotlin/domain/service/CommunityService.kt src/main/kotlin/api/controller src/test/kotlin
git commit -m "feat: restrict booking detail to its parties and member lists to the community admin"
```

---

## Task 11: State-changing endpoints stop being GET

`GET /user/community/{uuid}/join` and `GET /user/community/{uuid}/leave` are `GET`
(`CommunityController.kt:169-190`). Any `<img>` tag visits them. Both clients call them as GETs
(`communities.ts:131-132`, `community_service.dart:85,90`).

**Files:**
- Modify: `CommunityController.kt`, `frontend-nextjs/app/lib/communities.ts`,
  `frontend_flutter/lib/service/community_service.dart`, the cubits/screens that call them
- Test: `CommunityControllerTest.kt`

**Interfaces:**
- Produces: `POST /user/community/{uuid}/join`, `POST /user/community/{uuid}/leave`. Phase 6's invite
  join follows this shape.

- [ ] **Step 1: Update the controller tests to the new verb and watch them fail**

Change the join/leave tests to `post(...)`; add one asserting
`get("/user/community/{uuid}/join")` returns **405**. Run: Expected: FAIL (200 from the GET).

- [ ] **Step 2: Change the annotations**

`@POST` on both, `@RolesAllowed` not needed (the enforcer already requires `user`), everything else
about the methods unchanged — no request body.

- [ ] **Step 3: Both clients in the same push**

Next.js: `request(url, session, HttpMethod.Post)`. Flutter: `HttpMethod.post` on both calls. Then grep
for stragglers:
```bash
grep -rn "join\|leave" ../frontend-nextjs/app ../frontend-nextjs/pages ../frontend_flutter/lib --include="*.ts" --include="*.tsx" --include="*.dart" | grep -i "get\|request("
```
Expected: no remaining GET call sites for these two paths.

- [ ] **Step 4: Verify everything**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
cd ../frontend-nextjs && npm run typecheck && npx jest --ci
cd ../frontend_flutter && flutter analyze && flutter test
```
Expected: all green. Then drive join + leave once by hand against the running stack, once per client.

- [ ] **Step 5: Commit per repo**

backend `fix: make community join and leave post requests`;
web `fix: post community join and leave`;
mobile `fix: post community join and leave`.

---

## Task 12: Close the phase, and fix the field Phase 3 depends on

`UserService.updateUser` (`:75-78`) assigns `street`, `houseNumber`, `postalCode`, `city`
unconditionally, so a profile PATCH carrying only a phone number erases the stored address — and
Phase 3 is about to geocode that address. It is the one item from spec §Phase 2's *Backend logic* list
pulled into this plan, because leaving it means Phase 3 builds on sand.

**Files:**
- Modify: `src/main/kotlin/domain/service/UserService.kt`, `src/main/kotlin/api/controller/UserController.kt`
  (`@Valid` — from Task 7, confirm it landed here too)
- Test: `src/test/kotlin/domain/service/UserServiceTest.kt`

**Interfaces:**
- Produces: a `PATCH /user/me` that only changes what it was sent. Phase 3 reads `user.latitude`,
  `user.longitude` written from this address.

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `when patching only the last name, then the stored address survives`() {
    // seed a user with street/houseNumber/postalCode/city, then updateUser with only lastName set
    // assert all four address fields are unchanged
}
```
Run: `./mvnw test -Dtest=UserServiceTest` — Expected: FAIL, the address is now `null`.

- [ ] **Step 2: Assign only what the DTO actually carries**

`UserPatchDto`'s fields are nullable; guard each assignment (`patch.street?.let { user.street = it }`
or the codebase's existing idiom for partial updates — see how `ItemService.updateItem` does it and
match). Do not add a `PATCH` merge library.

- [ ] **Step 3: Verify, then turn off the password grant and see what breaks**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
```
Then Task 4's open question: set `directAccessGrantsEnabled: false` on `backend-service`, re-export, and
run the full suite. Record whether the tests authenticate by password grant — if they stop working,
set it back to `true`, write that dependency into `docs/security-notes.md`, and say so in the report.
That is a fact about the test harness, not a failure.

- [ ] **Step 4: Re-run the whole phase's verification, end to end**

```bash
cd backend-quarkus && docker compose -f docker/compose.dev.yml up -d
./mvnw -B -DskipTests package && java -jar target/quarkus-app/quarkus-run.jar   # with KEYCLOAK_ISSUER + the 5433 URL
```
Then, with a **freshly registered** user (Task 1's path), on the web *and* on mobile if a target is
available: register → log in → create a community → post an item → second user asks to join → admin
approves → second user requests a date → owner approves → owner marks returned. And confirm a third
user gets 403 on that item. Paste the observed status codes into `docs/runbook.md`'s Verified section
with today's date.

- [ ] **Step 5: Update the spec and commit**

Mark in the spec's Finding 3 which parts were confirmed by Task 1's measurement, and where the
read-authorization table now says "enforced". Note the two deferred halves as **Plan 2b (backend
logic)** and **Plan 2c (client defects)** under *Phase 2* so nothing is lost.

```bash
git add docs src/main/kotlin/domain/service/UserService.kt src/test/kotlin/domain/service/UserServiceTest.kt
git commit -m "fix: keep unchanged address fields on a profile patch"
```

---

## Out of scope for this plan

Everything else in spec §Phase 2, deliberately split forward:
- **Plan 2b — backend logic:** `updateCommunity` dropping `latitude`/`longitude`/`canBeJoined`;
  upload-extension allowlist; `deleteItem` order-of-operations and booked-item deletion; notification
  history when an item dies; `UserService.kt:22-31`'s catch-all; `PageConfig(pageSize = 1000)`;
  the `Location` header.
- **Plan 2c — client defects:** Flutter pagination (`custom_paginated_list.dart:71,79`), the
  mark-all-read race, `community_edit_screen` parse and title, `item_edit_screen`'s
  `availableUntil` round-trip and community index, `item_service.dart:64`, `LogoutCubit`, the side
  effect in `build`, the per-frame `PageController`, "Logout" localisation, "Elektric device"; the web
  `/about`, `/contact`, `/settings` stubs.
- Replacing the policy enforcer with `@RolesAllowed` (see *Architecture* — deliberately not done).
- Rate limiting, audit logging, GDPR/account deletion (Phase 6).
- Anything Phase 3+ needs from this plan: the only forward dependency is Task 12's address fix and
  Task 8's `requireMembership`.

## Known deviations to report

- Task 5 may be unverifiable without a device; the plan says how to record that.
- Task 1's role-granting mechanism may end up as a group mapper rather than a composite default role.
  Report which, because Phase 6's onboarding reuses it.
- Task 4's `directAccessGrantsEnabled` state at the end of this plan is an open question with a test
  attached (Task 12 Step 3). Report the answer, whichever way it goes.
