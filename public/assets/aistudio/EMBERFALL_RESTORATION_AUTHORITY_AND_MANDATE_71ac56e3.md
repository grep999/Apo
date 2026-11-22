# EMBERFALL — Restoration Authority & Production Mandate

**Document status:** Controlling restoration instruction for the current `emberfall-2` source snapshot  
**Prepared:** 21 November 2026  
**Purpose:** Stop design drift, restore the original EMBERFALL, and drive the project through a controlled, verifiable production sequence.  
**This document supersedes:** the current AI Studio implementation plan and the provisional choices recorded in `docs/ADR_CONFLICTS_AND_DECISIONS.md`, wherever they conflict with this mandate or an owner-approved source.

---

# 1. Executive direction

The current implementation is not to be treated as the new definition of EMBERFALL.

It is a partially useful implementation base that has drifted from the intended product. It restored some canonical names and visual identities, but it also invented or substituted important rules, class behaviours, resource interactions, status effects, Wall handling and ending conditions. Some tests now certify those substitutions.

**The job is restoration, not redesign.**

The production target remains the original EMBERFALL: a portrait, 7×7, orthogonal connected-group roguelike in which the player's deck composition shapes the board, board decisions create resources, cards reshape the board and tactical situation, the enemy telegraphs its next actions, and the rising Wall creates pressure. The classes, hues, neutral blocks, resource economy, combat cadence, Shade continuity and three-act run must remain coherent parts of one game.

The implementation agent must not:
- rename or replace the original classes;
- add substitute hues;
- invent new status-effect meanings;
- change the board into swap-match or falling-block gameplay;
- add mechanics because they are easy to code;
- treat a passing test as proof that a rule is canonical;
- expand content while the rules underneath it are still wrong;
- silently resolve missing or contradictory source material.

**The code must conform to the design. The design must not be rewritten to justify the code.**

---

# 2. Design authority: what is actually authoritative

The current archive contains a mixture of approved visual baseline, provisional design material, implementation notes and code. These must not be conflated.

## 2.1 Authority table

| Source | Current status | Permitted use |
|---|---|---|
| Owner-approved decisions in the conversation/project history | Highest authority | May lock or change product scope. Record every new decision. |
| `ART_BASELINE_LOCK.md` v0.4 | Canonical visual and class-identity lock | Enforce the three classes, their order, hue pairings, signatures, boss/class separation and visual identity. |
| Original EMBERFALL `SCOPE.md` and accepted ADR-001…ADR-023 | Governing mechanics/scope source where the accepted records are available | Implement exactly; preserve their status and wording. Recover the actual source files into this repository before relying on summaries. |
| `EMBERFALL_Full_Audit_and_Scope_Locked_Production_Guide.md` | Prior audit/roadmap synthesis | Use as a recovery map and implementation guide; it is not a replacement for missing original source documents. |
| `EMBERFALL_Maestro_Governed_Design_Bible_Program_v0.2.docx` | Program/volume outline, not the complete detailed game bible | Use to understand the planned documentation structure. Do not infer missing card, enemy, balance or rule details from its table of contents. |
| `CH01_Creative_Vision_Experience_Contract` v0.1 | Builder draft; explicitly not independently reviewed or approved | Use as candidate experience context only. Do not elevate its PROPOSED or OPEN items to DECIDED rules. |
| `docs/ADR_CONFLICTS_AND_DECISIONS.md` in this archive | Provisional implementation log; admits assumptions were invented | Historical evidence of what the current builder assumed. Not design authority. |
| `.aistudio/.../implementation_plan.md` | Stale and contradicts canonical class/hue identities | Superseded. Preserve for history, but do not execute it. |
| Current Kotlin code and current tests | Implementation evidence only | Establish what exists and where it fails. Never use code/tests to overrule accepted design. |
| Historical SLAY / UNMAKE files | Different games | Do not import their rules, board models, currencies, enemies, or progression into EMBERFALL. |

