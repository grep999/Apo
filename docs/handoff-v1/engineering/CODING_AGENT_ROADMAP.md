# EMBERFALL — Coding Agent Production Roadmap

| Handoff field | Baseline |
|---|---|
| Version | 1.0 |
| Specification | EMBERFALL Game Specification v0.2, 21 November 2026 |
| Workflow control plane | grep999/maestro v0.3.0; main inspected 21 November 2026 |
| Product target | Premium, portrait-first Android roguelike puzzle deckbuilder; iOS/gamepad at 1.0 subject to the M2 architecture gate. |

> This is an engineering execution plan, not a claim that code has already been written. The only authoritative gameplay facts are those in the supplied v0.2 game specification. Recommendations and open decisions are labelled separately. The coding agent must not silently convert a design proposal into a game rule.

## 0. Executive contract

Build EMBERFALL as a complete, deterministic, data-driven roguelike in which the color composition of the player's deck changes the board that generates the resources used to play cards. The board is not a minigame beside the deckbuilder: the board is the energy engine, spatial puzzle and pressure clock. The corridor, lighting, sound, cards, relics, enemies and run structure must all reinforce that loop.

The coding agent owns implementation quality and integration. It must:

1. Discover and inspect the actual source workspace before editing. The connected GitHub inventory had no existing EMBERFALL source implementation when this handoff was prepared. The handoff branch now exists at grep999/Apo; it contains engineering plans and references, not a playable game. Do not write into ramshield or third-age-mmorpg; they are unrelated projects.
2. Locate the referenced SLAY! prototype if it is present in the assigned workspace or attachments. Reuse only architecture and code that can be inspected and legally reused. If it is absent, record the dependency as unresolved and bootstrap from this architecture plan rather than pretending the prototype was inspected.
3. Preserve the specified rules. Put ambiguities into the decision register and obtain an explicit decision before locking them into code.
4. Keep the simulation deterministic and independent of Android APIs. Rendering and input must not mutate game state directly.
5. Store cards, relics, enemies, intents, events, bosses and balance numbers as validated data wherever practical. Do not create 360 bespoke card classes.
6. Work in isolated branches/worktrees. Every candidate has a base SHA, candidate SHA, changed-path list, exact GateRunner results and a matching independent review.
7. Use the current Maestro v0.3 lifecycle: GROUNDING → MOMENTUM → MASTERY → REPETITION. Stage is not a role. The builder cannot be the reviewer. Review must bind to the exact candidate reference. Missing evidence is not PASS.
8. Deliver incrementally. A milestone is closed only when its acceptance criteria, tests, content validation, device checks (where relevant), visual review and independent Mastery evidence are present.

## 1. Source-of-truth hierarchy

When sources disagree, apply this order and document the conflict:

1. Explicit product-owner decision made after this handoff.
2. EMBERFALL Game Specification v0.2 (source file under references; Sections 1–19).
3. Accepted ADRs in engineering/ARCHITECTURE_DECISIONS.md and engineering/OPEN_DECISIONS.md.
4. Accepted implementation behavior demonstrated by tests and an approved candidate.
5. Existing concept art and UI sketches, which are references only, not authoritative game rules or final text assets.

Never treat generated concept-board typography, generated in-image card values, or accidental visual details as specification. Those images can guide mood, silhouette, materials and composition; they cannot override the text specification.

## 2. Product invariants

- Deck paints board: new block hues are weighted by the hues present in the deck, using the specified formula unless an accepted ADR changes it.
- Tap feeds card; card reshapes board: groups generate colored Ink and chip damage; cards spend Ink and can manipulate the board.
- Readable threats: each enemy exposes two upcoming intents, including their numeric effect/target where applicable.
- Death becomes an encounter: a saved dead deck can return as a Shade in the next run, with a bounded reward.
- The dungeon is a place: the portrait screen reads as one torch-lit, first-person crypt scene with a carved slate board embedded in it.
- Shape as well as color: all five hues have unique glyphs; neutral Ash, Stone and Prism are visually distinguishable and accessible.
- Premium and fair: no ads, energy timers or randomized paid rewards. Optional cosmetics must not affect play.
- Local-first: a run is saved after every node. Backend 1.0 scope is limited to daily seeds, leaderboards and replay validation; no account is required by the spec.

## 3. Locked baseline from specification v0.2

