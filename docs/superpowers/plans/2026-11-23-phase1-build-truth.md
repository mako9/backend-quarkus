# Phase 1 — Build Truth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "green" mean "the thing that ships works". Today a green build can mean 58 API tests
silently skipped, a web frontend that has never compiled, and a shipped jar whose JSON keys differ
from the ones every test asserts.

**Architecture:** Four independent gates, then one atomic wire-format fix, then a real stack. Backend
gates are a shell script the CI calls (surefire has no "no skips" switch). Next.js gets `tsc` and
`build` added to CI after the errors they report are fixed at the root. The Jackson scope change and
the eight client field renames land in one commit because either half alone breaks a client.

**Tech Stack:** Quarkus 3.33 / Kotlin / surefire 3.5.2 / Testcontainers / GitHub Actions;
Next.js 16.2.4 (Pages Router) / pnpm 9 / next-auth 4.24 / jest; Flutter 3.x / flutter_lints;
Keycloak 26.5.4.

**Spec:** `docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — read *Current state*,
*Finding 0*, *Finding 5* and *Phase 1* before starting. Finding 0 is the reason this phase exists.

**Depends on:** Phase 0 merged. This phase's Task 6 renames the JSON key of `ItemBookingDto`
(`isItemOwner`), which Phase 0 added, so it cannot precede it.

## Global Constraints

- Repos, all under `/Users/mario/Projekte/Private/`: `backend-quarkus`, `frontend-nextjs`,
  `frontend_flutter`. Separate git repos — **commit per repo**, never one commit spanning two.
- Toolchain is not on the default `PATH`. Prefix shell steps with
  `export PATH="/usr/local/bin:/opt/homebrew/bin:$HOME/development/flutter/bin:$PATH"`.
- Do not modify `~/.gitconfig` or any repo's `.gitconfig`. Commits: Conventional Commits,
  imperative, **no `Co-Authored-By` and no AI attribution of any kind.**
- Backend verification is `./mvnw test` — the **full** suite. It now runs green on this machine
  (140 tests, 0 skipped, 50 s). If container-backed tests skip, that is a task failure, not a
  warning: Task 2 is what turns that into a build error.
- Next.js: `npx jest --ci` stays green throughout. `npx tsc --noEmit` starts at **17 errors** and
  must reach **0**; `pnpm build` starts failing and must succeed. Do not silence an error with
  `@ts-ignore`, `@ts-expect-error`, `as any`, or by narrowing `tsconfig.json` further
  (`strict: false` and `strictNullChecks: true` stay exactly as they are).
- Flutter: `flutter analyze` and `flutter test` stay green. `flutter gen-l10n` output in
  `lib/gen_l10n/` is committed.
- The `is*` naming rule from Phase 0 ("use the dev-name `isItemOwner`") is **reversed by Task 6 of
  this plan**: after Task 6 the production artifact emits the `is*` names, so `is*` is correct
  everywhere. Before Task 6 it is correct only in dev and tests. Do not do Task 6 partially.
- Nothing in this plan changes a product decision. No new user-facing strings, so no i18n work.
- Ports on this machine that are already taken: 5432 (host PostgreSQL, databases `quarkus` and
  `quarkus_test`), 8080 (backend), 8180 (free, reserved for Keycloak), 3000/49430 (Next.js).
  Anything Task 7 starts must not bind 5432.

## Review Focus

The failure modes this phase is specifically about. Each is pinned by a step in the task that owns it.

1. **A skipped test must not read as success.** `./mvnw test` returns BUILD SUCCESS with 58 tests
   skipped when the container runtime is unavailable. Expected: `scripts/ci-check-tests.sh` exits
   non-zero, locally and in CI, whenever any test is skipped or the total drops below 140. Task 2.
2. **A type error fixed by a cast is not fixed.** Expected: `tsconfig.json` is unchanged by this
   plan; `git diff tsconfig.json` is empty at the end. Task 4.
3. **The wire-format change is atomic or it is a regression.** Landing the Jackson scope alone
   silently breaks mobile item creation and Next.js item editing; landing the client renames alone
   breaks them against the currently shipped jar. Expected: one commit contains `pom.xml`,
   3 Flutter files, 3 Next.js files, and the runtime-classpath gate goes green in the same push.
   Task 6.
4. **`pnpm build` failing on CSS is the first domino, not the whole wall.** Expected: after the
   `@import` move, the plan records what the build reports next rather than assuming success, and
   stops and escalates rather than expanding scope indefinitely. Task 3.
5. **The realm file is 20.0.3-era and Keycloak is 26.5.4.** If the import fails, the fix is a fresh
   export, not a hand-edit of `config/quarkus-realm.json` — a silently dropped client or role
   produces an auth bug that surfaces as a 403 three phases from now. Task 7.
6. **Running the real artifact mutates the real database.** Booting `quarkus-run.jar` against
   `localhost:5432` runs Liquibase against the developer's own `quarkus` database, which is what
   happened on 2026-11-23 while writing this plan. Expected: Task 8 points the artifact at a
   throwaway database and says so in the log it records.

---

## File Structure

**backend-quarkus — create** `scripts/ci-check-tests.sh`, `docker/compose.dev.yml`,
`docs/runbook.md`. **modify** `pom.xml`, `src/test/kotlin/api/auth/PolicyEnforcerTest.kt`,
`.github/workflows/maven.yml`, `.gitignore`, `docs/superpowers/**` (becomes tracked).

**frontend-nextjs — modify** `app/styles/globals.css`, `tsconfig.json` (**no change expected —
assertion only**), `app/utils/request.ts`, `app/models/item.ts`, `app/models/pageModel.ts`,
`app/components/subpages/ownedItem.tsx`, `app/components/subpages/ownedItem.test.tsx`,
`app/lib/items.test.ts`, `app/utils/request.test.ts`,
`app/components/subpages/foreignItem.test.tsx`, `pages/items/create.tsx`,
`pages/bookings/index.tsx`, `__tests__/pages/bookings/index.test.tsx`,
`__tests__/pages/bookings/[uuid].test.tsx`, `__tests__/pages/notifications.test.tsx`,
`next.config.js`, `package.json`, `.github/workflows/node.js.yml`, `.gitignore`.
**create** `test/utils/session.ts`, `.env.development`, `.env.example`.

**frontend_flutter — modify** `lib/model/data_page.dart`, `lib/model/item.dart`,
`lib/model/community.dart`, `.github/workflows/dart.yml`.

---

## Task 1: Make the Keycloak test image pull reliably

`PolicyEnforcerTest.Profile` asks Testcontainers for `quay.io/keycloak/keycloak:26.5.4`. Cold pulls
from quay.io through Docker Desktop's daemon proxy (`http.docker.internal:3128`) fail with
`Head "https://quay.io/v2/keycloak/keycloak/manifests/26.5.4": EOF`, and Quarkus turns that into 58
*skipped* tests rather than a failure. `docker.io/keycloak/keycloak:26.5.4` is the same image by
digest (`sha256:ae8efb0d…`) and pulled first time on 2026-11-23.

**Files:**
- Modify: `src/test/kotlin/api/auth/PolicyEnforcerTest.kt` (the container image name)
- Read: `./mvnw test -Dtest=PolicyEnforcerTest` output

**Interfaces:**
- Produces: a test profile whose Keycloak image is fetched from a registry that works. Task 2's
  no-skip gate depends on this, because otherwise Task 2 fails the build for a registry flake.

- [x] **Step 1: Find the image reference**

```bash
cd backend-quarkus && grep -rn "quay.io\|withDockerImage\|server-type" src/test/kotlin | head
```
Expected: the reference lives in `PolicyEnforcerTest.kt` (around `:62-78`) and/or in a
`quarkus.test.keycloak.container.image.*` property. Record which; that is the line to change.

- [x] **Step 2: Change the registry, nothing else**

Replace `quay.io/keycloak/keycloak:26.5.4` with `docker.io/keycloak/keycloak:26.5.4`. Keep the tag.
If the image name comes from a Quarkus property rather than a literal, change the property
(`quarkus.test.keycloak.container.image=quay.io/keycloak/keycloak` → `docker.io/keycloak/keycloak`)
rather than the test.

- [x] **Step 3: Prove it pulls from cold**

```bash
docker rmi docker.io/keycloak/keycloak:26.5.4 quay.io/keycloak/keycloak:26.5.4 2>/dev/null
./mvnw test -Dtest=PolicyEnforcerTest
```
Expected: the image downloads, then `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` for that
class. A `Skipped:` above 0 here means the pull is still the problem — stop and record the exact
error string, do not continue to Task 2.

- [x] **Step 4: Commit**

```bash
git add src/test/kotlin/api/auth/PolicyEnforcerTest.kt
git commit -m "fix: pull the keycloak test image from docker hub"
```
If the change was a property in `src/test/resources/application.properties`, add that file instead.

---

## Task 2: Make a skipped test fail the build

Surefire reports container-unavailable as a skipped test, so `./mvnw test` has returned BUILD
SUCCESS while the entire HTTP and authorization layer (58 tests, every controller, the OpenAPI
surface, the policy enforcer) did not run. There is no surefire switch for "no skips", so the gate is
a script that reads the reports.

**Files:**
- Create: `scripts/ci-check-tests.sh`
- Modify: `.github/workflows/maven.yml`

**Interfaces:**
- Consumes: `target/surefire-reports/*.txt` (summary lines of the form
  `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: ...`) and
  `target/surefire-reports/*.xml` (`tests="44"`)
- Produces: `./scripts/ci-check-tests.sh` — exit 0 when every report shows `Skipped: 0` and the
  summed test count is at least 140; exit 1 with a printed reason otherwise. Task 3's CI does not use
  it; Task 2's does.

- [x] **Step 1: Write the script**

Create `scripts/ci-check-tests.sh`:

```bash
#!/usr/bin/env bash
# Gate: every backend test must actually run. Quarkus turns an unavailable container runtime into
# SKIPPED tests, which surefire and maven both report as success. Fail instead.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
reports=target/surefire-reports
[ -d "$reports" ] || { echo "FAIL: $reports does not exist — run ./mvnw test first"; exit 1; }

status=0
if skipped=$(grep -l "Skipped: [1-9]" "$reports"/*.txt 2>/dev/null); then
  echo "FAIL: tests were skipped; the container-backed API and authorization tests did not run:"
  echo "$skipped"
  grep -h "Skipped: [1-9]" "$reports"/*.txt
  status=1
fi

total=$(grep -ho 'tests="[0-9]*"' "$reports"/*.xml 2>/dev/null | grep -o '[0-9]*' | paste -sd+ - | bc)
: "${total:=0}"
if [ "$total" -lt 140 ]; then
  echo "FAIL: ran $total tests, expected at least 140 — an entire suite is missing"
  status=1
fi

[ "$status" -eq 0 ] && echo "OK: $total tests ran, none skipped"
exit "$status"
```

```bash
chmod +x scripts/ci-check-tests.sh
```

- [x] **Step 2: Run it against the current reports and confirm it agrees with what you know**

```bash
./mvnw test && ./scripts/ci-check-tests.sh; echo "exit=$?"
```
Expected: `OK: 140 tests ran, none skipped`, `exit=0` (the suite is green on this machine since
2026-11-23; if it is not, that is Finding 0's subject, not this task's).

- [x] **Step 3: Prove the gate fires**

Fabricate a report and confirm exit 1 without touching the real reports:
```bash
mkdir -p /tmp/gate && cp target/surefire-reports/api.controller.ItemControllerTest.txt /tmp/gate/fake.txt
sed -i '' 's/Skipped: 0/Skipped: 31/' /tmp/gate/fake.txt
grep -h "Skipped: [1-9]" /tmp/gate/fake.txt
```
Expected: the line prints, i.e. the pattern the script greps for is the pattern surefire emits. If
this grep finds nothing in the real file either, the script's pattern is wrong — fix it before
continuing.

- [x] **Step 4: Wire it into CI, and switch CI to the wrapper**

In `.github/workflows/maven.yml`, replace the `Run tests` step (`run: mvn clean test`) with:

```yaml
    - name: Run tests
      run: ./mvnw -B clean test
    - name: Gate on skipped tests
      if: always()
      run: ./scripts/ci-check-tests.sh
```
Keep `if: always()` off the gate if you prefer the standard fail-fast — the reason to keep it is that
the gate's message is more legible than a maven stack trace. Either is fine; say which you chose in
the commit body.

- [x] **Step 5: Verify the workflow file parses and the file list is right**

```bash
python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/maven.yml'));print('yaml ok')"
git diff --stat
```
Expected: `yaml ok`. If `pyyaml` is missing, `npx --yes js-yaml .github/workflows/maven.yml` is an
alternative.

- [x] **Step 6: Commit**

```bash
git add scripts/ci-check-tests.sh .github/workflows/maven.yml
git commit -m "build: fail the build when container-backed tests are skipped"
```

---

## Task 3: Make the web frontend build

`app/styles/globals.css:66` has `@import url('https://fonts.googleapis.com/…')` *after* ~65 lines of
rules. The dev server tolerates it; `next build` does not. Until this is fixed nothing behind it is
known, which is why this task comes before the type-check task.

**Files:**
- Modify: `app/styles/globals.css`
- Read: `pnpm build` output

**Interfaces:**
- Produces: a compiling `pnpm build`, and an honest list of whatever it then reports. Task 4 assumes
  the build works; Task 5 needs the build to emit a bundle to inspect.

- [x] **Step 1: Reproduce the failure verbatim**

```bash
cd frontend-nextjs && pnpm build 2>&1 | tail -30
```
This was run on 2026-11-23 and failed with, exactly:
```
@import rules must precede all rules aside from @charset and @layer statements
Import trace:
  Browser:
    ./app/styles/globals.css
    ./pages/_app.tsx
    at <unknown> (./app/styles/globals.css:66:8)
 ELIFECYCLE  Command failed with exit code 1.
```
so PostCSS, not Turbopack, is the one objecting, and the import is reached through `pages/_app.tsx:1`.
If your run says something different, stop: this task's premise has changed and the spec's Finding 0
needs correcting before continuing.

- [x] **Step 2: Move the import to the top of the file**

Move line 66 (`@import url('https://fonts.googleapis.com/css2?family=Work+Sans:wght@400;500;700&display=swap');`)
to line 1 of the same file, above every rule. Delete it from its old position. No other CSS edits.

- [x] **Step 3: Build again and record what is actually behind it**

```bash
pnpm build 2>&1 | tail -40
```
Expected: `Compiled successfully` / a route table. Three outcomes:
- **Success** → Task 4 can start.
- **Another mechanical build error** (import position, unsupported config key, missing file) → fix it
  the same way, one commit per distinct cause, and re-run.
- **Anything that needs a design decision** (an i18n/App-Router migration, a data-fetching rewrite) →
  **stop**. Write the error and the decision it implies into the *Open questions* section of the spec
  and report it. Do not spend this phase rewriting the routing layer.

- [x] **Step 4: Confirm dev still serves the font**

```bash
pnpm dev & sleep 12; curl -s localhost:3000 | grep -o "Work+Sans" | head -1; kill %1
```
Expected: the string appears (the stylesheet is linked, not inlined, so also confirm with
`curl -s localhost:3000 | grep -o 'href="[^"]*globals[^"]*css"'` and then grep that CSS for
`fonts.googleapis.com`).

- [x] **Step 5: Commit**

```bash
git add app/styles/globals.css
git commit -m "fix: move the font import to the top of globals.css so the build compiles"
```

---

## Task 4: Make `tsc --noEmit` report zero errors

Baseline: **17 errors**, all in test files or type declarations, none suppressed. Four root causes;
fix the cause, never the symptom.

**Files:**
- Modify: `app/utils/request.ts`, `app/models/item.ts`, `app/models/pageModel.ts` (only if a use
  requires it), `app/components/subpages/ownedItem.tsx`, `pages/bookings/index.tsx`,
  `app/utils/request.test.ts`, `app/components/subpages/foreignItem.test.tsx`,
  `app/components/subpages/ownedItem.test.tsx`, `app/lib/items.test.ts`,
  `__tests__/pages/bookings/index.test.tsx`, `__tests__/pages/bookings/[uuid].test.tsx`,
  `__tests__/pages/notifications.test.tsx`, `package.json`
- Create: `test/utils/session.ts`
- Assert unchanged: `tsconfig.json`

**Interfaces:**
- Consumes: the `Session` augmentation in `pages/api/auth/[...nextauth].ts:12-18`
  (`DefaultSession` + `status: string`, `accessToken: string`, `error: string`)
- Produces: `session(overrides?): Session` from `test/utils/session.ts`;
  `request(url, session: Session | null, …)`; `Item.isActive: boolean`,
  `Item.availability: Array<string>`, `Item.availableUntil: string | null`. Task 5 and Task 6 build on
  the corrected `Item`.

- [x] **Step 1: Capture the baseline list**

```bash
npx tsc --noEmit 2>&1 | tee /tmp/tsc-baseline.txt | grep -c "error TS"
```
The pre-Phase-0 number was **17**. Phase 0 Task 6 adds the missing `RETURNED` arm and a
`never` default to `statusColor`, so the expected count when this plan is executed in order is **16**.
Any other number is fine too — the groups in Steps 2–6 are what matter, and the only invariant is
that Step 6 ends at 0. Write the number you actually got into the report.

- [x] **Step 2: Three tests use a `getServerSideProps` result without awaiting it**

`__tests__/pages/bookings/[uuid].test.tsx:47`, `__tests__/pages/bookings/index.test.tsx:39`,
`__tests__/pages/notifications.test.tsx:40`. Each calls `getServerSideProps(...)` and reads a prop off
the `Promise`. Add `await` and make the enclosing `test` callback `async` if it is not already.

Run: `npx tsc --noEmit 2>&1 | grep -c "error TS"` — Expected: `14`.

- [x] **Step 3: Add a real `Session` fixture and use it everywhere one is faked**

Create `test/utils/session.ts`:

```ts
import { Session } from 'next-auth'

/** A complete Session as augmented in pages/api/auth/[...nextauth].ts.
 *  accessToken is widened because request() is specified to 401 on a missing token. */
