# EMBERFALL — New Build Review and Original-Design Refactor Plan

**Reviewed archive:** `emberfall-1.zip`  
**Comparison baseline:** `EMBERFALL_Master_Implementation_Roadmap.md` and the earlier scope-locked audit/production guide available in this workspace.  
**Review date:** 2026-11-21

## 1. Executive verdict

The new archive is a meaningful Android prototype, but it is **not a faithful implementation of the original EMBERFALL design**. It replaces the original product identity and several core rules with a different dark-fantasy match-group deckbuilder. It should be treated as a divergent prototype, not as the next compliant milestone of the original roadmap.

The project has a Kotlin/Jetpack Compose application shell, ten navigation destinations, a deterministic Kotlin engine, a campaign/map loop, catalogued cards/enemies/relics, Room/DataStore persistence code, and a small engine test suite. The pure engine source compiles with the installed `kotlinc` toolchain. A complete Android build was **not verified**: the archive contains neither `gradlew` nor `gradle-wrapper.jar`, and no system `gradle` executable is available in the review environment.

**Recommendation:** retain useful implementation work where it is mechanically compatible, but refactor the canonical model and engine before expanding content or polishing the current class UI. Do not continue adding Pyromancer/Cinderknight/Ashweaver content.

## 2. The most consequential design drift

### 2.1 Canonical classes have been replaced

| Original canonical identity | Original affinity | Original signature | New implementation |
|---|---|---|---|
| Ashen Monk | Ember / Moss | Prayer Beads — first Tap each turn grants +2 Ink | Pyromancer, Crimson / Amber, Ember Heart |
| Quarry Witch | Tide / Violet | Chisel Wand — once per fight free recolour | Cinderknight, Cobalt / Verdigris, Basalt Buckler |
| The Unlit | Gold / Ash | Hollow Crown — 1 Spark per Ash block popped | Ashweaver, Violet / Cobalt, Aether Spindle |

This is a product-level mismatch, not a naming-only issue. The class identities govern deck composition, hue affinity, relic mechanics, board probabilities, and strategic play. The three new classes and their associated relics/cards/portraits should be removed from the canonical content path and replaced with the original three identities.

The original Unlit starter deck is specified as a 12-card starter in ADR-014. Its unusual Gold-frame/Ash-hue details must be recovered and implemented exactly; do not normalize or reinterpret them. The available baseline does not provide enough detail to invent the Monk and Witch starter lists. Recover the referenced Design Bible / approved starter definitions before finalizing those lists.

### 2.2 The hue system has been replaced

Original hues: **Ember, Tide, Moss, Gold, Violet**.  
New hues: Crimson, Amber, Verdigris, Cobalt, Violet.

The replacement is embedded in `EngineModels.kt`, `GameCatalog.kt`, board painting, UI colors/glyphs, card definitions, and class affinity. Restore the original five hues as canonical IDs and use display labels/glyphs as presentation metadata. Do not keep the current five as aliases: aliases would contaminate saved state, card-frame identity, and balancing.

### 2.3 Architecture has changed without matching the governing roadmap

The original Master Roadmap explicitly calls for a lightweight Java 8-compatible pure domain core, minimal dependencies, and no Gradle/Compose/game-engine migration without an explicit architecture decision. The new archive is a full Kotlin/Compose/Room Android app. Its bundled AI Studio implementation plan says a full Kotlin migration and separate board/combat decks were confirmed, but that plan conflicts with the original roadmap and prior scope-locked guide available for this review.

Treat the original roadmap as governing unless the project owner explicitly supersedes it with a new ADR. If the owner chooses to retain Kotlin/Compose, record that as an architecture change and port the original domain semantics faithfully. Do not let a framework migration silently become a game-design rewrite.

## 3. Mechanics comparison and defects

