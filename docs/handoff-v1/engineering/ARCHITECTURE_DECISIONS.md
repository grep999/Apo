# EMBERFALL — Architecture Decision Records (Initial Recommendations)

## ADR-000 — Keep simulation headless and deterministic

**Status:** Accepted as the required architectural constraint from the game specification.  
**Decision:** The complete game rules live in a pure domain module with no Android imports. UI sends typed commands and renders emitted events.  
**Consequences:** Simulation tests and balance bots run without Android; UI bugs cannot mutate rules; future iOS adaptation remains possible. The app must not call domain setters directly.

## ADR-001 — Start native Android, gate cross-platform choice at M2

**Status:** Proposed; confirm after source repository inspection.  
**Decision to test:** Kotlin/JVM simulation module + Android Canvas vertical slice. At M2 evaluate Kotlin Multiplatform, Rust/C++ shared core or other approach only against actual iOS delivery requirements, measured frame time and maintenance cost.  
**Rejected for now:** Adding Unity/Godot or a complex renderer before proving the 7×7 puzzle loop.  
**Risk:** A future iOS port may require work; isolate the domain model to minimize it.

## ADR-002 — Reducer-style transitions and immutable snapshots

**Status:** Proposed.  
**Decision:** A `PlayerAction` is validated against a `GameState`; one reducer produces a new state and ordered `GameEvent` list. Preview is read-only and RNG-free.  
**Benefits:** deterministic replay, undo of last Tap, testable transitions and clean UI animation queue.

## ADR-003 — Data-driven content, typed effect DSL

**Status:** Required by source intent.  
**Decision:** Card, relic, enemy, boss, event and intent definitions use versioned schemas. Effects are an enum/sealed hierarchy with validated parameters. No reflection or arbitrary scripts in release content.  
**Benefits:** large content target can be authored and tested without per-card classes. Each card still requires a golden test.

## ADR-004 — One deterministic event queue

**Status:** Proposed.  
**Decision:** All triggers and nested effects enter one ordered queue with an explicit priority table and per-action effect budget. Echo may repeat only the specifically defined last-card effect and must not reopen an unbounded trigger loop.  
**Benefits:** deterministic outcomes and no softlock exploit.

## ADR-005 — Versioned replay and save

**Status:** Required by source intent.  
**Decision:** Every saved run and replay carries `rules_version`, `content_version`, `rng_version`, seed, state snapshot and action log. Use atomic save and migrations.  
**Benefits:** bug reproduction, daily seeds and leaderboard validation.

## ADR-006 — Canvas first; advanced lighting only after measurement

**Status:** Proposed.  
**Decision:** Build board/cards/HUD and corridor with Canvas for M0/M1. Add GL ES 3 effects only if target devices hold 60 fps and art direction requires dynamic light that Canvas cannot provide cleanly.  
**Benefits:** lower early complexity, quicker iteration and explicit engine gate.

## ADR-007 — Local-first backend boundary

**Status:** Required by source.  
**Decision:** Gameplay never requires auth or network. Backend is an optional adapter for daily seeds, leaderboard submission and replay validation. Local runs, saves and content must work offline.  
**Benefits:** lower cost and fewer external failure modes.