export function session(overrides: Partial<Session> = {}): Session {
    return {
        status: 'ok',
        error: '',
        expires: '2999-01-01T00:00:00.000Z',
        accessToken: 'test-token',
        ...overrides,
    } as Session
}
```

Replace the `{ accessToken: 'x' }` literals with `session()` in
`app/utils/request.test.ts` (lines ~40, ~76, ~93, ~104 and the `mockSession` const) and
`app/components/subpages/foreignItem.test.tsx` (~38, ~71). For the two cases that deliberately pass a
missing token, use `session({ accessToken: null as unknown as string })` — a documented cast at the
fixture boundary, not inside production code.

Run: `npx tsc --noEmit 2>&1 | grep -c "error TS"` — Expected: `6` (5 availability + 1 statusColor)
after Step 4 as well; if 8 remain, one call site was missed:
`npx tsc --noEmit 2>&1 | grep "Session"`.

- [x] **Step 4: Widen `request`'s session parameter to match its own behaviour**

`app/utils/request.ts:4-11` declares `session: Session` but the body null-checks it
(`:13`, `:14`, `:31`) and `request.test.ts:57` passes `null` on purpose. Change the parameter to
`session: Session | null`. This is the type admitting what the code already does — the errors are in
the test that tests it.

Run: `npx tsc --noEmit 2>&1 | grep -c "error TS"` — Expected: `5` or `6`.

- [x] **Step 5: `Item.availability` is an array and `availableUntil` is nullable — fix the type**

The backend sends `availability: List<ItemTimeIntervalDto>` (`ItemDto.kt:39`) and
`availableUntil: OffsetDateTime?` (`:41`); `pages/items/create.tsx:105` posts `availability: []`; five
test fixtures pass `[]`. The declaration `availability: string` / `availableUntil: string`
(`app/models/item.ts:12-13`) is what is wrong.

```ts
    isActive: boolean          // was: active: boolean  — see Task 6; do NOT split this from Task 6
    availability: Array<string>
    availableUntil: string | null
