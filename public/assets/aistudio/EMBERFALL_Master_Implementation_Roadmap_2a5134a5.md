# EMBERFALL --- Master Implementation Roadmap and Agent Mandate

**Baseline reviewed:** `emberfall_audit_full.zip`\
**Repository snapshot stated by audit pack:** branch `heartflame`, HEAD
`daa9803`\
**Primary code:** `src/com/slay/game/EMBERFALL.java`\
**Android shell:** `src/com/slay/game/MainActivity.java`,
`src/com/slay/game/BoardView.java`\
**Canonical visual baseline:**
`docs/AAA/emberfall/ART_BASELINE_LOCK.md`, graphics handoff v0.4\
**Purpose:** Deliver a coherent, testable, production-quality Android
roguelike without allowing historical prototypes or static mockups to
become the shipped game.

------------------------------------------------------------------------

## 0. Mission and governing principles

You are the implementation agent for EMBERFALL. Act as a senior
Java/Android engineer, roguelike systems designer, QA engineer and
technical producer. You are responsible for inspecting the real
repository, implementing the roadmap in small reviewable increments,
testing each change, maintaining documentation and producing evidence.

### Product identity --- immutable unless the owner approves a new ADR

-   EMBERFALL is a portrait Android, 7×7 orthogonal connected-group
    roguelike.
-   The board is painted by a weighted deck; it is not a swap-match-3
    board and not a falling-block board.
-   Five playable hues: Ember, Tide, Moss, Gold, Violet.
-   Neutral mechanics: Ash, Stone, Prism.
-   Three playable classes, in this order: Ashen Monk (Ember/Moss),
    Quarry Witch (Tide/Violet), The Unlit (Gold/Ash).
-   Shade is an encounter derived from the previous dead deck, not a
    playable class.
-   Act I boss: The Hollow Warden.
-   Full approximately 30-minute, three-act run is the v1.0 target
    (ADR-023).
-   No meta-currency. Progression is through
    run/codex/best-floor/Bloodstain systems as specified.
-   Portrait-first UI; tablets may center the portrait composition.
-   Lightweight Java 8-compatible pure domain core; no Android imports
    in domain code.
-   Keep dependencies minimal. Do not introduce Gradle, Compose, a game
    engine or a large framework without an explicit architecture
    decision. The existing handoff includes Compose sample guidance, not
    a mandate to adopt Compose.

### Engineering principles

1.  **One source of truth:** the domain engine owns all rules and state.
    UI renders projections and submits commands.
2.  **No feature-by-documentation:** a feature is complete only when
    executable, tested, integrated and documented.
3.  **Determinism:** seed plus ordered player commands must reproduce
    the same state sequence.
4.  **Transactional actions:** rejected commands do not partially mutate
    state or consume resources unless the rules explicitly say so.
5.  **Bounded effects:** effect processing is deterministic and has a
    hard event budget (ADR-015).
6.  **Data-driven content:** cards, relics, classes, enemies, intents,
    encounters and events are definitions, not scattered special-case UI
    logic.
7.  **Accessibility is mechanical correctness:** hues must retain
    distinct glyphs in grayscale; no essential information is
    color-only.
8.  **No silent design changes:** conflicting docs/code/ADRs are logged
    and resolved through an ADR before implementation.
9.  **Evidence-based completion:** every milestone has test commands,
    outputs, screenshots or traces as appropriate, commit SHA and known
    limitations.
10. **No fabricated claims:** report only tests actually run on the
    stated environment.

------------------------------------------------------------------------

## 1. Baseline facts and source-of-truth hierarchy

### 1.1 Repository snapshot

The supplied ZIP is a source/audit snapshot, not a complete checkout. It
contains Java sources, docs, a last-commit patch and an audit report. It
does not contain a complete Android project tree (`AndroidManifest.xml`,
`res/`, SDK/toolchain, complete runtime assets). The graphics
integration report describes a 337-file / 31 MiB tree, but that tree is
not physically materialized in the supplied ZIP; only the manifest,
validation report, handoff, previews/browser references and Git patch
are present. Do not claim the ZIP alone can build the APK or
independently prove all asset bytes.

The audit report says the graphics-only commit deliberately did not run
Android build/device acceptance. Treat that as an explicit open gate.

### 1.2 Canonical hierarchy

When documents conflict, use this order:

1.  Owner-approved product decisions and signed ADRs.
2.  `docs/AAA/emberfall/ART_BASELINE_LOCK.md` for visual identity and
    class lineup.
3.  `docs/AAA/emberfall/SCOPE.md` and governing design-bible / CH01
    material where available.
4.  Accepted `docs/adr/ADR-*.md` decisions.
5.  Current executable engine behavior, as evidence of implementation
    only --- not as authority over accepted design.
6.  Historical docs (`docs/DESIGN.md`, `docs/ART.md`,
    `docs/RESEARCH.md`, `docs/BESTIARY.md`, old SLAY/UNMAKE material)
    only where explicitly still applicable.

The governing source documents named in SCOPE (CH01 and Design Bible
v0.2) are not included in this ZIP. Do not invent their missing details.
Record a missing-source dependency and request/import the authoritative
files before implementing details that depend on them.

### 1.3 Current code reality to verify at start

-   `EMBERFALL.java` is a compact headless prototype with `Board`,
    `Deck`, `Engine`, tap/pop, gravity, Wall, overflow, Vigil, flasks,
    Ink/Spark, chip, simple two-slot enemy telegraph and hidden pulse.
-   `Game.java` and `BoardView.java` are the legacy SLAY implementation.
    `MainActivity` constructs `Game` and `BoardView`, so the Android
    shell is not connected to the canonical Emberfall engine.
-   `UNMAKE.java` is a separate 10×20 falling-block experiment and must
    remain isolated/archived.
-   The current `Deck.rebuild()` is empty.
-   `Board.seed()` creates a hard-coded striped layout; the initial
    board is not yet generated by the deck-weighted deterministic paint
    algorithm described by ADR-002.
-   `Deck.drawHue()` calculates weights but does not consume/draw a
    card. `Deck.draw()` and hue painting are not yet a complete shared
    deck lifecycle.
