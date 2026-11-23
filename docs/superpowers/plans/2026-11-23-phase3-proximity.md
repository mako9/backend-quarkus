# Phase 3 — Proximity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "nearby" mean something. The `radius`, `latitude` and `longitude` columns have existed
since `changelog-1.0.sql:34-36`, nothing has ever queried them, and `CommunityController`'s PATCH drops
the coordinates so they cannot even be corrected after creation. After this phase a signed-in user sees
the communities around them sorted by distance, cannot join-request one that is outside its own radius,
and the distance is always visible.

**Architecture:** Coordinates are derived from an address, never typed: a `GeocodingService` interface
with a Nominatim implementation behind it, called on profile save and community create/update, storing
`null` on failure so a geocoding outage degrades a label instead of losing a save. Distance is two-step
SQL — an indexable bounding box, then Haversine — with no PostGIS. Enforcement is one guard in
`joinCommunity`; discovery stays public, items stay members-only (Phase 2's table).

**Tech Stack:** Quarkus 3.33 / Kotlin / Hibernate-Panache named queries / Liquibase; `java.net.http.HttpClient`
for geocoding (the pom has no REST-client extension — do not add one for a single call); Next.js 16
Pages Router + the existing Leaflet dependency; Flutter `flutter_bloc`.

**Spec:** `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — read *Phase 3* in full, plus
*Finding 2* and the read-authorization table in *Phase 2*.

**Depends on:**
- **Phase 1** (Task 6 wire format, Task 7 local stack) and **Phase 2** (Tasks 8–10 membership guards,
  Task 12's `UserService.updateUser` partial-update fix — without that one, saving a phone number
  erases the address this phase geocodes).
- **Plan 2b's `updateCommunity` fix is pulled forward into Task 4 here**, because coordinates that can
  never be corrected make every other task in this phase untestable after a typo. Record the pull-forward
  against the spec's Phase 2 split.

## Global Constraints

- Repos under `/Users/mario/Projekte/Private/`. Commit per repo, never across two.
- `export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"` before shell steps (add
  `$HOME/development/flutter/bin` for Flutter).
- Verification stack: `docker compose -f docker/compose.dev.yml up -d`, backend packaged and run with
  `KEYCLOAK_ISSUER` and `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/quarkus`. **Never
  5432.** Users for testing are created through the admin API as in Phase 2 Task 1, never the three
  hand-seeded ones.
- `./mvnw test && ./scripts/ci-check-tests.sh` after every backend task. New code gets new tests in the
  same task; the existing conventions apply (`@QuarkusTest`, existing `@TestProfile`s, `entityUtil`).
- Liquibase changeset ids continue from the last one in the tree (`mario:7` after Phase 0 Task 4, which now
  takes two: one for the column default, one for the retroactive data change). Use
  `mario:8`, `mario:9`, … in order; one changeset per migration concern; `--rollback` on every one.
- **Never send an address to a geocoder from a test.** `GeocodingService` has a seam; unit tests use a
  fake, and any local run against real Nominatim is one request per second with an identifying
  User-Agent, per its usage policy. If the policy is unclear to you, stop and re-read the linked
  terms before making a request.
- `flutter analyze` and `flutter test` green; new strings go in both ARB files + `flutter gen-l10n`.
  Web strings go in `public/locales/{en,de}/common.json`. No user-facing string without both.
- `npm run typecheck` reports 0 and `npx jest --ci` is green after every web task.
- Conventional Commits, imperative, **no `Co-Authored-By`, no AI attribution.** Never touch `.gitconfig`.
- No money, no ratings, no search. This phase adds distance and nothing else.

## Review Focus

1. **`0.0` is not a coordinate, it is a missing one.** Production writes `0.0` for an absent payload and
   the demo data uses lat 20 / lon −30. Every read of a coordinate in this phase treats `(0, 0)`, a
   null, or a point outside the configured country bounding box as *"not geocoded"*, never as a place.
   A distance computed from `(0,0)` is 5 000 km and it will sort a neighbourhood to the bottom. Tasks 1, 5, 6.
2. **A geocoding outage must not lose a save.** The save commits with `latitude = null` and the UI says
   "location not confirmed". If a geocoder timeout ever turns into a 500 on profile save, the task is
   wrong, not the test. Task 2.
3. **Enforcement must have the documented escape hatch.** A community with no usable coordinates stays
   join-requestable; a community *with* coordinates and the user outside its radius is refused with a
   distinct error code. Both directions are pinned by tests, or the pilot locks people out the first
   time Nominatim is down. Task 6.
4. **Discovery shows communities, never their items.** The nearby endpoint returns name, address,
   radius, distance and member count — no item data, no member identities. Phase 2 built the guard;
   this phase must not leak around it by adding a convenient field. Task 5, Task 7.
5. **Bounding box before Haversine, in that order.** Filtering by the Haversine expression alone is a
   full scan; the bounding-box predicate must be a separate, indexable `BETWEEN` on the two columns.
   Task 5 checks the plan, not just the result.
6. **`app/components/map.tsx` is dead and broken.** No file imports it, and its signature
   (`const Map = (communityMarker: CommunityMarker) => …`) receives props as the first argument without
   destructuring, so `communityMarker.latitude` is `undefined` and `L.map('map')` targets an id that is
   not in any page. Wiring it means fixing all three. Task 8.

---

## File Structure