## 2.2 Required source-recovery gate

Before implementing any rule whose exact details are missing, the Architect must recover:
1. The actual accepted `docs/AAA/emberfall/SCOPE.md`.
2. The actual accepted `docs/adr/ADR-001.md` through `ADR-023.md`, including each record's status.
3. The full detailed game-design volumes, not merely the Design Bible Program outline.
4. The approved card, relic, enemy, class and starter-deck catalogs.
5. The exact accepted source commit or repository tree from which those documents came.

The current ZIP does not contain those original ADR files or the detailed volumes. The program outline and CH01 candidate do not fill that gap.

If a required source cannot be recovered:
- mark the dependent item `BLOCKED — SOURCE MISSING`;
- do not implement a guessed substitute;
- continue independent work that does not depend on the missing decision;
- ask the owner for the exact source only when it genuinely blocks the next implementation step.

No source recovery task may be “completed” by writing a new version from memory and labelling it original.

---

# 3. Product identity lock — immutable without owner-approved ADR

## 3.1 The game

EMBERFALL is one integrated decision system made from three inseparable surfaces:

1. **Board:** a 7×7 glass field of orthogonally connected groups. It is the tactical space and resource surface.
2. **Deck:** card frame-hue composition influences the hues painted onto the board. Cards spend Ink and alter the next board/tactical decision.
3. **Dungeon:** enemy intents, rooms, acts, narrative, sensory feedback and the rising Wall give those decisions context and consequence.

The intended player loop is: read the room and enemy tell → inspect the glass → identify a useful group → commit a pop → understand the Ink/chip/resource consequence → decide how to spend or reshape → respond to the enemy → survive the Wall → carry the altered deck and its consequences forward.

This is **not**:
- a swap-match-3 game;
- a falling-block game;
- a conventional deckbuilder with a decorative puzzle board;
- a generic fantasy battler with a renamed resource system.

## 3.2 Canonical board and vocabulary

| Element | Locked identity |
|---|---|
| Visible board | 7 columns × 7 rows |
| Connectivity | Orthogonal |
| Taps | 3 per turn |
| Playable hues | Ember, Tide, Moss, Gold, Violet |
| Neutral blocks | Ash, Stone, Prism |
| Pressure | Rising Wall with visible incoming row/preview as specified |
| Enemy information | Two-intent telegraph |
| Primary resources | Ink, Spark, Vigil, Flasks |
| Campaign | Three acts, full approximately 30-minute run target |
| Death continuity | Previous dead deck returns as Shade |
| Meta-currency | None |

The Spectrum wheel is a separate ordered mechanic: **Ember → Gold → Moss → Tide → Violet → Ember**. Do not confuse this wheel order with the presentation order in the hue list.

No group threshold, rounding rule, resource cap, starting value, tap-consumption edge case or damage number may be invented. Use the accepted source record. If unavailable, block that specific detail.

## 3.3 Canonical playable classes — exactly these three

The order is fixed.

### 1. Ashen Monk — Ember / Moss
- Core identity: burns Vigil for power; specializes in large, decisive single hits.
- Signature relic: **Prayer Beads**.
- Signature rule: the first Tap each turn pays **+2 Ink**.
- Required implementation consequence: Vigil must be a real spendable tactical resource. The Monk must be able to deliberately risk/spend Vigil to amplify power. Do not replace this identity with passive counterattacks, generic damage bonuses or a renamed class.
- Visual language: compact, grounded silhouette; prayer beads; scorched cloth and iron wraps; controlled stance; restrained ember flares on Vigil spend; heavy strike with little wasted motion.

### 2. Quarry Witch — Tide / Violet
- Core identity: recolors/repaints the board and builds Spectrum Runs.
- Signature relic: **Chisel Wand**.
- Signature rule: once per fight, recolor one block free.
- Required implementation consequence: board manipulation and ordered Spectrum setup must be a genuine strategic path, not just a label or one isolated card.
- Visual language: asymmetrical hood, chisel/prism tools, mineral ornament; precise hand sweeps and glass refraction.