```
Then `ownedItem.tsx:38` (`useState(itemData.active)`) must read `itemData.isActive` in the same commit
so the tree stays green, and `app/models/item.ts`'s `requiresApproval?: boolean` stays as it is.

Run: `npx tsc --noEmit 2>&1 | grep -c "error TS"` — Expected: `1`, and that one is
`pages/bookings/index.tsx(11,46): Function lacks ending return statement`.

**Sequencing note:** this step touches the same line as Task 6. If you are doing them in one sitting,
do Task 6's Next.js half here and skip it there; the rule is only "one commit per repo per coherent
change", never "half a rename".

- [x] **Step 6: `statusColor` — verify, and fix only if Phase 0 did not already**

Phase 0 Task 6 adds `case 'RETURNED'` and a `never` default to `pages/bookings/index.tsx:11`, so this
error is normally gone before this plan starts. Check:

```bash
npx tsc --noEmit 2>&1 | grep "bookings/index.tsx" || echo "already fixed by phase 0"
```
If it still reports `Function lacks ending return statement`, make the function exhaustive so the *next*
new status is a compile error — the type is `BookingStatus` in `app/models/booking.ts:1` and it already
includes `'RETURNED'`:

```ts
function statusColor(status: BookingStatus): string {
    switch (status) {
        case 'PENDING': return 'orange'
        case 'CONFIRMED': return 'green'
        case 'DECLINED': return 'red'
        case 'CANCELLED': return 'gray'
        case 'RETURNED': return 'blue'
        default: { const _exhaustive: never = status; return _exhaustive }
    }
}
```
Reuse the literals above, which are the ones in the file today (`orange`, not `yellow`; `gray`, not
`grey`) — a colour change is a design change and not this task's.

- [x] **Step 7: Add the script, keep jest green, verify the assertion**

`package.json` scripts, add:
```json
    "typecheck": "tsc --noEmit"
