# EMBERFALL — Review of `emberfall-2.zip`
**Review date:** 21 November 2026  
**Review type:** Static source, architecture, rules, UI, persistence and test audit  
**Baseline:** Original EMBERFALL scope and ADR decisions recorded in the previous full project audit  
**Verdict:** **NOT APPROVED — corrective refactor required before content expansion or release work**

## Executive verdict

This archive is materially better than the prior `emberfall-1.zip` in one important respect: the canonical class identities and five hues have been restored in the Kotlin engine and class-selection screen. The engine now contains Ashen Monk, Quarry Witch and The Unlit; Ember, Gold, Moss, Tide and Violet; Prayer Beads, Chisel Wand and Hollow Crown; a Prism single-hue resolver; and a 15-row Act map.

That is real progress. It is not, however, a faithful implementation of the original design. The implementation has replaced or invented several core mechanics, and the test suite validates those replacements rather than checking them against the original contracts.

The most serious issues are:
1. The Ashen Monk's defining Vigil-burning burst mechanic is absent.
2. ADR-010/011/012 (Brand, Fog and Seal) have been reinterpreted into different mechanics.
3. ADR-008 Wall overflow does not follow the specified overflow behaviour.
4. ADR-015 Echo repeats the entire card resolution, not one primary effect.
5. ADR-017 true-ending logic is a shortcut, not the required three-turn Ash-only condition.
6. The command API permits progression/reward commands in invalid states.
7. Defeat navigation is not handled after a run ends.
8. Save persistence can destroy data on schema migration and can race between asynchronous writes.
9. The Unlit starter deck is not the ADR-014 starter deck.
10. The project cannot be independently built from this archive: Gradle wrapper scripts/JAR and both configured signing keystores are absent.

**Do not add more cards, relics, enemies, or polish until the rules and state transitions below are corrected.**

---

## 1. What is genuinely improved

| Area | Assessment |
|---|---|
| Canonical classes | Restored in `HeroClass` and class selection: Ashen Monk, Quarry Witch, The Unlit. |
| Canonical hues | Restored in the domain model: Ember, Gold, Moss, Tide, Violet. |
| Signature relic identities | Prayer Beads, Chisel Wand and Hollow Crown exist and are granted at run start. |
| Prayer Beads | First Tap grants +2 Ink; the exact hue-selection fallback still needs owner/spec confirmation. |
| Chisel Wand | Once-per-combat free recolour is implemented. |
| Hollow Crown | Grants +1 Spark per Ash block popped. |
| Prism | The implementation selects one contacting hue using the Spectrum wheel and does not deliberately bridge unrelated hue clusters. |
| Spectrum | Uses integer half-steps, avoiding floating-point multiplier truncation. The initial Run/off-by-one semantics still need confirmation against the original contract. |
| Board size | 7×7 visible board plus an incoming row. |
| Campaign map | 15 rows; Act I Shade placement is at row 5. |
| Deterministic foundations | Seeded RNG streams, typed commands, command log and replay entry point exist. |
| Android shell | Compose navigation, ViewModel, Room, DataStore and the ten principal screen destinations exist. |
| Pure domain separation | `com.example.engine` appears free of Android/Compose/Room imports. |

These are foundations worth retaining. The review is not recommending a rewrite from zero.

---

## 2. P0 — Rules that must be corrected

### P0.1 — Ashen Monk identity is incomplete

**Files**
- `app/src/main/java/com/example/engine/EngineModels.kt`
- `app/src/main/java/com/example/engine/GameCatalog.kt`
- `app/src/main/java/com/example/engine/GameEngine.kt`

The class description says the Monk burns Vigil for power and big single hits. The current engine has:
- `vigilGain` on cards;
- Vigil gained from unused Weave taps;
- no `vigilCost` or equivalent card field;
- no Monk-specific Vigil conversion/spend action;
- a counterattack in the enemy phase using the accumulated Vigil pool.

`GameEngine.kt` lines 745, 829–831 and 859–861 apply the same `vigilPool` damage for each enemy attack hit. The pool is not consumed after the counterattack. A multi-hit or two-slot enemy can therefore trigger repeated damage from the same stored Vigil.