-   `enemyBlock` exists as a field, but chip application in `endTurn()`
    subtracts directly from enemy Vigor and does not consume Block.
-   Prism grouping currently depends on selection origin and does not
    implement the ADR-004 deterministic multi-hue contact/tie-break
    rule.
-   Spectrum code must be checked against ADR-005: the ADR specifies
    multipliers 1.0, 1.5, 2.0; current integer arithmetic `1 + run/2`
    does not represent the half-step.
-   The engine does not yet implement the complete Draw → Weave (3 Taps)
    → Cast → Wall → Enemy phase order in ADR-001. There is no complete
    card-cast system.
-   `gameOver()` must be checked against the scope rule that zero flasks
    is run failure; the current condition is redundant with Vigil
    reaching zero.
-   `BoardView` renders the legacy 6×8 `Game` model and SLAY vocabulary.
-   The supplied build script references Android SDK files and resources
    absent from the ZIP and contains a fallback that can mask resource
    compile errors. Fix this as part of build reproducibility, not by
    pretending the archive is a complete app.

Re-run all observations against the actual working repository before
changing code; the repository may have advanced beyond this snapshot.

------------------------------------------------------------------------

## 2. Programme governance and delivery protocol

Use a single feature branch from the actual current `heartflame` head
(or owner-designated branch). Never force-push. Do not overwrite
uncommitted user work. First record `git status`, current HEAD, remotes
and branch. If the worktree is dirty, preserve it and ask/establish a
safe worktree or branch strategy.

Every increment follows:

1.  **Ground:** identify exact source requirements, current code,
    affected files, risks and acceptance tests.
2.  **Plan:** define the smallest coherent change and API/data
    contracts.
3.  **Implement:** make one focused change; avoid opportunistic
    refactors.
4.  **Verify:** compile, run focused tests, full tests, static checks
    and relevant app/device checks.
5.  **Review:** inspect diff for scope, invariants, regressions,
    accidental asset churn and docs drift.
6.  **Deliver:** commit with a meaningful message; report SHA, files,
    tests, outputs and known gaps.
7.  **Learn:** update ADR/docs only where decisions changed; update
    roadmap status.

A milestone cannot be marked complete while a required test is skipped,
failing or represented only by a mock. If the environment lacks an
Android device or SDK, label that acceptance item **NOT RUN / BLOCKED**,
not PASS.

### Required artifacts maintained throughout

-   `docs/AAA/emberfall/IMPLEMENTATION_ROADMAP.md` --- this roadmap with
    status/commit updates.
-   `docs/AAA/emberfall/ENGINE_CONTRACT.md` --- state, commands, phases,
    events and invariants.
-   `docs/AAA/emberfall/CONTENT_CATALOG.md` ---
    class/card/relic/enemy/encounter IDs and balance data.
-   `docs/AAA/emberfall/TEST_STRATEGY.md` --- test layers, deterministic
    seeds and golden traces.
-   `docs/AAA/emberfall/DECISION_LOG.md` or individual ADRs ---
    conflicts and decisions.
-   `docs/AAA/emberfall/RELEASE_ACCEPTANCE.md` --- release gates and
    evidence.
-   `docs/AAA/emberfall/reports/` --- actual test/simulation/device
    reports.

Keep historical docs, but mark obsolete SLAY/UNMAKE instructions clearly
as historical/experimental. Do not delete historical design knowledge
without owner approval.

------------------------------------------------------------------------

## 3. Phase 0 --- Repository, specification and asset consolidation (P0)

### Objectives

Establish a clean, reproducible baseline and remove ambiguity about what
is being shipped.

### Tasks

**0.1 Repository reconnaissance** - Inspect actual repo, branch,
remotes, HEAD, dirty files, ignored files, submodules and build
scripts. - Compare actual working tree against the supplied `daa9803`
snapshot. - Identify the real location of all Android resources and
graphics assets. - Confirm whether `desktop/EmberfallView.java` is
current, experimental or a required reference. - Inventory dependencies
and ensure no generated archives/JRE packages are accidentally
committed.

**0.2 Canonical spec audit** - Read every ADR-001 through ADR-023. -
Import or request the missing CH01 and Design Bible v0.2 sources
referenced by SCOPE. - Produce a contradiction register: spec vs ADR vs
code vs UI handoff. - Resolve each contradiction through an owner
decision/ADR before coding.

**0.3 Explicit contradictions to resolve** - Ink: older docs refer to
cap 10; current engine uses cap 8; ADR-009 describes Spark overflow and
ADR references must be reconciled with current spec. - Vigil: SCOPE says
start 16; current engine uses 70; latest commit history says an earlier
increment changed it to 70. Confirm canonical value from the governing
source. - Enemy Vigor: current code initializes 50; ADR-013 defines
node-scaled Vigor formula. Define encounter scaling. - Enemy cadence:
docs mention 6--7 turns; current code uses `enemyTicks=6` with a
specific decrement order. Lock exact telegraph timing. - Board initial
topology: ADR-002 requires 7×7 plus one hidden row and weighted
deck-painted draw; current code uses a fixed striped board and two empty
rows. - Deck weighting: ADR-003's formula must operate over a
well-defined card/deck population and handle reshuffle consistently. -
Spectrum Run: ADR-005 says 1 + 0.5×Run; current code uses integer
division. - Phase order: ADR-001 is Draw → Weave (3 Taps) → Cast → Wall
→ enemy. SCOPE prose also describes interleaving card play with taps.
ADR-001 explicitly rejects interleaving for the current state machine.
Resolve UI copy and implementation to accepted ADR. - Flask/zero-flask
failure semantics: align `gameOver()` and run state with SCOPE. - Hidden
pulse: verify current accepted behavior, whether the damage
amplification is intentionally invisible, and its balance/readability. -
Class names and pairings: baseline lock is Ashen Monk, Quarry Witch, The
Unlit; SCOPE's older Monk/Witch/Unlit terms must be reconciled without
renaming canonical identities. - Graphics palette: use v0.4
`ART_BASELINE_LOCK.md` / manifest, not older palette in historical
SCOPE. - Card/deck terminology: distinguish board-paint deck from player
combat deck if they are separate systems. Never overload one `Deck` type
for two unrelated roles.