| Area | Original design requirement | Current archive | Assessment |
|---|---|---|---|
| Board | 7×7 orthogonal connected-group board, one incoming/hidden row | 7×7 grid only | Incoming row and wall-rise lifecycle absent |
| Hue set | Ember/Tide/Moss/Gold/Violet | Crimson/Amber/Verdigris/Cobalt/Violet | Wrong canonical data model |
| Board painting | Weighted by player deck card-frame hues using ADR-003 formula | Independent `BoardPaintDeck`; static class weights 8/7/6 | Wrong source of weights and wrong deck relationship |
| Combat deck | Canonical player deck; card definitions carry frame hue | Separate `CombatDeck`, but card definitions have no frame-hue field | Cannot derive canonical board weights |
| Prism | ADR-004: joins the contacting hue; ties resolved by wheel order from current Run hue; no unrelated cluster bridge | Prism tap bridges all adjacent hue clusters; test explicitly expects multi-hue bridge | Direct ADR violation |
| Spectrum | ADR-005 ordered wheel; Run starts at 0; correct next hue increments; out-of-order pop resets; Prism is next hue | Adds count of new matched hues plus a Prism bonus; no current Run hue | Not the original mechanic |
| Ash | ADR-006: Ash pop yields no Ink and applies n−1 chip after enemy Block | Ash adjacent to matches is removed and adds Ink; no n−1 chip pipeline | Direct ADR violation |
| Stone | ADR-007: group of 4+ breaks orthogonally adjacent Stone; deterministic iterative chain | Stone breaks for 4+ groups **or any Prism**; no specified bounded chain loop | Rule expanded beyond baseline; chain semantics absent |
| Turn | ADR-001: Draw → Weave (up to 3 Taps) → Cast → Wall → enemy → cleanup | Only `PLAYER_ACTION`, `ENEMY_TURN`, terminal phases; cards can be played during the same action window as taps | Phase machine incomplete; ordering cannot be enforced |
| Ink/Spark | Five hue pools; colored pips match hue; generic pips can use Spark/selected Ink; Spark never colored | Single integer `ink`; no Spark model or colored costs | Economy model replaced |
| Wall/overflow | ADR-008 board row insertion, preview, overflow consequences, skip rise | Wall is a defensive integer capped at 20; excess becomes direct damage | Different mechanic; incoming row/overflow contract absent |
| Brand/Fog/Seal | ADR-010/011/012 exact status mechanics | No implementations found | Missing enemy mechanics |
| Enemy | ADR-013 scaled Vigor, per-act damage ranges, Block timing, two intent slots | Two intent slots and Block field exist; enemy HP mostly catalog constants; no full scaling contract | Partial foundation only |
| Event queue | ADR-015 ordered bounded queue (100 events/action), Echo rules | No deterministic event queue or Echo-of-Echo handling found | Missing engine infrastructure |
| Campaign map | ADR-018 15-row branching map with validated route mix | Six rows (`rowCols = [2,3,3,3,2,1]`) | Structural mismatch |
| Shade | ADR-016 Act I row 5; previous dead deck informs encounter; fair Vigor; Cinders + Remnant reward; relics do not pass | Shade appears in generated Act II map; intents derived from previous deck, but reward/legacy rules absent | Partial concept, wrong placement and incomplete consequences |
| Three acts | ADR-023 full ~30-minute three-act run | Three-act progression is scaffolded | Keep progression scaffold; rebuild route/content to spec |
| Endgame | Unlit Covenant + Dread true-ending conditions (ADR-017), Ascension/Covenants as specified | No Covenant, Dread, Ascension or true-ending implementation found | Missing |
| Meta progression | No meta-currency; Codex/best-floor/Bloodstain systems | Run currency called `shards`; no evidence of canonical Bloodstain progression | Clarify whether shards are strictly run-local; do not add permanent currency |
| Persistence | Versioned save and deterministic replay from seed + commands | Room active-run JSON snapshot, history, Shade deck | Save scaffold exists; command replay, content/engine versioning and full deterministic resume not demonstrated |

## 4. Source-level observations

### Positive work worth retaining

- `SeededRng.kt` provides a deterministic PRNG and explicit seed parsing.
- `BoardEngine.kt` has a useful 7×7 grid representation, orthogonal traversal, stable column gravity, and deterministic tile IDs.
- `CombatDeck.kt` provides draw/hand/discard/exhaust zones.
- `GameEngine.kt` centralizes much of the combat mutation rather than placing all rules in Compose UI.
- `CampaignGenerator.kt` creates a connected branching graph.
- The UI is divided into reusable screen/component packages, and `MainActivity` launches the Compose app.
- Room/DataStore persistence and the ten-screen navigation shell are useful scaffolding if the architecture decision is explicitly ratified.

### High-risk implementation details