| System | Baseline |
|---|---|
| Platform | Android first; min API 24; portrait; target API must be rechecked at submission |
| Screen | 390×844 design reference; corridor 30%, slate 40%, hand/HUD 30% |
| Board | 7 columns × 7 visible rows plus one hidden/incoming row; 44 dp target cell |
| Hues | Ember, Tide, Moss, Gold, Violet; neutral Ash, Stone, Prism |
| Starting turn | Draw 5; 3 Taps; Cast; Wall rises; enemy intent resolves; discard hand |
| Starting deck | 12 cards; 5-card hand |
| Ink | Cap 8 per hue; overflow to Spark; Ink carries between turns |
| Player | 70 Vigil; Block resets each turn; 2 Flasks initially, max 5; each heals 30 Vigil |
| Pop | Orthogonally connected same-hue group, minimum 2; gravity is vertical only; columns never slide sideways |
| Pop yield | min(n,8) + floor(max(n-8,0)/2) Ink, then Run multiplier; chip damage n-1 after enemy Block |
| Spectrum | Wheel: Ember → Gold → Moss → Tide → Violet → Ember; bonus 1 + 0.5 × Run; out-of-order pop resets Run to 0 for that pop |
| Neutral blocks | Ash pops but pays no Ink; Stone breaks when an adjacent group of 4+ pops; Prism joins a group and counts as the next hue |
| Deck weights | weight(h)=max(0.08,(cards_of_hue_h+1)/(deck_size+5)), normalized |
| Enemies | Show two upcoming intents; threats may damage Vigil or alter the board |
| Run | Three acts; 15-row branching map per act; around 30 minutes; save after every node |
| Classes | Ashen Monk, Quarry Witch, The Unlit |
| Archetypes | Monochrome, Spectrum, Demolition, Ash Warden, Vigil-burner |
| Content target | 360 cards; 150 relics; 36 regular enemies; 12 elites; 3 main bosses; 3 optional covenant bosses; 50 events; 5 Covenants; 15 Ascension levels; 400 codex entries |
| Quality targets | 60 fps on a four-year-old mid-range phone; 99.5% crash-free; deterministic replay; 3,000 seeded runs per class/build in the balance sweep |

All numbers are starting values/HUNCH where the source says so. They must be configurable and measured, not scattered as literals.

## 4. Recommended engineering architecture

### 4.1 Application shape

Start with a native Android app and a pure Kotlin/JVM simulation module. Keep the simulation module free of android.* imports. Use a renderer adapter so the vertical slice can begin with Android Canvas and a measured OpenGL ES 3 lighting pass can be introduced later. Do not add a full game engine or large dependency stack before the M2 gate. The iOS requirement is real but its cross-platform implementation strategy is explicitly an M2 decision; preserve a clean platform boundary now.

### 4.2 Proposed Gradle modules

- game-core: deterministic domain model, rules, reducer, RNG, replay.
- game-content: schemas, content bundles, validation, localization keys.
- balance-sim: headless bots, seeded sweeps, metrics export.
- android-app: lifecycle, navigation, rendering, input, storage, audio, haptics.
- asset-tools: optional asset manifest validation and atlas tooling.

If the discovered prototype already has a sound equivalent, adapt it rather than renaming everything. Keep the domain boundaries even if the exact module names differ.

### 4.3 Simulation API

Prefer a command/reducer interface:

    interface GameEngine {
        fun newRun(seed: Long, classId: ClassId, options: RunOptions): GameState
        fun preview(state: GameState, action: PlayerAction): ActionPreview
        fun apply(state: GameState, action: PlayerAction): TransitionResult
    }

    data class TransitionResult(
        val state: GameState,
        val events: List<GameEvent>,
        val outcome: ActionOutcome
    )

GameState is the complete serializable truth. UI animation consumes GameEvent values after a valid transition; it does not decide gameplay. Preview must be pure and must not consume RNG or mutate state.

### 4.4 Determinism

Use an explicit, versioned RNG implementation owned by game-core; never rely on global randomness, wall-clock time, UI callbacks or collection iteration order. A replay identity must include seed, rules_version, content_version, rng_version, input_log and initial_class_and_run_options. Replays against a different rules/content version must be rejected or run in a compatible versioned interpreter. Never silently replay an old run with new card definitions.

### 4.5 Event resolution

All card/relic/enemy triggers flow through one deterministic EffectResolver/event queue. Define priority and ordering. Prevent infinite recursion with explicit per-action limits and cycle detection. No card may execute arbitrary embedded code. Content uses a typed effect DSL and validated parameters.

## 5. Proposed repository layout

    emberfall/
      app/                         Android application shell
      game-core/src/main/kotlin/    pure simulation and domain
        domain/board/              board, blocks, groups, gravity, wall
        domain/combat/             players, enemies, intents, phases
        domain/cards/              cards, costs, hand, deck, effects
        domain/run/                map, nodes, rewards, acts, covenants
        domain/progression/        relics, unlocks, ascension, shade
        engine/                    reducer, rules, event queue, invariants
        random/                    deterministic RNG and versioning
        replay/                    input logs, hashes, compatibility
      game-content/                JSON content and schemas
        cards/ classes/ relics/ enemies/ bosses/ events/ localization/
      balance-sim/                 bot policies, sweep runner, metrics
      android-app/
        ui/screens/ ui/combat/ ui/map/ ui/cards/ ui/components/
        render/ audio/ haptics/ storage/ accessibility/
      tools/                       content validator, replay checker, asset manifest
      docs/                        ADRs, known gaps, build, release, testing
      .maestro/                    local DB ignored; task packets and evidence exports

If the existing source repository uses a sound equivalent, adapt it; preserve these domain boundaries.

## 6. Implementation sequence for the game model

### 6.1 Board primitives

Implement typed IDs and immutable value objects: Hue, NeutralKind, Block, Cell, Coordinate, Board, ConnectedGroup, BoardSnapshot and IncomingRow. Board dimensions and hidden-row semantics come from validated configuration. Use a flat indexed array or compact arrays to avoid allocation churn in the simulation loop.