**backend-quarkus — create** `src/main/resources/db/changelog/changelog-3.2.sql` (nullable community
coordinates), `changelog-3.3.sql` (user coordinates + index),
`src/main/kotlin/domain/service/GeocodingService.kt`,
`src/main/kotlin/infrastructure/geocoding/{NominatimGeocoder,GeocodingConfig}.kt`,
`src/main/kotlin/api/dto/NearbyCommunityDto.kt`,
`src/test/kotlin/domain/service/GeocodingServiceTest.kt`,
`src/test/kotlin/domain/service/NearbyCommunityTest.kt`,
`src/test/kotlin/api/controller/ProximityControllerTest.kt`.
**modify** `infrastructure/entity/{Community,User}.kt`, `domain/model/{CommunityModel,UserModel}.kt`,
`api/dto/CommunityDto.kt`, `domain/service/{CommunityService,UserService}.kt`,
`api/controller/CommunityController.kt`, `api/controller/UserController.kt`,
`src/main/resources/application.properties`, and the matching tests.

**frontend-nextjs — create** `pages/profile.tsx`, `app/lib/profile.ts` (+ tests),
`__tests__/pages/profile.test.tsx`. **modify** `app/components/map.tsx`, `app/models/community.ts`,
`app/lib/communities.ts`, the page that lists communities, `pages/communities/[id].tsx`,
`components/navBar.tsx` (link to profile), `public/locales/{en,de}/common.json`.

**frontend_flutter — modify** `lib/model/{community,user}.dart`, `lib/service/{community,user}_service.dart`,
`lib/widget/community/community_screen.dart`, `lib/widget/my_area/my_area_screen.dart`, the profile
form that already exists, `lib/l10n/app_{en,de}.arb`, `lib/gen_l10n/*`, and the affected tests.

---

## Task 1: Community coordinates become nullable and honest

`latitude`/`longitude` are `DOUBLE PRECISION NOT NULL` (`changelog-1.0.sql:35-36`) with Kotlin
`Double` (non-null), so "we do not know where this is" is not representable and the code writes `0.0`.
Phase 1 Task 6 put `jackson-module-kotlin` on the runtime classpath, which is what finally makes an
absent JSON field a failure rather than a silent `0.0` — this task makes absent representable.

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.2.sql`
- Modify: `infrastructure/entity/Community.kt:57-60`, `domain/model/CommunityModel.kt:15-16`,
  `api/dto/CommunityDto.kt` (both response DTOs and `CommunityRequestDto:76-79`)
- Create: `src/main/kotlin/common/Coordinates.kt`
- Test: `src/test/kotlin/common/CoordinatesTest.kt`, `CommunityServiceTest.kt`

**Interfaces:**
- Produces: `Community.latitude: Double?`, `Community.longitude: Double?`;
  `fun Coordinates.isUsable(latitude: Double?, longitude: Double?): Boolean` — false for null, for
  `(0.0, 0.0)`, and for anything outside `app.geocoding.bbox.*` (Task 2 adds those properties; give
  them defaults in this task so it compiles);
  `CommunityDetailDto.latitude/longitude: Double?`. Tasks 2, 5, 6 all call `isUsable` and nothing else.

- [ ] **Step 1: Write the failing predicate test**

```kotlin
class CoordinatesTest {
    @Test fun `null is not usable`() { assertFalse(Coordinates.isUsable(null, null)) }
    @Test fun `zero zero is not usable`() { assertFalse(Coordinates.isUsable(0.0, 0.0)) }
    @Test fun `a point outside the configured country box is not usable`() {
        assertFalse(Coordinates.isUsable(20.0, -30.0))   // the demo data's value
    }
    @Test fun `a point inside the box is usable`() { assertTrue(Coordinates.isUsable(52.52, 13.405)) }
}
```
Run: `./mvnw test -Dtest=CoordinatesTest` — Expected: FAIL, unresolved reference.

- [ ] **Step 2: Implement `Coordinates` as a plain object with injected bounds**

Keep it a pure function taking the box as parameters, plus a `@ApplicationScoped` `GeocodingConfig`
holding `@ConfigMapping`-style properties (`app.geocoding.bbox.min-lat` etc., defaults covering
Germany + Austria + Switzerland: `45.0/4.0/56.0/18.0` — record that this box is a decision and where it
is changed for another country). The pure function is what the unit test calls; no Quarkus boot needed.

- [ ] **Step 3: Write the migration**

`changelog-3.2.sql`:
```sql
--liquibase formatted sql

--changeset mario:8 labels:nullable-community-coordinates context:nullable-community-coordinates
--comment: Community coordinates become optional; 0/0 is rewritten as unknown
ALTER TABLE community ALTER COLUMN latitude DROP NOT NULL;
ALTER TABLE community ALTER COLUMN longitude DROP NOT NULL;
UPDATE community SET latitude = NULL, longitude = NULL
 WHERE latitude = 0 AND longitude = 0;
UPDATE community SET latitude = NULL, longitude = NULL
 WHERE latitude NOT BETWEEN 45 AND 56 OR longitude NOT BETWEEN 4 AND 18;