**0.4 Asset provenance and delivery** - Materialize the full graphics
source tree from the original source archive/repository. - Verify every
manifest path, byte size, SHA-256, dimensions, decode and
license/provenance status. - Keep candidate concept assets separate from
approved runtime assets. - Do not ship the 146 concept/provenance-review
assets as production-approved art. - Verify SVG parsing and Android
conversion; install Pillow or another approved decoder only if needed,
and record tool versions. - Keep original SVG masters; generate runtime
WebP/VectorDrawable outputs deterministically. - Create a runtime asset
manifest with asset ID, path, dimensions, source hash,
license/provenance, maturity and usage. - Never use the master artboard
or screen mockups as a full-screen clickable bitmap.

**0.5 Build baseline** - Establish one reproducible Android build
path. - Fix `build.sh`: remove `|| true` around `aapt2 compile`; fail
fast; validate manifest/resource paths; report SDK/build-tools versions;
make outputs deterministic where possible. - Decide whether the project
uses the existing direct build-tools pipeline or Gradle. Do not mix
undocumented build paths. - Add CI or a reproducible local script for
pure Java compile/tests and Android packaging. - Set `minSdk 24`; target
SDK policy per ADR-020, rechecking current Play requirements at
submission. - Add APK signing separation: debug key for development,
protected release key for production. Never ship the throwaway debug
key. - Produce a clean baseline report.

### Acceptance gate G0

-   One clean branch and known HEAD.
-   No untracked/dirty files silently discarded.
-   All governing specs available or explicitly listed as blockers.
-   Contradiction register approved.
-   All ADRs indexed and statuses correct.
-   Full graphics tree physically present and manifest checks pass, or
    asset delivery remains blocked.
-   Pure Java compile and current self-check pass.
-   Android build either succeeds reproducibly or has an exact blocker
    report.
-   No feature implementation begins against unresolved core-rule
    contradictions.

------------------------------------------------------------------------

## 4. Phase 1 --- Pure domain architecture and state machine (P0)

### Objectives

Refactor the one-file prototype into a small, testable domain model
without changing approved mechanics accidentally.

### Target package architecture

Use simple Java packages and immutable definitions where practical.
Exact names may adapt to repository conventions.

``` text
domain/
  model/
    RunState
    TurnState
    BoardState
    Cell
    PlayerState
    EnemyState
    EncounterState
    DeckState
    RelicState
    MapState
  rules/
    TurnController
    BoardRules
    GroupResolver
    GravityResolver
    WallResolver
    SpectrumResolver
    DamageResolver
    ResourceResolver
  effects/
    Effect
    EffectEvent
    EffectQueue
    EffectResolver
  content/
    ClassDefinition
    CardDefinition
    RelicDefinition
    EnemyDefinition
    IntentDefinition
    EncounterDefinition
  rng/
    RunRandom
    RandomStreams
  command/
    GameCommand
    TapCell
    EndWeave
    CastCard
    ChooseNode
    SelectReward
  result/
    CommandResult
    GameEvent
    GameSnapshot
```

Do not create abstractions for hypothetical future features. Extract
only where it improves correctness, testability or UI integration.

### State ownership

-   `RunState` owns seed, act, map position, player, deck, relics,
    current encounter, run history and run status.
-   `EncounterState` owns board, enemy instances, telegraphs,
    per-encounter flags and current turn.
-   `TurnState` owns phase, taps used/remaining, per-turn Spectrum Run,
    accumulated chip and effect context.
-   `BoardState` owns a 7×7 visible board plus the explicitly specified
    incoming/hidden row representation.
-   Definitions (cards/classes/relics/enemies) are immutable data;
    mutable state is separate.
-   UI receives a read-only `GameSnapshot`/presentation projection,
    never mutable internal collections.

### Turn state machine

Implement ADR-001 exactly unless a new ADR supersedes it:

1.  Draw / turn initialization.
2.  Weave: exactly up to three Taps.
3.  Cast: card-play phase after Weave; legal costs and targets checked.
4.  Wall rises.
5.  Enemy intent resolves.
6.  Turn cleanup and next turn.

Define legal commands per phase. Invalid commands must not consume taps,
Ink, Spark, cards or mutate board. Add explicit `CommandResult` values
for accepted/rejected commands with stable reason codes.

### Deterministic RNG

-   One run seed is the reproducibility root.
-   Use explicit RNG streams (board paint, shuffle, map generation,
    events, enemy decisions) derived deterministically from the run seed
    to prevent unrelated features from perturbing all later random
    outcomes.
-   Persist RNG state/stream counters or enough deterministic state to
    resume exactly.
-   No `Math.random()`, unseeded `new Random()` or platform RNG in
    domain rules.
-   Record engine/content version in saves and golden traces.
-   A run seed plus ordered command log must reproduce the same state
    hashes.

### Effect queue

Implement deterministic queue with priority ordering and maximum 100
events per player action, per ADR-015. - Define event priority and
stable tie-break. - Echo repeats exactly one primary card effect once. -
Echo of Echo is ignored. - Detect and fail safely on budget exhaustion;
do not hang or silently drop effects. - Effects must not mutate UI state
directly.

### Core invariants

At minimum: - Board dimensions fixed; each cell has exactly one valid
typed state. - No invalid hue/neutral combinations. - Taps remaining
within 0..3. - Resources never negative and never exceed declared
caps. - Flask current \<= maximum. - Enemy Vigor and Block
nonnegative. - Card zones are disjoint and every card belongs to exactly
one zone. - No card can be played twice without being returned by a
defined effect. - Current phase accepts only legal commands. -
Wall/overflow cannot corrupt board dimensions. - Sealed cells/columns
obey ADR-012. - Effect queue bounded. - Run status terminal states
cannot accept normal combat commands. - Serialization round-trip
preserves state and deterministic continuation.

### Acceptance gate G1

-   Domain compiles with plain `javac` and no Android classpath.
-   Commands and phases enforced.
-   State is not publicly mutable by UI.
-   Seed/input replay stable.
-   Invariants/property tests pass.
-   Golden behavior for existing accepted ADRs is captured before new
    content is added.