This is neither the original Monk playstyle nor a safe resource implementation.

**Required correction**
- Restore the original Vigil purpose and spend/burn operation from the authoritative design source.
- Add an explicit cost/effect model for Vigil-consuming attacks or actions.
- Define whether unused Vigil persists, expires, defends, or converts; do not infer it from the current counterattack code.
- Add tests proving exact spend, remaining balance, single-hit and multi-hit behaviour.
- Remove the repeated counterattack unless the authoritative rules explicitly require it.

### P0.2 — Brand, Fog and Seal are not the original mechanics

**Files**
- `EngineModels.kt`
- `GameEngine.kt`
- `CombatArenaScreen.kt`
- `GameCatalog.kt`

The current implementation has replaced the original mechanics:

| Original contract | Current implementation |
|---|---|
| **Brand:** marks blocks; each marked block popped costs 2 Vigil per block. | Enemy Brand increases damage taken by +2 per stack; player Brand increases incoming attack damage. No marked-block state exists. |
| **Fog:** hides hue colours and Ink pools for 3 Taps; neutral glyphs remain; Gold Eye restores colour. | Fog reduces incoming enemy attack damage by 2 per stack and decays after an enemy turn. UI only displays a Fog badge; it does not hide board colours or Ink pools. |
| **Seal:** locks a column against taps, pops, gravity and targeted effects for 3 turns; Wall may rise in unsealed columns. | Seal disables enemy Slot II. No sealed-column state exists in `BoardState`. |

This is not an implementation detail. These are different game rules.

**Required correction**
- Remove the current Brand/Fog/Seal interpretations.
- Model marked blocks and their Vigil cost.
- Add a sealed-column representation to board state and enforce it in tap, group, gravity, paint/target effects and Wall handling.
- Implement Fog as a visibility effect with a tap counter, not an enemy-damage debuff.
- Implement Gold Eye restoration as specified by the canonical content.
- Add tests for every interaction, including a sealed column during a Wall rise.

### P0.3 — Wall overflow is materially wrong

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`, approximately lines 754–801; `BoardEngine.kt`, approximately lines 407–450.

The original ADR-008 contract specifies the incoming row, row orientation, overflow penalty, top-two burn and skipped rise. The current implementation:
- always performs the rise before deciding whether overflow occurred;
- triggers overflow from the presence of any displaced Ash/Stone or at least three neutrals across the top two rows;
- calculates an invented surge damage value;
- allows Guard to absorb the surge;
- reduces Vigil by only 1 when HP damage remains;
- drains a Flask when at least three displaced neutrals are present;
- does not burn the specified top two tiles;
- does not skip the rise on overflow.

That is a replacement ruleset, not ADR-008.

**Required correction**
Implement the exact overflow predicate and exact consequence sequence from ADR-008. Keep the incoming row preview and deterministic row movement. Do not add Guard mitigation, Flask loss or new thresholds unless the source explicitly authorizes them.

### P0.4 — Echo repeats the whole card, not one primary effect

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`, approximately lines 550–656.

`applyCardResolution()` repeats damage, Brand, Seal, Guard, Vigil, Fog, Spark, Ink, Stone shatter, tile painting, Prism spawning, Wall skip, Flask gain and card draw. The second invocation is therefore a full duplicate card resolution.

The test explicitly approves this behaviour: the Echo test expects damage and Brand to apply twice, with the second hit benefiting from the Brand added by the first hit. That is not a test of the original ADR-015 contract.

ADR-015 says Echo repeats one primary card effect once; Echo-of-Echo is ignored.

**Required correction**
- Represent a card's primary effect explicitly.
- Resolve the primary effect once.
- If Echo applies, enqueue/resolve that one effect once more.
- Do not duplicate costs, status application, draw, exhaust, resource gain, or secondary effects unless the source defines them as the primary effect.
- Add a card-by-card Echo test matrix.

