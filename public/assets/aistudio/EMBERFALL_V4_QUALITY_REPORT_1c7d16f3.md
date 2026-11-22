# EMBERFALL v4 — Full Source Quality Audit

**Target:** `emberfall-4.zip`  
**Archive size:** 10,746,201 bytes  
**SHA-256:** `fca382562bf08b8c086158950a69b169d03ffa4679370909117a9fa975593d7d`  
**Review date:** 21 November 2026  
**Review type:** source-level audit + locally executable Kotlin/JVM tests + adversarial engine probes  
**Code changes:** none; review only

---

## 1. Executive verdict

**Current quality: 61/100 — substantial playable-engine prototype, not a release candidate.**

The pure Kotlin domain engine is materially stronger than the previous quick review implied. It compiles independently, its 30 engine tests pass, and its catalog is structurally complete at the documented counts. The procedural audio DSP also compiles and passes its three tests.

However, adversarial execution reproduced four gameplay defects, including incorrect simultaneous-lethal resolution, shortened Seal duration, a board-rescue routine that can return an unplayable board, and an Unlit Dread-ending streak that survives a branded Ash pop which spends Vigil.

There are also significant save/restore, campaign-memory, audio lifecycle, navigation, and release-engineering risks.

**Decision: HOLD release approval.** Fix the P1 defects, add regression tests, reconcile rule authority, then run the full Android/Compose/Room suite in a real Android build environment.

---

## 2. Scope and review coverage

Archive inventory:

| Item | Count |
|---|---:|
| Total files | 108 |
| Production Kotlin files | 28 |
| Production Kotlin LOC | 12,516 |
| Kotlin test files | 5 |
| Test LOC | 1,691 |
| Test methods discovered | 44 |
| XML resources/config files | 10 |
| JSON files | 4 |

The source review covered the game engine, board algorithms, seeded RNG, campaign generation, content catalog, simulation runner, persistence codec/repository/database, DataStore settings, ViewModel, navigation, combat and encounter UI, audio synthesizer/controller, Gradle configuration, manifest/resources, test sources, and Maestro workflow definitions.

### Static integrity checks executed

| Check | Result |
|---|---|
| Pure engine Android/AndroidX/Room imports | 0 |
| XML parse | 10/10 valid |
| JSON parse | 4/4 valid |
| Referenced drawable/string/font resources | No missing references |
| Card IDs | 65 unique |
| Relic IDs | 22 unique |
| Remnant IDs | 6 unique |
| Enemy IDs | 20 unique |
| Event IDs | 12 unique |
| Relic hooks | All 22 have non-catalog source references |
| Kotlin engine compilation with `-Werror` | PASS |
| Pure audio DSP compilation with `-Werror` | PASS |

The catalog breadth is a real positive: 65 cards, 22 relics, 6 remnants, 20 enemies and 12 events are present. All 22 relics have code hooks; this is stronger than a decorative catalog.

---

## 3. Executed verification

### 3.1 Pure Kotlin compilation

Compiled all nine files under:

`app/src/main/java/com/example/engine/`

Command:

```bash
kotlinc -Werror app/src/main/java/com/example/engine/*.kt -d engine.jar
```

**Result: PASS.**

Compiled:

`app/src/main/java/com/example/audio/MinimalTechnoSynth.kt`

with `-Werror`.

**Result: PASS.**

Compiler: Kotlin/JVM 1.9.0, JRE 21.

### 3.2 Existing tests executed locally

The archive has 44 test methods in total. I executed the tests that can run against the available pure JVM sources:

| Test source | Executed | Result |
|---|---:|---|
| `EmberfallEngineTest.kt` | 30 | 30 PASS |
| `MinimalTechnoSynthTest.kt` | 3 | 3 PASS |
| `ExampleUnitTest.kt` | 1 | 1 PASS |
| **Total executed** | **34** | **34 PASS** |

Because Gradle and the project's JUnit dependency resolution were unavailable, these tests were compiled and invoked using a small local JUnit-compatible annotation/assertion shim and reflection runner. This is not represented as an official Gradle/JUnit run.