--rollback ALTER TABLE community ALTER COLUMN latitude SET NOT NULL;
```
The two `UPDATE`s destroy demo coordinates on purpose: they were never real, and leaving them makes
Task 5's distance sort nonsense. Say so in the commit body.

- [ ] **Step 4: Make the Kotlin side nullable**

`Community.kt:57-60` → `var latitude: Double? = null`, same for longitude. `CommunityModel.kt:15-16` →
`Double?`. Response DTOs → `Double?`. **`CommunityRequestDto`'s `latitude`/`longitude` become
`Double?` with no default and the `@Range` annotations stay** — a create without coordinates is legal
(Task 2 geocodes it), and the DTO stops accepting a hand-typed coordinate as the only way in.
Then fix the compile errors: every constructor call and mapper. Grep them:
```bash
grep -rn "latitude" src/main/kotlin src/test/kotlin
```

- [ ] **Step 5: Verify nothing else changed its mind**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
```
Expected: green. A controller test that asserted `latitude: 20` now gets `null` — that fixture was
using junk coordinates; change it to a coordinate inside the box and note that you did.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/changelog/changelog-3.2.sql src/main/kotlin src/test/kotlin
git commit -m "fix: make community coordinates optional and reject unusable values"
```

---

## Task 2: Geocoding behind an interface, with a fake in tests

Addresses are what people type; coordinates are what the queries need. This task adds the translation,
with the provider swappable and failure harmless.

**Files:**
- Create: `src/main/kotlin/domain/service/GeocodingService.kt`,
  `src/main/kotlin/infrastructure/geocoding/NominatimGeocoder.kt`,
  `src/main/kotlin/infrastructure/geocoding/GeocodingConfig.kt`,
  `src/test/kotlin/domain/service/GeocodingServiceTest.kt`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Consumes: `Coordinates.isUsable` (Task 1)
- Produces:
  ```kotlin
  data class GeoPoint(val latitude: Double, val longitude: Double)
  interface AddressGeocoder { suspend/Blocking: fun geocode(address: PostalAddress): GeoPoint? }
  data class PostalAddress(val street: String?, val houseNumber: String?,
                          val postalCode: String?, val city: String?)
  @ApplicationScoped class GeocodingService(geocoder: AddressGeocoder, config: GeocodingConfig) {
      fun geocode(address: PostalAddress): GeoPoint?    // null = "not located yet", never throws
  }
  ```
  Task 3 (users) and Task 4 (communities) call `GeocodingService.geocode`. Tests substitute a fake
  `AddressGeocoder` bound with `@Alternative @Priority`.

- [ ] **Step 1: Write the failing tests for the service, not the provider**

```kotlin
@Test fun `a complete address resolves to a point`()
@Test fun `an address with no street and no city resolves to null without calling the provider`()
@Test fun `a provider exception resolves to null`()
@Test fun `a provider result outside the country box resolves to null`()
@Test fun `a provider result of 0,0 resolves to null`()
```
The fake geocoder records whether it was called, which is how test 2 is meaningful.

- [ ] **Step 2: Run to verify they fail**

`./mvnw test -Dtest=GeocodingServiceTest` — Expected: FAIL, unresolved references.

- [ ] **Step 3: Implement `GeocodingService`**

Guard first (all-blank address → `null`, no provider call), then delegate, then `Coordinates.isUsable`
on the result, then `catch (t: Throwable) { log; null }`. **Nothing outside this class may throw on a
geocoding failure** — Review Focus 2. Log at WARN with the address omitted from the message (it is
personal data; log the fact, not the value).

- [ ] **Step 4: Implement the Nominatim provider**

`java.net.http.HttpClient` (no new dependency; `pom.xml` has no REST-client extension and adding one
for a single call is not worth it — say so in the commit body). Requirements, each of which is a test
in `GeocodingServiceTest` or a comment where it cannot be:
- `GET {base}/search?q={formatted}&format=jsonv2&limit=1`, URL-encoded.
- `User-Agent: comshare/{version} (contact: {app.geocoding.contact})` — Nominatim's policy requires an
  identifying agent. `app.geocoding.contact` is **required in `%prod`** with no default, following
  Phase 1/2's `${ENV:no-default}` pattern; a deployment that forgets it fails at boot rather than
  hammering a public service anonymously.
- A one-second minimum interval between requests, enforced inside the provider with a mutex-guarded
  timestamp (not a scheduler; one pilot instance, and the spec's rate is per second).
- A timeout of `app.geocoding.timeout=3s` and **no retry**. A miss leaves the coordinates null and the
  next save tries again.
- Results cached in-process in a bounded `LinkedHashMap` keyed by the normalised address, sized
  `app.geocoding.cache-size=500`. The spec asks for caching; an in-process map is enough at this scale
  and says so in a comment.

- [ ] **Step 5: Properties and defaults**

```properties
app.geocoding.enabled=true
app.geocoding.base-url=https://nominatim.openstreetmap.org
app.geocoding.timeout=3S
app.geocoding.cache-size=500
app.geocoding.bbox.min-lat=45.0
app.geocoding.bbox.min-lon=4.0
app.geocoding.bbox.max-lat=56.0
app.geocoding.bbox.max-lon=18.0
%prod.app.geocoding.contact=
```
`%test` gets `app.geocoding.enabled=false` and, more importantly, **`%test` must never hit the real
provider**: bind the fake `AddressGeocoder` as an `@Alternative @Priority(1)` in the test sources so
`QuarkusTest` never selects `NominatimGeocoder`. Prove it: `./mvnw test` while offline-ish (or with
`app.geocoding.base-url=http://127.0.0.1:1`) still passes.