```
```bash
npm run typecheck && npx jest --ci
git diff tsconfig.json | wc -l
```
Expected: typecheck silent, jest all green, and **`0`** lines of `tsconfig.json` diff — any
non-zero diff here means an error was hidden rather than fixed. Revert it.

- [x] **Step 8: Commit**

```bash
git add app test/utils/session.ts pages __tests package.json
git commit -m "fix: repair the type errors in the web client's tests and item model"
```

---

## Task 5: Take the backend URL from the environment, not from the config file

`next.config.js` hardcodes `env: { BACKEND_BASE_URL: 'http://127.0.0.1:8080/api' }`. `env` values are
inlined into the bundle at build time, which is exactly when deployment needs to change them. This
task first measures the actual precedence, then removes the hardcode.

**Files:**
- Modify: `next.config.js`, `package.json` (only if a script needs it)
- Create: `.env.development`, `.env.example`

**Interfaces:**
- Produces: `BACKEND_BASE_URL` resolved only from `.env*` files / the deploy environment. Task 8's
  `next start` run depends on this.

- [x] **Step 1: Measure the precedence instead of asserting it**

```bash
echo 'BACKEND_BASE_URL=http://sentinel.example/api' > .env.local
pnpm build >/dev/null 2>&1
grep -rlo "sentinel.example" .next/server .next/static 2>/dev/null | head -3
grep -rlo "127.0.0.1:8080" .next/server .next/static 2>/dev/null | head -3
rm .env.local
```
Record which of the two the bundle contains. (This is the question the spec left open; answer it with
the bundle, not from documentation.)

- [x] **Step 2: Remove the inline value**

```js
const { i18n } = require('./next-i18next.config')

module.exports = {
  i18n,
  env: {
    LOG_LEVEL: process.env.LOG_LEVEL ?? 'info',
  },
}
```
`BACKEND_BASE_URL` is deleted from the file. `LOG_LEVEL` keeps its default, now overridable.

- [x] **Step 3: Give development a committed default**

Create `.env.development` (not gitignored — only `.env*.local` are, see `.gitignore:28-32`):
```
BACKEND_BASE_URL=http://127.0.0.1:8080/api
NEXTAUTH_URL=http://localhost:3000
LOG_LEVEL=debug
```
Create `.env.example` documenting every variable the app reads, with the four Keycloak vars
`pages/api/auth/[...nextauth].ts:6-9` throws without, `NEXTAUTH_SECRET`, `NEXTAUTH_URL` and
`BACKEND_BASE_URL`. No real secret values — placeholders and a comment saying so.

- [x] **Step 4: Verify dev works from a cold shell and prod honours the environment**

```bash
pnpm dev & sleep 12; curl -s -o /dev/null -w "%{http_code}\n" localhost:3000/; kill %1
BACKEND_BASE_URL=http://env-wins.example/api pnpm build >/dev/null 2>&1
grep -rlo "env-wins.example" .next/server .next/static | head -2
```
Expected: dev serves `200`; the build output contains `env-wins.example`. If the second grep is empty,
`env` inlining is still winning and the job is not done.

- [x] **Step 5: Commit**

```bash
git add next.config.js .env.development .env.example
git commit -m "fix: read the backend base url from the environment"
```

---

## Task 6: Make the shipped jar emit the JSON the tests assert

The single highest-risk change in the programme, and it is eight client lines plus one pom line.
`jackson-module-kotlin` is `<scope>test</scope>` (`pom.xml:113-117`) and absent from
`target/quarkus-app/lib/main/` — verified by listing the directory on 2026-11-23. Without it, Jackson
strips the `is` prefix from Kotlin boolean properties, so **the artifact emits `active`/`owner`/`admin`
/`member`/`firstPage`/`lastPage` while `ItemControllerTest:534,572` and
`CommunityControllerTest:204,223` assert `isItemOwner`/`isAdmin` and pass**. Flutter's `Item` model
contradicts itself: it *writes* `'isActive'` (`item.dart:71`) and *reads* `json['active']` (`:56`), so
against a prod-shaped backend item creation silently loses the flag, `ItemRequestDto.isActive`
defaults to `false`, and `ItemService:61` only lists `isActive = true` — a mobile-created item does not
appear in the feed.

**Files:**
- Modify: `pom.xml`; `frontend_flutter/lib/model/{data_page,item,community}.dart`;
  `frontend-nextjs/app/models/item.ts`, `app/components/subpages/ownedItem.tsx`, and the fixtures in
  `app/components/subpages/ownedItem.test.tsx`, `app/lib/items.test.ts`,
  `app/components/subpages/foreignItem.test.tsx`, `__tests__/pages/items/[id].test.tsx`;
  `scripts/ci-check-tests.sh` (**not** — a new script instead, Step 5)
- Create: `scripts/check-runtime-classpath.sh`

**Interfaces:**
- Consumes: Task 4's corrected `Item` type
- Produces: one wire format. `Item.isActive`, `Item.isOwner`, `CommunityDetailDto.isAdmin/isMember`,
  `PageDto.isFirstPage/isLastPage`, `ItemBookingDto.isItemOwner` on the wire in every mode. Phases
  2–6 build against that.

- [x] **Step 1: Prove the gap before closing it**

```bash
cd backend-quarkus && ./mvnw -B -DskipTests package
ls target/quarkus-app/lib/main/ | grep -i jackson
```
Expected: `jackson-databind`, `jackson-core`, `jackson-annotations`, `jackson-datatype-jdk8`,
`jackson-datatype-jsr310`, `jackson-module-parameter-names`, three Quarkus jars — **no
`jackson-module-kotlin`**. Paste the list into the report.

- [x] **Step 2: Write the gate that fails first**

Create `scripts/check-runtime-classpath.sh`:
```bash
#!/usr/bin/env bash
# The test classpath has jackson-module-kotlin; if the runtime classpath does not, the shipped jar
# renames every is* property (isOwner -> owner) and every test that asserts the wire format is lying.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
dir=target/quarkus-app/lib/main
[ -d "$dir" ] || { echo "FAIL: $dir missing — run ./mvnw -DskipTests package first"; exit 1; }
if ls "$dir"/com.fasterxml.jackson.module.jackson-module-kotlin-*.jar >/dev/null 2>&1; then
  echo "OK: jackson-module-kotlin is on the runtime classpath"; exit 0