### 3. The Unlit — Gold / Ash
- Core identity: turns enemy Ash into fuel; favors thin, fast decks.
- Signature relic: **Hollow Crown**.
- Signature rule: each Ash block popped pays **1 Spark**.
- Required implementation consequence: Ash generation/removal and Spark economy must support the class identity. Do not turn Ash into an ordinary colored hue or award it ordinary Ink.
- Visual language: low-saturation form, ash veil, broken lantern/hollow crown motif; Ash is drawn inward.

**Shade is not a class.** Shade is the previous dead deck represented as an encounter.  
**The Hollow Warden is the Act I boss, not a class.**

Never reintroduce Pyromancer, Cinderknight, Ashweaver, Crimson, Amber, Verdigris or Cobalt as canonical identities.

## 3.4 Acts and progression

- Act I: warm ash/cinder crypt.
- Act II: drowned/flooded vault with teal atmosphere.
- Act III: white-hot reliquary / exposed memory.
- Full three-act run is the v1.0 target.
- The shorter crawl is post-launch only if evidence supports it.
- No meta-currency.
- Shade carries the previous dead deck into the next run.
- Covenants, Ascension, Bloodstain/Codex and true ending must follow the actual accepted source, not a simplified approximation.
- iOS is deferred; do not build it as part of this Android production programme.
- Backend/online/account/telemetry work remains out of scope unless separately approved.

---

# 4. Rules that must be restored before the project can claim design fidelity

The following are the known rule summaries from the original audit baseline. They are implementation constraints, not permission to fill in missing numeric details. The exact accepted ADR files must be imported and attached to each traceability row.

## 4.1 Turn and action order — ADR-001
The accepted order is:
**Draw → Weave (3 Taps) → Cast → Wall → Enemy → Cleanup.**

Do not interleave card casts into Weave unless an owner-approved replacement ADR explicitly changes the order. Model legal commands and phase transitions in the engine. A UI button being hidden is not sufficient enforcement.

## 4.2 Board painting — ADR-002 / ADR-003
- 7×7 visible board plus the specified hidden/incoming row.
- Seeded weighted painting.
- Hue weights derive from the player's canonical deck composition using card frame hues.
- Known formula: `weight(h) = max(0.08, (cards_of_hue_h + 1) / (deck_size + 5))`, then normalize.
- Dread contributes Violet as specified.
- The Unlit's Ash cards use Gold frame hue; Ash itself is weightless.
- The board painter must derive from the one canonical player deck composition. Do not maintain an unrelated independent “board deck” alongside a separate combat deck unless the recovered authoritative source explicitly requires it.
- Resolve whether weights use the full owned deck or a specified zone from the Design Bible/ADR; do not let draw order accidentally alter painting weights.
- No fixed stripe layouts, hidden hue bias, arbitrary pair insertion or free Prism injection to rescue a board.

## 4.3 Prism — ADR-004
- Prism joins a contacting hue group.
- If multiple distinct hues contact it, resolve deterministically using the Spectrum wheel and current Run tie-break rule.
- Prism never bridges unrelated groups or splits a group.
- Tapping Prism and tapping a contacting hue must resolve consistently.
- Add golden tests for single contact, multiple contacts, tie, edge/corner and chained Prism.

## 4.4 Spectrum — ADR-005
- Run begins at zero.
- Correct next hue on the wheel advances Run.
- Prism counts as the next hue.
- An out-of-order pop is calculated at Run 0 and then resets the sequence.
- Multiplier is `1 + 0.5 × Run`.
- Use deterministic fixed-point/half-unit arithmetic and the source-defined rounding rule.
- Test first pop, expected next hue, repeated hue, out-of-order pop, wraparound and Prism.

