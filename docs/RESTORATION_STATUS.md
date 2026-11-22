# EMBERFALL — Restoration Status

**Updated:** 2026-11-21  
**Controlling Document:** `docs/RESTORATION_AUTHORITY_AND_MANDATE.md` (mirror of `public/assets/aistudio/EMBERFALL_RESTORATION_AUTHORITY_AND_MANDATE_71ac56e3.md`)  
**Traceability Matrix:** `docs/RESTORATION_TRACEABILITY.csv`

---

## 1. Provenance & Repository Baseline

- **Workspace Type:** AI Studio Android container workspace (`/app/applet`).
- **Git Checkout:** **NONE PRESENT** (`.git/` does not exist in workspace; no branch, remote, or commit SHA can be generated locally without a live Git checkout).
- **Superseded / Non-Authoritative Markers Applied:**
  - `.aistudio/artifacts/brain/d93fa0cb-8387-4b53-8804-81c38ae65aef/implementation_plan.md` marked **SUPERSEDED** (preserved intact for history).
  - `docs/ADR_CONFLICTS_AND_DECISIONS.md` marked **NON-AUTHORITATIVE PROVISIONAL LOG** (preserved intact for history).
- **Visual Baseline Available:** `public/assets/aistudio/ART_BASELINE_LOCK_fead5899.md` v0.4 + `public/assets/aistudio/class_roster_painterly_1__23f2a842.png` (extracted into `app/src/main/res/drawable-nodpi/ef_class_{ashen_monk,quarry_witch,the_unlit}.webp`).
- **Stale Asset Hygiene:** The four confirmed unreferenced legacy JPGs (`img_class_pyromancer.jpg`, `img_class_cinderknight.jpg`, `img_class_ashweaver.jpg`, `img_act_map_backdrop.jpg`) and AI Studio `.tempmediaStorage` scratch files were removed from the completion source tree. Runtime-referenced assets were preserved.

---

## 2. Confirmed Restorations Implemented Against Known Baseline Rules

All quarantined substitute behaviors flagged in Section 5 of `RESTORATION_AUTHORITY_AND_MANDATE.md` have been removed from the engine, UI, and tests:

1. **Pure Domain Isolation:** `app/src/main/java/com/example/engine/` has **0** `android.*`, `androidx.*`, or `Room` imports.
2. **Transactional Phase & Command Guards (`ADR-001`):** `GameEngine.validateTransition` rejects out-of-order `ChooseNode`, `TapCell`, `CastCard`, `OpenRewards`, `FinishRewards`, `Campfire*`, `Shop*`, `ChooseEventOption`, and `LeaveNode` without mutating state.
3. **Ashen Monk Vigil Burn (`P0.1`):** Removed repeated passive enemy-phase counterattack; Vigil persists across turns in combat (capped 30) and is deliberately burned for burst damage (`monk_bead_strike`, `monk_lotus_combustion`).
4. **Board Status Mechanics (`ADR-010` / `ADR-011` / `ADR-012`):**
   - **Brand:** Marks board tiles (`Tile.isBranded`); moves with gravity; costs `2 Vigil` per marked block popped (HP shortfall), previewed before tap.
   - **Fog:** Conceals tile hues and Ink pool counts (`?`) for `3 Taps` while preserving all hue/neutral glyphs; dispelled immediately by `Gold Eye`.
   - **Seal:** Locks a board column (`board.sealedColumns`) for `3 turns` against taps, gravity, refill, targeted effects, and Wall rise; cleansed by `Quarry Purge`.
5. **Wall Headroom & Pre-Rise Overflow (`ADR-008`):** Board opens with 2 `TileKind.Empty` headroom rows; pre-rise overflow when unsealed top row has no Empty cell deals `-8 HP`, burns top 2 unsealed rows to `Empty`, deducts `-1 Flask`, and skips Wall rise (no invented Guard mitigation).
6. **Echo (`ADR-015`):** Repeats only `CardDef.primaryEffect` once; ignores Echo-of-Echo and never repeats costs, secondary effects, draws, or exhaust.
7. **Unlit Covenant & Dread True Ending (`ADR-017`):** Covenant strictly restricted to `THE_UNLIT`; requires 3 consecutive Ash-only, 0-card, 0-Flask, 0-overflow turns against Act III `The Hearthkeeper` (attacks fall on empty air while qualifying; normal HP kill sets `dreadEndingAchieved = false`).
8. **15-Row Map, Events & Scaling (`ADR-013`, `ADR-016`, `ADR-018`):** Added `MapNodeType.EVENT` + `EventScreen`, O(V+E) topological DP route-composition validator in `CampaignGenerator.validateMap`, `round(36 * 1.11^step)` scaling (`1.6x` Elite, `3.0x` Boss), and full-deck Shade intent derivation.
9. **Persistence & Replay (`Gate G6`):** Explicit non-destructive Room migrations (`1->2->3->4`, destructive fallback removed), `Mutex`-serialized repository writes, and `RunSnapshotCodec` v3 with v2->v3 migration + strict rejection of unknown `TileKind`.