Not executed:
- 9 Robolectric/Compose tests in `ExampleRobolectricTest.kt`.
- 1 Android instrumented test.
- Android app compilation, Room integration tests and device playback.

### 3.3 Adversarial engine probes

Four additional executable probes were compiled against the actual engine classes and run. They reproduced:

```text
SEAL_AFTER_APPLICATION expected=3 actual=2 all={2=2}

SIMULTANEOUS_LETHAL hp=0 gameOver=null enemyHp=0
pendingReward=true phase=COMBAT_WON

NO_MOVE_RESCUE didReshuffle=true hasLegalMove=false occupied=2

DREAD_AFTER_BRANDED_ASH tapStreak=2 tapHp=98
afterEndRunWon=true dreadEnding=true streak=3
```

These are actual outputs from the v4 engine, not hypothetical examples.

---

## 4. P1 — Release-blocking defects

### EFB-001 — Player death is overridden by enemy death

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`  
**Location:** `resolvePostAction()`, lines 1443–1449; call paths include `handleTapCell()` and `handleUseFlask()`.

Current order:

1. If enemy HP is zero, return `handleCombatVictory`.
2. Otherwise call `checkGameOver`.

A player can reach zero HP from Brand's Vigil shortfall while the same Tap kills the enemy. Likewise, a Flask action can consume the final Flask while Alchemist Coil damage kills the enemy.

The engine then returns a combat victory with player HP zero and no `gameOverReason`.

**Reproduced:** yes. The adversarial Tap returned `hp=0`, `gameOver=null`, `enemyHp=0`, `pendingReward=true`, `phase=COMBAT_WON`.

**Required fix:** centralize terminal arbitration. Player-death conditions must be evaluated before victory is awarded, or an explicit simultaneous-outcome rule must be approved and consistently applied. Add simultaneous lethal tests for Brand and last-Flask/Alchemist Coil.

### EFB-002 — Enemy-applied Seal lasts two player turns, not three

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`  
**Locations:** Seal is applied at lines 1227–1235; cleanup decrements all seals at lines 1333–1337.

The Enemy phase sets `turns=3`, then Cleanup immediately subtracts one before the player receives the next turn. The player therefore experiences only two full subsequent turns with the column locked.

The current test seeds a column that is already sealed and verifies its decay. It does not test the enemy-application sequence.

**Reproduced:** yes. An enemy applying `SealColumn(3)` leaves `{column=2}` after the same EndTurn.

**Required fix:** duration accounting must exclude the application turn. Add a test that applies Seal through an enemy intent and verifies three complete player turns.

### EFB-003 — Board rescue can return a board with no legal move

**File:** `app/src/main/java/com/example/engine/BoardEngine.kt`  
**Location:** `ensurePlayableMove()`, lines 578–641.

The algorithm tries 24 swaps and then three discard/redraw passes. After the third pass it returns without verifying `hasAnyValidMove()`.

This contradicts the method's documented guarantee and the engine contract's no-move policy.

**Reproduced:** yes. A deliberately constrained board with two separated tiles and six sealed columns returned `didReshuffle=true` while `hasAnyValidMove=false`.

**Required fix:** make the final fallback mathematically guarantee a legal group under all supported board states, or return an explicit unrecoverable-board result that the engine handles safely. Do not claim success when the postcondition is false. Add adversarial tests for heavily sealed, sparse and malformed boards.