## 4.5 Ash — ADR-006
- Ash pops as its own group under the source-defined threshold.
- Ash gives no Ink.
- Ash chip damage is `n − 1`, applied after enemy Block.
- Hollow Crown Spark is awarded per Ash block popped.
- Do not award ordinary colored Ink for Ash.

## 4.6 Stone — ADR-007
- Stone is not directly poppable.
- It breaks from the specified adjacent orthogonal qualifying group (baseline: group size at least four).
- Resolve iteratively and deterministically.
- Bound cascades and test the order: pop → Stone cascade → gravity → new spawns.

## 4.7 Wall — ADR-008
- Wall rises from the bottom, with the incoming row visible/dimmed.
- Overflow must follow the exact accepted predicate and consequence sequence.
- The known baseline describes Vigil loss, top-two-row burn, Flask loss and skipped Wall rise.
- Do not invent Guard mitigation, arbitrary neutral thresholds, bonus Flask drains or a different surge-damage formula.
- Implement the exact predicate and order from the accepted ADR. If the precise wording is missing, block the overflow implementation rather than guessing.

## 4.8 Ink and Spark — ADR-009
- Colored Ink pips are paid only by matching hue.
- Generic/colorless pips may be paid by Spark or a player-chosen eligible Ink.
- Spark never pays a colored pip.
- UI must allow a deliberate choice where required; do not silently choose a hue on the player's behalf.
- Exact caps and initial values must be source-verified.

## 4.9 Brand — ADR-010
- Brand marks individual board blocks.
- Each marked block popped costs 2 Vigil per block.
- Preview the total Vigil cost before committing the pop.
- Do not implement Brand as a generic +damage status.

## 4.10 Fog — ADR-011
- Fog hides hue colors and Ink pools for three Taps.
- Board tiles render gray while retaining their hue glyphs.
- Gold Eye restores color visibility.
- Do not implement Fog as an enemy damage reduction status.
- Preserve accessibility and glyph readability.

## 4.11 Seal — ADR-012
- Seal locks a column for the specified three-turn duration.
- Sealed column cannot be tapped or popped.
- No gravity occurs in the sealed column.
- Effects targeting sealed cells are ignored.
- Wall may rise in non-sealed columns.
- Do not implement Seal as disabling an enemy intent slot.

## 4.12 Enemy — ADR-013
- Enemy Vigor baseline: `round(36 × 1.11^node)`.
- Elite multiplier: ×1.6.
- Boss multiplier: ×3.
- Use the specified round-half-away-from-zero rule.
- Enemy Block absorbs damage for that turn and resets at the start of the enemy turn.
- Chip is applied after Block.
- Act damage ranges: Act I 6–12, Act II 12–22, Act III 20–40.
- Intent preview must match actual execution.

## 4.13 Cards and effects — ADR-015
- Use a deterministic event queue with the accepted priority table.
- Maximum 100 events per action.
- Echo repeats exactly one defined primary card effect once.
- Echo-of-Echo is ignored.
- Do not repeat card costs, draws, exhaust, secondary status effects or all card resolution steps unless the source explicitly defines that element as the primary effect.

## 4.14 Shade — ADR-016
- Shade appears at the specified Act I row 5 encounter.
- Previous dead deck becomes the Shade's intent identity.
- Vigor is performance-scaled but fair according to the source.
- Victory awards carried Cinders plus one Remnant card.
- Relics do not pass to Shade.
- Gravedigger's Spade's 150% multiplier applies only to Cinders + Remnant value.
- Do not replace the prior deck with an arbitrary four-card sample unless the accepted source defines that mapping.

## 4.15 True ending — ADR-017
- Requires the Unlit covenant with Dread.
- Requires three consecutive qualifying turns of Ash-only pops and no cards.
- Hearthkeeper attacks fall on empty air under the specified condition (zero damage/no Guard heal).
- Any specified disqualifying non-Ash pop, card, Flask or Vigil-burn action resets the streak.
- Evaluate at the specified point after the enemy phase.
- A Remnant alone or a covenant boolean alone is not sufficient.