Required operations:
- Enumerate orthogonal neighbors only.
- Find every connected component deterministically.
- Validate whether a group can be popped (size ≥ 2, same hue, Prism rules resolved by ADR).
- Compute Ink and chip damage as separate values.
- Remove group and apply per-column gravity; never shift columns sideways.
- Apply Stone break checks and resolve whether chain-breaking is allowed; unresolved ordering is a P0 decision.
- Preview future row and execute Wall rise only through one rule function.
- Expose topology/board metrics for bots without changing state.

### 6.2 Ink and Spectrum

Model each colored Ink pool independently, with cap 8. Model Spark separately. The implementation must define whether generic cost 1 and Spark are distinct costs and how colored Ink may pay colorless pips. Cost parsing and payment must be atomic: insufficient resources means no partial card play. All arithmetic is integer/fixed-point and deterministic; no float rounding drift in replay.

Spectrum calculation is centralized in one pure function. It receives previous Run state, popped group hue interpretation, wheel orientation and card modifiers, and returns new Run plus exact multiplier. Use rational or integer fixed-point arithmetic. Every edge case is covered by golden tests before card content depends on it.

### 6.3 Deck, hand and card definitions

A card definition includes stable card_id, display name key, hue/frame, type, rarity, cost, effect sequence, upgrade reference, class/neutral ownership, tags and spawn-weight contribution. Runtime cards use IDs and mutable upgrade/exhaust state; content definitions remain immutable.

Starter decks are data, not constructors. The 12-card class deck must be generated by deterministic deck definitions. All acquisition/removal paths recalculate board spawn weights from the current deck. Curses count according to their specified hue contribution; do not special-case Dread in a hidden way.

### 6.4 Effect DSL

Implement typed operations from the specification: DAMAGE, BLOCK, POP, RECOLOR, FUSE, SPAWN, SHATTER, DRAW, TAPS, DELAY_INTENT, HEAL, GAIN_INK, ROTATE_WHEEL, SKIP_WALL_RISE, REMOVE_STATUS, APPLY_STATUS, ECHO_LAST_CARD and EXHAUST. These names are proposed schema keys; they do not change the effects described by the spec. Each operation has schema validation, target validation, deterministic ordering and at least one golden test. Add operations only with an ADR and effect-specific tests.

### 6.5 Turn state machine

The source v0.2 order is Draw → Weave (3 Taps) → Cast → Wall rises → enemy acts → hand discard. Implement this order as the baseline. The possibility of interleaving Taps and card plays is a design question, not permission to change the order. Cards that contain POP can still trigger board pops without spending a Tap as the keyword specifies.

Every phase transition is explicit. Invalid actions are rejected without partial mutation. Enemy intent previews must be computed before the player commits and must match the eventual resolution.

### 6.6 Enemy and boss system

Enemy definitions include HP/Vigor, intent sequence/policy, board attacks, statuses, phases, rewards and art references. Each enemy shows the next two intents. Bosses use explicit three-phase state machines and unique board mechanics. The intent planner must be deterministic for a given state/seed and must not hide untelegraphed damage.

### 6.7 Run and progression

Run state includes seed/version, act, map node, deck, relics, Cinders, Flasks, current Shade record, unlocked data and save snapshot. A map is a 15-row branching graph per act with validated path connectivity and target node mix. Every node transition persists state atomically. Reward choice, skipping, shop purchase, bonfire actions and event choice are all commands with evidence-friendly replay entries.

### 6.8 Death and Shade

On death, persist the dead deck snapshot and relevant metadata. In the next run, generate the Shade node at Act 1 row 5. The Shade uses a bounded, deterministic representation of the prior deck as enemy intents; it must not literally execute unrestricted player card effects without a balance model. Rewards are Cinders recovered and one Remnant chosen from the dead deck, matching the spec. The exact Shade scheduler, relic inheritance and difficulty scaling need ADR decisions.

## 7. Android presentation and interaction

### 7.1 Combat screen composition

Honor the stacked scene: corridor/enemy/intents in the upper third; carved-glass slate in the middle; cards and controls in the lower third. Keep the board square and readable. HUD values must be legible over textured backgrounds. Keep the critical End Turn action reachable by one thumb.

### 7.2 Touch interactions

- Tap a highlighted legal group to pop it.
- Long-press a group to preview size, Ink and Run result.
- Drag a card to a valid target; provide explicit target highlights and cancel affordance.
- Long-press a card for readable detail.
- Optional confirm-Tap mode.
- One Undo for the latest Tap before any card play, subject to deterministic rollback rules.

Use a single input router that converts touch gestures into domain commands. Never let a view mutate a board cell.

### 7.3 Screen inventory

Build these as independent screens/states, with reusable components:
1. Boot/loading and title menu.
2. New run / class select.
3. Run map / node preview.
4. Combat.
5. Card detail/collection/deck management.
6. Reward card selection.
7. Relic selection/collection.
8. Shop.
9. Bonfire.
10. Event dialogue/choice.
11. Shade encounter and reward.
12. Boss phase transitions.
13. Death/results/run summary.
14. Codex/bestiary/lore.
15. Settings/accessibility/audio.
16. Daily seed/leaderboard (backend-gated).

### 7.4 Accessibility and device range

Use glyph + color for every hue and status. Do not communicate legality, danger or intent through color alone. Support scalable text, reduced motion, flashing reduction, haptics off, re-mappable/repositionable controls and reachable lower-third actions. Test actual touch accuracy at 44 dp and on smaller devices. Fog behavior must preserve accessibility without defeating the intended information mechanic; see P0 decisions.