### EFB-004 — Dread streak survives a branded Ash pop that spends Vigil

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`  
**Locations:** Brand Vigil deduction lines 528–546; streak reset lines 661–663; Dread evaluation in EndTurn around lines 1280–1330.

`nextDreadStreak` resets only when `nonAshPopped` is true. A direct Ash-only pop with a branded block can spend Vigil and still preserve the existing Dread streak.

Under the previously established ADR-017 rule, Vigil burn is a streak-breaking action. The current archive's summary documents list card, Flask, non-Ash and overflow resets but omit Vigil burn. This is both an implementation defect against the prior rule baseline and a source-authority discrepancy that must be reconciled explicitly.

**Reproduced:** yes. Starting at streak 2, a branded Ash-only Tap spent 2 HP in place of Vigil; EndTurn then awarded the Dread ending.

**Required fix:** if ADR-017's Vigil-burn reset remains authoritative, track Vigil expenditure this turn and reset the streak. Add a branded-Ash regression test and a separate test for any other source of Vigil burn.

### EFB-005 — Continuing after a used Campfire can deadlock the map

**Files:**
- `app/src/main/java/com/example/ui/viewmodel/EmberfallViewModel.kt`, lines 561–572.
- `app/src/main/java/com/example/engine/GameEngine.kt`, lines 101–108.

After a Campfire action, the run is persisted with `inNonCombatNode=true` and `campfireUsed=true`. On process restart, `resolveDestinationForRun()` routes to `ActMap` because the Campfire route is only selected when `!campfireUsed`.

But `availableNodeIds()` returns an empty set whenever `inNonCombatNode` is true. The player is restored to the map with no available nodes and no Campfire leave control.

**Required fix:** restore the Campfire screen whenever the saved run is still inside a Campfire node, even if its rest/upgrade has already been used. The screen must show the completed action as unavailable and retain the Leave action. Add process-death/continue coverage.

### EFB-006 — Successful runs overwrite the previous dead deck used by Shade

**Files:**
- `app/src/main/java/com/example/data/EmberfallRepository.kt`, lines 51–84.
- `app/src/main/java/com/example/ui/viewmodel/EmberfallViewModel.kt`, lines 99–123.

`recordFinishedRun()` always writes the just-finished deck into `ShadeDeckEntity`, regardless of `run.runWon`. The next run always loads that saved deck as its Shade memory.

The established Shade premise is the previous **dead** deck. A successful run should not replace the previous dead deck unless that rule has deliberately changed.

**Required fix:** preserve the prior Shade deck on a victory; update it only for the qualifying defeat/ending state defined by ADR-016. Store enough metadata to make this rule explicit and test win-after-loss and loss-after-win sequences.

### EFB-007 — v2 snapshot migration can misclassify a map save as an active non-combat node

**File:** `app/src/main/java/com/example/data/RunSnapshotCodec.kt`  
**Location:** lines 119–129, especially line 125.

The migration infers `inNonCombatNode` using:

```kotlin
hasShop || (!hasCombat && !hasReward &&
    !migrated.isNull("currentNodeId") &&
    !migrated.optBoolean("campfireUsed", false))