## 4.16 Map and run — ADR-018 / ADR-023
- 15-row branching map.
- Route mix includes approximately six fights, one or two elites, two bonfires, one shop, two events, one treasure and one boss.
- Validate every playable path against the accepted route constraints.
- Full approximately 30-minute three-act run for v1.0.

## 4.17 Platform and content constraints
- ADR-019: iOS deferred until evidence gate.
- ADR-020: min API 24; target SDK 36+ subject to current Play policy at submission.
- ADR-021: final title trademark/legal review before public reveal.
- ADR-022: preserve five hues and progressive onboarding.
- ADR-023: full three-act run; short crawl is not launch scope.

---

# 5. Immediate quarantine: what in the current implementation must not be treated as canonical

The following current implementation choices are explicitly provisional or wrong relative to the original baseline:

| Current implementation choice | Required disposition |
|---|---|
| Monk Vigil used as a repeated enemy-phase counterattack | Remove. Restore deliberate Vigil spend/risk for power and large single hits. |
| Brand as bonus damage taken / incoming damage | Remove. Restore marked-block state and 2 Vigil cost per marked block popped. |
| Fog as enemy attack damage reduction | Remove. Restore visual concealment of hues/Ink for three Taps. |
| Seal as enemy Slot II disable | Remove. Restore column lock affecting tap/pop/gravity/target effects. |
| Wall overflow with invented surge damage, Guard mitigation, neutral thresholds and Flask drain | Remove. Implement accepted ADR-008 sequence exactly. |
| Echo replays full card resolution | Remove. Replay one primary effect once; suppress Echo-of-Echo. |
| Dread ending triggered by Remnant or covenant flag | Remove. Implement full consecutive-turn condition. |
| Unlit starter list of Gilded Ash / Hollow Prayer / Cinder Toll / Pale Kiln / Spark of the Crown | Not canonical. Replace only after recovering exact ADR-014 content. |
| Invented Monk/Witch 10-card starters | Not canonical. Block exact implementation until detailed source is recovered. |
| Independent board bag separate from combat deck | Do not retain unless the authoritative source explicitly defines it. Board hue weights must derive from canonical deck composition. |
| Fixed tile substitutions / free Prism injections to rescue no-move boards | Remove unless an approved rule defines the exact deterministic policy and deck accounting. |
| Six-row map or route validator that ignores events/path composition | Replace with 15-row route contract and event support. |
| Current tests that assert the above behaviours | Rewrite. A test that codifies a wrong rule is a defect, not evidence. |

Do not “fix” these by merely renaming classes, statuses or fields. The state model and player-facing consequences must be corrected.

---

# 6. Architecture decision: preserve useful work without accepting accidental redesign

The current archive is Kotlin + Compose + Room. The earlier baseline called for a lightweight Java 8 pure domain core. The current conflict log says Kotlin is provisional pending owner sign-off. There is no evidence in this archive that the owner approved a permanent architecture migration.

Therefore:

1. **Do not perform a broad rewrite back to Java as a reflex.**
2. **Do not treat the current Kotlin/Compose migration as approved.**
3. Preserve the useful current application shell and deterministic JVM engine while the owner decision is recovered.
4. Keep the domain engine completely independent of Android, Compose, Room, Firebase, Gemini and UI state.
5. Record a single architecture ADR before expanding framework-specific work.
6. Do not add dependencies or cloud services unless an approved requirement needs them.

Required boundaries:
- `engine`: immutable definitions, domain state, commands, transitions, deterministic RNG, effects and rules.
- `content`: source-traceable card/class/relic/enemy/node definitions.
- `application`: use cases, save/load orchestration, command dispatch.
- `data`: serialization, Room/DataStore and migrations.
- `ui`: render state and send commands only.

No gameplay rule belongs in a Composable, ViewModel, repository or persistence codec.

---