-   No accidental mechanical drift vs baseline, except explicitly
    approved fixes.

------------------------------------------------------------------------

## 5. Phase 2 --- Board, resource and combat engine (P0)

Implement in this order. Each increment must have unit tests, property
tests and a golden trace.

### 2.1 Board generation and deck painting

-   Implement 7×7 visible board plus one incoming/hidden row exactly as
    ADR-002 specifies.
-   Weighted paint formula per ADR-003.
-   Define whether a draw consumes a card, samples the current deck
    distribution, or reshuffles a discard pile. The current `drawHue()`
    does not consume a card; do not assume it is correct.
-   Separate board-paint deck from combat card deck if they have
    distinct semantics.
-   Deterministic shuffle and exhaustion/rebuild.
-   Ensure initial and incoming rows use the same authoritative painter.
-   Collect topology metrics: group-size histogram, isolated cells, hue
    distribution, neutral distribution, playable first move
    availability.

### 2.2 Group resolution

-   Orthogonal connected groups only.
-   Minimum pop size 2 for playable hues and Ash per accepted spec.
-   Out-of-bounds and empty taps rejected without mutation.
-   Clarify whether a tap on a non-poppable cell consumes a Tap; lock in
    tests.
-   Prism must implement ADR-004: joins the group whose frame hue
    contacts it; multi-hue ties resolve deterministically using wheel
    order starting from current Run hue; Prism counts as the next wheel
    hue for Spectrum. A Prism-selected tap must behave consistently with
    a hue-selected tap.
-   Prevent one Prism from incorrectly bridging unrelated hue groups
    unless the ADR explicitly allows it.
-   Return a preview (group size, hue, Ink, chip, Brand cost, Stone
    breaks, Spectrum result) without mutating RNG/state.

### 2.3 Gravity and Stone

-   Stable per-column gravity.
-   Stone cannot be popped directly.
-   A qualifying adjacent group \>=4 breaks orthogonally adjacent Stone
    in the same resolution step.
-   Iterative Stone chain break is deterministic and bounded per
    ADR-007.
-   Order: pop → Stone break cascade → gravity → new spawns.
-   Test edge/corner, multiple Stone chains, disconnected Stone, and
    event-budget safety.

### 2.4 Ash and chip

-   Ash pops in groups, yields no Ink and applies n−1 chip damage per
    ADR-006.
-   Chip is applied after enemy Block.
-   Confirm whether chip accumulates across multiple Taps and is applied
    at enemy action or at another exact phase; current prototype
    accumulates until enemy acts.
-   Prevent chip from carrying into a later enemy/encounter
    unintentionally.

### 2.5 Spectrum Run

-   Wheel order Ember → Gold → Moss → Tide → Violet → Ember.
-   Run starts at zero per turn.
-   Correct next hue increments Run.
-   Out-of-order pop computes that pop at Run=0 then resets sequence
    according to ADR-005.
-   Prism uses next hue.
-   Multiplier 1.0, 1.5, 2.0... must be represented without accidental
    integer truncation. Choose fixed-point or half-unit arithmetic and
    define rounding for Ink awards.
-   Add table-driven tests for E→G→M, repeated hue, wraparound, Prism
    and out-of-order reset.

### 2.6 Ink and Spark

-   Resolve canonical Ink cap from accepted source/ADR.
-   Implement per-hue Ink pools if required by the design; current
    single pool is explicitly noted as simplified and must not silently
    masquerade as per-hue.
-   Overflow creates Spark.
-   Spark pays only generic/colorless cost pips; it never pays a colored
    pip (ADR-009).
-   Payment order is player-chosen; test partial payment rejection
    transactionally.
-   No negative balances; caps and overflow behavior exact.
-   Add UI preview data for gained Ink/Spark and card affordability.

### 2.7 Wall and overflow

-   Wall inserts one row at bottom; board shifts upward according to
    ADR-008.
-   Incoming row preview shown one turn ahead.
-   If top row has no empty cell, overflow: lose 8 Vigil, burn top two
    rows, lose one Flask, skip rise.
-   Define interaction with sealed columns per ADR-012.
-   Test full board, partial top row, multiple overflow, zero flasks,
    and board recovery.
-   `gameOver()` must implement the actual run-failure contract; zero
    flasks must not be accidentally ignored.

### 2.8 Enemy damage, Block and intents

-   Enemy Block absorbs damage for the current turn and resets at start
    of enemy turn per ADR-013.
-   Apply chip after Block.
-   Current `enemyBlock` field is not sufficient: implement, test and
    expose actual Block semantics.
-   Enemy Vigor scaling per ADR-013: `round(36 × 1.11^node)` fights,
    ×1.6 elites, ×3 bosses, with specified rounding.
-   Enemy attack damage ranges per act per ADR-013.
-   Two telegraph slots alternate and are fully represented in state;
    actual intent identity, preview, countdown and resolution must
    match.
-   Implement BRAND, ASH_ROW and other source-specified intents; do not
    treat two placeholder damage numbers as a complete enemy AI.
-   Brand: each marked block popped costs 2 Vigil per block (ADR-010),
    preview exact total cost before commit.
-   Fog: hides hue colors and Ink pools for three Taps but preserves
    glyph shapes; Gold Eye restores colors (ADR-011).
-   Seal: column cannot be tapped/popped, has no gravity, and board
    effects targeting it are ignored for three turns; wall may rise into
    non-sealed columns (ADR-012).
-   Pulse/hidden tension: isolate as a configurable encounter mechanic,
    test its cadence/amplification, document whether it remains hidden,
    and run balance/readability playtests. Do not remove or expose it
    without a design decision.

### Acceptance gate G2 --- first complete encounter

-   One encounter can be started, played and ended using domain
    commands.
-   At least one enemy with two real intent types.
-   Board, Ink/Spark, chip, Block, Wall, overflow, Vigil, flasks and
    terminal result work together.
-   All edge cases above have tests.
-   Golden replay deterministic.
-   No Android UI dependency in engine.
-   Human-readable combat trace generated from a seed.

------------------------------------------------------------------------