fi
echo "FAIL: jackson-module-kotlin is not on the runtime classpath."
echo "The artifact will serialize is* properties without the is prefix."
exit 1
```
```bash
chmod +x scripts/check-runtime-classpath.sh && ./scripts/check-runtime-classpath.sh; echo "exit=$?"
```
Expected: FAIL, `exit=1`. This is the red half of the task.

- [x] **Step 3: Move the module to runtime scope**

In `pom.xml:113-117`, delete `<scope>test</scope>` from the `jackson-module-kotlin` dependency.
Nothing else in the pom.

```bash
./mvnw -B -DskipTests package && ./scripts/check-runtime-classpath.sh; echo "exit=$?"
```
Expected: `OK: jackson-module-kotlin is on the runtime classpath`, `exit=0`. If Quarkus'
`quarkus-extension-verification` or the dependency-analysis rule complains, read the message before
changing anything else.

- [x] **Step 4: Prove the full suite still agrees with the artifact**

```bash
./mvnw test && ./scripts/ci-check-tests.sh
```
Expected: BUILD SUCCESS, 140 tests, 0 skipped. The assertions that matter here already exist and
already assert the `is*` names over HTTP — they must stay green, which with Step 3 done means the
artifact and the tests now describe the same format.

- [x] **Step 5: Add it to CI next to the other gate**

In `.github/workflows/maven.yml`, after the test gate:
```yaml
    - name: Package
      run: ./mvnw -B -DskipTests package
    - name: Gate on the runtime classpath
      run: ./scripts/check-runtime-classpath.sh
    - name: Upload the runnable artifact
      uses: actions/upload-artifact@v4
      with:
        name: quarkus-app
        path: target/quarkus-app
        retention-days: 7
```

- [x] **Step 6: Flutter reads the `is*` names**

Six reads, three files:

| File:line | From | To |
|---|---|---|
| `lib/model/data_page.dart:37` | `json['firstPage']` | `json['isFirstPage']` |
| `lib/model/data_page.dart:38` | `json['lastPage']` | `json['isLastPage']` |
| `lib/model/community.dart:51` | `json['admin']` | `json['isAdmin']` |
| `lib/model/community.dart:56` | `json['member']` | `json['isMember']` |
| `lib/model/item.dart:56` | `json['active']` | `json['isActive']` |
| `lib/model/item.dart:62` | `json['owner']` | `json['isOwner']` |

`Item.toJson()` already sends `'isActive'` and is **not** touched — that is the half that was already
right. Then:
```bash
flutter test && flutter analyze
```
Expected: green. If a unit test fed a hand-written `{'active': true}` map, update the fixture to the
new key; the test was encoding the old wire format, which is what changed.

- [x] **Step 7: Flutter regression test for the write/read pair**

The defect survived because nothing asserted the two directions agree. Add to
`test/service/item_service_test.dart` (following that file's existing mocked-`RequestService` style)
a test that a `Map` produced by `Item.toJson()` round-trips through `Item.fromJson()` with
`isActive` preserved, and one that `Item.fromJson({'isActive': true, …})` yields `isActive == true`.

```bash
flutter test test/service/item_service_test.dart
```
Expected: green.

- [x] **Step 8: Next.js reads `isActive`**

`app/models/item.ts` — if Task 4 Step 5 did not already do it, rename `active: boolean` →
`isActive: boolean`. Then update the one read and two writes and every fixture:

```bash
grep -rn "\.active\b\|active:" app pages __tests__ --include="*.ts" --include="*.tsx"
```
Expected sites: `app/components/subpages/ownedItem.tsx:38` (`itemData.active` → `itemData.isActive`),
`:242` and `pages/items/create.tsx:100` (`active: isActive` → `isActive: isActive`), plus fixtures in
`ownedItem.test.tsx`, `foreignItem.test.tsx`, `app/lib/items.test.ts`,
`__tests__/pages/items/[id].test.tsx`.

```bash
npm run typecheck && npx jest --ci
```
Expected: 0 type errors, all suites green. A remaining `TS2551: Property 'active' does not exist` is
the checklist telling you the truth — fix the site, do not add the old key back to the type.

- [x] **Step 9: Commit, per repo, as one logical change each**

```bash
# backend-quarkus
git add pom.xml scripts/check-runtime-classpath.sh .github/workflows/maven.yml
git commit -m "fix: ship jackson-module-kotlin so the artifact matches the tested wire format"
# frontend_flutter
cd ../frontend_flutter && git add lib/model/data_page.dart lib/model/item.dart lib/model/community.dart test/service/item_service_test.dart
git commit -m "fix: read the is-prefixed boolean fields the backend actually sends"
# frontend-nextjs
cd ../frontend-nextjs && git add app pages __tests__
git commit -m "fix: use the isActive field name the backend serializes"
```

- [x] **Step 10: Record what changed on the wire**

Append the six-key table from Step 6 and the Next.js field name to the spec's *Finding 0* as a
"resolved 2026-11-mm-dd" note, so Phase 2's serialization bullet is read against reality. Commit in
backend-quarkus with the docs commit from Task 10.

---

## Task 7: A local stack that is not a test fixture

Nothing has ever run the realm file through a real Keycloak. `config/quarkus-realm.json` was exported
from Keycloak 20.0.3 (`keycloakVersion` in the file); the code and tests use 26.5.4. And the packaged
jar answers **500 on every route**, including `/q/health`, while Keycloak is down — the policy
enforcer fetches `.well-known/uma2-configuration` per request. So there is no way to run the real
artifact locally today.

**Files:**
- Create: `docker/compose.dev.yml`
- Read: Keycloak startup logs, `config/quarkus-realm.json`

**Interfaces:**
- Produces: `docker compose -f docker/compose.dev.yml up -d` giving Postgres on **5433** (5432 is a
  host process — do not bind it) and Keycloak on **8180** with the `quarkus` realm, client
  `backend-service`, roles `user`/`admin`/`confidential`. Task 8 consumes it.

- [x] **Step 1: Write the compose file**

```yaml
# Local stack for running the packaged artifacts. Not for tests (Testcontainers owns those)
# and not for production. Postgres is on 5433 because a host postgres already owns 5432.
name: comshare-dev
services:
  postgres:
    image: docker.io/library/postgres:17
    environment:
      POSTGRES_USER: quarkus
      POSTGRES_PASSWORD: quarkus
      POSTGRES_DB: quarkus
    ports: ["5433:5432"]
    volumes: ["pgdev:/var/lib/postgresql/data"]
  keycloak:
    image: docker.io/keycloak/keycloak:26.5.4
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    ports: ["8180:8080"]
    volumes: ["../config:/opt/keycloak/data/import:ro"]