### 7.5 Rendering pipeline

For the vertical slice, render the board, cards and UI with Canvas or the inspected prototype renderer. Use layered corridor art, parallax and light overlays. Add OpenGL ES 3 point lighting/bloom only after the Canvas vertical slice meets performance targets and profiling shows a measurable visual/performance need. Every animation is driven by emitted game events and can be disabled/reduced.

## 8. Visual asset and content pipeline

The art direction is a torch-lit crypt, carved slate, iron settings, stained glass, aged parchment and restrained gold metalwork. The five hues are unmistakable; neutral Ash/Stone/Prism have distinct silhouettes. Act palettes: warm cinder crypt, cold flooded vault, white-hot reliquary. The corridor must respond to pops and danger without obscuring intent or touch targets.

Implement an asset manifest with: stable asset key, file path, intended screen, source/artist, license/provenance, dimensions, frame count, pivot, atlas group, color profile, accessibility label and status (concept, approved, production, deprecated).

The included AI concept images are references only. They are not final sprites, final typography, final UI copy, or a licensed art source. The coding agent must not crop incorrect generated text into shipped interfaces. Build with greybox assets first, then integrate approved deliverables. Required asset families include: five hue blocks + Ash/Stone/Prism; board frames and states; card frames by hue/rarity; icon set; corridor layers per act; regular/elite/boss sprites; map/node icons; relic art; event/NPC portrait art; VFX sprites/atlases; menu/brand assets.

## 9. Audio, lighting and haptics

Map five hues to the specified D-minor-pentatonic notes and instrument families: Ember—low brass/taiko D; Gold—handbells F; Moss—marimba/wood flute G; Tide—glass harmonica/singing bowl A; Violet—choir pad/bowed psaltery C. Group size changes voicing; a Spectrum Run climbs the hue sequence. The ambient bed has state-driven layers: drone always; pulse at Wall ≥5; run melody at Run ≥2; choir during boss phase or Vigil <30%.

Audio has to be event-driven, low latency and mixed so player-caused outcomes outrank ambient/enemy sounds. Haptics: size-scaled pop tick, rising Run pulses, double thud for damage, slow Flask pulse; all togglable. Oboe under-40-ms is a HUNCH and must be measured on physical hardware before it becomes a requirement.

## 10. Persistence, replay and backend

- Save after every node; use versioned schema and atomic replace/transaction.
- Include seed, rules/content/RNG versions, player state, board, deck, relics, map, current node and pending choices in save snapshots.
- Keep an append-only action log for deterministic reproduction and replay validation.
- Test app kill, process death, device restart, low storage, interrupted save and migration from every released save schema.
- Backend 1.0: daily seeds, leaderboard submissions and replay validation only; no account requirement. Network failures must never prevent offline runs. Analytics are anonymous and opt-in.

## 11. Balance laboratory

Implement two policies: a greedy expected-value bot and a lookahead bot for skill ceiling. The bot must obey legal actions and must consume the exact same deterministic core as the client. Run the source target of 3,000 seeded runs per class per build for formal balance reports; use a smaller smoke sample per pull request and full/nightly sweeps for CI where runtime cost is high.

Track at minimum: class win rate; archetype win rate; card offer/pick/win contribution; relic pick rate; median run length; death cause split (attacks, Overflow, Vigil-burn); Ink yield per turn; Run distribution; board fragmentation/mean group size; card play rate; boss phase failures; Shade completion rate. Acceptance targets from spec: Ascension 0 class win rate 15–30%; archetype win rates within 10 points; no card above 60% pick rate when offered; median run about 30 minutes; no single death cause dominates; no unbounded Ink/damage pair.

## 12. Production roadmap and gates

The source estimates a lean pro team at 14–18 people and about 24 months; a full studio at 40–60 and about 18 months. Treat these as HUNCH estimates, not staffing commitments. The previous 100-person art-department concept is an aspirational organization chart, not a requirement for the coding agent or a hiring claim.

### M-1 — Source discovery and baseline (1–2 weeks, planning estimate)

Deliver: actual repo inventory; SLAY! prototype search result; game specification import; baseline commit; build reproducibility report; dependency inventory; source/license/IP risks; Maestro v0.3 installed and verified.  
Exit: no unknown source-of-truth path, clean initial build, current upstream Maestro tests/contracts pass, architecture and ADR log created.

### M0 — Core prototype (4 weeks; source target)

Deliver: group-pop board; 12-card sample deck; hue-weighted spawn; one enemy with visible intents; Ink, chip damage and basic Run; rising Wall prototype; headless simulation CLI.  
Exit: board is enjoyable without the full progression system; at least five external testers understand the deck-paints-board loop; no unexplained softlocks; deterministic replay of test seeds.

### M1 — Vertical slice (10 weeks; source target)

Deliver: Act 1 for one class at final target art/audio quality; 60 cards; 25 relics; 8 enemies; 3 elites; 1 boss; map, bonfire, shop, rewards and Shade; complete Android combat UX.  
Exit: 60 fps on target device; complete 15–20 minute session without crashes; board/deck interaction is understandable; core loop is repeatably fun; save/reload works.

### M2 — Engine/platform gate