1. **`HeroClass` is the wrong domain enum.** It hardcodes the substituted class names, affinity, HP, flask counts, starting shards, relic IDs, and descriptions.
2. **`GameCatalog.starterDeckIds()` defines three invented 8-card decks.** These do not implement the canonical class starter decks. The exact 12-card Unlit starter is absent.
3. **`CardDef` has no frame-hue property.** Therefore ADR-003 cannot derive board hue weights from card frames.
4. **`BoardPaintDeck.defaultWeights()` uses fixed class affinity weights.** It does not implement `max(0.08, (cards_of_hue_h + 1)/(deck_size + 5))` and does not represent the intended deck-driven painter.
5. **Prism resolution is overpowered and nondeterministic relative to ADR-004.** A Prism tap collects adjacent hue components into one group. The engine has no current Run hue or wheel-order tie resolver.
6. **Ash is mechanically inverted.** It is automatically removed around a match, awards Ink, and does not create the required n−1 chip damage. The `inkGained` formula explicitly adds `clearedAsh.size`.
7. **Spectrum is not a sequence.** `matchedHuesThisTurn` tracks a set, and damage scales from the number of newly seen hues. It cannot enforce the wheel order, repeated-hue reset, or out-of-order behavior.
8. **Turn phases are underspecified.** `TurnPhase` has no Draw/Weave/Cast/Wall/Cleanup phases. `playCard()` is callable during `PLAYER_ACTION`, so the engine cannot enforce the canonical Weave-then-Cast order.
9. **Board wall-rise is missing.** The board remains 7×7 and cascades refill vacancies; no incoming row is represented or advanced. `applyWallAndOverflow()` implements a separate >20 Wall-to-damage rule.
10. **Map structure is not canonical.** Six rows replace the 15-row route, and Shade is assigned to an Act II row rather than the specified Act I row 5.
11. **Shade's legacy is incomplete.** Previous card IDs are used to construct attack/defend intent patterns, but the Cinders/Remnant reward and no-relic-transfer rules are not represented.
12. **Persistence is not replay.** Saving a JSON snapshot is useful, but it is not proof that seed + command log reproduces each state. Verify RNG state, engine/content version, all map state, pending effects, and exact resume behavior.
13. **The test suite is too small and some tests institutionalize wrong rules.** The Prism test asserts multi-hue bridging; the Spectrum test checks integer arithmetic rather than the Run sequence; the Ash/Stone tests do not cover ADR-006/007. Rewrite these tests against the canonical ADRs before using them as regression gates.
14. **The supplied build cannot be independently reproduced from this archive.** There is no Gradle wrapper script or wrapper JAR. The pure engine sources compile with local `kotlinc`; Android build, Compose compilation, Room generation, and instrumentation tests were not verified.

## 5. Refactor strategy — preserve the product, then strengthen the implementation

### Phase A — Freeze identity and restore canonical content (P0)

1. Declare the original Master Roadmap and scope-locked ADRs as the governing baseline.
2. Create a single canonical domain vocabulary: `Hue { EMBER, TIDE, MOSS, GOLD, VIOLET }`; `HeroClass { ASHEN_MONK, QUARRY_WITCH, THE_UNLIT }`; neutral tile kinds `ASH`, `STONE`, `PRISM`.
3. Replace class data definitions with data-driven definitions. Do not keep the three new classes as selectable options or compatibility aliases.
4. Implement signature relic definitions and mechanics: Prayer Beads, Chisel Wand, Hollow Crown.
5. Restore canonical frame hue to `CardDef` and canonical starter deck data. Recover missing Design Bible definitions rather than inventing the Monk/Witch decks.
6. Replace all class art and UI labels with the canonical characters. The current Pyromancer/Cinderknight/Ashweaver artwork is not reusable as canonical character art.
7. Record unresolved source conflicts (including Vigil/Ink caps and row-overflow wording) in an ADR decision log. Do not guess.

**Gate A:** no noncanonical class/hue identifiers in engine/content/UI; signature relic tests pass; starter-deck data matches approved source.

### Phase B — Rebuild the pure rules core (P0)