volumes: { pgdev: {} }
```

- [x] **Step 2: Start it and check the realm actually imported**

```bash
cd backend-quarkus && docker compose -f docker/compose.dev.yml up -d
sleep 35
docker compose -f docker/compose.dev.yml logs keycloak | grep -iE "Imported|realm imported|ERROR|WARN.*import" | head -20
curl -s localhost:8180/realms/quarkus/.well-known/openid-configuration | head -c 200
```
Expected: the discovery document resolves, and the logs say the realm was imported. Then confirm the
specifics that the clients depend on:
```bash
export PATH="/usr/local/bin:/opt/homebrew/bin:$PATH"
curl -s -u admin:admin "localhost:8180/admin/realms/quarkus/clients" | python3 -c "import json,sys;print(sorted(c['clientId'] for c in json.load(sys.stdin)))"
curl -s -u admin:admin "localhost:8180/admin/realms/quarkus/roles" | python3 -c "import json,sys;print(sorted(r['name'] for r in json.load(sys.stdin)))"
```
(`admin` needs a session for the admin API; if basic auth is refused, use
`/realms/master/protocol/openid-connect/token` to get a token first.)
Expected: `backend-service` among the clients; `user`, `admin`, `confidential` among the roles.

- [x] **Step 3: If the import fails or silently drops something**

Do **not** hand-edit `config/quarkus-realm.json` to make the error go away. Capture the full error,
and either (a) re-export the realm from the 26.5.4 instance after recreating the few settings the app
needs, or (b) stop and write the failure into the spec's Open questions with the exact message. A
realm that imports with a warning today is a 403 three phases from now. Record which path you took.

- [x] **Step 4: Commit**

```bash
git add docker/compose.dev.yml
git commit -m "feat: add a local compose stack for running the packaged artifacts"
```

---

## Task 8: Run the artifacts, once, honestly, and write it down

This is the baseline the whole phase exists to create. Every step records its real output; steps that
cannot be run are written as "not verified", never as assumed-passing.

**Files:**
- Create: `docs/runbook.md`
- Read: everything

**Interfaces:**
- Consumes: Tasks 3, 5, 6, 7
- Produces: `docs/runbook.md` — the commands, the real observed output, and the list of things that do
  not work. Phase 5 copies its container contract from this file.

- [x] **Step 1: Backend, packaged, against the throwaway database**

```bash
cd backend-quarkus && ./mvnw -B -DskipTests package
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/quarkus \
  java -jar target/quarkus-app/quarkus-run.jar 2>&1 | tee /tmp/run-backend.log
```
Expected: `Profile prod activated`, `started in ~2s`, Liquibase applies
`changelog-1.0/2.0/3.0` against the **5433** database. **Check that URL before every run**: pointing
it at 5432 mutates the developer's own `quarkus` database, which happened on 2026-11-23.

- [x] **Step 2: One unauthenticated and one authenticated request**

```bash
curl -s -o /dev/null -w "health=%{http_code}\n" localhost:8080/q/health
curl -s -o /dev/null -w "openapi=%{http_code}\n" localhost:8080/q/openapi
curl -s -X POST localhost:8180/realms/quarkus/protocol/openid-connect/token \
  -d "client_id=backend-service&client_secret=secret&grant_type=password&username=<user>&password=<pw>" | head -c 300
```
With the token: `GET /api/item?pageNumber=0&pageSize=5` and record whether the body's page fields are
`isFirstPage`/`isLastPage` (Task 6 says they should be). If no user exists in the imported realm,
create one through the admin console and record the exact steps you took — that procedure is part of
the runbook, because Phase 6's onboarding depends on it.

- [x] **Step 3: Web, built and served from the build output**

```bash
cd frontend-nextjs && BACKEND_BASE_URL=http://127.0.0.1:8080/api NEXTAUTH_URL=http://localhost:3000 \
  NEXTAUTH_SECRET=dev-only pnpm build && pnpm start
```
Then load `/`, log in through Keycloak, list items, open an item. Record each as pass/fail with the
browser's actual error text for any failure. `NEXTAUTH_SECRET=dev-only` is for this local run only —
say so in the runbook.

- [x] **Step 4: Flutter, against the running stack**

Point `assets/config/config.yaml` at `backendHost: localhost`, `backendPort: 8080`,
`issuer: http://localhost:8180/realms/quarkus` (web/desktop target), rebuild, and run one full
community → item → booking loop. Record what works. If the mobile auth flow cannot be driven on this
machine, write "not verified on 2026-11-mm-dd, reason: …" — that line is a result, not a failure to
report.

- [x] **Step 5: Write `docs/runbook.md`**

Structure: `## One-time setup` (compose up, realm users), `## Backend` (package + run + the 5433
warning), `## Web` (build + start + env vars, referencing `.env.example`), `## Mobile` (config.yaml +
target), `## What does not work` (verbatim list, including the 500-without-Keycloak behaviour and any
Step 2–4 failure), `## Verified` (date + the recorded outputs, trimmed to the decisive lines).

- [x] **Step 6: Commit**

```bash
git add docs/runbook.md
git commit -m "docs: add a runbook for the packaged artifacts with the observed baseline"
```

---

## Task 9: Give the mobile CI the same standard

`frontend_flutter/.github/workflows/dart.yml` runs `dart analyze` and `flutter test`. The project's
gate is `flutter analyze` (Flutter-specific lints that `dart analyze` does not apply), and the format
check that would catch hand-edits to generated files is commented out.

**Files:**
- Modify: `.github/workflows/dart.yml`

**Interfaces:**
- Produces: nothing consumed; a gate only.

- [x] **Step 1: Check locally what the new steps would report**

