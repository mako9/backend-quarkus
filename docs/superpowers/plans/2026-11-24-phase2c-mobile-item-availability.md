# Mobile Item Availability Implementation Plan (Phase 2c)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Flutter app able to open, create and save an item against the real backend, by giving `Item.availability` the type the backend actually sends.

**Architecture:** One new value type, `ItemTimeInterval`, that carries the four wire fields verbatim — the app is a faithful carrier, not an editor. `Item` parses the array into it and writes it back out unchanged, and the item edit screen stops dropping the windows it was not asked to edit. Nothing in the backend changes, and no widget displays an availability window yet.

**Tech Stack:** Flutter 3.41.9 / Dart 3.11.5 (`flutter_test`, `mockito` via `mock.mocks.dart`), Quarkus/Jackson backend as the wire contract, packaged-jar verification against `docker/compose.dev.yml`.

**Spec:** `backend-quarkus/docs/superpowers/specs/2026-11-22-pilot-readiness-design.md` — Phase 2's *Flutter* list, "Availability type" bullet (line ~695), and Finding 0's resolution note. Plan 2c, reordered ahead of plans 2 and 2b on 2026-11-24.

## Global Constraints

- **Toolchain: Flutter 3.41.9 / Dart 3.11.5**, the versions `.github/workflows/dart.yml` pins. Put `$HOME/development/flutter/bin` first in `PATH` — `/usr/local/bin/dart` on the authoring machine is a standalone Dart 2.17.3 that formats differently.
- `dart format --output=none --set-exit-if-changed .` must pass, `flutter analyze` must report **No issues found**, `flutter test` must stay green. Do **not** add `--no-fatal-infos` to anything.
- The wire shape is fixed by `backend-quarkus/src/main/kotlin/api/dto/ItemDto.kt:86-91` — `ItemTimeIntervalDto(startDayOfWeek: Int, endDayOfWeek: Int, startTimeAt: OffsetTime, endTimeAt: OffsetTime)`, serialized by the artifact as `{"startDayOfWeek":1,"endDayOfWeek":3,"startTimeAt":"09:00Z","endTimeAt":"17:00Z"}`. Days are `java.time.DayOfWeek`: **1 = Monday … 7 = Sunday**, never 0-based.
- Times are passed through as **strings, byte-identical**. No parsing, no reformatting, no local-time conversion, no assumption that the offset is `Z`.
- `availability: []` means *no restriction*, not *never bookable* — `ItemService.kt:485` guards with `itemModel.availability.isNotEmpty() &&`. The app therefore always sends a list, never `null` and never an omitted key, because `PATCH` replaces the column (`ItemController.kt:138`).
- No new localization keys, no widget changes, nothing under `lib/gen_l10n/` (generated; `flutter gen-l10n` owns it).
- No backend code changes in this plan. No money, ever.
- Never `git add -A`, never `git stash`, no `Co-Authored-By` or AI attribution in commit messages, never edit `~/.gitconfig`.
- Work happens on branch `feature/mobile-item-availability` in `frontend_flutter` and `backend-quarkus`, stacked on `feature/complete-booking-logic` (the branch PRs #19/#23/#15 are reviewing). Do not commit to the branch under review.
- A packaged-artifact run must use the compose stack's postgres on **5433**; it defaults there since `d50e638`. `./mvnw test` is the one thing that legitimately uses the host postgres on 5432.

**Not in scope, deliberately:** an availability editor on mobile (plan 2d — until it exists, a phone-created item has no lending window and is bookable for any date, and the owner sets a window on the web); `custom_paginated_list.dart`'s broken controls; the `?isOwned=` query parameter; `availableUntil` being cleared on edit; token refresh; the `item.dart`/`data_page.dart` `is*` renames (Phase 1 Task 6).

---

### Task 1: `ItemTimeInterval` and a faithful `Item.availability`

**Files:**
- Create: `frontend_flutter/lib/model/item_time_interval.dart`
- Modify: `frontend_flutter/lib/model/item.dart:19` (field), `:40` (constructor), `:63` (`fromJson`), `:80` (`toJson`)
- Test: `frontend_flutter/test/model/item_test.dart`
- Test: `frontend_flutter/test/service/item_service_test.dart` (fixtures that hand the model a `String`)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces:
  - `class ItemTimeInterval` in `package:frontend_flutter/model/item_time_interval.dart` with `final int startDayOfWeek; final int endDayOfWeek; final String startTimeAt; final String endTimeAt;`, a positional-or-named constructor matching those four names, `factory ItemTimeInterval.fromJson(Map<String, dynamic> json)`, `Map<String, dynamic> toJson()`, and value equality (`operator ==` + `hashCode`) so tests and callers can compare it.
  - `Item.availability` is `final List<ItemTimeInterval> availability`, defaulting to `const []` in the constructor (non-nullable — "we have no windows" and "the server said nothing" must not be two states, because both serialize the same way).
  - `Item.fromJson` maps `json['availability']` through `ItemTimeInterval.fromJson`, treating an absent or `null` value as `const []`.
  - `Item.toJson` writes `'availability': availability.map((e) => e.toJson()).toList()` — always a JSON array.
  - `ItemEditScreen` (Task 2) relies on `Item(…, availability: …)` accepting a `List<ItemTimeInterval>`.

- [ ] **Step 1: Write the failing parse test**

Add to `frontend_flutter/test/model/item_test.dart`. The literal body is the packaged artifact's answer, measured 2026-11-23 and recorded in `backend-quarkus/docs/runbook.md` ("What does not work" 8); do not "tidy" it.

```dart
test('when parsing the availability the artifact sends, then the four fields survive', () {
  final item = Item.fromJson({
    'uuid': 'i1',
    'name': 'pressure washer',
    'availability': [
      {'startDayOfWeek': 1, 'endDayOfWeek': 3, 'startTimeAt': '09:00Z', 'endTimeAt': '17:00Z'}
    ],
  });

  expect(item.availability, [
    ItemTimeInterval(startDayOfWeek: 1, endDayOfWeek: 3, startTimeAt: '09:00Z', endTimeAt: '17:00Z'),
  ]);
});
```

- [ ] **Step 2: Run it and watch it fail the way the app fails in the field**

Run: `flutter test test/model/item_test.dart` (with the pinned toolchain on `PATH`)
Expected: FAIL. Today it is `type 'List<dynamic>' is not a subtype of type 'String?'` — after the field's type changes and before the class exists, it is a compile error naming `ItemTimeInterval`. Either way it must fail *before* `lib/` is touched, and if it fails with something else, stop and work out why.

- [ ] **Step 3: Write the remaining five failing tests**

Still in `test/model/item_test.dart`. One assertion each; the `import` line gains `item_time_interval.dart`.

```dart
// Review Focus 4: a Sunday window in a +01:00 offset is the case a well-meaning
// DateTime.roundTrip() or a 0-based day index breaks. Strings, not DateTimes.
test('an item written by toJson is read back by fromJson with its availability intact', () {
  final item = Item(name: 'ladder', availability: const [
    ItemTimeInterval(startDayOfWeek: 7, endDayOfWeek: 1, startTimeAt: '08:30+01:00', endTimeAt: '12:00+01:00'),
    ItemTimeInterval(startDayOfWeek: 2, endDayOfWeek: 4, startTimeAt: '09:00Z', endTimeAt: '17:00Z'),
  ]);

  expect(Item.fromJson(item.toJson()).availability, item.availability);
});

// Review Focus 1: the create path. This is the HTTP 400 — "availability": null.
test('a new item sends an empty availability list, never null', () {
  expect(Item(name: 'drill').toJson()['availability'], isEmpty);
  expect(Item(name: 'drill').toJson().containsKey('availability'), isTrue);
});

// Review Focus 2: page payloads may carry no availability at all.
test('an item response without the availability key parses to no windows', () {
  expect(Item.fromJson({'uuid': 'i1', 'name': 'drill'}).availability, isEmpty);
});

// Review Focus 5: a server that changed under the app must not silently eat a window.
test('an interval whose keys the DTO does not know is rejected, not ignored', () {
  expect(
    () => ItemTimeInterval.fromJson({'startAt': '09:00Z', 'endAt': '17:00Z'}),
    throwsA(isA<ArgumentError>()),
  );
});

// The cross-language pin: these four spellings live in ItemDto.kt:86-91 and in this
// list, and no test on either side of the network can see the other.
test('an interval serializes exactly the four keys the DTO declares', () {
  expect(
    const ItemTimeInterval(
            startDayOfWeek: 1, endDayOfWeek: 3, startTimeAt: '09:00Z', endTimeAt: '17:00Z')
        .toJson()
        .keys
        .toSet(),
    {'startDayOfWeek', 'endDayOfWeek', 'startTimeAt', 'endTimeAt'},
  );
});
```

- [ ] **Step 4: Run all six and watch them fail**

Run: `flutter test test/model/item_test.dart`
Expected: FAIL — currently a compile error on the unknown `ItemTimeInterval` (and, once it exists, `type 'List<dynamic>' is not a subtype of type 'String?'` from `item.dart:63` for the parse tests).

- [ ] **Step 5: Create `lib/model/item_time_interval.dart`**

`fromJson` requires all four keys and throws `ArgumentError` naming what is missing — the backend answers HTTP 400 for an interval with unknown keys, so an interval the client cannot name is a contract break, not a thing to drop quietly. `toJson` emits exactly the four keys. Equality is by all four fields.

- [ ] **Step 6: Change `Item` to carry it**

`lib/model/item.dart`: the field becomes `final List<ItemTimeInterval> availability;`, the constructor gains `this.availability = const []`, `fromJson` becomes `availability: (json['availability'] as List?)?.map((e) => ItemTimeInterval.fromJson(e as Map<String, dynamic>)).toList() ?? const []`, and `toJson` becomes `'availability': availability.map((e) => e.toJson()).toList()`. Add the import. Add a doc comment on the field saying that `[]` means unrestricted (`ItemService.kt:485`) and that the times are passed through untouched, because a future reader will otherwise be tempted to parse them.

- [ ] **Step 7: Run the model tests to green**

Run: `flutter test test/model/item_test.dart`
Expected: PASS, 9 tests — the 6 above plus the 3 `requiresApproval` tests already in the file, which must still pass untouched.

- [ ] **Step 8: Fix the two fixtures that hand the model a `String`**

In `frontend_flutter/test/service/item_service_test.dart`:
- `:31` `availability: null,` → a one-window list, so the shared fixture exercises the real shape rather than the degenerate one.
- `:45` `'availability': item.availability,` → `'availability': item.availability.map((e) => e.toJson()).toList(),` — a `List<ItemTimeInterval>` is not JSON.
- `:61` `'availability': 'otherFirst',` → `'availability': [],`. That fixture is a page entry for an item with no window, and a bare `String` was never a possible server answer; leaving it would keep alive the assumption this plan exists to kill.

- [ ] **Step 9: Run the whole suite**

Run: `flutter test`
Expected: PASS, 149 + 6 new = 155 tests. Any failure elsewhere is a call site that treated `availability` as a `String`; `grep -rn "availability" lib test` should then show only `lib/model/item.dart`, `lib/model/item_time_interval.dart` and the two test files.

- [ ] **Step 10: Commit**

```bash
git add lib/model/item_time_interval.dart lib/model/item.dart test/model/item_test.dart test/service/item_service_test.dart
git commit -m "fix: give Item.availability the type the backend sends"
```

The message body must say, in the author's own words: the field was `String?` and received a JSON array, so **every** item response threw and mobile item detail has never worked; `toJson` sent `null`, which the artifact answers with 400, so mobile create/edit has never worked either; both were reproduced against the pre-Phase-1 artifact, so this is inherited, not new.

---

### Task 2: Saving an edited item keeps the windows the server sent

**Files:**
- Modify: `frontend_flutter/lib/widget/item/item_edit_screen.dart:292-308` (the `Item(...)` built on Save)
- Test: `frontend_flutter/test/widget/item_edit_screen_test.dart`

**Interfaces:**
- Consumes: `Item.availability` as `List<ItemTimeInterval>` and `ItemTimeInterval`'s value equality from Task 1.
- Produces: nothing another task uses. This task owns the rule "the form only sends what the form shows".

- [ ] **Step 1: Write the failing widget test**

The file already wraps the screen in `AppLocalizations` with `Locale('en', '')`, registers `MockItemService` in `getIt`, and constructs the screen as `ItemEditScreen(item: saved)` — follow `editing an item shows the approval the item actually has` for the scaffolding, and add:

```dart
testWidgets('saving an edited item keeps the lending windows the server sent',
    (WidgetTester tester) async {
  const windows = [
    ItemTimeInterval(
        startDayOfWeek: 1, endDayOfWeek: 3, startTimeAt: '09:00Z', endTimeAt: '17:00Z')
  ];
  final saved = Item(uuid: 'i2', name: 'Ladder', availability: windows);
  when(mockItemService.getItem('i2')).thenAnswer(
      (_) async => DataResponse<Item>(data: saved, errorMessage: null));
  when(mockItemService.getItemImage('i2')).thenAnswer(
      (_) async => DataResponse<Uint8List?>(data: null, errorMessage: null));
  when(mockItemService.updateItem(any)).thenAnswer((_) async =>
      DataResponse<Item>(data: saved, errorMessage: null));

  await tester.pumpWidget(_wrap(ItemEditScreen(item: saved)));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Save'));
  await tester.pumpAndSettle();

  final sent = verify(mockItemService.updateItem(captureAny)).captured.single as Item;
  expect(sent.availability, windows);
});
```

`find.text('Save')` is verified, not guessed: `lib/l10n/app_en.arb:3` is `"save": "Save"`, and `CustomButton` renders its title in a `Text` (`custom_button.dart:54`), so the label is findable in the test's `Locale('en','')` wrapper.

- [ ] **Step 2: Run it and watch it fail**

Run: `flutter test test/widget/item_edit_screen_test.dart`
Expected: FAIL with `Expected: [...]` / `Actual: []` — the screen builds its `Item` from form fields only, so `availability` is the constructor default. If it fails any other way, find out why before continuing.

- [ ] **Step 3: Carry the windows through the save**

In `lib/widget/item/item_edit_screen.dart`, inside `final updatedItem = Item(`, add `availability: _item?.availability ?? const [],` next to `uuid: _item?.uuid`. One line. The reason belongs in the commit, not a comment: `PATCH` replaces the column from `ItemRequestDto`, whose `availability` defaults to `emptyList()`, so a body that omits windows the form never displayed **erases the lending window the owner set on the web**.

- [ ] **Step 4: Run the file to green, then the suite**

Run: `flutter test test/widget/item_edit_screen_test.dart && flutter test`
Expected: PASS both.

- [ ] **Step 5: Commit**

```bash
git add lib/widget/item/item_edit_screen.dart test/widget/item_edit_screen_test.dart
git commit -m "fix: editing an item no longer clears its lending windows"
```

---

### Task 3: Prove the shape against the packaged artifact, and write down what it said

Every claim in this plan about the wire comes from a measurement made while writing Phase 1. This task re-measures them on the code the fix will ship beside, so the Dart tests are pinned to an observed artifact rather than to a remembered one. No code changes; the deliverable is the runbook.

**Files:**
- Modify: `backend-quarkus/docs/runbook.md` — items 8 and 9 of *What does not work*, and the *Verified* section

**Interfaces:**
- Consumes: Task 1's exact JSON, unchanged.
- Produces: measurements that Task 1's test literals and the spec's Phase 2c bullet cite.

- [ ] **Step 1: Bring up the stack and the jar**

```bash
cd backend-quarkus
docker compose -f docker/compose.dev.yml up -d
./mvnw -B -DskipTests package
java -jar target/quarkus-app/quarkus-run.jar      # no datasource env needed; defaults to 5433
```

Expected: `Profile prod activated`, Liquibase quiet (already migrated), `curl -s localhost:8080/q/openapi` answers YAML. If the jar writes to the host postgres on 5432, stop: that is the `d50e638` regression.

- [ ] **Step 2: Get a token as a seeded realm user**

Use the recipe already in the runbook's *Backend* section (`POST localhost:8180/realms/quarkus/protocol/openid-connect/token` with the `backend-service` client and a seeded user). The runbook records that the *web's* scripted login got HTTP 400 from Keycloak 26 while this one works — if this one now 400s too, say so in the runbook instead of working around it, and continue Task 3 by hand from a browser session cookie.

- [ ] **Step 3: Send exactly what the fixed app sends, and record the answers**

Three requests, bodies verbatim from Task 1's model output (`"availability": []` for the create, one real window for the update):

| Request | Expected |
|---|---|
| `POST /api/user/item/` with `"availability": []` | 200/201, and `GET /api/user/item/{uuid}` answers `"availability": []` |
| `PATCH /api/user/item/{uuid}` with the one-window array | the `GET` afterwards answers `[{"startDayOfWeek":1,"endDayOfWeek":3,"startTimeAt":"…","endTimeAt":"…"}]` — the same four keys, the day numbers unchanged |
| `PATCH /api/user/item/{uuid}` **with the key omitted** | documents the hazard Task 2 exists for: the windows are gone afterwards. Verify by `GET` before and after, and write the result down whichever way it goes |

The third row is the one that may surprise: if omitting the key turns out *not* to clear the windows, Task 2's fix is still right but its justification changes, and the plan's and runbook's wording must change with it. Do not record an assumption as a measurement.

- [ ] **Step 4: Update the runbook**

Mark *What does not work* 8 and 9 as fixed-by-plan-2c with the commit sha and the date, naming the three requests above and their answers; add the same to *Verified*. Keep the "verified against the pre-Phase-1 artifact too" sentence — it is what proves the defect was inherited. Note in the same edit that the artifact-level automated check (boot the jar, read one `is*` field, POST one item) is still Phase 5's, since no CI job boots the artifact.

- [ ] **Step 5: Tear down and commit**

```bash
pkill -f "quarkus-app/quarkus-run.jar"
docker compose -f docker/compose.dev.yml down
git add docs/runbook.md
git commit -m "docs: record the availability measurements behind plan 2c"
```

- [ ] **Step 6: Run every gate on both repos**

```bash
cd frontend_flutter && dart format --output=none --set-exit-if-changed . && flutter analyze && flutter test
cd ../backend-quarkus && ./scripts/ci-check-tests.sh   # after ./mvnw -B clean test, if any backend file changed
```

Expected: format 0, `No issues found`, 155 tests passing. This plan touches no backend code, so the backend suite only has to be re-run if Task 3's measurements turned out to contradict the spec.

---

## Self-Review Notes

- **Spec coverage.** The Phase 2c bullet's two open questions are answered as Global Constraints (send `[]`; empty is unrestricted) and its "Flutter model plus the fixtures" scope is Task 1. The bullet's remaining items (`custom_paginated_list.dart`, `notification_screen.dart`, `item_edit_screen.dart`'s `availableUntil`/community-index bugs, `setting_screen.dart`'s "Logout", `app_en.arb`'s "Elektric device", the Next.js stubs) belong to plan 2d and are named as out of scope rather than dropped — the spec's 2d row says so.
- **Review Focus coverage.** 1 → Task 1 Step 3 (`a new item sends an empty availability list`) and Step 1; 2 → Step 3 (`without the availability key`); 3 → Task 2 Step 1; 4 → Step 3 (`its availability intact`); 5 → Step 3 (`rejected, not ignored`). The `PATCH`-clears-the-window hazard is reviewed by Task 3 Step 3's third row, which is a measurement, not a test.
- **Type consistency.** `ItemTimeInterval(startDayOfWeek:, endDayOfWeek:, startTimeAt:, endTimeAt:)` is used identically in Task 1's tests, Task 1's implementation, Task 2's test, and Task 3's requests.