- [ ] **Step 6: Verify and commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/main/resources/application.properties src/test/kotlin
git commit -m "feat: resolve addresses to coordinates through a swappable geocoder"
```

---

## Task 3: Coordinates on the user, written from the profile save

"Distance from me" has no left-hand side: `User` has no coordinates
(`domain/model/UserModel.kt` has address fields only). Also `geocoded_at` so the UI can say *when* this
was last believed, and so a failed geocode is distinguishable from an untried one.

**Files:**
- Create: `src/main/resources/db/changelog/changelog-3.3.sql`
- Modify: `infrastructure/entity/User.kt`, `domain/model/UserModel.kt`, `api/dto/UserDto.kt`,
  `domain/service/UserService.kt` (`updateUser`), `api/controller/UserController.kt`
- Test: `src/test/kotlin/domain/service/UserServiceTest.kt`

**Interfaces:**
- Consumes: `GeocodingService.geocode` (Task 2); Phase 2 Task 12's `updateUser` which only assigns what
  it was sent — **do not start this task before that lands**, or the geocode will be built on a field
  that the next PATCH erases.
- Produces: `UserModel.latitude: Double?`, `longitude: Double?`, `geocodedAt: OffsetDateTime?`;
  `GET /api/user/me` returns all three; `PATCH /api/user/me` re-geocodes when any address component
  changed. Task 5 reads `user.latitude`, Task 6 reads it for the radius guard, Tasks 7–9 display it.

- [ ] **Step 1: The migration**

```sql
--changeset mario:9 labels:user-coordinates context:user-coordinates
--comment: Users gain coordinates and a geocoding timestamp
ALTER TABLE user ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE user ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE user ADD COLUMN geocoded_at TIMESTAMP WITH TIME ZONE;
--rollback ALTER TABLE user DROP COLUMN latitude; ALTER TABLE user DROP COLUMN longitude; ALTER TABLE user DROP COLUMN geocoded_at;
```

- [ ] **Step 2: Failing service tests**

In `UserServiceTest`:
```kotlin
@Test fun `when updating an address, then the coordinates are resolved and geocoded_at is set`()
@Test fun `when the geocoder returns nothing, then the address is saved and coordinates stay null`()
@Test fun `when the address is unchanged, then the geocoder is not called again`()
@Test fun `when only the last name changes, then coordinates and address survive`()  // Phase 2's rule, re-pinned here
```
The fake geocoder from Task 2 makes 1 and 2 deterministic. "Not called again" (test 3) is what stops
every profile save costing a rate-limited HTTP call: compare the four address components against the
stored values, and re-geocode only when at least one differs **or** the stored coordinates are unusable.

- [ ] **Step 3: Implement**

In `UserService.updateUser`, after assigning the fields, build a `PostalAddress` from the resulting
values; if `PostalAddress` is empty (user has typed no address at all), leave coordinates null and
return. Otherwise call `geocodingService.geocode` and assign `latitude`, `longitude`,
`geocodedAt = now()` **only when it returns a point** — a failed geocode of a *changed* address keeps
the old coordinates and does not clear `geocodedAt`, so the UI does not start nagging on a provider
outage. Comment that choice; it is deliberate.

- [ ] **Step 4: Expose on the DTO and verify**

`UserDto` gains `latitude`, `longitude`, `geocodedAt` (nullable). Then:
```bash
./mvnw test && ./scripts/ci-check-tests.sh
```
Then against the running stack, PATCH an address for a probe user and `GET /api/user/me`; paste the
response into the report. **Expected: the probe user's coordinates are null or a real street in the
configured box — never `0, 0`.**

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/changelog/changelog-3.3.sql src/main/kotlin src/test/kotlin
git commit -m "feat: resolve and store user coordinates when the profile address changes"
```

---

## Task 4: Communities get coordinates, and can be corrected

Two defects meet here: `CommunityController.updateCommunity` (`:131-137`) copies `name`, `address`,
`radius`, `canBeJoined` onto the model and **drops `latitude`/`longitude`**, while
`CommunityService.updateCommunity` (`:82-94`) writes those two back **from the values it loaded** — so
coordinates are frozen at creation, and the only creation input is a hand-typed number nobody types.
This task geocodes on create, fixes the PATCH path (pulled forward from plan 2b), and adds
`geocoded_at` to the community.

**Files:**
- Create: `changelog-3.4.sql` (community `geocoded_at`)
- Modify: `api/controller/CommunityController.kt` (create + update), `domain/service/CommunityService.kt`,
  `infrastructure/entity/Community.kt`, `domain/model/CommunityModel.kt`, `api/dto/CommunityDto.kt`
- Test: `CommunityServiceTest.kt`, `CommunityControllerTest.kt`

**Interfaces:**
- Consumes: `GeocodingService` (Task 2)
- Produces: a community whose coordinates come from its address and can be changed by an admin;
  `CommunityModel.geocodedAt: OffsetDateTime?`. Task 5's distance query needs at least one community with
  real coordinates, which for the first time this phase is possible without SQL.

- [ ] **Step 1: Failing PATCH test — the pulled-forward 2b fix**

```kotlin
@Test
fun `when an admin patches the address, then the coordinates are re-resolved`() {
    // seed community at a usable point, PATCH street+city, assert new coordinates differ
}
@Test
fun `when an admin patches canBeJoined to false, then it reads back false`()
```
Run: `./mvnw test -Dtest=CommunityServiceTest,CommunityControllerTest` — Expected: FAIL (coordinates
unchanged; `canBeJoined` unchanged).

- [ ] **Step 2: Fix the copy**