## 6. Phase 3 --- Classes, cards, relics and content system (P0)

### 3.1 Content architecture

-   Stable IDs for every class/card/relic/enemy/intent.
-   Immutable definitions with effect descriptions, costs, targeting
    rules, rarity, tags, unlock rules and art asset IDs.
-   Runtime state separate: upgrades, exhaust/discard/draw zones, relic
    counters, charges, temporary modifiers.
-   Text is generated from content data and must match actual effects.
-   Content catalog validation: unique IDs, valid references, valid
    cost/resource types, valid targets, asset links and localization
    keys.

### 3.2 Classes --- exactly three canonical identities

Implement in locked order: 1. Ashen Monk --- Ember/Moss; burns Vigil for
power; big single hits; Prayer Beads signature relic (first Tap each
turn pays +2 Ink, subject to authoritative source confirmation). 2.
Quarry Witch --- Tide/Violet; board repainting and Spectrum strategy;
Chisel Wand signature relic (once per fight, recolor one block free). 3.
The Unlit --- Gold/Ash; enemy Ash as fuel; thin/fast deck; Hollow Crown
signature relic (each Ash block popped pays 1 Spark).

Each class must have: - Starting deck exactly matching approved
source. - Starting relic. - Unique but balanced mechanic. - Class-select
content and portrait. - Tests for identity, starting loadout, signature
interaction and save/load. - Distinct viable strategies, not merely
different labels or portraits.

The Unlit's exact 12-card starter described in ADR-014 must be
implemented and golden-tested once the source card definitions are
available.

### 3.3 Card system

-   Draw pile, hand, discard pile, exhaust pile and any special zones.
-   Turn draw count and hand size from source spec.
-   Colored Ink and generic Spark cost handling.
-   Target selection and legal-target validation.
-   Effect resolution via bounded event queue.
-   Card play only in Cast phase per ADR-001 unless a new ADR approves
    interleaving.
-   Cards must support upgrades, retain, exhaust, ethereal, echo and
    other source-defined keywords.
-   Implement enough content for a complete vertical slice first; do not
    create dozens of cards before the effect model is validated.
-   Minimum vertical slice content: 12--18 distinct cards across all
    three classes, with attacks, defense, board manipulation, resource
    generation and class identity.
-   Card detail UI must derive name, cost, type and rules text from
    definitions.
-   Deck exhaustion/rebuild is mandatory; no empty-list removal crash or
    silent default-deck reset that discards run composition.

### 3.4 Relics

-   Passive modifiers and event-triggered relics use explicit
    hooks/events.
-   Avoid relic logic in renderer.
-   Define trigger timing, once-per-turn/fight/run counters and stacking
    order.
-   Implement signature relics first, then a small coherent starter
    pool.
-   Test each relic trigger, timing, save/load and interaction with
    effects/echo.
-   Relic gallery uses actual owned relic state and catalog definitions.

### 3.5 Enemies and bosses

-   Implement core source-named enemies: Cinder Husk, Ash Caller, Stone
    Herald, Memory Leecher, and others only when supported by source.
-   Each enemy must have distinct telegraph patterns and counterplay.
-   Act I boss The Hollow Warden with phases and board mechanic
    callouts.
-   Bosses are not classes; never introduce disallowed class names from
    the art anti-drift list.
-   Add enemy and boss simulation tests and readable intent previews.

### Acceptance gate G3 --- build identity

-   Three classes selectable and mechanically distinct.
-   Each has a complete starting deck and signature relic.
-   Card draw/play/cost/effect/discard/exhaust loop works.
-   Enemy Block, card effects and relic hooks interact
    deterministically.
-   At least one boss can be defeated.
-   No placeholder UI descriptions; content text matches behavior.
-   Save/load preserves deck zones, relic counters and combat state.

------------------------------------------------------------------------

## 7. Phase 4 --- Full roguelike run and meta progression (P1)

### 4.1 Run map

-   Each act is a 15-row branching floor-plan graph per ADR-018.
-   Validate every possible traversable route meets expected node-mix
    bounds: approximately 6 fights, 1--2 elites, 2 bonfires, 1 shop, 2
    events, 1 treasure, 1 boss.
-   Build deterministic map from run RNG.
-   Only reachable nodes selectable; node choice is a domain command.
-   Map UI is a projection of `RunMapState`, never a hand-drawn
    independent graph.

### 4.2 Nodes

Implement: - Normal combat. - Elite combat. - Treasure/reward. - Shop. -
Bonfire. - Events with 2--3 meaningful choices. - Codex/map nodes if
specified. - Boss.

Each node must have entry, resolution, reward and save semantics. All
paths must terminate at the act boss.

### 4.3 Rewards and economy

-   Card rewards, relic rewards, currency/cinders and recovery as source
    specifies.
-   Prices and rewards use data definitions and balance tables.
-   No meta-currency if prohibited by product decision.
-   Shop must have meaningful choices and prevent invalid purchases.
-   Bonfire choices depend on current deck/resources and are persisted.

### 4.4 Three-act run

-   Act I Ash/Cinder; Act II Drowned Teal; Act III White-hot Ember
    palettes per visual/design baseline.
-   Full run approximately 30 minutes.
-   Act transitions preserve only specified state.
-   Act III ending and true ending route must match ADR-017.
-   Fast true-ending route: Unlit covenant/Dread prerequisite; three
    consecutive Ash-only/no-card turns; any violating action or relevant
    end-turn penalty resets streak; exact timing per ADR-017.
-   Ascension and Covenants only after core full-run quality is proven;
    no premature scope creep.

### 4.5 Shade on death

-   Shade appears on Act I row 5 per ADR-016.
-   Use previous saved deck as a strategy-profile Shade; cards become
    intents.
-   Scale Vigor based on previous deck performance, bounded to
    expected-fair fight.
-   Relics do not pass through.
-   Victory recovers carried Cinders plus one Remnant card from dead
    deck; reward cap and Gravedigger's Spade multiplier follow ADR-016.
-   Persist and validate dead-run profile safely.
-   Shade is an encounter, not a class.

### 4.6 Save/resume