---

## 3. Owner-Approved v1.0 Content & Architecture Sign-Off

Per Owner Sign-Off (2026-11-21), the locked core remains 100% intact (3 canonical classes, 5 canonical hues, 3 signature relics, Ash/Stone/Prism/Empty neutrals, 3 Taps, Deck-Painted Board, Ink/Spark, 2 Enemy Telegraph Slots, Rising Wall & Overflow, 3 Acts, Act I Row 5 Shade, zero meta-currency) while all missing v1.0 catalog & ADR gaps were filled and verified:

- **Full v1.0 Catalog Depth:** **65 Cards** (18 Ashen Monk, 18 Quarry Witch, 17 The Unlit preserving ADR-014 12-card Gold starter, 12 Neutral), **22 live-hooked Relics** (3 Signatures + 19 draftable; note: **Gravedigger's Spade** currently implements 1.5x Elite/Boss/Shade scaling under an `UNRESOLVED SOURCE CONFLICT` pending recovery of upstream ADR-016 authority), **6 Shade Remnants**, **20 Act I–III Enemies + Bosses + Shade**, **12 Act-Scoped Branching Events** (including deck-thinning card removal), **Shade Vigor scaling**, and **Dread Tiers 0–3** (opening Ash/Stone hazards at Dread 2–3).
- **Starter Authority Lock (`P0-7`):** All three canonical starter decks are explicitly locked and regression-tested in `GameCatalog.starterDeckIds` (`ASHEN_MONK`: 10-card Ember/Moss; `QUARRY_WITCH`: 10-card Tide/Violet; `THE_UNLIT`: 12-card Gold per `ADR-014`).
- **Custom Painterly Art & Combat/Map Polish:** Added custom painterly drawables (`img_boss_warden`, `img_boss_furnace`, `img_boss_hearthkeeper`, `img_enemy_shade`, `img_act_map_backdrop`, Copperplate hero/card/seal frames), circular 5-hue **Spectrum Wheel Dial**, **Floating Combat Callouts**, **Wall Overflow** pre-rise danger cue, and an interactive **15-Row Act Map** with real `Canvas` graph-edge paths, node legend, and tap-to-inspect tooltips.
- **`BLK-04` CLOSED (Owner Confirmed):** Pure-JVM Kotlin domain engine (`com.example.engine`) + Jetpack Compose Material 3 Android shell officially approved.

### Core Experience Lock & Release Verification (`P0-1`..`P0-7`, Phase 2–5)

- **P0-1 (Match-3 Swap Contract):** `riseIncomingRow` and Dread 2–3 opening hazard recovery enforce `BoardEngine.ensurePlayableSwap` (zero idle 3+ lines + guaranteed valid swap); `SimulationRunner`, Settings rules copy, and `Prayer Beads` copy aligned strictly to Match-3 `SwapCells` (legacy `TapCell` retained solely for deterministic replay & ADR regression tests).
- **P0-2 (Functional One-Tap Quick-Swap):** `EmberfallViewModel.onBoardCellTapped` reads live `settings.value.oneTapBoardFire` — single-tap fires top-ranked legal Match-3 swap when ON and selects for 2-tap swap when OFF (drag-swap supported in both modes).
- **P0-3 (Coroutine Cancellation Safety):** `EmberfallRepository.loadActiveRunResult()` explicitly rethrows `CancellationException` and catches `Exception` (not `Throwable`) so cancellation is never misclassified as save corruption.
- **P0-4 (Truthful Simulation Reporting & Turn Accounting):** `SimulationRunner` separates `runsAttempted`, terminal `runsCompleted`, `incompleteRuns`, `turnCapHits`, `nodeCapHits`, and per-class stats; `GameEngine.resolvePostAction` credits mid-turn kills with `+1` turn.
- **P0-5 (Seal Duration Preservation):** `BoardEngine.ensurePlayableSwap` preserves 100% of active `sealedColumns` durations whenever `>= 1` unsealed column exists (vertical bent-3 / Prism+Playable rescue inside the unsealed column), unsealing the shortest-duration column only when all 7 columns are sealed.
- **P0-6 (FIFO Persistence Queue):** `EmberfallViewModel` serializes all active-run save/clear mutations through a single FIFO `Channel<PersistRequest>(Channel.UNLIMITED)` worker inside `withContext(NonCancellable)`, preventing out-of-order overwrite races on rapid player input.
- **P0-7 (Authoritative 3-Class Starters):** Locked and verified exact starter card IDs, counts, class ownership, and frame hues for all 3 classes.
- **Phase 2–3 (Board Pacing, Accessibility & P0/P1 UI Layout Lock):**
  - **Board & Overdrive Pacing:** Fixed `BoardPaintDeck` Prism recycling and tiny-bag reshuffle (`< 18` tiles), dampened runaway auto-cascade explosions, scoped Overdrive one-shot support multipliers and primary-wave Overdrive charge gain, capped Ember Surge to 1 bonus move/turn, and upgraded sub-48dp interactive buttons (`chisel_cancel_button`, `trigger_overdrive_button`, `inline_reroll_cards`, `inline_skip_card`) to `heightIn(min = 48.dp)`.
  - **P0-UI-1 (Combat Arena Viewport Dominance):** Replaced 6 stacked full-width HUD cards above the 7×7 puzzle grid with 3 slim bands (inline rival bar `combat_enemy_header` `<= 95dp` with `CompactWaxSealBadge` intents, compact vitals + `38.dp` `SpectrumWheelDial` + 5 Ink chips, and unified `38.dp` `CombatSystemPillRibbon` with expandable dialogs for Mind Illusion, Signature Skill, Upāya Circuit, and Prakāśa Kiln Ledger), moved transient banners to a zero-layout-cost overlay inside the board `BoxWithConstraints`, and raised tile glyph contrast (`0.56f -> 0.78f`). Verified by Robolectric layout-budget regression test (`boardWidth >= 350dp`, `boardHeight >= 350dp`, `tile >= 44x44dp`, `enemyHeader <= 95dp` under worst-case Boss/Twist/Dread/Illusion/Overdrive density).
  - **P1-UI-2 (Illustrated Parchment Act Map):** Generated custom antique parchment cartography artwork (`img_world_map_parchment.jpg`, rendered at `alpha = 0.88f` with soft edge vignette only), replaced 5 stacked top strips with a slim floating `act_map_header` (`<= 110dp`) + Cartographer's Key dialog (`map_key_button`), replaced flat `"ROW X"` boxes with 4 biome elevation cartouches, seed-driven organic landmark medallions (`MapLandmarkMedallion`) + curved double-inked `GraphPathConnector` trails, and docked `next_crossroads_strip` cleanly at the bottom (`act_map_canvas >= 640dp` on 915dp phone). *(The four unreferenced legacy JPG placeholders were removed during the completion pass.)*

### Remaining Open Items

| Blocker ID | Status | Detail |
|---|---|---|
| `BLK-01..BLK-03` | `PARTIALLY_OPEN_AUTHORITY` | All 3 starter decks locked in `GameCatalog.starterDeckIds` (`P0-7`); Gravedigger's Spade remains 1.5x Elite/Boss/Shade pending upstream ADR-016 authority diff if ever imported. |
| `BLK-04` | `CLOSED` | Owner approved Kotlin pure-JVM engine + Jetpack Compose Android shell. |
| `BLK-05` | `PARTIALLY_CLOSED` | A local Git repository was initialized for this handoff with an imported-source baseline and implementation commits. No upstream remote or authoritative project branch was supplied, so provenance/push/tagging remain unverified. |

---

## 4. Verification Evidence & Release Gate

- **Verified Commands & Raw Counts (2026-11-22 — Core Experience Lock & Release Verification):**
  - Pure JVM Engine Isolation (`grep` for `android.*`/`androidx.*` in `app/src/main/java/com/example/engine/`): **0 matches**.
  - Full Test Suite (`gradle :app:testDebugUnitTest --console=plain`): **86 / 86 passed (0 failed, 0 skipped, 0 errors)** — `EmberfallEngineTest` (53/53), `ExampleRobolectricTest` (26/26, including `P0-UI-1` & `P1-UI-2` layout-budget assertions), `MinimalTechnoSynthTest` (4/4), `LyriaSoundtrackRepositoryTest` (2/2), `ExampleUnitTest` (1/1).
  - Multi-Seed Deterministic Simulation (`Phase4`): 24/24 Dread 0 + 12/12 Dread 2 runs completed (`0` incomplete, `0` turn-cap hits, `0` node-cap hits, strict cross-run determinism verified).
  - Android Compilation (`compile_applet`): **BUILD SUCCEEDED** (0 Kotlin warnings).
  - Android Static Analysis (`lint_applet` -> `:app:lintDebug` + `:app:lint`): **BUILD SUCCESSFUL** (0 lint errors; HTML report generated).
- **Execution Boundary Note:** Physical Android device / live interactive emulator playtesting, real hardware Lyria 3 / `AudioTrack` playback, human visual/playtest sign-off, and production upload-keystore release signing remain outside this container's execution boundary.





## 4. Completion Mandate Execution Delta (2026-11-22)

The following changes were applied to the supplied `emberfall-8` snapshot after inspecting the implementation and the controlling restoration authority. A local Git repository was initialized: baseline `cd3ff9d`; current implementation HEAD recorded in the handoff bundle. This section records source changes only; it does not claim that Android builds or tests were executed.

- **P0-1/P0-5 board shuffle correction:** Each randomized swap candidate now starts from an immutable baseline grid. Rejected permutations no longer compound into later attempts. Existing bounded retries and deterministic rescue behavior remain in place.
- **P0-2 one-tap default:** `PlayerSettings.oneTapBoardFire` and the DataStore missing-key fallback now default to `false`. Explicit previously saved preferences remain respected. The Robolectric default assertion was updated accordingly.
- **P0-4 simulation accounting:** Batch inputs now reject negative seed counts and Dread values outside `0..3`; end-of-batch invariants assert attempted = completed + incomplete, cap hits are counted only among incomplete runs, and per-class totals reconcile. A zero-run/invalid-input regression test was added.
- **P0-6 persistence lifecycle:** The FIFO persistence worker now runs in a process-lived `SupervisorJob` scope rather than `viewModelScope`. `onCleared()` closes the request channel, allowing the worker to drain accepted requests. `flushPendingPersistence()` is a 10-second completion barrier; it now surfaces any earlier write failure instead of reporting a false success. This improves ViewModel teardown behavior but does not claim survival of abrupt OS process termination before a Room transaction commits. The queue remains `Channel.UNLIMITED`; bounding/coalescing it without losing terminal writes remains an engineering follow-up.
- **T-02 App Check isolation:** `firebase-appcheck-debug` is now a `debugImplementation`. Debug and release source sets provide separate installer implementations; release source has no reference to the debug provider class. Release still uses the configured reCAPTCHA provider.
- **T-03 backup policy:** Explicit cloud-backup and device-transfer rules include the Room database and DataStore preferences and exclude generated Lyria soundtrack files.
- **CI release gates:** GitHub Actions now runs debug/release lint, assembles the debug APK, and assembles a release APK/AAB using a short-lived CI-only signing key. That key is not a production upload key; CI execution remains pending.

### Verification status of this delta

- Source inspection and targeted edits: **completed**.
- New regression tests: **added, not executed**.
- Android Gradle build/tests/lint: **not executable in the supplied container** because there is no Gradle executable, no Gradle wrapper JAR, no cached Gradle distribution, no Android SDK/platform installation, and outbound DNS/network access is unavailable.
- Device/emulator validation: **not executed** (no ADB/emulator/device available).
- Signed APK/AAB: **not generated**; no signing keystore or credentials were supplied.
- Release R8/minification: intentionally **not enabled without a successful optimized release build and runtime verification**.
- Android 16 large-screen behavior: **not device-verified**; portrait orientation and responsive layout remain a release validation item.

**Current verdict: NOT RELEASE READY.** The source improvements are packaged, but the mandatory build, test, lint, signing, and device-verification gates remain open.