# 7. Production execution plan — one controlled sequence

No parallel feature expansion. The Architect owns ordering and integration. A separate Reviewer must review each phase.

## Phase 0 — Freeze, preserve and establish truth

**Do first.**
1. Record branch, HEAD, remotes, dirty status and file hashes from the real repository.
2. Preserve user changes; do not reset, clean, rebase destructively or overwrite local state.
3. Create a restoration branch/worktree from the actual source checkout.
4. Preserve `emberfall-2.zip` as an immutable audit artifact.
5. Import the authoritative source files listed in Section 2.2.
6. Mark the current AI Studio implementation plan superseded.
7. Mark `ADR_CONFLICTS_AND_DECISIONS.md` non-authoritative; retain its assumptions as history.
8. Create `docs/RESTORATION_TRACEABILITY.csv` and `docs/RESTORATION_STATUS.md`.

**Gate G0:** source provenance, branch baseline, dirty-state preservation and authority table are recorded. No gameplay code changed.

## Phase 1 — Reconstruct the original design contract

1. Read every accepted ADR and detailed source volume.
2. Extract every rule, value, timing, card ID, starter count, enemy action, UI obligation and save boundary.
3. Assign every item one status: `LOCKED`, `PROVISIONAL`, `OPEN`, `BLOCKED`, `IMPLEMENTED`, `VERIFIED`.
4. Create a source-to-code-to-test traceability row for every mechanic.
5. Resolve only genuine source conflicts through owner-approved ADRs.
6. Do not tune, rebalance or expand content in this phase.

**Gate G1:** every P0 gameplay mechanic has a source location and an acceptance test. No invented values are marked LOCKED.

## Phase 2 — Repair the deterministic domain engine

Work in this order:
1. Domain state invariants and transactional command rejection.
2. Explicit turn-phase machine: Draw → Weave → Cast → Wall → Enemy → Cleanup.
3. Board topology, bounds, group preview, pop and gravity.
4. Single canonical deck composition and deterministic board painter.
5. Prism and Spectrum.
6. Ash and Stone.
7. Ink, Spark and Vigil accounting.
8. Brand, Fog and Seal.
9. Wall, incoming row and overflow.
10. Enemy Block, chip order, telegraph and scaling.
11. Deterministic effect queue and Echo.
12. Game-over, encounter victory/defeat and true-ending state machines.

For each item:
- write the failing golden test first;
- implement the smallest coherent change;
- test the edge cases;
- review the diff for accidental mechanic changes;
- update the traceability record.

**Gate G2:** engine golden tests pass against accepted source rules. No UI or balance work can override them.

## Phase 3 — Restore the three canonical classes and content

1. Ashen Monk: Vigil spend/risk, burst identity, Prayer Beads.
2. Quarry Witch: recolor/board manipulation, Spectrum identity, Chisel Wand.
3. The Unlit: Ash fuel, thin/fast identity, Hollow Crown.
4. Recover and implement exact starter deck IDs and copy counts.
5. Recover exact card/relic definitions and costs.
6. Recover enemy and boss definitions.
7. Verify the Act I Hollow Warden.
8. Remove all stale substitute-class art and content.

Do not invent starter decks while the source is missing. Do not create cards from art filenames.

**Gate G3:** class selection, deck composition, relics and signature mechanics are source-traceable and pass class-specific golden tests.

## Phase 4 — Restore the complete campaign

1. 15-row map with exact node taxonomy, including Events.
2. Path-level route validation.
3. Normal/elite/boss encounters and scaling.
4. Rewards, shop, bonfire and event resolution.
5. Three Acts with their distinct atmosphere and source-defined content.
6. Shade from the previous dead deck, including correct scaling/rewards.
7. Covenants, Ascension, Remnants, Cinders and true ending.
8. Save/resume after every required node boundary.

**Gate G4:** deterministic complete run from New Run through Act III result for each canonical class; loss and Shade continuity verified.