### P0.5 — True ending is not implemented

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`, approximately lines 1025–1031.

The current code sets `dreadEndingAchieved` if either:
- `run.remnants.isNotEmpty()`, or
- `run.unlitCovenantActive`.

The original ADR-017 condition is substantially stricter: the Unlit covenant with Dread, three consecutive turns of Ash-only pops and no cards, special Hearthkeeper attacks falling on empty air, and reset conditions when the player breaks the sequence.

The current model has no Ash-only turn streak, no qualifying-turn evaluator, no reset logic, and no Hearthkeeper miss/empty-air behaviour. Any Remnant is treated as sufficient for the Dread ending. The class-selection UI also exposes the covenant flag without enforcing that it is the Unlit.

The ADR-017 test currently proves the wrong rule: it makes the final boss low HP and asserts that a covenant flag alone grants the true ending.

**Required correction**
- Add explicit covenant eligibility and Unlit-only enforcement.
- Track consecutive qualifying turns in persisted run state.
- Evaluate the condition at the specified point after the enemy phase.
- Reset the streak on every specified disqualifying action.
- Implement Hearthkeeper's special behaviour.
- Make the true-ending test simulate the actual three qualifying turns and all reset cases.

### P0.6 — Command API does not enforce run-state invariants

**File:** `app/src/main/java/com/example/engine/GameEngine.kt`, approximately lines 107–138 and 190 onward.

The central command dispatcher does not validate the broader progression state before dispatching commands. In particular:
- `OpenRewards` unconditionally sets `combat = null`.
- `FinishRewards` clears `pendingReward` and `combat` without requiring a completed reward state.
- `ChooseNode` checks that a node is in `availableNodeIds`, but `availableNodeIds` does not reject active combat or pending rewards.
- `LeaveNode` clears reward/shop/campfire state without verifying the current node or interaction state.

As a result, direct engine callers can skip an active encounter or bypass the reward sequence. The UI may not expose these calls in normal navigation, but the engine is supposed to be the rules authority; UI restrictions are not sufficient.

**Required correction**
Create a transition-guard table for every `GameCommand`. Reject invalid transitions transactionally. At minimum:
- cannot choose a new node during combat;
- cannot choose a new node while rewards are pending;
- cannot open rewards before combat victory;
- cannot finish rewards before the reward bundle is resolved;
- cannot leave a node that is not a leaveable non-combat node;
- cannot use campfire actions outside a campfire;
- cannot use shop actions outside a live shop.

Add adversarial tests that call commands directly, bypassing the UI.

---

## 3. P1 — Core rules and campaign fidelity

### P1.1 — The Unlit starter deck does not match ADR-014

**File:** `app/src/main/java/com/example/engine/GameCatalog.kt`, approximately lines 434–456.

The implemented 12 cards are:
- 3 × Gilded Ash
- 3 × Hollow Prayer
- 3 × Cinder Toll
- 2 × Pale Kiln
- 1 × Spark of the Crown

The original ADR-014 starter specification recorded in the baseline is:
- 4 Gold-hued Strikes
- 4 Ash-hued Wards with Gold frame
- 2 Pop Shot
- 1 Sift
- 1 Hollow Crown

The implemented list is not the specified list. The ADR conflict document itself admits that the exact upstream IDs/copies were unavailable and that a new list was created.

**Required correction**
Do not label the current Unlit deck canonical. Recover the Design Bible/ADR-014 source and implement its exact card identities, counts, frame hues and signature treatment. Until then, keep this deck explicitly marked provisional.

### P1.2 — Monk and Witch starter decks are invented

The Monk and Witch each receive ten-card starter lists. The original Design Bible v0.2 containing their exact starters is absent from this archive. The ADR conflict document explicitly says the lists were created pending verification.

**Required correction**
Keep canonical class identities and signatures, but mark starter lists, stats and card content provisional until verified against the missing Design Bible. Do not tune around provisional starters.

### P1.3 — Monk's resource economy and class distinction are not proven

The Monk currently receives a standard Ember/Moss deck and Prayer Beads. The class's Vigil-burn burst identity is absent. The Witch has a free recolour relic and some paint effects; the Unlit has Ash/Spark cards and Hollow Crown. These are not yet balanced class systems because the canonical starters and resource rules remain unresolved.

### P1.4 — Spectrum's first-pop semantics need source verification

`BoardEngine.advanceSpectrum()` starts the first hue pop from Run 0 at 1.5×. The test hard-codes that interpretation. The original baseline states Run begins at 0, the next hue advances the Run, Prism counts as the next hue, and an out-of-order pop is computed at Run 0 before reset. The Design Bible/complete ADR text is missing from this archive.

Do not treat the current first-pop 1.5× behaviour as proven. Recover the exact Run transition table and test every case, including first pop, expected next hue, repeated hue, out-of-order hue, Prism and wraparound.

### P1.5 — Enemy scaling does not implement ADR-013

**File:** `app/src/main/java/com/example/engine/CampaignGenerator.kt`, approximately lines 161–164.

The current function is:
`baseHp + ((act - 1) * 6) + (dreadLevel * 8)`, with a separate Shade formula.

The original ADR-013 formula recorded in the baseline is node-based: `round(36 × 1.11^node)`, with ×1.6 for elites and ×3 for bosses, using the specified rounding rule. The current function has no node parameter and no elite/boss multiplier. The fixed enemy attack patterns also need verification against the original per-Act damage bands.

### P1.6 — Map generator does not implement the full route contract

**Files:** `EngineModels.kt`, `CampaignGenerator.kt`.

The 15-row map and Shade placement are present, but:
- `MapNodeType` has no Event node;
- no event encounter/content model exists;
- the validator checks connectivity and boss reachability, not the required route composition on every valid path;
- node type selection is largely per-node random assignment, not path-level route validation.

The baseline route mix includes events. A map with no Event node type cannot satisfy that contract.

**Required correction**
Add the Event node and its content/state flow. Validate every playable route against the original route mix and required node counts. Keep the 15-row structure.

### P1.7 — Shade is only partially faithful

The previous deck snapshot is used, but `buildShadeIntentsFromDeck()` takes only the first four card definitions to form the two intent patterns. Shade Vigor is based on `52 + 2 × deck size`, not the original performance-scaled-but-fair model. The previous deck is not represented as a full intent transformation.

This is a useful prototype, not a verified ADR-016 implementation. Recover the exact Shade scaling and deck-to-intent mapping before calling it complete.

### P1.8 — Board generation introduces silent bias

**File:** `app/src/main/java/com/example/engine/BoardEngine.kt`, approximately lines 36–69, 387–397 and 432–440.

When no valid move exists, the engine forcibly changes tiles:
- initial board: overwrites two cells with Ember;
- after a cascade: inserts a Prism at a random cell;
- after Wall rise: inserts a Prism at a random cell.

These rescue mutations do not come from the paint bag and do not reconcile the replaced tile with the paint-deck discard state. The original ADR-002 baseline explicitly requires seeded weighted painting without silent bias.

**Required correction**
Use a specified, deterministic no-move policy that preserves the deck accounting and is visible in the rules. Do not silently inject hue pairs or Prisms.

### P1.9 — Combat phase machine is only partially explicit

`TurnPhase` declares DRAW, WEAVE, CAST, WALL, ENEMY and CLEANUP. In practice:
- combat starts directly in WEAVE;
- DRAW is never entered;
- WALL, ENEMY and CLEANUP are not represented as actual state transitions;
- `handleEndTurn()` performs Wall, Enemy and Cleanup as one monolithic function.

This may be acceptable as an internal atomic transaction only if the design explicitly allows it, but it is not the fully observable strict phase FSM claimed by the code comments. Decide whether phases are real state or internal substeps, then make the model and tests honest and consistent.

### P1.10 — Several combat formulas are unverified inventions

`BoardEngine.kt` currently sets:
- base power from `count * 2`, a group bonus, Prism bonus and Stone bonus;
- Ink from `1 + count / 3 + prismCount`;
- Stone defence from `2 × shatteredStone`;
- neutral paint bag quantities to fixed counts.

The source material available in this archive does not establish these values as canonical. The ADR conflict log acknowledges that some missing parameters were filled with chosen values. Treat these as tuning placeholders, not original design rules.

---

## 4. P1 — Android, persistence and UI defects

### P1.1 — Defeat does not navigate to Run Summary

**File:** `app/src/main/java/com/example/ui/viewmodel/EmberfallViewModel.kt`, approximately lines 164–170 and 296–300.

`checkRunEndTransition()` only navigates when `runWon == true`. It ignores `gameOverReason != null`. The engine can correctly mark HP depletion or Zero-Flask Collapse, but the live UI does not transition to Run Summary after defeat. `resolveDestinationForRun()` handles defeat only when loading/continuing a saved run.

Fix the transition to handle both victory and defeat.

### P1.2 — Flask command commits even when rejected

`useFlask()` commits `result.run` without checking `result.accepted`, unlike the other command handlers. This can re-record a terminal run or issue unnecessary persistence writes on rejected actions. Make all ViewModel handlers follow one consistent accepted/rejected command path.

### P1.3 — Persistence writes can race

`commitRunUpdate()` launches a new coroutine for every save/history write. Rapid accepted actions can enqueue concurrent writes; completion order is not guaranteed. An older snapshot may overwrite a newer one.

Serialize writes through a single actor/mutex or a single ordered persistence stream. Keep in-memory state and persisted state ordered.

### P1.4 — Room destructive migration can erase player data

**File:** `app/src/main/java/com/example/data/EmberfallDatabase.kt`, approximately lines 80–96.

The database uses `fallbackToDestructiveMigration(true)`. A schema version change can delete the database, including the active run, run history and Shade deck.

For a game with resumable runs, this is unacceptable in production. Add explicit Room migrations and export schema snapshots. Test upgrades from every supported version.

### P1.5 — Snapshot decoding silently changes unknown tiles to Ember

**File:** `app/src/main/java/com/example/data/RunSnapshotCodec.kt`, approximately lines 113–118.

Unknown tile kinds fall back to `TileKind.Playable(Hue.EMBER)`. That silently corrupts a run while pretending to load it successfully. Unknown enum/content values should fail with a clear, recoverable compatibility error or be migrated explicitly.

The codec also only accepts schema version 2. There is no migration path for older snapshots. Implement versioned migrations before shipping save compatibility.

### P1.6 — Generic Ink payment is not user-selectable in the UI

`GameEngine.tryPayCardCost()` supports a preferred generic hue, but the ViewModel's `playCard(cardUid)` does not accept/pass it. The UI therefore falls back to automatic payment order. ADR-009 allows generic pips to be paid by Spark or a chosen Ink; the user needs a way to choose.

### P1.7 — Generated class art is stale and unused

The archive contains:
- `img_class_pyromancer.jpg`
- `img_class_cinderknight.jpg`
- `img_class_ashweaver.jpg`

These assets preserve the previous, noncanonical class identities. The current class-selection screen uses `ClassEmblemArt` vector drawings rather than those portrait assets. Remove/replace the stale images with Ashen Monk, Quarry Witch and The Unlit art, then wire the correct assets into the class screen. Do not leave old character art in the canonical project.

### P1.8 — Visual Fog effect is absent

The combat screen displays a Fog badge but continues to render the true hue colours and all five Ink pools. This directly contradicts ADR-011. Fix as part of the Fog rules correction, not as a cosmetic afterthought.

### P1.9 — Room model does not match all promises in the implementation plan

The implementation plan describes compendium unlock entities and run unlocks. The database currently has only ActiveRun, ShadeDeck and RunHistory entities. There is no unlock entity/table. Either implement the approved unlock model or remove the promise from the plan. Do not claim it is implemented.

---

## 5. P1 — Build and release reproducibility

The archive contains `gradle/wrapper/gradle-wrapper.properties`, but does not contain:
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`