1. Decide architecture explicitly. Preferred baseline remains Java 8 pure domain with thin Android presentation. If Kotlin/Compose is retained by owner decision, keep the same package boundary: pure Kotlin domain with no Android imports, no Compose references, no Room types.
2. Introduce immutable definitions and explicit state models: `RunState`, `EncounterState`, `TurnState`, `BoardState`, `PlayerState`, `EnemyState`, `DeckState`, `ResourceState`.
3. Implement typed commands and stable rejection reasons: `TapCell`, `CastCard`, `EndTurn`, `UseFlask`, `ChooseNode`, `ChooseReward`.
4. Enforce ADR-001 phase machine transactionally. A rejected command must not consume taps/resources or mutate the board.
5. Use deterministic named RNG streams (board paint, card shuffle, map, enemy/event) derived from the run seed. Persist stream state/counters.
6. Add bounded event queue per ADR-015 with stable priority/tie-break and a 100-event action budget.
7. Implement the canonical deck-driven board painter and ADR-003 formula. Clarify draw/consume/reshuffle semantics from the accepted spec before coding.

**Gate B:** domain compiles independently of Android; command legality, determinism, transactional rejection, and invariants are covered by tests.

### Phase C — Implement board mechanics in canonical order (P0)

1. Add visible 7×7 board plus incoming/hidden row.
2. Implement group search and nonmutating preview.
3. Implement Prism contact-hue selection and wheel-order tie resolution (ADR-004); do not bridge unrelated hue groups.
4. Implement Spectrum wheel and reset semantics (ADR-005) with fixed-point half steps.
5. Implement Ash pop/no-Ink/n−1 chip after Block (ADR-006).
6. Implement Stone iterative orthogonal chain break for qualifying 4+ groups, bounded and deterministic (ADR-007).
7. Implement gravity then new paints in the exact specified order.
8. Implement row insertion, incoming-row preview and overflow consequences (ADR-008).
9. Implement five colored Ink pools, Spark, colored/generic pip payment (ADR-009).
10. Implement Brand/Fog/Seal (ADR-010/011/012).

**Gate C:** golden tests for every ADR; property tests for board dimensions, gravity, no invalid cells, bounded cascades, resource caps, and deterministic previews.

### Phase D — Combat and canonical classes (P0)

1. Implement ADR-013 enemy scaling, two telegraph slots, per-act attack bands and Block timing.
2. Add exact class-specific mechanics through relic/passive hooks, not UI special cases.
3. Restore card effects through typed effects and the event queue.
4. Ensure Hollow Crown awards Spark for Ash blocks popped; Ash does not award Ink.
5. Implement complete enemy statuses and Act I Hollow Warden.
6. Ensure all class starter decks are source-backed and tested.

**Gate D:** one complete deterministic encounter per class, with exact class signature behavior and at least two real enemy intent types.

### Phase E — Full campaign and legacy loop (P1)

1. Replace six-row map with the 15-row ADR-018 map and validate every route.
2. Restore canonical node mix and route counts.
3. Put Shade at the specified Act I row 5; implement performance-scaled Vigor, previous-deck intent derivation, Cinders + one Remnant reward, and no relic inheritance (ADR-016).
4. Implement the three-act full-length run (ADR-023), Act transitions, rewards, campfires, shops/events/treasure as source-defined.
5. Implement Covenant/Ascension/Bloodstain/Codex progression without introducing meta-currency.
6. Implement Unlit/Dread true-ending conditions exactly (ADR-017).

**Gate E:** seeded end-to-end run with route validation, Shade chain, act bosses, final ending, defeat, and reproducible save/resume.

### Phase F — Android/UI integration and release (P1)

1. Keep the UI as a projection of engine snapshots; it must never own game rules.
2. Restore the approved ten-screen flow and portrait-first layout, but bind each screen to canonical game state and commands.
3. Replace class select content/art with Monk/Witch/Unlit; display starting deck, affinities, starting resources and signature relic from canonical definitions.
4. Implement exact combat preview for Spectrum, Ink/Spark, Ash chip, Stone breaks, Brand cost and Wall/overflow.
5. Verify all screens on small phones/tablets and colorblind/glyph mode.
6. Add versioned save migration, deterministic command log/replay, accessibility, performance, crash recovery, and release signing.
7. Add Gradle wrapper files and document reproducible build/test commands if Kotlin/Android remains the approved stack.

**Gate F:** reproducible Android debug/release builds, device tests, save/resume/replay proof, screenshots for all screens, and no unresolved P0/P1 design defects.

## 6. Recommended code disposition

