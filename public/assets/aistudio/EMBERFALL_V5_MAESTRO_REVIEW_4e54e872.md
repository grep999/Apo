# Emberfall v5 — Maestro Quality Review

**Target:** `emberfall-5.zip`  
**SHA-256:** `bdb84a6b1febad79d3a79948199fcffbdafbbcb67c663ad7c13d485abb6cc75e`  
**Review date:** 2026-11-21  
**Scope:** v4→v5 delta review, source inspection, pure-JVM compilation, engine/audio test execution, workflow and release-evidence audit.

## Executive decision

**v5 is a substantial improvement and the pure Kotlin engine is in materially better shape. It is not yet release-approved.**

The v5 archive reports remediation of EFB-001..026. I independently compiled the v5 engine sources with `kotlinc -Werror` and executed the v5 engine tests against those freshly compiled classes: **37/37 passed**. I also freshly compiled the pure audio DSP and ran its tests: **3/3 passed**.

One further migration edge case remains: **EFB-027 — a v2 snapshot saved after a Campfire action can be migrated as if the player has already left the Campfire.** The v2→v3 inference sets `inNonCombatNode` for Campfire only when `!campfireUsed`, even though the player remains inside the node until `LeaveNode`. Add a used-Campfire v2 fixture and correct the inference before closing persistence quality.

**Verdict: CONDITIONAL HOLD.** The engine and pure DSP pass their independently rerun suites. Android app compilation, Room/Robolectric tests, and physical-device audio remain unverified in this environment.

## Maestro lifecycle

### Grounding — PASS WITH AUTHORITY GAPS

- Reviewed the v5 archive as the source of truth; it contains 110 files and 28 production Kotlin files.
- Compared the v5 tree against the prior v4 extraction. v5 changes engine, persistence, audio, ViewModel, navigation, UI, tests, and restoration documentation.
- The three-class / five-hue scope lock remains explicit.
- The Monk and Witch starter authority remains provisional pending Design Bible v0.2; Gravedigger's Spade remains an unresolved upstream ADR-016 conflict. These must not be silently promoted to canonical rules.
- The archive contains no `.git` checkout, no `.github/workflows` CI workflow, no `gradlew`, and no `gradle-wrapper.jar`.

### Momentum — PASS FOR PURE JVM GATES

Commands executed against v5 source:

```bash
kotlinc -Werror app/src/main/java/com/example/engine/*.kt -d engine.jar
```

Result: **PASS**.

The 37 v5 `EmberfallEngineTest` methods were compiled against the freshly built engine and executed using a local JUnit-compatible shim/reflection runner (not Gradle/JUnit): **37 passed, 0 failed**.

The pure DSP source was independently compiled with `-Werror`; `MinimalTechnoSynthTest`: **3 passed, 0 failed**.

Combined independently rerun pure-JVM coverage: **40/40 passed**.

The archive has 57 `@Test` annotations across JVM, Robolectric and instrumented source sets. The v5 workflow reports 56/56 Gradle unit tests (37 engine + 15 Robolectric + 3 DSP + 1 unit); that Gradle run is documented in the archive but could not be independently replayed from this ZIP because the wrapper and Gradle executable are absent. The one instrumented test is not part of `testDebugUnitTest`.

### Mastery — PARTIAL

Verified from source and execution:

- Engine compiles warning-free with `-Werror`.
- 37 engine tests pass, including regression tests for simultaneous lethal arbitration, enemy-applied Seal duration, hostile-board rescue, Dread/Vigil reset, RNG stream separation, typed combat events, and map graph validation.
- 3 pure audio DSP tests pass.
- No Android/AndroidX/Room imports in engine source (the one grep hit is a comment, not an import).
- Legacy substitute class names do not occur in production code or tests; one documentation/workflow sentence names the old classes to prohibit them.
- Room migrations 1→2→3→4 and transactional finish logic are present in source.
- Snapshot decoding includes strict version/catalog/state validation and v2→v3 migration logic.

Not independently verified:

- Full Android/Compose app compilation.
- Robolectric/Compose tests and Room migration execution.
- Android audio controller lifecycle/focus behavior on API 24, 26+, and a physical device.
- APK packaging, release signing, Play target SDK/policy compliance.

### Repetition — OPEN

The archive's Maestro YAML files are governance manifests describing the lifecycle and checks; they are not accompanied by a GitHub Actions runner or a reproducible build wrapper. Add a checked-in CI workflow and wrapper (or document the exact AI Studio runner environment and retain raw logs/artifacts) so the next batch can reproduce the claimed full Android gate.

## New finding

### EFB-027 — Used Campfire is misclassified by v2→v3 snapshot migration

**File:** `app/src/main/java/com/example/data/RunSnapshotCodec.kt`  
**Function:** `migrateV2ToV3()`  
**Current inference:**

```kotlin
(currentNodeType == MapNodeType.CAMPFIRE && !campfireUsed)
```

A Campfire remains the current active node after Rest/Upgrade; the player must still invoke `LeaveNode`. A v2 save captured after the Campfire action has `campfireUsed=true`. The current inference writes `inNonCombatNode=false`, even though the run is still at that Campfire. This bypasses the v5 EFB-005 restoration route, which explicitly expects `inNonCombatNode=true` to reopen the Campfire and expose Leave.

**Required correction:** infer active Campfire membership from `currentNodeType == CAMPFIRE` regardless of `campfireUsed`, provided the snapshot is not in Combat/Reward. `campfireUsed` controls whether the rest/upgrade action is available, not whether the player remains inside the node.

**Required regression fixtures:** v2 snapshot at Campfire before action; after Rest; after Upgrade; after Leave/map transition. Verify migration destination and command availability in each state.

## Quality and release gates

| Gate | Status | Evidence / remaining work |
|---|---|---|
| Pure engine compile | PASS | Fresh v5 compile with `-Werror` |
| Engine regression suite | PASS | 37/37 freshly compiled and executed |
| Pure audio DSP compile/tests | PASS | Fresh compile; 3/3 pass |
| Persistence correctness | OPEN | EFB-027 used-Campfire v2 migration fixture |
| Room migrations | NOT REPLAYED | Requires Gradle/Android test environment |
| Compose/Robolectric | NOT REPLAYED | Archive reports 15 tests; no local Gradle runner |
| Android app build | NOT REPLAYED | No wrapper or Gradle executable in archive/environment |
| Device audio | NOT REPLAYED | Requires Android device/emulator |
| Reproducible CI | OPEN | No `.github/workflows` present |
| Design authority | OPEN | Design Bible v0.2 / CH01 and ADR-016 upstream source needed |

## Recommended next bounded increment

1. Fix EFB-027 and add the four migration-state fixtures.
2. Add Room migration tests against real schema versions 1, 2, and 3, preserving existing active run/history/Shade data.
3. Run the full `:app:testDebugUnitTest` and `assembleDebug` in the intended Android environment; retain raw logs and test XML.
4. Add a GitHub Actions workflow using a committed Gradle wrapper, pinned JDK/Gradle/Android SDK, and explicit engine + Android test jobs.
5. Keep unresolved Design Bible / ADR authority items visibly provisional; do not “solve” them by inference.

No game source files were modified in this review. The only delivered artifact is this review record; it does not claim EFB-027 is fixed.