-   Save every node (SCOPE).
-   Also save at safe combat checkpoints so Android process death does
    not destroy meaningful progress.
-   Versioned schema, migration strategy and corruption recovery.
-   Save seed, RNG state, phase, board, player, deck zones, relics,
    enemy intent state, map, node, act, history and content version.
-   Atomic write (temporary file then replace); checksum or integrity
    validation.
-   No credentials/secrets in save.
-   Test round-trip, migration, truncated file, invalid enum, unknown
    content ID and resume determinism.

### Acceptance gate G4 --- complete run

-   New run → class selection → map → all node types → three acts →
    boss/ending → results.
-   At least one complete golden run per class.
-   Every node saves/resumes.
-   Shade flow and true-ending route tested.
-   Route generator validator passes large deterministic sweep.
-   Run length and difficulty metrics reported.
-   No softlocks or unreachable terminal states.

------------------------------------------------------------------------

## 8. Phase 5 --- Android application and UI integration (P1)

### 5.1 Architecture

The current Android `MainActivity` creates legacy `Game` and
`BoardView`. Replace this connection incrementally.

Target flow:

``` text
MainActivity / Navigation
        |
        v
Screen Controller / ViewModel-like presenter
        |
        +--> GameEngine.handle(GameCommand)
        |
        +--> GameSnapshot / ScreenState
        |
        v
Native Android Views / Canvas renderer
        |
        +--> board cells
        +--> enemy and two intents
        +--> Vigil / Block / Flasks / Ink / Spark
        +--> card hand / end turn
        +--> map / rewards / event screens
```

-   Domain module remains plain Java and testable without Android.
-   UI must not call internal mutators or change resources directly.
-   UI commands return accepted/rejected results and updated snapshots.
-   Animations consume emitted events; they do not own game rules.
-   No hardcoded fixture values from SVG previews.
-   Do not rewrite all UI at once. First bind a minimal combat screen to
    the canonical engine.

### 5.2 Combat vertical slice

Implement: - 7×7 board from actual `BoardState`. - Incoming Wall row
preview. - Correct glyph and color for each hue/neutral. - Enemy
portrait/name/Vigor/Block. - Exactly two intent slots where available,
with real countdown/damage/status. - Vigil, flasks, Ink/Spark, taps
left, card hand and End Turn. - Tap preview (group size, Ink, chip,
Spectrum, Brand cost, Stone breaks). - Card targeting/detail and legal
state feedback. - Undo only if source rules permit and only before card
play; do not fake undo by mutating the displayed board. - Minimum 48dp
hit targets; invisible hit areas may exceed visual cells.

### 5.3 Ten screens and navigation

Implement all ten approved screens: 1. Title / Main Menu. 2. Class
Select. 3. Run Map. 4. Combat. 5. Card Detail. 6. Relic Gallery. 7.
Event. 8. Bonfire. 9. Shade. 10. Boss.

Screen transitions must be driven by run/encounter state. Back
navigation must not bypass or corrupt combat phase.

### 5.4 Visual integration

-   Read `ART_BASELINE_LOCK.md` and both Android handoff documents
    before touching assets.
-   390×844 dp is the design reference; test 360×800, 412×915 and
    tablets centered in portrait composition.
-   Preserve approved class order and identity.
-   Scene, frame, portrait, board, tiles, glyphs, card art, text, cost
    pips and dynamic values remain separate layers.
-   Static environment plates may be WebP; scalable shape assets may use
    VectorDrawable; preserve SVG masters.
-   Runtime values and text come from content/state models.
-   Concept art requires provenance/art-direction approval before
    release.
-   Maintain manifest/hash validation.
-   Never render entire artboard as a background screenshot or use
    screen composition SVG as the gameplay UI itself.

### 5.5 Accessibility and usability

-   Hue + unique glyph, never color only.
-   Grayscale test must preserve identity.
-   TalkBack labels for cells, cards, intents, resources and controls.
-   Scalable text and large font support.
-   Minimum 48dp targets.
-   Reduced motion and flashing reduction.
-   Haptics/audio toggles and off defaults as appropriate.
-   Contrast checks against palette.
-   Touch remapping/input settings per CH01 if source confirms.
-   No time pressure; turn-based input.
-   Onboarding ramp: fight 1 board-only, fight 2 Ink + one card type,
    one keyword per later fight (ADR-022).

### Acceptance gate G5 --- Android vertical slice

-   APK builds reproducibly.
-   Canonical engine is the only source of gameplay truth.
-   Combat screen plays a complete encounter on device.
-   No legacy SLAY mechanics visible in product flows.
-   All ten routes navigate without dead ends (screen contents may be
    staged only if explicitly labeled; release gate requires actual
    functionality).
-   UI values match engine state.
-   Accessibility smoke tests pass.
-   Screenshots at required dimensions are captured and reviewed.

------------------------------------------------------------------------

## 9. Phase 6 --- Art production, content quality and UX polish (P1)

-   Complete character consistency pass for all three canonical
    portraits.
-   Approve environment plates, enemy/boss art, cards, relics and VFX.
-   Record provenance/license and source hashes.
-   Establish sprite/animation budgets and memory lifecycle.
-   Optimize loading/unloading by act; do not hold all environment
    plates in memory.
-   Visual regression compare against 10 SVG screen references and
    approved artboard.
-   Verify UI remains readable at all device sizes.
-   Ensure no text is rasterized into illustrations.
-   Add onboarding/tutorial overlays from real rules.
-   Add sound/haptics only where they reinforce meaningful feedback;
    settings must disable them.
-   Conduct external tester sessions and record observations, not only
    opinions.

### Acceptance gate G6

-   All runtime assets approved for use.
-   Manifest and hashes pass.
-   Visual comparison reviewed for all ten screens.
-   Device screenshots and accessibility checks attached.
-   No unapproved concept art or placeholder art in release build.

------------------------------------------------------------------------

## 10. Phase 7 --- Balance, simulation and playtesting (P1)

### 10.1 Automated simulation

Build a headless bot harness with multiple policy archetypes: - Greedy
largest-group pop. - Spectrum-seeking. - Survival/Wall-clearing. -
Ash/chip-focused. - Card-economy focused. - Random legal baseline. -
Class-specific policies.