The build file also explicitly points to:
- `debug.keystore` — absent;
- `my-upload-key.jks` — absent unless supplied externally.

Therefore this archive is not a self-contained reproducible Android build. I could not run Gradle tests or an APK build in this environment because there is no installed Gradle executable, wrapper JAR/script, Android SDK, or configured signing material.

Other release concerns:
- `applicationId` is still `com.aistudio.emberfall.k7m2x`, not a final product identifier.
- Google Services and Secrets Gradle plugins are configured even though this is specified as an offline-first local game and no Firebase/Gemini runtime integration is present in the inspected source.
- `metadata.json` advertises a server-side Gemini API capability that is not represented by the game code. Remove unused AI/server capability declarations and build plugins unless an approved scoped feature actually needs them.
- The Compose BOM is dated 2024-09 while other Android/Kotlin toolchain versions are much newer; validate dependency alignment rather than assuming it is compatible.

Before accepting any build claim, restore the wrapper, supply a controlled debug build setup, and run a clean build from the archive.

---

## 6. Test suite audit

The engine test file contains 15 named tests. This is a useful start, but test count is not proof of design fidelity.

### Tests currently certifying incorrect rules
- `ADR-010_011_012 Brand Fog Seal and ADR-015 Echo execute without Echo-of-Echo recursion` explicitly asserts the wrong Brand/Fog/Seal semantics and approves full-card Echo duplication.
- `ADR-017 Act III Boss victory triggers Dread true ending when Unlit Covenant or Remnant active` approves the shortcut instead of the three-consecutive-turn condition.
- The Spectrum test assumes the first hue pop immediately advances to 1.5× without verifying that against the original full rule text.
- The Unlit starter test checks only 12 cards and Gold frames; it does not verify the ADR-014 card identities and copy counts.