Deliver: measured architecture decision (stay native or move presentation/shared core to a cross-platform approach); Act 2 greybox.  
Exit: decision supported by device profiling, iOS delivery estimate, renderer maturity, build complexity and measured frame-time data. Do not port for fashion.

### M3 — Alpha

Deliver: all three acts and three classes playable; card/relic/enemy/event targets substantially populated; economy tuned via bot sweep; death/Shade and true-ending routes implemented.  
Exit: all acts can be completed; no blocker bugs; no major content placeholders in primary flow; deterministic save/replay stable.

### M4 — Beta

Deliver: content complete; accessibility pass; localization pipeline; daily seed and replay service if approved; store assets; performance and memory optimization.  
Exit: no P0/P1 defects, target device matrix passes, save migrations pass, content validation passes, balance report within targets.

### M5 — Gold / launch

Deliver: release candidate, privacy/legal review, store listing, premium purchase configuration, crash reporting policy, telemetry consent, support/restore process.  
Exit: 99.5% crash-free target supported by pre-release test evidence and monitored launch; store policy compliant; IP/trademark review complete; offline run works; no unapproved external action.

## 13. Work breakdown — build in this order

| ID | Increment | Main output | Depends on |
|---|---|---|---|
| E00 | Repository and source baseline | reproducible repo, CI, inventory | none |
| E01 | Rules and ADR freeze | executable rule contract and P0 decisions | E00 |
| E02 | Board and deterministic simulation | group-pop, gravity, Ink, Run, Wall | E01 |
| E03 | Turn/combat state machine | draw/weave/cast/rise/intent/cleanup | E02 |
| E04 | Card/effect engine | typed DSL, resolver, golden tests | E02–E03 |
| E05 | Starter content and classes | 3 classes, 12-card decks, vertical-slice content | E04 |
| E06 | Enemies, elites and bosses | telegraphed intent system, phased bosses | E03–E05 |
| E07 | Run map and progression | acts, nodes, rewards, shops, bonfires, events | E05–E06 |
| E08 | Death, Shade and narrative flags | save dead deck, Shade node, Remnant, endings | E07 |
| E09 | Android app and combat UI | input, board rendering, cards, HUD, screens | E02–E07 |
| E10 | Art/asset integration | asset manifest, art atlases, act scenes, sprites | E09 and approved art |
| E11 | Audio, lighting and haptics | hue music, effects, accessibility toggles | E09–E10 |
| E12 | Save/replay/versioning | atomic save, action log, deterministic replay | E02–E09 |
| E13 | Balance lab and content tuning | bots, 3k/class sweep, dashboard | E04–E08, E12 |
| E14 | Full content production | 360 cards, 150 relics, all enemies/events/codex | all previous |
| E15 | Backend/daily seed/leaderboards | optional no-account service | E12–E13 |
| E16 | QA/performance/accessibility/release | gold build and release evidence | all required 1.0 work |

### E00 — Repository and source baseline

- Confirm the target repository and active handoff branch; inspect all files, build scripts, assets, licenses, tests, and any available SLAY! prototype.
- Create a reproducible Gradle build and CI; pin JDK/Gradle/Android Gradle Plugin/SDK and dependency versions.
- Add README, build instructions, KNOWN_GAPS, ADR index, source/asset provenance and baseline test results.
- Add .maestro to ignore rules except exportable task/evidence files; never commit secrets or local state DB unless explicitly required.

### E01 — Rules and ADR freeze

- Convert every numerical rule and card/enemy example into a typed spec/data model.
- Resolve all P0 items in OPEN_DECISIONS.md.
- Create golden examples for group sizes 1/2/7/8/9/10+, each hue, Ash, Stone, Prism, overflow, Run, cost payment and Wall rise.
- Add one testable rules version and content version.

### E02 — Board and deterministic simulation

- Implement model, deterministic RNG, board creation, connected components, pop, gravity, group scoring, Ink, chip, Spectrum, spawn weights, neutral blocks and Wall prototype.
- Add property tests and invariant checks.
- Build a headless CLI that can print a board state and replay action sequences.

### E03 — Turn/combat state machine

- Implement player/enemy resources, turn phases, visible intents, intent queue, target resolution, block reset, damage, death and end-turn cleanup.
- Verify the previewed intent equals the resolved intent.
- Ensure all invalid actions are rejected atomically.

### E04 — Card/effect engine

- Define content schema and validate all effects/costs/tags/rarities/upgrades.
- Implement deterministic resolver and trigger queue, per-action recursion budget, exhaust/retain/echo and target selection.
- Create one golden test per card and one per interaction pair in the vertical slice.

### E05 — Starter content/classes

- Implement the three named class definitions and starter relics.
- Implement 12-card starting decks exactly after resolving the Unlit/Ash hue issue.
- Implement the initial 60-card vertical-slice set; add content completeness and localization-key checks.

### E06 — Enemies and bosses

- Create 8 regular enemies, 3 elites and one three-phase boss for M1; all attacks have two-turn previews.
- Add enemy behavior data and boss state machine; validate every board-changing intent.
- Expand to 36/12/3 core targets as Alpha/Beta progresses.

### E07 — Run/progression

- Generate and validate 15-row branching maps; implement node traversal and per-node save.
- Implement fights, elites, bonfires, shops, events, treasure, rewards, card upgrade/removal, Cinders, flasks, covenants and Ascension.
- Add the 30-minute run telemetry target in the bot/simulation layer, not as intrusive timers.