| Current file/package | Action |
|---|---|
| `engine/SeededRng.kt` | Retain concept; split deterministic named streams and add persisted stream state |
| `engine/EngineModels.kt` | Refactor heavily; replace hue/class/phase/resource models with canonical vocabulary |
| `engine/GameCatalog.kt` | Rebuild canonical content data; remove invented classes/cards/relics |
| `engine/BoardPaintDeck.kt` | Replace with ADR-003 deck-frame-driven painter; do not retain static class weights |
| `engine/BoardEngine.kt` | Retain grid/traversal/gravity foundation; rewrite Prism, Ash, Stone, Spectrum, row-rise and painter rules |
| `engine/CombatDeck.kt` | Retain card-zone concept; integrate canonical deck/frame data and exact phase transitions |
| `engine/GameEngine.kt` | Refactor around typed commands, explicit phase machine, transactionality and effect queue |
| `engine/CampaignGenerator.kt` | Replace six-row map and Shade placement with ADR-018/016 rules |
| `engine/SimulationRunner.kt` | Retain as a harness only after replacing bot assumptions and adding deterministic trace output |
| `data/*` | Retain persistence scaffolding only after versioned save schema and replay requirements are met |
| `ui/*` | Retain navigation/layout components selectively; replace class identities, hue rendering and all incorrect rule previews |
| `res/drawable/img_class_*` | Replace; current character art depicts noncanonical classes |
| `app/src/test/.../EmberfallEngineTest.kt` | Rewrite ADR tests; remove tests that assert incorrect Prism/Spectrum behavior |

## 7. Required test matrix before content expansion

- Class identity, hue affinity, signature relic and starter deck for all three classes.
- Board painter weights computed from card frame hue and seeded draw sequence.
- Prism: one contacting hue, multi-contact tie by current Run hue wheel order, no unrelated bridge.
- Spectrum: each wheel transition, wrap, repeat, out-of-order reset, Prism step.
- Ash: no Ink; n−1 chip; enemy Block absorbs before chip reaches Vigor.
- Stone: direct tap rejected; qualifying group breaks orthogonal Stone; iterative chain bounded.
- Turn machine: Draw/Weave/Cast/Wall/enemy ordering; fourth tap rejected; card during Weave rejected; invalid commands transactional.
- Ink/Spark: five pools, colored pip restrictions, generic pip choice, Spark restrictions, caps/overflow.
- Wall: incoming row preview, rise, top-row overflow, Vigil loss, top-two-row burn, Flask loss, skipped rise.
- Enemy: scaled Vigor, Act damage bands, Block reset timing, both telegraph slots.
- Brand/Fog/Seal status durations and interaction with board operations.
- Event queue: deterministic ordering, Echo, Echo-of-Echo ignored, 100-event budget.
- Campaign: 15 rows, route mix, all paths valid, correct bosses and Shade position.
- Shade: previous deck snapshot, intents, Vigor scaling, Cinders/Remnant reward, no relic transfer.
- Endgame: three acts, Unlit covenant/Dread ending requirements, Ascension and progression rules.
- Save/replay: versioned serialization round-trip and exact command-sequence reproduction.

## 8. Agent execution mandate

> Work from the original EMBERFALL Master Implementation Roadmap and the accepted ADRs as the governing design. The current Kotlin/Compose archive is a divergent prototype and must be refactored to restore the canonical game, not extended as a new design. First restore Ashen Monk (Ember/Moss), Quarry Witch (Tide/Violet), and The Unlit (Gold/Ash), including Prayer Beads, Chisel Wand, and Hollow Crown. Restore Ember/Tide/Moss/Gold/Violet and all ADR mechanics. Do not invent class decks, resource rules, map structure, status effects, art identities, or endgame content. Recover missing source specifications and record unresolved conflicts before implementing. Keep all domain logic pure and deterministic. Implement in the phase order in this report, with tests and golden traces at every gate. Preserve compatible infrastructure only when it does not encode incorrect rules. Do not mark a phase complete based on UI mockups, documentation, or unexecuted tests. Report changed files, exact test/build commands and outputs, commit SHA, known limitations, and any ADR requiring owner approval.

## 9. Immediate next move

Do not add more cards, relics, enemy variants, or visual polish yet. Start with **Phase A (identity/content restoration)** and a domain-model diff. Once the canonical classes, hues, relics, starter decks, and source hierarchy are correct, proceed to the phase machine and board mechanics. This avoids multiplying the current design drift across dozens of new assets and content definitions.