### Important missing tests
- No direct `ZERO_FLASKS` test.
- No direct HP-depletion/game-over navigation test.
- No Vigil spend/burn tests.
- No multi-hit Vigil conservation test.
- No Brand marked-block tests.
- No Fog concealment/tap-duration/Gold Eye tests.
- No sealed-column tap, gravity, paint or Wall tests.
- No Wall overflow exact-predicate/top-two-burn/skip-rise tests.
- No command API adversarial tests for opening rewards during combat or choosing nodes while combat/rewards are active.
- No persistence ordering/race test.
- No Room migration test.
- No unknown snapshot enum/content compatibility test.
- No full three-act victory assertion per class.
- No path-composition validation test for every map route.
- No combat UI tests for the full tap → cast → wall → enemy → reward → map loop.

The batch simulation test runs only two seeds per class and asserts that six runs were attempted, average turns are positive, and average Act reached is at least one. It does not assert that the runs reach Act III or win. It is a smoke test, not a balance validation.

The Android instrumented test is still the template package-name test. Robolectric tests cover title/class selection and a snapshot round-trip, but not the core combat interaction sequence.

---

## 7. Documentation contradictions and scope control

There are two conflicting architecture statements:
- `.aistudio/.../implementation_plan.md` says Kotlin/Compose was confirmed as an owner decision.
- `docs/ADR_CONFLICTS_AND_DECISIONS.md` says the Kotlin decision is provisional pending owner sign-off.