### E08 — Death/Shade/narrative

- Persist the dead deck and required run record; generate Shade row-5 node on next Act 1 run.
- Make Shade play as a bounded intent model based on prior deck identity and return Cinders + one Remnant.
- Implement codex/dialogue/card/relic/environmental narrative flags; implement normal ending and Fast true-ending sequence exactly once approved.

### E09 — Android app

- Implement screen/navigation graph, portrait layout, adaptive safe areas, touch routing, preview overlays, card drag targets, undo, confirm-tap mode, settings and offline behavior.
- Build combat UI first; do not polish menus while combat is unreadable.
- Add deterministic screenshot fixtures for key states and device aspect ratios.

### E10 — Art and asset integration

- Integrate approved source art through manifests; atlases, 9-slice frames, animation clips, resolution/scale variants and fallbacks.
- Preserve pixel-art/upscale plus true-resolution lighting direction if profiling and readability support it.
- Do not bake gameplay values or critical text into raster images; render all numbers and labels from localization/content data.

### E11 — Audio/lighting/haptics

- Implement hue notes, pop voicings, dynamic ambient layers, card material sounds, enemy tells, act beds and haptics.
- Provide master/music/SFX/ambience/haptics toggles and reduced-intensity option.
- Benchmark audio onset on supported physical Android devices; Oboe is conditional on measured need.

### E12 — Save/replay

- Implement schema migrations, atomic node saves, crash recovery, run seed and action log.
- Replay exact seed + actions and compare state hashes at every checkpoint.
- Reject or version-route incompatible replays.

### E13 — Balance lab

- Add greedy and lookahead bots, metrics, seeded run batches, archetype reports, exploit scans and dashboard export.
- Add small PR smoke sweeps and full 3,000-runs-per-class nightly/release sweeps.
- Every balance change is versioned; keep before/after data.

### E14 — Full content

- Expand to 360 cards (270 class, 70 neutral, 20 curses), 150 relics, 36 regular enemies, 12 elites, 3 bosses, 3 optional covenant bosses, 50 events and 400 codex entries.
- Add all class-specific pools, upgrades, rarities, synergy tags, rewards and interactions.
- All content passes schema checks, reference checks, translation key checks and golden behavior tests.

### E15 — Backend

- Build only after deterministic offline runs and replay validation work.
- Services: daily seed distribution, leaderboard submission, replay validation. No mandatory account.
- Design abuse/rate limits, replay size caps, server resource limits and privacy policy; backend outage never blocks local play.

### E16 — QA/performance/release

- Test minimum supported Android API, current target API, low/mid device and common screen sizes.
- Profile 60 fps, load times, memory, battery, audio latency, input latency and thermal behavior.
- Verify 99.5% crash-free launch target plan, accessibility, content localization, store assets, licensing and privacy.
- Run full regression, Maestro Mastery, release checklist and post-release monitoring design.

## 14. P0 design questions — must be settled before production code depends on them

The coding agent must create an ADR for each. It may prototype alternatives, but must not silently choose:

1. Turn sequencing: source order is Draw → 3 Taps → Cast → Wall → enemy. Are card plays allowed between Taps? Baseline implementation remains strict phases unless the owner approves interleaving after prototype comparison.
2. Initial board generation: board seed, initial fill, row distribution, and whether all cells spawn independently or through topology-aware generation.
3. Hue weights: exact sampling, minimum floor behavior, inclusion of curses/neutral/no-hue cards, deck-size denominator and The Unlit starting deck.
4. Prism semantics: how Prism joins mixed groups, multiple Prism blocks, and wheel interpretation when a group contains more than one candidate hue.
5. First Spectrum pop: initial Run value and what hue begins a sequence after turn reset.
6. Ash: confirm whether Ash pops always deal n-1 chip damage despite paying zero Ink, and whether Ash affects/resets Spectrum Run.
7. Stone: exact timing of break checks, adjacency, multi-Stone break chains and whether a breaking pop can open a new group in the same action.
8. Wall orientation/overflow: incoming row insertion, top-row ejection behavior, hidden row occupancy, condition for cannot rise, and exact rows burned on overflow.
9. Ink/Spark: distinguish 1 any-Ink cost from Spark and define payment order for mixed colored/colorless costs.
10. Brand: clarify whether 2 Vigil penalty is per marked block, per pop, or per action; define when it is applied relative to damage and triggers.
11. Fog: reconcile hues hidden with glyph visibility and colorblind access; define whether glyphs are hidden too.
12. Seal: determine whether locked columns prevent group pop, gravity, card effects, Wall insertion or only direct player actions.
13. Enemy Vigor: formalize fight/elite/boss scaling, node index and rounding.
14. Enemy Block: define Block reset/retention for enemies, and whether chip damage is applied before/after enemy Block as specified.
15. Unlit: its pair is Gold + Ash; specify how Ash cards and Ash-weight contribution work in a 12-card starting deck.
16. Card cost/effect order: atomic payment, target validity, exhaustion, echo, multi-step effects and trigger priority.
17. Shade: whether prior relics affect Shade, how its actions are scheduled, and difficulty scaling while remaining a bounded echo.
18. Boss true ending: reconcile Unlit Covenant unlock with three-turn Fast condition; define state reset, exact telegraphs and failure/retry behavior.
19. Map node arithmetic: reconcile 15 rows with expected fight/elite/rest/shop/event/treasure/boss counts and path constraints.
20. iOS at 1.0: decide at M2 whether to stay native with shared-core port, use KMP, or adopt another renderer; use measured evidence.