`CommunityController.updateCommunity`: copy `latitude`/`longitude` **only if the request carried them**
(the DTO fields are nullable since Task 1) and copy `canBeJoined` — and read
`CommunityService.updateCommunity` first so the controller and the service do not both try to be
authoritative. The rule to implement: **the service owns coordinates, derived from the address in the
request; the controller owns the scalar fields.** No request field writes a coordinate directly any
more; `CommunityRequestDto.latitude/longitude` are therefore **deleted** from the request DTO in this
step (Task 1 made them nullable; here they go away entirely, because a user-supplied coordinate is the
hole Phase 6's self-serve communities would widen). Delete every client-side reference to them at the
same time (Tasks 7–9 replace them with the address form).

- [ ] **Step 3: Geocode on create**

`CommunityService.createCommunity` calls `GeocodingService.geocode` on the request's address and stores
the result (possibly null). Creation still succeeds with null coordinates — a community created from a
phone in a dead-spot is joinable (Task 6's escape hatch) and shows "location not confirmed".

- [ ] **Step 4: Migration, verify, commit**

`changelog-3.4.sql`:
```sql
--changeset mario:10 labels:community-geocoded-at context:community-geocoded-at
--comment: When the community address was last resolved
ALTER TABLE community ADD COLUMN geocoded_at TIMESTAMP WITH TIME ZONE;
--rollback ALTER TABLE community DROP COLUMN geocoded_at;
```
```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/resources/db/changelog/changelog-3.4.sql src/main/kotlin src/test/kotlin
git commit -m "fix: derive community coordinates from the address and allow them to be corrected"
```

---

## Task 5: The nearby query and endpoint

`GET /user/community/nearby?lat=&lon=` returning the communities within a generous window, each with
`distanceMeters`, sorted by distance. Bounding box first (indexable), Haversine second.

**Files:**
- Create: `api/dto/NearbyCommunityDto.kt`, `src/test/kotlin/domain/service/NearbyCommunityTest.kt`,
  `src/test/kotlin/api/controller/ProximityControllerTest.kt`
- Modify: `infrastructure/entity/Community.kt` (named query), `domain/service/CommunityService.kt`,
  `api/controller/CommunityController.kt`, plus a changeset adding the composite index

**Interfaces:**
- Consumes: `Community.latitude/longitude` nullable (Task 1), user coordinates (Task 3)
- Produces: `GET /api/user/community/nearby?lat=&lon=&pageNumber=&pageSize=` →
  `PageDto<NearbyCommunityDto(uuid, name, street, houseNumber, postalCode, city, radius, latitude,
  longitude, distanceMeters: Long?, memberCount: Long, isMember: Boolean)>`, ordered by
  `distanceMeters` nulls last. Tasks 7–9 render it. `isMember` lets the client label what the user
  can already open, without leaking anything about non-member communities.

- [ ] **Step 1: Write the ordering test first, in the service**

Seed four communities: one at ~300 m, one at ~4 km, one at ~40 km, one with null coordinates, and ask
for nearby from the centre with a 50 km window.
```kotlin
@Test fun `nearby communities come back ordered by distance`()
@Test fun `a community outside the window is not returned`()
@Test fun `a community without coordinates is returned last, with distanceMeters null`()
@Test fun `the haversine distance for a known pair is right within one percent`()
```
That last one is the whole point of the task and must use a hand-computed pair, e.g.
`(52.5200, 13.4050)` → `(52.5245, 13.4101)` ≈ 0.52 km (Berlin). Compute it yourself and put the
derivation in the test comment.

- [ ] **Step 2: Run to verify it fails**

`./mvnw test -Dtest=NearbyCommunityTest` — Expected: FAIL, no such method.

- [ ] **Step 3: Add the index**

`changelog-3.5.sql`:
```sql
--changeset mario:11 labels:community-coords-index context:community-coords-index
--comment: Bounding-box scan for nearby communities
CREATE INDEX community_coords_bbox ON community (latitude, longitude);
--rollback DROP INDEX community_coords_bbox;
```

- [ ] **Step 4: Write the query as two steps**

A `@NamedNativeQuery` on `Community` is the honest shape here, because the Haversine expression is
database SQL, not JPQL:
```sql
SELECT c.uuid, c.name, c.street, c.house_number, c.postal_code, c.city, c.radius,
       c.latitude, c.longitude,
       (6371000 * acos(least(1.0, greatest(-1.0,
            cos(radians(:lat)) * cos(radians(c.latitude)) * cos(radians(c.longitude) - radians(:lon))
          + sin(radians(:lat)) * sin(radians(c.latitude)))))) AS distance_meters,
       (SELECT count(*) FROM user_community_join_request r
         WHERE r.community_uuid = c.uuid AND r.status = 'APPROVED') AS member_count
  FROM community c
 WHERE c.latitude BETWEEN :minLat AND :maxLat
   AND c.longitude BETWEEN :minLon AND :maxLon
 ORDER BY distance_meters NULLS LAST
```
`member_count` must count **approved** members — read the actual table and status enum names first
(`grep -rn "class UserCommunityJoinRequest" -A12 src/main/kotlin/infrastructure/entity`) and use what
exists; do not guess a schema. Communities with null coordinates cannot satisfy `BETWEEN`, so Step 1's
"returned last" case needs them fetched in a second query and appended — implement that explicitly
rather than dropping them, and say in a comment why a `LEFT JOIN`-style single query was not used.

`lat`/`lon` bounds: `±(windowKm / 111.0)` for latitude and
`±(windowKm / (111.0 * cos(radians(lat))))` for longitude, guarding `cos ≈ 0` (the poles) — Review
Focus 1's cousin. Default `windowKm = 50`, configurable via `app.proximity.window-km`.

- [ ] **Step 5: Prove the bounding box is what does the filtering**

```bash
# with the stack up and data loaded:
psql "postgresql://quarkus:quarkus@localhost:5433/quarkus" -c "EXPLAIN (ANALYZE, BUFFERS) <the query above with literals>"
```
Expected: an index scan (or at minimum a filter on the bbox predicate) using
`community_coords_bbox`, not a sequential scan with the Haversine applied per row. **Paste the plan into
the report.** If it says Seq Scan and the table is big enough to matter, add the missing predicate; if
the table has 4 rows the planner will reasonably ignore the index — write that caveat down instead of
inventing a claim that the index is used.

- [ ] **Step 6: Endpoint + authorization**

`GET /user/community/nearby`, `@RolesAllowed`-free (the enforcer already requires `user`), body as
produced above. `lat`/`lon` are `@NotNull @Range(-90..90)` / `@Range(-180..180)` (Phase 2 Task 7's
pattern). `isMember` is computed with the same `isMember` helper from Phase 2 Task 8 — one query per
page, not per row. **The response must contain no item data and no member identity** (Review Focus 4);
the OpenAPI schema is the evidence, so print the new schema path in the report:
```bash
curl -s "localhost:8080/q/openapi?format=json" | python3 -c "import json,sys;print(sorted(json.load(sys.stdin)['components']['schemas']['NearbyCommunityDto']['properties']))"
```

- [ ] **Step 7: Verify and commit**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
git add src/main/kotlin src/main/resources/db/changelog/changelog-3.5.sql src/test/kotlin
git commit -m "feat: add a distance-ordered nearby communities endpoint"
```

---

## Task 6: The radius actually stops a join request

`joinCommunity` (`CommunityService.kt`) checks `canBeJoined` and nothing else. After this task it also
refuses when the community has usable coordinates and the user's stored coordinates are outside its
`radius`, with a distinct error code so the client can say *why*.

**Files:**
- Modify: `domain/service/CommunityService.kt` (`joinCommunity`),
  `domain/model/exception/ErrorCode.kt`, `api/controller/CommunityController.kt` (only if the error
  mapping needs it)
- Test: `CommunityServiceTest.kt`

**Interfaces:**
- Consumes: `Coordinates.isUsable`, the Haversine helper from Task 5 (extract it to
  `common/GeoDistance.kt` so the query and the guard cannot drift apart), user coordinates (Task 3)
- Produces: `ErrorCode.CommunityTooFar` → 400 with that code. Escape hatch: no usable coordinates on
  either side means **allow**.

- [ ] **Step 1: Failing tests, both directions**

```kotlin
@Test fun `when the user is outside the radius of a community with coordinates, then COMMUNITY_TOO_FAR`()
@Test fun `when the user is inside the radius, then the request is created`()
@Test fun `when the community has no coordinates, then the request is created`()          // escape hatch
@Test fun `when the user has no coordinates, then the request is created`()               // escape hatch
@Test fun `a request from 10 m outside the radius is refused`()                            // boundary is inclusive
```
Radius is in **metres** everywhere — check `Community.radius`'s unit before writing the tests: the
client form and the Leaflet circle both treat it as metres, and the realm's demo rows use small values.
If the codebase ever treats it as kilometres, stop and correct the spec, do not silently pick one.

- [ ] **Step 2: Implement the guard**

`joinCommunity` gains the check after the `canBeJoined` test and before creating the request row. Use
the extracted `GeoDistance.meters(a, b)`; compare `> community.radius` (so exactly-on-the-edge is
allowed, as the boundary test asserts). `CommunityTooFar` maps to 400 like the other domain refusals —
read `ErrorCode` and `CustomBadRequestException` first and match the existing shape exactly.

- [ ] **Step 3: Verify, and confirm the error reaches the client body**

Against the running stack: probe user far away → `POST /api/user/community/{uuid}/join` → expect 400 and
record the body, confirming the code is machine-readable and not just prose. That response shape is
what Tasks 8–9 translate.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin src/test/kotlin
git commit -m "feat: refuse join requests from outside a community's radius"
```

---

## Task 7: A web profile page, because the web cannot state an address

G1 needs an address, and the web app has no way to enter one: `/settings` is a stub and nothing in the
web app calls `/user/me` at all. The spec's answer is *build it*, and it is small: four address fields,
a save, and the geocoding status.

**Files:**
- Create: `pages/profile.tsx`, `app/lib/profile.ts`, `app/lib/profile.test.ts`,
  `__tests__/pages/profile.test.tsx`
- Modify: `app/components/navBar.tsx` (link), `app/models/user.ts` (or create it),
  `public/locales/{en,de}/common.json`

**Interfaces:**
- Consumes: `GET /api/user/me`, `PATCH /api/user/me` with `street, houseNumber, postalCode, city`
  (Task 3's shape; `@Valid` sizes from Phase 2 Task 7)
- Produces: `getMyProfile(session?)`, `updateMyProfile(patch, session?)`, and a page whose save shows
  one of three states: located (`"Located: {street}"`), pending (`"Location not confirmed yet"`), or
  refused. Tasks 8 and 9 read the same status.

- [ ] **Step 1: lib tests first**

Following `app/lib/*.test.ts`'s `mockFetch` style: `getMyProfile` hits `/user/me` and returns the
profile incl. `latitude`, `longitude`, `geocodedAt`; `updateMyProfile` PATCHes it and returns the
response; a 400 returns the error rather than throwing.

- [ ] **Step 2: Page tests, then the page**

```tsx
test('renders the stored address', …)
test('saving posts the four address fields and shows the located state', …)
test('when the response has no coordinates, the not-confirmed notice is shown', …)
test('a 400 shows the server message and keeps the typed values', …)
```
`getServerSideProps` uses `getSession`; no session → `redirect` to `/login`, matching the pattern in
`pages/my-area.tsx`. The page is `pages/profile.tsx` with a heading, four inputs, a save button, and one
status line. No map here — Task 8 owns the map.

Copy (`en` / `de`): `profile.title` `"Profile"` / `"Profil"`, `profile.address` `"Address"` /
`"Adresse"`, `profile.street`, `profile.houseNumber`, `profile.postalCode`, `profile.city`,
`profile.save` `"Save"` / `"Speichern"`, `profile.saved` `"Saved."` / `"Gespeichert."`,
`profile.located` `"Location confirmed."` / `"Standort bestätigt."`,
`profile.notLocated` `"Location not confirmed yet. Save your address again to try once more."` /
`"Standort noch nicht bestätigt. Speichere die Adresse erneut, um es noch einmal zu versuchen."`,
`profile.nav` `"Profile"` / `"Profil"`.

- [ ] **Step 3: Verify**

```bash
cd frontend-nextjs && npm run typecheck && npx jest --ci && pnpm build
```
Expected: all green, and the build still compiles (Phase 1 Task 3). Then log in against the running
stack, save an address, reload, and confirm the status line matches what `GET /api/user/me` returns.

- [ ] **Step 4: Commit**

```bash
git add pages/profile.tsx app/lib/profile.ts app/lib/profile.test.ts __tests__/pages/profile.test.tsx app/components/navBar.tsx app/models public/locales
git commit -m "feat: add a profile page where the address can be stated"
```

---

## Task 8: Distance in the web community list, and the map that was already written

The list shows distance and sorts by it, and `app/components/map.tsx` — dead, and broken three ways
(Review Focus 6) — finally gets used.

**Files:**
- Modify: `app/components/map.tsx`, `app/lib/communities.ts`, the community list page
  (`pages/my-area.tsx` and/or `pages/communities/[id].tsx` — find them with Step 1),
  `app/models/community.ts`, `public/locales/{en,de}/common.json`, the affected tests
- Test: extend `__tests__/pages/communities/[id].test.tsx`, add map tests

**Interfaces:**
- Consumes: `getNearbyCommunities(session?, lat?, lon?)` (new lib fn over Task 5's endpoint), the
  profile's coordinates (Task 7)
- Produces: a list of `NearbyCommunity` items rendered with `distanceLabel`, and a
  `<MapMarker community={…}/>` component that renders one marker plus the radius circle for one
  community's detail page.

- [ ] **Step 1: Find every place communities are listed, before changing any of them**

```bash
cd frontend-nextjs && grep -rln "getCommunities\|getMyCommunities\|communities" pages app/components | sort
```
List them in the report. The plan assumes the *discovery* surface (nearby) and the *mine* surface (my
communities) are different lists; if they are the same component, one change covers both and you should
say so.

- [ ] **Step 2: Fix `map.tsx`**

Convert it to a real component: `export default function CommunityMap({ latitude, longitude, radius,
name }: { latitude: number; longitude: number; radius: number; name: string })`; take a `ref`-based
container div instead of the global `L.map('map')` id; add a cleanup (`map.remove()`) in the
`useEffect` return, because Next.js Pages Router mounts and unmounts pages and the current version
leaks a map instance per visit; keep OSM tiles and attribution; import Leaflet dynamically
(`next/dynamic` with `ssr: false`) because `window` does not exist during SSR. Handle
`latitude == null` by rendering the "location not confirmed" line (Task 7's copy) instead of a map.

- [ ] **Step 3: List tests, then implement**

```tsx
test('a nearby community shows its distance in kilometres', …)          // 850 -> "850 m", 4200 -> "4.2 km"
test('a community without coordinates shows the not-located label', …)
test('the list is rendered in the server-supplied order and not re-sorted', …)
test('a community the user belongs to is labelled as a member', …)      // isMember
```
Server order is authoritative (the SQL already sorts) — a client-side `.sort()` would silently undo the
NULLS LAST rule. Distance formatting lives in one helper, `formatDistance(meters: number | null): string`
(`"850 m"`, `"4.2 km"`, `t('community.notLocated')`), so mobile and web at least agree on the wording
rule if not the code. Keys: `community.distance` `"{{distance}} away"` / `"{{distance}} entfernt"`,
`community.notLocated` `"Location not confirmed"` / `"Standort nicht bestätigt"`,
`community.members_one` `"{{count}} member"` / `"{{count} Mitglied"`,
`community.members_other` `"{{count}} members"` / `"{{count}} Mitglieder"`,
`community.isMember` `"You are a member"` / `"Du bist Mitglied"`.

- [ ] **Step 4: Wire the map into the community detail page**

One `<CommunityMap …/>` block on `pages/communities/[id].tsx`, above the address, rendered only when
coordinates exist. Confirm the radius circle scales (a 300 m radius and a 5 km radius must not both fill
the viewport — set the view from `radius`).

- [ ] **Step 5: Verify and commit**

```bash
npm run typecheck && npx jest --ci && pnpm build
git add app pages public/locales __tests__
git commit -m "feat: show distance and a map for communities on the web"
```

---

## Task 9: Distance on mobile

The list shows distance and sorts by it. **No map** — the spec excludes one, and adding a
`flutter_map` dependency to prove a number is the wrong trade.

**Files:**
- Modify: `lib/model/community.dart`, `lib/service/community_service.dart`,
  `lib/widget/community/community_screen.dart` (and the my-area list if it lists communities),
  `lib/l10n/app_{en,de}.arb`, `lib/gen_l10n/*`
- Test: `test/service/community_service_test.dart`, plus a widget/cubit test for the ordering

**Interfaces:**
- Consumes: `GET /user/community/nearby` (Task 5), the user's coordinates from the profile the app
  already fetches
- Produces: `CommunityService.getNearbyCommunities({double? lat, double? lon, int pageNumber})`,
  `Community.distanceMeters: int?`, a `distanceLabel` extension used by the list tile, and a
  "confirm your address" banner when the signed-in user has no usable coordinates.

- [ ] **Step 1: Service tests**

Assert the path, that a `lat`/`lon`-less call still succeeds (server-side fallback is the user's stored
coordinates — confirm which of the two the endpoint actually implements in Task 5 and mirror it), and
that `distanceMeters == null` parses as null rather than 0.

- [ ] **Step 2: List rendering tests**

```dart
test('a community 4200 metres away renders 4.2 km', …);
test('a community with no distance renders the not-located label', …);
test('the list keeps the server order', …);
test('when the user has no coordinates, the confirm-address banner is shown', …);
```

- [ ] **Step 3: Implement**

Add the field to `Community.fromJson` as `json['distanceMeters'] as int?` (**not** `?? 0` —
Review Focus 1), the service method beside its siblings, and the banner above the list when
`user.latitude` is null/unusable, linking to the profile-ish flow the app already has. Format in one
place (`util/`), with the same thresholds as the web helper so both clients say `4.2 km`.

ARB keys (EN/DE): `communityScreen_distance` `"{{distance}} away"` / `"{{distance}} entfernt"`,
`communityScreen_notLocated` `"Location not confirmed"` / `"Standort nicht bestätigt"`,
`communityScreen_confirmAddress`
`"Confirm your address to see what is near you."` /
`"Bestätige deine Adresse, um zu sehen, was in der Nähe ist."`,
`communityScreen_member` `"Member"` / `"Mitglied"`. Then `flutter gen-l10n`.

- [ ] **Step 4: Verify and commit**

```bash
flutter analyze && flutter test
git add lib test && git commit -m "feat: show community distance in the mobile list"
```

---

## Task 10: Prove proximity end to end, and record it

The claim this phase makes is "a neighbour sees what is on their street". That is only checkable with
two users and two real-ish addresses.

**Files:**
- Modify: `docs/runbook.md` (Verified section), this plan file

- [ ] **Step 1: Build the fixture**

Two probe users, ~400 m apart in the configured box (Step 5's coordinates from Task 5 are a good pair).
One community centred on user A with `radius = 1000`. Record the addresses, the coordinates the geocoder
returned, and the distance the endpoint reported. If the geocoder will not resolve your test addresses,
say which ones failed — that is data about the provider, not a reason to hand-write coordinates.

- [ ] **Step 2: Walk it**

- A: create the community → coordinates are non-null, `GET /api/user/me`-style check shows `geocoded_at`.
- A: nearby list shows B's home community at ~0.x km, sorted correctly against a farther one.
- B: nearby list shows the community; **the item list for that community is 403** (Phase 2's rule,
  re-verified here because this phase is the one that advertises communities to non-members).
- B: join request from inside the radius → created.
- C (a third user ~30 km away): join request → **400 `COMMUNITY_TOO_FAR`**, and the web/mobile UI shows
  the reason, not a generic failure.
- Community with null coordinates: joinable, and both clients show "location not confirmed".

- [ ] **Step 3: Record it**

Append to `docs/runbook.md` under *Verified*: date, the fixture, the four status codes, the geocoder's
behaviour (including any address it refused), and the `EXPLAIN` line from Task 5 Step 5. Anything that
did not happen is written as not verified with the reason.

- [ ] **Step 4: Commit**

```bash
git add docs/runbook.md docs/superpowers/plans/2026-11-23-phase3-proximity.md
git commit -m "docs: record the phase 3 proximity baseline"
```

---

## Out of scope for this phase

- **Address verification.** Addresses stay self-declared; the spec states this as an accepted risk and
  this plan does not quietly improve on it. No postcodes-are-real check, no ID, no photo of a doorbell.
- **Map on mobile.** Excluded by the spec.
- **PostGIS, trigrams, or any new database extension.** Bounding box + Haversine, and the `EXPLAIN`
  output is the evidence it is adequate.
- **Search, sorting by anything except distance, member-count accuracy guarantees.** The member count is
  whatever "approved membership" means in the existing schema; making it exact is 2b/2c work.
- **Radius as an exclusivity mechanism.** It constrains open join only; invitations bypass it (Phase 6),
  and self-serve communities can be created anywhere. The spec's "Proximity will constrain joining, not
  creation" paragraph is the accepted wording — do not describe the radius as a neighbourhood guarantee.

## Known deviations to report

- If `Community.radius` turns out not to be metres (Task 6 Step 1), stop and correct the spec — do not
  pick a unit.
- If the geocoder cannot resolve addresses in the configured box, the pilot needs a different provider;
  record the failing addresses.
- If `EXPLAIN` cannot show an index use because the table is tiny, say that instead of claiming the
  index works.