Run at least 10,000 seeded encounters/runs once systems are stable. Use
stratified seeds and record engine/content version.

Measure: - Win/completion rate by class and act. - Turns per
encounter. - Run duration. - Vigil and flask remaining. - Wall overflow
frequency. - Average group size. - Group-size distribution and
no-legal-pop rate. - Ink/Spark generated, spent and wasted. - Card
draw/play rates and dead-hand frequency. - Damage dealt/taken and Block
efficiency. - Intent damage and telegraph counterplay. - Relic/card pick
rates. - Map route node distribution. - Boss failure rate. - Shade
win/reward rates. - True-ending route completion rate.

Do not use a single aggregate win rate to declare balance. Segment by
class, seed, strategy, act and player skill proxy.

### 10.2 Human playtests

Proposed cohorts: - 5 testers: comprehension, controls, first
encounter. - 15 testers: class identity, card clarity, Wall pressure and
difficulty. - 30 testers: run pacing, replayability and content balance.

These are future test plans, not completed results.

Record: - Where players hesitate. - Rules misunderstood. - Invalid taps
and accidental actions. - Card affordability confusion. - Whether enemy
intents are readable. - Whether Wall pressure feels fair. - Whether each
class has a recognizable strategy. - Whether players want another run
and why.

### 10.3 Balance gates

-   No class has an extreme win-rate disparity without an intentional
    explanation.
-   No dominant single action/policy trivializes the Wall or enemy.
-   No card/relic is objectively dead or mandatory without a deliberate
    design reason.
-   All enemy attacks are previewable and counterplay is understandable.
-   Five-hue comprehension is validated before changing hue count
    (ADR-022).

### Acceptance gate G7

-   Simulation harness deterministic and reports raw data.
-   Balance report includes confidence/seed counts and limitations.
-   Human test evidence reviewed.
-   Changes are tied to measured problems and versioned balance tables.

------------------------------------------------------------------------

## 11. Phase 8 --- Performance, reliability, security and release engineering (P1)

### Android/device matrix

Test at minimum: - Min supported API 24 where practical. - Current
target SDK / current Play policy. - Low-memory device. - Mid-range
Android phone. - High-density and small-screen devices. - Rotation
policy (portrait lock or supported behavior). - Process
death/background-resume. - Storage low/corrupt save. - Large font and
accessibility services.

### Performance

-   Target 60 fps on agreed mid-range hardware.
-   No allocations or image decoding in per-frame hot loops.
-   Board input remains responsive.
-   Profile startup, screen transitions, combat animations, memory and
    GC.
-   Lazy-load large environment plates and release unused act assets.
-   Avoid unbounded event queues, logs and retained Activity references.

### Reliability

-   App lifecycle save/resume.
-   Atomic saves and corruption recovery.
-   Crash reporting strategy that respects privacy.
-   No network requirement for core offline run unless explicitly
    approved.
-   Graceful behavior on missing optional art/audio assets.
-   Versioned content and save migration.
-   Deterministic bug reproduction via seed/input log.

### Release

-   Release keystore protected and never committed.
-   R8/minification only if build path supports and tests.
-   Target SDK checked against current Play policy at submission.
-   App ID, signing, versionCode/versionName and privacy/data safety
    declarations.
-   Store listing, screenshots, icon and legal title review.
-   Trademark/IP review before public reveal (ADR-021).
-   Open-source/license and art provenance review.
-   Release candidate checklist signed by owner.

### Acceptance gate G8 --- release candidate

-   Release APK/AAB built from clean checkout.
-   Build reproducible from documented environment.
-   Automated tests and device matrix pass.
-   No P0/P1 defects.
-   Save migration and resume pass.
-   Performance targets met.
-   Art/IP/legal review complete.
-   Store policy and target SDK verified at submission.
-   Owner approves release.

------------------------------------------------------------------------

## 12. Priority defect register from the supplied snapshot

Revalidate against current repository before fixing.

  -------------------------------------------------------------------------
  ID                Severity          Finding             Required
                                                          correction
  ----------------- ----------------- ------------------- -----------------
  EF-001            P0                Android starts      Bind Android to
                                      legacy `Game` /     canonical
                                      `BoardView`         Emberfall engine

  EF-002            P0                `Deck.rebuild()`    Implement
                                      empty               deterministic
                                                          deck lifecycle
                                                          and exhaustion

  EF-003            P0                Initial board is    Implement ADR-002
                                      hard-coded stripes  deck-painted
                                                          topology

  EF-004            P0                `drawHue()` does    Define and
                                      not consume deck    implement
                                      draw                board-paint deck
                                                          semantics

  EF-005            P0                Phase state         Implement ADR-001
                                      machine/card cast   command phases
                                      absent              

  EF-006            P0                `enemyBlock` not    Apply Block
                                      applied to chip     before chip
                                                          damage per
                                                          ADR-013

  EF-007            P0                Prism               Deterministic hue
                                      selection/group     contact and
                                      behavior violates   tie-break
                                      ADR-004             

  EF-008            P0                Spectrum multiplier Correct
                                      integer truncation  1.0/1.5/2.0
                                                          arithmetic and
                                                          rounding

  EF-009            P0                Run failure         Align terminal
                                      semantics may       state with
                                      ignore zero flasks  approved spec

  EF-010            P0                RNG streams not     Deterministic run
                                      unified/recorded    streams and
                                                          replay/save

  EF-011            P1                Wall/board has no   Implement preview
                                      explicit incoming   and hidden row
                                      row state           per ADR-002/008

  EF-012            P1                Enemy model is one  Data-driven
                                      placeholder         enemy/intent
                                      Vigor/alternating   definitions
                                      damage              

  EF-013            P1                No class/card/relic Implement
                                      system              data-driven
                                                          content

  EF-014            P1                No run              Implement
                                      map/three-act       ADR-018/023
                                      progression         

  EF-015            P1                No run save/resume  Versioned
                                                          deterministic
                                                          save

  EF-016            P1                Graphics tree       Recover and
                                      absent from         validate complete
                                      supplied ZIP        pack

  EF-017            P1                Build script masks  Fail-fast
                                      `aapt2 compile`     reproducible
                                      errors              build

  EF-018            P1                Historical docs     Mark historical
                                      conflict with       and publish
                                      current baseline    canonical docs

  EF-019            P1                Accessibility and   Implement and
                                      device acceptance   test handoff
                                      unverified          requirements

  EF-020            P1                146 assets require  Approve/reject
                                      provenance/art      before release
                                      direction review    
  -------------------------------------------------------------------------