## 15. Platform target correction

The source spec says target 35. The handoff's platform-policy check records that, for new Google Play apps and app updates submitted from 31 August 2026, target API 36 or higher is required. Keep minSdk 24 as the compatibility baseline unless device research says otherwise, but configure release targetSdk to current Play policy and re-check immediately before submission. Target SDK is not the same as minimum supported SDK. Official references: https://support.google.com/googleplay/android-developer/answer/11926878 and https://developer.android.com/google/play/requirements/target-sdk.

## 16. Test strategy and definition of done

### Core simulation

- Unit tests for every pure rule function.
- Property tests for board connectivity, gravity conservation, valid coordinates, no horizontal movement, resource non-negativity, cap/overflow behavior, legal card payment and deterministic RNG.
- Golden fixtures for every card and every boss intent.
- Fuzz legal action sequences from many seeds; assert invariants after each transition.
- Replay tests: same version/seed/input log produces identical state hashes; altered input changes state; version mismatch is handled explicitly.

### Android/client

- UI tests for tap, long press, drag/drop, undo, cancel, End Turn, card detail and settings.
- Screenshot tests for combat, map, reward, shop, bonfire, event, Shade, boss phases and death.
- Accessibility checks for content descriptions, focus order, scalable text, contrast, color-independent hue identities and motion settings.
- Device tests at min supported API and current API; test 360×800, 390×844 and 412×915 layouts plus larger font scale.

### Content and balance

- JSON schema validation, unique IDs, valid references, no missing localization keys, no unreachable cards, no empty effects, rarity/pool consistency.
- 3,000 seeded runs per class/build for formal sweep. Track all targets and regression deltas.
- Adversarial exploit tests: infinite Ink, recursive Echo, free repeated Pops, unbounded Taps, unbounded Block, repeated Wall cancellation, card/relic loops, Shade exploits.

### Release

- 60 fps target on a four-year-old mid-range device; measure frame-time percentiles, not only average FPS.
- 99.5% crash-free target at release; include crash/ANR monitoring design and sample threshold.
- Save after each node and after critical state transitions; crash recovery and migration tests pass.
- Offline first-run and offline full-run succeed.
- Legal/trademark/IP and store target API verified immediately before submission.
- Every external action (publish, spend, merge to protected branch, deploy) remains approval-gated.

A task is DONE only when implementation, tests, evidence, docs, known gaps, candidate SHA, Mastery findings and Repetition learning are present. A green build alone is not DONE.

## 17. Maestro v0.3.0 — authoritative workflow procedure

### 17.1 Upstream state inspected

Repository: https://github.com/grep999/maestro  
Branch: main  
Latest commit observed during this handoff: e5247d950fd48a8e1e07acec68ffd04d1445ba55  
Current engine blob observed: bb98d37b43a951f7de368c271896fb03ac31846e  
Version in maestro/__init__.py: 0.3.0.

Important inspected upstream files and blobs:
- maestro/engine.py — bb98d37b43a951f7de368c271896fb03ac31846e
- maestro/cli.py — 90ba6c49aeb34ca298047cc238e3e4cbb155dc0e
- maestro/coding.py — 1cec7ed4bb9b36c32e2999324c3d6a66f458fa1e
- maestro/checks.py — 7dd138b453833287325c2eeb5616f011ea7d204b
- maestro/adapters/protocol.py — 84af929c708a3b79caecca136611db0e4dde18d1
- schemas/task.schema.json v2 — 15b4050fd6392cf8e6bd16a2099383a9ed349124
- schemas/handoff.schema.json v2 — e92582b45827c8efcc04d2860d5143b90c303e06

The current engine enforces reviewer actor inequality with builder actor, current-cycle candidate submission, exact candidate_ref binding, at least one required check for PASS, and (when configured) GateRunner VERIFIED evidence with exit code 0 and a non-empty log hash. It also scopes packets by cycle and resets stale packets when a new cycle begins. These protections are why this handoff archives the older local snapshot rather than calling it the current runtime.

### 17.2 Install and verify upstream Maestro

Run from a trusted development machine with network access. Do not install an unpinned moving branch into a release process without recording the resolved commit:

    git clone https://github.com/grep999/maestro.git ../maestro
    cd ../maestro
    git checkout e5247d950fd48a8e1e07acec68ffd04d1445ba55
    python3 -m venv .venv
    . .venv/bin/activate
    pip install -e .
    python -m unittest discover -s tests -v
    python scripts/validate_contracts.py
    python -m compileall -q maestro scripts tests
    maestro adapters
    maestro adapter-health --name echo

Do not use the echo adapter as evidence of implementation or independent review. It is a smoke-test adapter only.

### 17.3 Start a feature workflow

After the EMBERFALL repository has a baseline commit:

    maestro coding-start EMBERFALL EFB-002-BOARD "Implement deterministic group-pop board" --workspace . --risk R1 --actor human
    maestro show <WORKFLOW_ID>
    maestro begin-momentum <WORKFLOW_ID> --revision <REV>
    maestro coding-seed-work <WORKFLOW_ID> --revision <NEW_REV>