```bash
export PATH="/usr/local/bin:/opt/homebrew/bin:$HOME/development/flutter/bin:$PATH"
cd frontend_flutter && dart format --output=none --set-exit-if-changed . ; echo "format exit=$?"
flutter analyze; echo "analyze exit=$?"
```
Record both exit codes. If `dart format` reports diffs, format the repo **in its own commit**
(`dart format .`) *before* adding the gate, so the gate commit is not also a 40-file reformat.

- [x] **Step 2: Edit the workflow**

Uncomment the format step, and run `flutter analyze` in addition to `dart analyze`:
```yaml
      - name: Verify formatting
        run: dart format --output=none --set-exit-if-changed .
      - name: Analyze project source
        run: flutter analyze
```
Keep the `flutter test` step.

- [x] **Step 3: Verify and commit**

```bash
python3 -c "import yaml;yaml.safe_load(open('.github/workflows/dart.yml'));print('yaml ok')"
flutter test
git add .github/workflows/dart.yml
git commit -m "build: gate mobile CI on formatting and flutter analyze"
```
(Plus, if Step 1 needed it, a preceding `git commit -m "style: apply dart format"`.)

---

## Task 10: Put the plans and specs under version control

`docs/superpowers/` is gitignored in `backend-quarkus/.gitignore:43` and
`frontend-nextjs/.gitignore:36`, so the design this work implements exists only on one laptop.

**Files:**
- Modify: `.gitignore` (backend-quarkus, frontend-nextjs)
- Add: `docs/superpowers/specs/*.md`, `docs/superpowers/plans/*.md`

- [x] **Step 1: Drop the ignore line in both repos**

Delete the `docs/superpowers/` line. Keep the rest of `.gitignore` as is.

- [x] **Step 2: Look at what is about to be committed before committing it**

```bash
cd backend-quarkus && git status --short docs/
```
Expected: the specs and plans from 2026-07-08/09/11-22/11-23. If anything unrelated is in there,
`git add` the markdown files explicitly rather than the directory.

- [x] **Step 3: Commit in both repos**

```bash
cd backend-quarkus && git add .gitignore docs/superpowers && git commit -m "docs: track the project specs and plans"
cd ../frontend-nextjs && git add .gitignore && git commit -m "docs: track docs/superpowers in version control"
```
`frontend_flutter` has no such line and needs nothing.

---

## Out of scope for this phase

Everything the spec puts later, and specifically: the `@RolesAllowed` / policy-enforcer replacement
and `quarkus.security.jws-trust-all` + `token.issuer=any` (Phase 2 — this phase only makes the
existing authorization *observable*), the `%prod` mailer settings and Qute templates (Phase 4),
proximity (Phase 3), any container image build or registry push (Phase 5 — CI uploads a workflow
artifact, nothing more), and the Next.js App-Router `i18n` warning, which is parked deliberately:
this app is Pages Router and the warning is about a router it does not use.

## Known deviations to report

- Task 3 may end in "stopped and escalated" by design. That is a completed task, not a failed one, but
  it must be named in the report.
- Task 8's mobile step may be unverifiable on this machine. Record the reason.
- If Task 6's Step 6 requires fixture edits in Flutter tests, list each fixture and why the old key
  was in it — that is evidence about which format the team was actually testing against.

---

## Deviations and findings

Recorded while executing (2026-11-23). The per-task measurements, including every command's exit code, are in
the execution ledger at `.superpowers/sdd/2026-11-23-phase1-build-truth/progress.md`; this is what a person
reading only the plan needs to know.

### Task 2 and Task 4: the numbers in the plan were stale

The suite is 149 tests, not 140, so the no-skip gate's floor is 149. Phase 0 added nine tests after the plan
was written; a floor of 140 would have passed while nine tests went missing. Likewise Task 4's baseline was 14
`tsc` errors, not the 12 the plan lists.

### Task 3: the web app did not build *or run*, and it had not since 2023

`app/styles/globals.css` carried a font `@import` on line 66, after other rules. That is what broke
`pnpm build` and `pnpm dev` (Phase 0 recorded the dev symptom). Moving it to line 1 fixes both; the built CSS
chunk and the dev chunk contain the font import. Pre-existing: it came in with 5d2b06b (2023-11-14,
"Implement item booking"). Task 3 was **not** escalated — it completed.

### Task 4: the `Item.active` rename is Task 6's, not Task 4's

Task 4's Step 5 asked for `availability`/`availableUntil` and left `active` for Task 6, which is how it was
done; `tsc` reports 0 errors with the field still named `active`, verified before shipping the rename in the
atomic wire-format commit. Also here: `test/utils/session.ts` (a real `Session` fixture), `request(url,
session: Session | null, …)`, `pnpm typecheck` as a script, and `git diff tsconfig.json` left at zero lines —
`next build` and `tsc` rewrite `tsconfig.json`, `next-env.d.ts` and `tsconfig.tsbuildinfo` on every run, so
those are restored after every build, not committed.

### Task 5: the plan's literal config was applied, built, and reverted

Deleting `BACKEND_BASE_URL` from `next.config.js`'s `env` block (Step 2 as written) does not make the app read
the environment — it makes the *browser* request `undefined/user/item/...`. Measured: with the key removed, 0
chunks anywhere contained the address, and the client bundle compiled the reads to
`${t.default.env.BACKEND_BASE_URL}/user/item/...`; nine client chunks had carried the inlined address before.
The web app calls the API from the browser in item create/edit, booking submit, join/leave and community
create. So the key stays and its value comes from the environment; `BACKEND_BASE_URL=<x> pnpm build` put
`env-wins.example` in 9 server and 9 client chunks. Consequence, stated in the config comment, `.env.example`
and the runbook: the browser-side value is baked at build time, so moving the API means rebuilding. Routing
those calls through Next is the spec's open question on web topology; this phase does not decide it.

Two more measurements worth keeping. Before the change, `next.config.js`'s `env` block beat `.env.local` —
that was the answer to the question the plan asked Task 5 to measure, not assume. And `pnpm build` itself needs
`KEYCLOAK_*` and `NEXTAUTH_SECRET` present (page-data collection imports the nextauth route and dies with
"KEYCLOAK_CLIENT_ID is not set"), so an image build must pass them in even though they are runtime values;
`.env.development` is not read by `next build`. Hence `.env.development` carries the local realm values too —
a committed default that does not boot is not a default.

### Task 6: the artifact did not speak the language the tests assert

`jackson-module-kotlin` was test-scoped, so the shipped jar stripped the `is` prefix from Kotlin boolean
properties: it answered `active`/`owner`/`admin`/`member`/`firstPage`/`lastPage` while the controller tests
asserted `isItemOwner`/`isAdmin` and passed. `scripts/check-runtime-classpath.sh` is the gate (exit 1 before,
0 after) and CI packages, gates and uploads the artifact. The Flutter side had six un-prefixed reads
(`data_page.dart` firstPage/lastPage, `community.dart` admin/member, `item.dart` active/owner) while
`Item.toJson()` already wrote `isActive` — a mobile-created item therefore lost the flag, `ItemRequestDto`
defaulted it to false, `ItemService` lists only active items, and the item appeared in nobody's feed. The web
side needed one field, one read and two writes. One logical change, three commits: backend `d7f5b77`, Flutter
`0c8a93e`, web `29c1a96`.