------------------------------------------------------------------------

## 13. Agent work breakdown and parallelization

Use independent workstreams only after Phase 0 and API contracts are
agreed.

### Workstream A --- Domain engine

Owns phases 1--3. No Android dependencies. Produces commands, snapshots,
deterministic tests and content definitions.

### Workstream B --- Android shell and presentation

Can inventory assets, build navigation shell and create static
components in parallel. Must not implement gameplay rules. Integrates
against agreed `GameSnapshot` contracts.

### Workstream C --- Content and game design

Owns class/card/relic/enemy definitions, source verification, content
catalog and balance hypotheses. Cannot invent unsupported lore or rules
where governing source is missing.

### Workstream D --- QA/simulation

Builds test harness, property tests, golden traces and balance bot.
Should review engine changes independently and avoid sharing
implementation assumptions with the coding agent.

### Workstream E --- Art integration

Recovers graphics tree, validates provenance and builds runtime
resources. Does not embed dynamic gameplay text/numbers into images.

### Integration protocol

-   Workstreams use separate branches or isolated worktrees.
-   Integrate only against agreed interfaces.
-   One integrator resolves conflicts.
-   No concurrent edits to the same core engine files without explicit
    coordination.
-   Every PR includes tests and a concise design-impact note.
-   Reviewer must inspect source and run tests; never approve based only
    on agent summary.

------------------------------------------------------------------------

## 14. Definition of done for every feature

A feature is DONE only when all applicable statements are true:

-   The rule is traceable to an approved spec/ADR.
-   Domain implementation exists and owns behavior.
-   Invalid inputs are handled transactionally.
-   State invariants are preserved.
-   Deterministic tests cover happy path, boundaries and interactions.
-   Save/load includes the feature if it affects persistent state.
-   UI is a projection of real state and sends domain commands.
-   Accessibility requirements are met.
-   Content text and art IDs are correct.
-   Build/tests are run and results recorded.
-   Documentation and roadmap status updated.
-   Commit SHA and diff are provided.
-   Known limitations are explicit.

A class with methods, a mock screen, a screenshot, a design document, or
a passing compile is not by itself a completed feature.

------------------------------------------------------------------------

## 15. Required agent deliverables

At the end of each phase, return:

1.  **Phase report:** scope, files changed, behavior added.
2.  **Decision log:** ADRs followed, conflicts found, decisions
    requested.
3.  **Test evidence:** exact commands, environment, pass/fail/skip
    counts and raw logs.
4.  **Replay evidence:** seeds and state hashes for changed mechanics.
5.  **Visual evidence:** screenshots/device details for UI work.
6.  **Diff summary:** insertions/deletions and notable files.
7.  **Commit:** SHA and branch.
8.  **Risk register:** unresolved blockers and severity.
9.  **Next increment:** smallest next coherent task.

Never report "all tests pass" without listing the exact suite and count.
Distinguish PASS, FAIL, NOT RUN and BLOCKED.

------------------------------------------------------------------------

## 16. First execution order --- do this now

Do not start by implementing all cards or building all ten screens.

1.  Inspect the actual repository and compare it with snapshot
    `daa9803`.
2.  Preserve dirty work and create a safe implementation
    branch/worktree.
3.  Read all ADRs and import missing CH01/Design Bible source documents.
4.  Produce a one-page canonical contradiction register; request owner
    decisions for Ink, Vigil, board topology, Spectrum rounding, phase
    timing and deck semantics.
5.  Re-run pure Java compile/self-check and add a test runner that
    returns nonzero on failure.
6.  Add regression tests for EF-002 through EF-010 before changing
    behavior.
7.  Implement the domain state machine and deterministic RNG contract.
8.  Complete board/deck/Prism/Spectrum/Block/Wall rules.
9.  Deliver one complete combat encounter with real enemy intents.
10. Only then implement the minimum class/card/relic vertical slice and
    bind the Android combat UI.

**First milestone target:** a deterministic, headless, complete
encounter that can be played from start to victory/defeat, with a
readable trace and golden replay. This is the foundation for all
subsequent Android and roguelike work.

------------------------------------------------------------------------

## 17. Independent review scorecard

The reviewer (not the implementing agent) scores each category 0--5 and
cites evidence:

  -------------------------------------------------------------------------
  Category                                      Score Evidence required
  ---------------------- ---------------------------- ---------------------
  Canonical spec                                   /5 ADR-to-code
  compliance                                          traceability

  Domain architecture                              /5 State ownership,
                                                      command API, no
                                                      Android imports

  Board correctness                                /5 Property/golden tests

  Combat correctness                               /5 Block, intents, chip,
                                                      Wall, terminal states

  Determinism                                      /5 Seed + command replay
                                                      hashes

  Cards/classes/relics                             /5 Content tests and
                                                      real play

  Run progression                                  /5 Complete three-act
                                                      run

  Android integration                              /5 Device-tested real
                                                      engine

  Visual quality                                   /5 Ten-screen comparison
                                                      and approved assets

  Accessibility                                    /5 Glyph, TalkBack,
                                                      scaling, reduced
                                                      motion

  Balance                                          /5 Stratified simulation
                                                      and human tests

  Build/release                                    /5 Clean build, signing,
                                                      policy and device
                                                      evidence
  -------------------------------------------------------------------------

No overall average can override a P0 blocker. A release is rejected if
any P0 category is below 4/5 or any mandatory acceptance gate is
unverified.

------------------------------------------------------------------------

**End state:** one canonical EMBERFALL game, one authoritative
deterministic engine, a complete three-act roguelike, a native Android
application driven by real game state, approved and traceable art,
reproducible testing, and an independently reviewed release candidate.