## Phase 5 — Bind the Android application to the actual game

Retain the current Compose shell only as a presentation layer unless an architecture ADR changes it.

Required:
- ten approved screens and correct navigation;
- combat UI bound to engine state, not duplicate local rules;
- board taps submit domain commands;
- full enemy telegraph and Wall preview;
- five hues and neutral glyphs;
- class identities and canonical artwork;
- Ink/Spark/Vigil/Flasks and card hand;
- Fog/Seal/Brand visual and interaction states;
- victory and defeat both navigate correctly;
- no stale Pyromancer/Cinderknight/Ashweaver portraits;
- no legacy SLAY/UNMAKE mechanics.

**Gate G5:** complete phone-playable vertical slice; UI and engine state remain identical under replay.

## Phase 6 — Persistence, replay and reliability

1. Serialize writes in command order.
2. Add explicit schema migrations; remove destructive migration fallback.
3. Version snapshots and migrate old versions deliberately.
4. Unknown enum/content must produce recoverable compatibility errors, not silently become Ember.
5. Save every required node/transition.
6. Replay seed + ordered commands to identical state hashes.
7. Test process death, app background/foreground, interrupted save and schema upgrade.
8. Preserve Shade deck data and run history.

**Gate G6:** deterministic replay and upgrade-safe save/resume tests pass.

## Phase 7 — Balance, UX, art and release

Only after G0–G6:
- multi-seed simulation across all classes;
- full three-act win/loss and route statistics;
- five-hue onboarding/comprehension;
- accessibility testing with glyph redundancy;
- portrait phone layout and tablet centering;
- motion/audio/haptic budgets and reduced-motion support;
- device performance and memory tests;
- reproducible clean Android build;
- legal/title review;
- release acceptance and store preparation.

**Gate G7:** independently reviewed release candidate with reproducible build and device evidence.

---

# 8. Agent operating protocol — how to prevent another drift cycle

## Roles

- **Architect / Design Authority:** reads sources, owns traceability, resolves dependencies, approves implementation plans. Does not invent missing rules.
- **Implementer:** changes only the assigned bounded task and writes regression tests.
- **Independent Reviewer:** different agent/person from the implementer; compares diff to source and tests. Must reject unsupported behavior.
- **QA / Simulation:** runs reproducible tests, seed sweeps, state-machine checks and device acceptance.
- **Producer / Maestro Orchestrator:** maintains task status, dependencies, evidence and phase gates.

Do not let multiple agents edit `GameEngine` or shared domain files concurrently. Parallelize documentation recovery, isolated UI asset preparation and test harness work only when file ownership is clear.

## Mandatory task contract

Every task must state:
1. Source ID and exact source paragraph/ADR.
2. Current implementation and exact affected files.
3. Required behavior in observable terms.
4. Explicit non-goals.
5. Regression tests to add.
6. Acceptance commands and expected results.
7. Reviewer identity and review outcome.
8. Commit SHA and traceability update.

## Required implementation cycle

`SOURCE → CONTRACT → FAILING TEST → IMPLEMENTATION → TEST → INDEPENDENT REVIEW → COMMIT → TRACEABILITY UPDATE`

A task is not done because:
- code compiles;
- a screenshot looks right;
- a test was rewritten to match new behavior;
- a feature exists behind a flag;
- the agent wrote a document claiming completion.

A task is done only when its source, implementation, test and review agree.

## Stop conditions

The agent must stop the affected task if:
- source is missing;
- source status is draft/proposed/open;
- two accepted sources conflict;
- a mechanic requires an invented numeric value;
- the implementation needs a new mechanic to make another feature work;
- a test can pass while violating the player-facing rule;
- the only available workaround silently mutates the board or resource economy.

The agent must not stop unrelated, source-independent work.

---

# 9. Definition of done for the restoration

EMBERFALL is considered restored only when all of these are true:

- The canonical class lineup is exactly Ashen Monk, Quarry Witch, The Unlit.
- Their class identities are mechanically distinct and source-accurate.
- The five canonical hues and three neutral blocks are used consistently.
- The deck-to-board relationship is correct and deterministic.
- The three-Tap board loop, Ink/Spark economy and card Cast phase are correct.
- Prism, Spectrum, Ash, Stone, Wall and Overflow match accepted rules.
- Vigil is a deliberate tactical resource and Monk risk/reward works.
- Brand, Fog and Seal match their original meanings.
- Enemy Block, chip, telegraphs and scaling match source.
- Echo repeats one primary effect only.
- The 15-row map, events, three acts, Shade and true ending are correct.
- The app renders the domain state and cannot bypass engine rules.
- Save/replay is deterministic and migration-safe.
- No noncanonical class/hue/status mechanics remain.
- Tests verify the original design rather than the current implementation's assumptions.
- A clean build and real-device playthrough are independently evidenced.

---

# 10. Immediate agent instruction — execute this first

> You are taking over EMBERFALL restoration. This is not a greenfield redesign and not a request to make the current prototype more complete. Your task is to recover the original game and bring the implementation into conformance with it.
>
> Read `RESTORATION_AUTHORITY_AND_MANDATE.md` first. It supersedes the current AI Studio implementation plan and the provisional assumptions in `ADR_CONFLICTS_AND_DECISIONS.md`.
>
> Preserve the current project and all user work. Establish the real Git baseline and a safe restoration branch. Do not modify gameplay code in the first task.
>
> Recover the actual accepted SCOPE and ADR-001 through ADR-023, the detailed Design Bible volumes and the approved content catalogs. The Design Bible Program v0.2 file is an outline, not the complete mechanics bible. CH01 v0.1 is explicitly a builder draft, not independently approved. Do not pretend either resolves missing mechanical detail.
>
> Create the source-to-code-to-test traceability matrix. Identify every current implementation behavior that contradicts the canonical class lock or the accepted ADRs. Mark the provisional Brand/Fog/Seal/Wall/Echo/Dread/Unlit-starter choices as noncanonical. Do not add content, rebalance, rename or redesign anything.
>
> After the source audit is reviewed, implement the engine restoration in the phase order in this mandate. Start with state invariants, turn phases and board/resource mechanics; then restore classes and campaign; then app integration and persistence. Each change requires a failing regression test first and an independent review.
>
> Keep the domain engine pure and deterministic. Keep Android UI as a projection and command surface. Do not add backend, cloud, online, iOS, new game modes, new classes, new hues or meta-currency.
>
> If a required rule is missing, mark it BLOCKED and continue independent work. Never fill the gap with a plausible invention. Never rewrite a test merely to bless an implementation that contradicts the source.
>
> Report actual commands, raw test results, changed files, commit SHA, reviewer and remaining blockers. Do not claim Android build/device success without running it.
>
> The success criterion is not that the code looks modern. The success criterion is that the shipped game is recognisably and mechanically the original EMBERFALL.

---

# 11. Owner-level decisions required — keep this list short

Only ask the owner when a real decision cannot be recovered from source.

1. **Architecture:** confirm whether the original Java 8 lightweight Android architecture remains binding or whether the current Kotlin/Compose shell is an approved migration. Until confirmed, preserve the shell but keep the domain independent and do not expand framework dependence.
2. **Missing source pack:** provide/import the accepted SCOPE, ADR-001…023, detailed Design Bible volumes and content catalogs if they are not recoverable from the repository/Library.
3. **Actual repository access:** provide the live Git checkout/remote or a complete source archive with `.git` and the original design docs. The current `emberfall-2.zip` is not a Git checkout and cannot be used to create or verify a remote commit.

No other product redesign decision is needed to begin restoration.

---

**Final directive:** Stop extending the current approximation. Preserve the useful engineering foundation, restore the original mechanics and identities, and move through the gates above. One coherent game. One design authority. No silent substitutions.