coding-start records the actual Git baseline SHA. coding-seed-work adds research, implementation and GateRunner work items. Keep each feature slice small enough to review independently.

### 17.4 Builder execution

- Assign BUILDER in Momentum only; Momentum is a stage, not a role.
- Use one isolated Git worktree/branch per candidate.
- Record adapter model_identity, run ID, changed paths, base SHA and candidate SHA.
- Use least privilege, scoped environment and no unneeded network access.
- A builder must return code, tests and exact changed paths; a narrative claim is not verification.

### 17.5 GateRunner evidence

The current Maestro GateRunner records command, exit code, SHA-256 log hash, runner identity, candidate SHA and duration. The built-in Python preset is not suitable for a Kotlin/Gradle game. Use the supplied custom gate helper or call GateRunner with CheckSpec commands for the actual project, such as:
- ./gradlew --no-daemon :game-core:test
- ./gradlew --no-daemon :game-content:validateContent
- ./gradlew --no-daemon :android-app:lintDebug
- ./gradlew --no-daemon :android-app:assembleDebug
- ./gradlew --no-daemon :balance-sim:smokeTest

Only include a task once it exists. A command that was not run is not_run, never passed. Run device tests separately on an emulator/physical device and attach their evidence.

### 17.6 Submit exact candidate and independent Mastery

The Momentum packet must include candidate_ref (use git:<candidate_sha>), outcome, changed artifacts, checks and risks. Then:

    maestro deliver <WORKFLOW_ID> <MOMENTUM_PACKET.json> --revision <REV> --actor <builder-identity>
    maestro submit-mastery <WORKFLOW_ID> --revision <NEW_REV>
    maestro review <WORKFLOW_ID> PASS --revision <NEW_REV> --findings '[]' --evidence '["review:<artifact>","gate:<log-hash>"]' --actor <different-reviewer-identity> --candidate-ref git:<candidate_sha> --require-verified

The reviewer must not be the builder, must inspect the exact candidate SHA and changed paths, must challenge every acceptance criterion, and must record findings. Prefer a separate agent/model/vendor for reviewer identity. Engine comparison of actor strings is a guardrail, not cryptographic proof of a human identity.

If review is FAIL, return to Momentum, fix findings and submit a new candidate. If two consecutive Mastery failures occur, the engine flags a root-cause review requirement. If BLOCKED, record the blocker and resume only after it is resolved.

### 17.7 Repetition

After PASS/CONDITIONAL, produce a Repetition packet: expected vs actual outcome; lessons learned; durable state updates; next decision/increment; evidence references; monotonic progress and objective-complete flag. Use maestro learn. The next cycle must not reuse previous cycle evidence. Every review finding should either be fixed, explicitly accepted with owner and rationale, or remain a blocker.

## 18. Coding agent master prompt

The separate MASTER_CODING_AGENT_PROMPT.md is the directly reusable instruction set. Give it the current game spec, this roadmap, the assets/reference folder and an authorized code workspace. The agent must begin with source inventory, then E00/E01, then M0. It must not skip the prototype test to rush into content or produce a code dump that has no runnable build.

## 19. First execution sprint — five working sessions

These are sequencing checkpoints, not a promise of five calendar days. Stop at any session boundary if the evidence is incomplete.

1. Repository reconnaissance: identify the actual game repository, prototype, branch protection, license/provenance and build environment. Record what exists and what is absent; do not create a parallel project if an authorized repository already exists.
2. Maestro baseline: bootstrap the pinned v0.3.0 source, run its upstream tests/contracts, capture the resolved commit and create the 17 Grounding workflows. Verify every workflow is at Grounding and preserve the generated ID map. Do not treat the Echo adapter as a builder.
3. Rules contract: read the source spec line-by-line, map every number and mechanic into typed rule definitions, and turn the 20 P0 items into owner-signed ADRs or explicit prototype-only variants. Keep source-order Draw → Weave → Cast as the baseline until interleaving is approved.
4. Board-only proof: implement deterministic board creation, connected components, legal pop, vertical gravity, Ink/chip calculation, Spectrum and a minimal Wall prototype in the pure core. Add a headless seed/action runner and invariant/property tests. No Android screen polish or large content catalogue yet.
5. External playtest gate: package a reproducible build or headless playable prototype, recruit at least five testers, observe whether they understand and enjoy the deck-paints-board loop, capture confusion/softlock evidence, and return findings to Grounding. Advance to Momentum/Mastery only on evidence, not elapsed time.

## 20. Definition of project success

EMBERFALL succeeds technically when the deterministic game core, data-driven content, Android presentation, saves/replays, bot laboratory and release process are independently testable and integrated. It succeeds experientially when players can understand a board at a glance, make meaningful pop/card decisions, read threats, feel the board respond through sound and light, and recognize that their deck has changed the puzzle world. Keep the whole loop coherent: deck → board → Ink → cards → board → threat → choice → consequence → memory.

The final engineering handoff must include the source commit, reproducible build steps, test and GateRunner evidence, complete changed-path inventory, known gaps, content validation report, Android device matrix, performance evidence, replay compatibility status, release policy checks and a Repetition record. A playable build without this evidence is a prototype, not a completed release increment.