The Flutter fixtures encoded *both* formats at once — `item_service_test.dart`'s single-item response said
`'active'` while its page fixtures were built with `Item.toJson()` and so said `'isActive'` — which is why no
test ever saw the mismatch. Full fixture list in the ledger. The two round-trip tests were written first and
watched failing (`Expected: true, Actual: <null>`) before the model changed: the plan put them after the fix,
and that order was flipped.

Task 6's own step ordering also changed for a reason: the Flutter regression tests are TDD-red only if they
precede the model change, so Steps 6 and 7 swapped.

### Task 7: nothing had to be escalated

The 20.0.3-era `config/quarkus-realm.json` imports into Keycloak 26.5.4 cleanly and keeps everything the
clients need (clients, the `user`/`admin`/`confidential` roles, the three realm users; `jdoe`'s password grant
returns a token), so Step 3 did not fire and the realm file was not hand edited. Note for whoever reads the
admin API next time: `curl -u admin:admin` is refused; you need a master-realm token from the admin-cli client.

### Task 8: what does not work, found by running it

- **`GET /q/health` is 404 — always, not only when Keycloak is down.** There is no `quarkus-smallrye-health`
  dependency, so the artifact has no health endpoint at all. Phase 5 needs one for container healthchecks.
- **`isOwner` is `false` on `GET /api/user/item/{uuid}` even for the owner.** `ItemDetailDto.isOwner` defaults
  to false and `ItemController.getItem` builds the DTO from the model alone, so it never learns who is asking.
  The mobile item screen gates the booking form and the booked-dates list on that flag, so your own item looks
  like a stranger's. Left for Phase 2's read authorization, which hands those endpoints the requester's
  identity anyway; recorded in the runbook.
- Empty `GET /api/user/item/my` and `/user/community/{uuid}/item` next to a non-empty `/user/item/owned` is
  **not** a defect: `getItemsPageOfCommunities` filters `userUuid <> :me` on purpose — those are "what can I
  borrow" lists.
- **Mobile runtime is not verified.** No Android SDK on this machine (`ANDROID_HOME` unset, no `sdk.dir`, no
  emulator), so no APK build. `flutter test` (149) and `flutter analyze` are green and the wire format the app
  reads was verified over HTTP. `assets/config/config.yaml` already pointed at this stack, so the setup edit
  the pre-flight promised to revert never had to happen.
- **The scripted login used for the web walk failed** (Keycloak 26 answered the credential form POST with 400).
  A real browser authenticated the same realm and client in Phase 0, so this is the script's limit, not the
  app's — but it means a human browser pass (sign in, list, open, create, book) is still owed on this build.
  What was verified without a browser: 17 routes built, `pnpm start` serves and its server render calls the
  backend, and the built bundle carries the inlined backend address (18 chunks) and reads `isActive` (4).

What the task existed to prove, on the packaged artifact: pages answer `isFirstPage`/`isLastPage`, communities
answer `isAdmin`/`isMember`, and a `POST /api/user/item/` carrying `"isActive": true` comes back stored — the
request direction, which no test in the repo could demonstrate.

### Task 9: the mobile CI could not have been passing

`lib/gen_l10n/` is generated from `lib/l10n/*.arb`, is gitignored (since 904ff99, 2026-05-03) and was never
tracked. Neither `flutter analyze` nor `flutter test` runs the generator: on a simulated fresh checkout,
analyze reported 162 errors and test failed to load 27 test files. So the workflow gained a `flutter gen-l10n`
step the plan does not mention — a gate that cannot go green is worse than no gate. Verified in CI order from
that state: pub get, gen-l10n (output byte-identical to what was on disk), format check, analyze, test 149
passed — all exit 0.

Enabling the format gate cost two extra commits: `dart format .` in the Dart 3.11.5 style touched 85 files
(`f2cfaaf`, pure tool output — skip it in review), and tall style joins a single-statement `if` onto one line,
which trips this repo's own `curly_braces_in_flow_control_structures` at 10 sites; those got braces in
`4c50f26`, behaviour unchanged. Beware when running the gate locally: `/usr/local/bin/dart` is a standalone
Dart 2.17.3 that formats differently and is not what CI runs, so `flutter/bin` must come first in `PATH`.
Neither Flutter nor Dart is pinned in the workflow, so a future stable can reflow the repo and turn CI red
with no code change — pinned with the Phase 5 images.

### Task 10: one line ignored the whole design history

Removed from `.gitignore` in both `backend-quarkus` (7 plans + 5 specs became tracked) and `frontend-nextjs`
(1 spec). The plan's Step 3 only staged `.gitignore` in the web repo, which would have left the spec
untracked; `docs/superpowers` was added there too. The scratch ledger stays out — `.superpowers/sdd/.gitignore`
contains `*`. Checked before committing: no production secrets in these documents, only the local realm's
already-committed dev credentials.

### Late addition, not a plan step: the web CI built nothing

`## File Structure` lists `.github/workflows/node.js.yml` as a file this phase touches, but no task ever wires
it: the job runs `pnpm install` and `pnpm run test-ci`, and nothing else. No `tsc`, no `next build` — which is
precisely how the CSS break Task 3 fixed could sit in the repo since 2023 with a green badge, and the 14 type
errors with it. The spec's Phase 1 asks for both steps explicitly, so they are added (`cd6f4ee`), verified by
simulating a runner: with `.env.local` moved aside and only the step's `env` block, `pnpm run build` exits 0
with 17 routes; without the four values it dies with "KEYCLOAK_CLIENT_ID is not set".

Related and deliberately not done: no CI job anywhere *boots* the artifact. The classpath gate proves the
runtime has the Kotlin module, and Task 8 proved the wire format by hand. The automated boot-and-read-one-`is*`-field
check is Phase 5's, because that is the first job that runs the artifact automatically. This is recorded in the
spec's Phase 1 status note so it is not inherited as a surprise.

### Still open, and not this phase's to answer

- **Spec open question 16**: whether Phase 0's retroactive approval backfill (`mario:7`) flips items that
  already exist with `requiresApproval = false`. G11 covers the new default, not the data rewrite.
- The pre-existing `frontend-nextjs` stash `6ffabe2` ("On feature/basic-error-handling: temp") — drop or keep.
- `pages/index.tsx` still fetches `/user/item/image/null`; Phase 2c.
- `./mvnw quarkus:dev` still cannot run (wrapper 3.8.4, Quarkus 3.33 needs >= 3.9.0). Phase 1 did not schedule
  it; it is the reason the runbook runs the packaged jar.