```

A normal map save after a completed combat has a non-null current node, no combat, no pending reward and `campfireUsed=false`. It is therefore migrated as `inNonCombatNode=true`.

`availableNodeIds()` then returns an empty set. The migrated campaign is locked.

**Required fix:** infer state from the actual current node type and the legacy node-specific fields. Add v2 fixtures for map-after-combat, active/used Campfire, Merchant, Event, Combat and Reward states.

### EFB-008 — Brand shuffle consumes RNG but discards the resulting RNG state

**Files:**
- `app/src/main/java/com/example/engine/BoardEngine.kt`, lines 836–860.
- `app/src/main/java/com/example/engine/GameEngine.kt`, lines 1198–1201.

`brandTiles()` calls `rng.shuffled(candidates)` and discards the returned RNG. The caller advances `nextBoardRng` by only one `nextInt(1)` draw.

The stored stream therefore does not reflect the random draws consumed by the shuffle. Later board randomness can reuse/correlate with values already consumed by Brand selection.

**Required fix:** return the updated RNG from `brandTiles()` and store that exact state. Add deterministic sequence tests for different candidate counts.

### EFB-009 — Run completion is not one database transaction

**File:** `app/src/main/java/com/example/data/EmberfallRepository.kt`  
**Location:** lines 51–84.

`recordFinishedRun()` performs history insertion, Shade deck update and active-run deletion as three DAO operations. A repository `Mutex` serializes calls within that repository instance but does not make those operations atomic in Room.

A process interruption can leave duplicate history, stale active-run state or mismatched Shade memory.

**Required fix:** execute the complete finish sequence inside a Room transaction (`withTransaction` or a DAO transaction method). Add idempotency using a stable run identifier and test interruption/retry.

### EFB-010 — Corrupt saves are silently presented as missing saves

**Files:**
- `app/src/main/java/com/example/data/EmberfallRepository.kt`, lines 35–38.
- `app/src/main/java/com/example/ui/viewmodel/EmberfallViewModel.kt`, lines 126–134.

`loadActiveRun()` catches every snapshot decoding error and returns `null`. `continueSavedRun()` silently returns when it receives null. The active-run database row remains, so the title screen can still show a Continue option that does nothing.

**Required fix:** distinguish missing, corrupt and unsupported saves. Preserve the original payload, expose an error/recovery state and offer a controlled reset/export path. Never silently equate corruption with absence.

---

## 5. P2 — Significant correctness and reliability findings

### EFB-011 — Enemy random targeting consumes the board-paint RNG stream

**File:** `GameEngine.kt`, lines 1227–1235.

`SealColumn` selects a random column using `nextBoardRng`, while `RngStreams` has a separate `enemy` stream. Enemy intent randomness therefore perturbs future board painting, contrary to the stated subsystem-stream isolation.

**Fix:** use the enemy RNG for random enemy target selection and persist the updated enemy stream. Keep board-paint RNG for tile generation/transformation draws.

### EFB-012 — Wall-rise rescue does not receive the active Spectrum hue

**File:** `BoardEngine.kt`, around `riseIncomingRow()` and its calls to `ensurePlayableMove()` at lines 738–739 and 779–780.

`consumeAndCascade()` passes the current Spectrum hue to move validation, but Wall-rise/overflow rescue calls do not. Prism legality can therefore be evaluated using the Run-0 default while the active Spectrum anchor differs.

**Fix:** pass `currentRunHue` through Wall rise and overflow paths and use it for all legal-move checks.

### EFB-013 — Global mute destroys independent music/SFX preferences

**File:** `EmberfallViewModel.kt`, lines 511–521.

The title/combat mute control sets both `musicEnabled` and `sfxEnabled` to the same value. If the user previously had music off and SFX on, muting and unmuting loses that configuration.

**Fix:** add a separate `masterAudioMuted` preference. Do not overwrite the independent channel settings.

### EFB-014 — One-tap preference migration discards the old value

**File:** `app/src/main/java/com/example/data/SettingsDataStore.kt`, lines 24–44.

The key changes from `one_tap_board_fire` in v3 to `one_tap_board_fire_v2` in v4 without migration. Existing users who deliberately disabled one-tap firing are reset to the default true.

**Fix:** migrate the old key to the new key or keep the existing key name. Do not silently discard a user preference.

### EFB-015 — Audio initialization failure permanently blocks retry

**File:** `MinimalTechnoAudioEngine.kt`, lines 61–97.

`initialized` is set before the initialization body succeeds. A caught exception does not reset the flag, so future initialization attempts are ignored.

**Fix:** use explicit initialization states and allow controlled retry after failure.

### EFB-016 — Audio initialization can race with release and leak SoundPool

**File:** `MinimalTechnoAudioEngine.kt`, lines 55–97 and 253–263.

Initialization is synchronous inside an IO coroutine while `release()` can run concurrently. If release happens before SoundPool is assigned, initialization can assign a new SoundPool after release has already cleaned resources. That pool is then leaked.

**Fix:** serialize initialization and release under a lifecycle/resource lock or single actor. Do not publish resources after `RELEASED`.

### EFB-017 — AudioTrack write/loop/play results are not checked

**File:** `MinimalTechnoAudioEngine.kt`, lines 197–224.

`write()`, `setLoopPoints()` and playback state are not validated. If setup fails after constructing the local track but before assigning it to `musicTrack`, the local track is not reliably released.

**Fix:** check write count, loop-point result and track state; release the local track in every failure path; expose diagnostics.

### EFB-018 — Android audio focus is not managed

**File:** `MinimalTechnoAudioEngine.kt`.

There is no `AudioManager` focus request/abandon/interruption handling. Playback behavior during calls, navigation prompts, other app audio and output-device changes is not controlled.

**Fix:** implement focus handling, transient/permanent loss behavior, resume policy and headphone/Bluetooth route tests.

### EFB-019 — Audio event mapping depends on message strings and unrelated HP deltas

**File:** `EmberfallViewModel.kt`, lines 324–339.

Overflow detection searches `CombatEvent.message` for `"WALL OVERFLOW"`. Enemy-hit sound is inferred from any HP reduction. An overflow HP penalty therefore triggers both the overflow alarm and `ENEMY_HIT`.

**Fix:** emit typed engine events and map those events to audio. Do not parse user-facing message text or infer damage source from HP deltas.

### EFB-020 — Pop audio uses post-pop Spectrum rather than the pop's effective Spectrum

**File:** `EmberfallViewModel.kt`, `triggerPopAudio()` around lines 242–250.

The pitch is selected from `after.combat?.spectrum?.halfSteps`, which is the state after the pop. A successful wheel advance therefore plays the current pop at the *next* Spectrum step.

**Fix:** pass `effectiveHalfSteps` from the engine's pop resolution or compute the pitch from the pre-action Spectrum and resolved hue.

### EFB-021 — Navigation back stack grows instead of replacing the app's state route

**File:** `EmberfallNavHost.kt`, lines 51–57.

Every destination change calls `navigate(...){ launchSingleTop=true }` without `popUpTo`. BackHandlers commonly navigate to Title, adding Title to the stack. Pressing system Back on Title can reveal a stale prior screen.

**Fix:** make navigation state single-source-of-truth with `popUpTo`/restore-state rules, or use a single-route state host. Add back-stack tests across Title → Class Select → Map → Title.

### EFB-022 — Snapshot engine/content versions are read but not validated

**File:** `RunSnapshotCodec.kt`, lines 81–85.

`engineVersion` and `contentVersion` are loaded into `RunState` but never compared with supported versions. Unknown card/relic/remnant/enemy IDs and invalid state relationships can reach strict catalog lookups later.

**Fix:** enforce compatibility policy and validate snapshot invariants before publishing a run: board dimensions, tile IDs, unique card UIDs, known catalog IDs, resource bounds, map references, seal indices/durations and combat consistency.

### EFB-023 — Map validator omits several graph integrity checks

**File:** `CampaignGenerator.kt`, `validateMap()`.

The validator checks reachability and path-composition bounds but does not comprehensively reject duplicate node IDs, dangling edge targets, invalid row transitions, duplicate row/column coordinates or malformed row ranges.

**Fix:** validate node ID uniqueness, all edge targets, exactly-next-row edges, row/act/column constraints and boss terminality before running path DP. Add mutated-map negative tests.

---

## 6. P3 — Cleanup and maintainability

### EFB-024 — Obsolete class JPGs remain packaged

The three unused resources `img_class_pyromancer.jpg`, `img_class_cinderknight.jpg` and `img_class_ashweaver.jpg` have no code references. Resource shrinking is disabled, so they may remain in the APK.

Remove them or enable and verify resource shrinking.

### EFB-025 — `GameEngine.kt`, `GameCatalog.kt` and `CombatArenaScreen.kt` are oversized

Approximate file lengths:
- `GameEngine.kt`: 1,793 lines.
- `GameCatalog.kt`: 1,755 lines.
- `CombatArenaScreen.kt`: 1,406 lines.

Do not rewrite wholesale. Extract bounded responsibilities while preserving deterministic behavior and existing test coverage.

### EFB-026 — Seeded RNG uses modulo reduction

`SeededRng.nextInt()` reduces a 63-bit value with modulo. This introduces a small modulo bias for bounds that do not divide the source range.

This is low priority for a single-player deterministic game, but rejection sampling is preferable if uniformity is a formal requirement.

---

## 7. Design authority and documentation risks

### 7.1 Monk/Witch starter approval contradiction

`docs/AAA/emberfall/DECISION_LOG.md` and `CONTENT_CATALOG.md` still mark Monk and Witch starter definitions provisional pending Design Bible v0.2. `docs/RESTORATION_STATUS.md` claims owner-approved v1.0 content sign-off.

The original accepted Design Bible/ADR artifacts and a traceable sign-off artifact are not included in this archive. These statements cannot all be treated as equally authoritative.

### 7.2 Gravedigger's Spade rule requires source reconciliation

Current code implements 1.5× outgoing damage against Elites, Bosses and Shade (`GameEngine.kt:1405–1415`). The current restoration status repeats that interpretation. The previously established ADR-016 summary described the 150% modifier as applying to Cinders + Remnant value instead.

The original accepted ADR is not present in the archive, so this must be marked **UNRESOLVED SOURCE CONFLICT**, not silently accepted as canonical.

### 7.3 Maestro verification records are stale

The two `.maestro/workflows/*.yaml` files still report 39/39 tests and do not include the v4 audio tests or audio playback verification. They are Maestro workflow definitions, not GitHub Actions workflows.

Update their evidence and status after the actual run. Do not label v4 fully verified based on v3 counts.

---

## 8. Build and CI execution boundary

The archive does not include:
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `.github/workflows/*`

The environment has:
- Java 21.
- Kotlin/JVM 1.9.0.
- No Gradle executable.
- No Android SDK environment (`ANDROID_HOME` / `ANDROID_SDK_ROOT` unset).
- No network package access.

The related GitHub handoff branch identified during review is a documentation/handoff repository, not the Android source repository. The uploaded v4 application source is not mirrored there, so it cannot run this archive in GitHub Actions.

Therefore:
- Pure Kotlin engine and DSP compilation: verified locally.
- 34 local JVM tests: executed and passed.
- Android/Compose/Room compilation: not run.
- Robolectric tests: not run.
- Instrumented/device tests: not run.
- Real SoundPool/AudioTrack playback: not run.
- Full Gradle build: not run.

This is an execution limitation, not a claim that the Android build is broken. The release cannot be marked build-verified until the actual Android project is in a runnable Gradle/SDK environment.

---

## 9. Remediation order

### Gate A — Correct game state
1. Terminal outcome precedence.
2. Seal duration.
3. Board rescue postcondition.
4. Dread Vigil-burn reset rule.
5. Campfire resume route.
6. Shade deck only on qualifying defeat.

### Gate B — Save integrity
1. Correct v2 migration inference.
2. Validate snapshot versions and invariants.
3. Surface corrupt-save recovery.
4. Make run finish transactional and idempotent.

### Gate C — Determinism and rules
1. Return RNG state from Brand.
2. Separate enemy RNG from board-paint RNG.
3. Pass active Spectrum into Wall-rise rescue.
4. Reconcile Monk/Witch starter approval and Gravedigger's Spade source authority.

### Gate D — Audio and UI
1. Master mute preference.
2. One-tap preference migration.
3. Audio init/release state machine.
4. AudioTrack result validation and cleanup.
5. Audio focus and route handling.
6. Typed gameplay events for SFX.
7. Correct Spectrum pitch timing.
8. Navigation stack replacement/back behavior.

### Gate E — Release validation
1. Restore Gradle wrapper.
2. Configure a clean Android SDK build.
3. Add GitHub Actions for `assembleDebug`, `testDebugUnitTest`, lint and artifact upload.
4. Run all 44 tests under the actual Gradle/JUnit/Robolectric environment.
5. Run instrumented gameplay and audio lifecycle tests on an emulator and physical device.
6. Generate and inspect a release APK/AAB; verify package ID, signing, shrink, resource payload and backup behavior.

---

## 10. Final decision

**HOLD production approval.**

The engine and DSP foundations are credible. The 34 executable tests passing is useful evidence, and the catalog is structurally broad. But the four reproduced defects show that the current test suite misses meaningful adversarial state transitions.

The highest risk is not lack of content or polish. It is the integrity of the game state: who wins simultaneous lethal outcomes, how long statuses last, whether the board can become unplayable, whether a Dread ending can be earned under a prohibited action, and whether a saved run can resume.

Fix Gate A and Gate B before expanding content or polishing the trailer.

**No source files were modified during this audit.**