The archive does not contain a decision record resolving that contradiction. The original roadmap's Java 8 pure-domain architecture was not simply a stylistic preference; the migration needed an explicit decision. Keep the pure deterministic engine boundary regardless of language, and record the approved language/runtime decision in one canonical ADR.

The implementation plan is also stale: it still describes Pyromancer/Cinderknight/Ashweaver and Crimson/Amber/Verdigris/Cobalt. It must not remain in the project as an apparently authoritative plan.

The ADR conflict log is more candid: it acknowledges that starter decks and Brand/Fog/Seal parameters were invented because upstream sources were missing. That is useful audit honesty, but it does not make the inventions approved design.

**Missing authoritative references remain a release blocker for content fidelity:**
- Design Bible v0.2;
- CH01 Creative Vision Experience Contract;
- complete authoritative text for ADR-001 through ADR-023;
- the exact upstream baseline source/commit identified by the project handoff.

Until those are recovered, distinguish:
1. verified canonical rules;
2. implementation decisions explicitly approved by the owner;
3. temporary assumptions awaiting source confirmation.

Do not label category 3 as canonical.

---

## 8. Recommended corrective execution plan

### Phase A — Re-lock the design (no feature expansion)
1. Recover the Design Bible v0.2, CH01 and complete ADR set.
2. Replace the stale implementation plan with one scope-locked plan.
3. Create a rules traceability table: source paragraph/ADR → model field → engine function → UI representation → test.
4. Mark all invented stats, starter cards, damage formulas and status values as provisional.

**Exit:** every P0 mechanic has an authoritative source and an acceptance test.

### Phase B — Repair the domain engine
1. Implement Monk Vigil burn and remove repeated counterattack exploit.
2. Rebuild Brand/Fog/Seal to match the original semantics.
3. Correct Wall overflow exactly.
4. Correct Echo to repeat one primary effect only.
5. Implement the actual Dread true-ending state machine.
6. Add command transition guards and make all invalid commands transactional.
7. Correct enemy scaling and Shade scaling from their authoritative formulas.
8. Remove silent board-generation injections or replace them with an approved, deck-accounted no-move policy.

**Exit:** P0 and P1 engine tests pass against the source contracts, not against assumptions.

### Phase C — Restore canonical content
1. Implement the exact ADR-014 Unlit starter.
2. Recover Monk/Witch starter decks and implement them exactly.
3. Restore Monk's class-specific card/resource identity.
4. Verify all card, relic, enemy and reward content against the Design Bible.
5. Add Event node/content support and validate route composition.

**Exit:** no unverified content is presented as canonical.

### Phase D — Repair app integration
1. Route defeat and victory to Run Summary.
2. Add generic Ink payment selection.
3. Implement Fog visual concealment and sealed-column visuals/interactions.
4. Replace stale class art.
5. Serialize persistence writes.
6. Add Room migrations and snapshot compatibility migrations.
7. Remove unused Gemini/Google Services configuration unless explicitly approved.

**Exit:** app behaviour matches the engine and a save can survive process death and version upgrade.

### Phase E — Reproducible QA and release
1. Restore Gradle wrapper scripts/JAR and deterministic build configuration.
2. Add a reproducible debug signing setup; keep release signing secrets out of source archives.
3. Run JVM tests, Robolectric UI tests, Android instrumented tests and clean APK build.
4. Simulate many seeds per class, assert Act III reach and win/loss distribution, and test route variety.
5. Run a complete three-act manual playthrough for each class.
6. Complete accessibility, audio/haptics, performance and release checks.

**Exit:** clean build, no P0/P1 rule defects, verified saves, deterministic replay and measured balance.

---

## 9. Agent execution mandate

Use this as the next agent instruction:

> You are the EMBERFALL implementation engineer. The original EMBERFALL scope, Design Bible and ADR contracts are authoritative. Do not invent missing mechanics or content. Do not use the stale AI Studio implementation plan as a source of truth.
>
> First recover and read the authoritative Design Bible v0.2, CH01 and complete ADR-001..ADR-023. If a source is unavailable, stop that specific implementation and mark it blocked; do not fill the gap with your own rules.
>
> Work in this order: (1) Ashen Monk Vigil-burn identity, (2) Brand/Fog/Seal, (3) Wall overflow, (4) Echo, (5) Dread true ending, (6) command-state guards, (7) enemy and Shade scaling, (8) exact starter decks, (9) Event/map route fidelity, (10) UI and persistence corrections.
>
> Preserve the deterministic pure domain boundary. Do not expand the card pool, enemy roster, meta systems, backend, cloud, online play or new game modes. Do not reintroduce Pyromancer/Cinderknight/Ashweaver or the noncanonical hues.
>
> Every change must include: source trace, failing regression test first, minimal implementation, updated test, and a short reviewer note. Do not claim a phase complete unless its acceptance tests run successfully. Report build/test commands and raw results. Never claim Android build success if the wrapper/SDK/signing prerequisites are missing.
>
> Preserve all existing project files and user work. No broad rewrites, no deleting assets or state, and no scope expansion without owner approval.

---

## Final decision

**Keep the Kotlin/Compose shell and deterministic engine foundations. Reject the current rules implementation as canonical.**

The archive has repaired the visible naming drift, but it still has serious design-fidelity defects, an exploitable command API, incorrect status/overflow/ending mechanics, incomplete persistence safety, and no reproducible build setup.

The next milestone is not “more content” and not “polish.” It is a rules-correctness milestone with source traceability. The Monk must actually play like the Monk; Brand, Fog, Seal, Wall, Echo and Dread must behave as specified; and the engine must reject invalid progression commands even if the UI is bypassed.

**Approval status: NOT APPROVED for production or further content expansion. Approved only as a refactor base after the corrections above.**
