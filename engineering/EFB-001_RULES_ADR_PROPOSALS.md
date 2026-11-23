# EFB-001 — Rules ADR Proposal Package

**Status:** PROPOSAL ONLY — not approved; no gameplay implementation authorized by this document  
**Depends on:** EFB-000 repository/source baseline  
**Authority:** `references/EMBERFALL_GAME_SPEC_v0.2.txt`, `engineering/OPEN_DECISIONS.md`  
**Purpose:** Convert the 19 OPEN / BLOCKING decisions into a coherent, testable M0 rules contract for product-owner review.

## 1. Executive recommendation

Approve a strict, deterministic, portrait-first **group-pop puzzle roguelike** baseline:

- The turn remains Draw → Weave (3 Taps) → Cast → Wall → Enemy → Cleanup.
- A Tap pops one orthogonally connected group of at least two cells; gravity is vertical only.
- The deck determines hue spawn weights; board generation is weighted and seeded, not hand-shaped.
- Every ambiguous interaction (Prism, Spectrum, Stone, Wall, Seal, Brand, Spark) has one deterministic rule and a golden test.
- The effect queue is bounded, ordered and replayable. Echo is deliberately narrow.
- Shade is a one-run bounded recovery encounter, not a stacking meta-progression system.
- The true ending is a precise three-turn Ash-only Fast, gated by the Unlit Covenant.
- M0 proves the board loop before content scale, cosmetics, iOS or backend work.

This proposal preserves the five-hue identity and the full-run target. It does not change the source specification, mark ADRs resolved, or authorize implementation. Every recommendation below remains **PROPOSED** until the product owner signs it and the corresponding individual ADR is written under `docs/adr/`.

## 2. Decision bundles

| Bundle | ADRs | Recommended baseline |
|---|---|---|
| Board and turn contract | 001–008 | Strict phases, seeded deck-weighted board, deterministic Prism/Run/Ash/Stone/Wall rules |
| Costs, enemies and effects | 009–015 | Explicit Spark payment, per-block Brand, readable Fog, bounded Seal, integer combat math, exact Unlit deck, bounded effect queue |
| Run structure and ending | 016–018 | One bounded Shade, explicit Fast ending, 15-row map with a fixed node budget |
| Platform | 019 | Android first; preserve pure domain; defer iOS implementation choice to M2 evidence |

---

## 3. Blocking ADR recommendations

### ADR-001 — Strict phases vs interleaved Tap/card play

**Recommendation:** Keep strict phases for M0 and the first vertical slice: Draw 5 → Weave exactly 3 Taps → Cast any number of affordable cards → Wall rises → Enemy intent resolves → Cleanup. Cards may create additional board effects, but they do not reopen the Tap phase unless a card explicitly grants a Tap in the current Cast phase.

**Why:** The source spec already presents this order. It makes the board/card relationship legible, gives the Wall a predictable cadence, and keeps the state machine auditable. Interleaving is a separate experiment, not an implicit exception.

**Acceptance tests:** A phase-transition table covers every legal action; a fourth ordinary Tap is rejected; a card-granted Tap is tagged as a card effect and does not reopen arbitrary player input; Wall and enemy phases execute exactly once per turn.

### ADR-002 — Initial board fill and topology

**Recommendation:** Use independent seeded cell sampling from the normalized deck hue weights plus the defined neutral-block rules. Do not force a desired group-size histogram or place handcrafted patterns. If the generated board contains no legal group of size ≥2, perform a bounded whole-board rejection sample using the same deterministic RNG stream; after 32 failed attempts, create one minimal adjacent pair using a hue sampled from the same weights and emit an explicit `BoardGenerationFallback` event.

**Why:** The deck-paints-board pillar must remain measurable. Hidden local repairs would make spawn weights dishonest. A rare, logged fallback avoids a no-action opening without silently shaping normal boards.

**Acceptance tests:** 100,000 seeded boards reproduce the expected hue distribution within declared statistical tolerances; every board has at least one legal pop; fallback frequency is reported and remains below 0.01%; identical seed + deck + rules version yields byte-identical boards.

### ADR-003 — Hue-weight formula with curses and neutral cards

**Recommendation:** Apply the spec formula exactly:

`rawWeight(h) = max(0.08, (hueCardCount(h) + 1) / (totalDeckCards + 5))`

Then normalize the five playable-hue weights to sum to 1. `totalDeckCards` includes every card in the deck. `hueCardCount(h)` includes cards with that playable hue, including curses explicitly assigned a hue. Neutral Ash/Stone/Prism cards do not add to any playable-hue numerator. Dread counts as Violet. The Unlit's Ash Wards remain neutral and do not count as Gold or a sixth hue.

**Why:** This preserves the example distribution and prevents neutral cards from accidentally inflating a playable hue. The formula, including its floor and normalization order, must be the single source of truth.

**Acceptance tests:** Golden tests cover the example 5 Ember / 4 Tide / 3 Moss deck; Dread adds to Violet; Ash Wards do not; all weights are ≥0.08 before normalization and sum to 1 after normalization; statistical sampling matches computed weights.

### ADR-004 — Prism group interpretation

**Recommendation:** A pop resolves to one base hue. When a player taps a normal playable-hue cell, flood-fill orthogonal cells of that hue plus adjacent Prism cells. A Prism is consumed by that resolved group only; it cannot bridge two different base-hue components into one mixed-hue pop. If the player taps a Prism directly, choose the adjacent playable-hue component that yields the largest group; ties use the next expected Spectrum hue, then the fixed wheel order. Prism may join an Ash group, but that pop still pays no Ink.

For Spectrum, a pop containing one or more Prisms counts as the next expected wheel hue for one Run step; multiple Prisms do not create multiple steps. Ink is still paid in the resolved base hue. A Prism-only cluster with no adjacent hue/Ash group is not a legal pop.

**Why:** This preserves Prism's wildcard identity without allowing one block to belong to multiple simultaneous pops or creating order-dependent flood fills.

**Acceptance tests:** Golden boards cover one Prism touching one hue, one Prism touching two hues, multiple Prisms, direct Prism tap, Prism + Ash, and tie-breaks. Replays must produce identical group membership and Run outcome.

### ADR-005 — First Spectrum pop and Run reset

**Recommendation:** At the start of each turn set `lastRunHue = null` and `run = 0`. The first pop establishes `lastRunHue` and receives a ×1.0 multiplier. A subsequent pop whose Run hue is the next hue on the five-hue wheel increments Run by 1; its Ink multiplier is `1 + 0.5 × run`. An out-of-order pop sets Run to 0 for that pop and makes its resolved Run hue the new `lastRunHue`, allowing a new sequence to begin. Reset Run and `lastRunHue` during Cleanup.

**Why:** The first pop has no previous pop to follow, so it should not receive a free Run bonus. The out-of-order rule must define both the multiplier and the next comparison anchor.

**Acceptance tests:** Table-driven tests cover first pop, one through five ordered pops, wrap Ember after Violet, out-of-order pop, a new sequence after reset, and Prism substitution. Ink rounding occurs once at the final payout, not after each multiplier step.

### ADR-006 — Ash chip damage

**Recommendation:** Ash groups of size `n ≥ 2` deal `n - 1` chip damage after enemy Block, exactly like other popped groups, but pay zero Ink. Ash remains a fuel-denial / damage action. Relics such as Hollow Crown may add their separately defined Spark effect; they do not change the base Ink payout.

**Why:** The spec defines chip damage for pops and separately exempts Ash from Ink. Applying the chip rule consistently makes Ash meaningful without turning it into an economy source.

**Acceptance tests:** Ash group sizes 2, 3 and 8 deal 1, 2 and 7 chip respectively, pay zero Ink, and still trigger explicitly defined Ash relics. Brand interaction is covered by ADR-010.

### ADR-007 — Stone chain break order

**Recommendation:** Resolve Stone in one snapshot pass. At the moment a qualifying group (size ≥4) is popped, identify every Stone cell orthogonally adjacent to that group in the pre-removal board snapshot. Break all identified Stones in one deterministic batch, then apply gravity once. Newly exposed Stone-to-Stone adjacency does not recursively trigger more breaks in the same pop.

**Why:** Recursive chain breaks create free multi-breaks and hidden ordering exploits. A single snapshot pass is deterministic and easy to preview.

**Acceptance tests:** Golden boards cover one Stone, multiple adjacent Stones, Stone exposed by gravity, and two Stone clusters separated by one cell. A Stone broken in the batch cannot trigger another Stone break until a later qualifying pop.

### ADR-008 — Wall orientation and overflow

**Recommendation:** Index the visible board with row 0 at the enemy/top edge and row 6 at the player/bottom edge. The hidden preview row sits below row 6. On a normal rise, rows shift toward row 0, the hidden row becomes row 6, and a new hidden row is generated. If any cell in row 0 is occupied before the rise, Overflow occurs: lose 8 Vigil, clear rows 0 and 1, skip the shift, and retain the pending hidden row for the next turn.

**Why:** This makes “rises from below” and “burn the top two rows” unambiguous. It also gives the player a stable mental model of which side is dangerous.

**Acceptance tests:** Board-coordinate tests cover normal rise, occupied top row, empty top row, multiple occupied cells, hidden-row retention after Overflow, and save/replay across a rise.

### ADR-009 — Spark and generic cost

**Recommendation:** A colored cost can only be paid with matching colored Ink. A generic `1` cost can be paid with any colored Ink or Spark. Spark cannot pay a colored pip. When paying generic cost, the player may choose the source; the default auto-payment spends Spark first, then the least abundant eligible colored Ink, with wheel order as a tie-break.

**Why:** This preserves Spark as a colorless overflow resource without letting it erase hue identity. Explicit source selection prevents surprising payment behavior.

**Acceptance tests:** Payment tests cover colored-only, Spark-only, mixed payment, insufficient payment, auto-payment priority and player override. Card previews show the exact payment source before commitment.

### ADR-010 — Brand penalty semantics

**Recommendation:** Each branded block that is actually popped costs 2 Vigil. The cost is per block, not per pop. It applies to manual Taps and card effects whose semantics explicitly perform a Pop. Recolor, Fuse, Wall movement or non-pop destruction do not trigger Brand unless their effect definition explicitly says “Pop.” The preview shows the exact Vigil cost before the player commits.

**Why:** “Marked blocks cost 2 Vigil when popped” is most naturally per block. Per-pop damage would make the penalty scale unpredictably with group size and conceal the cost.

**Acceptance tests:** One and multiple branded blocks, mixed branded/unbranded groups, Pop Shot, Recolor, row destruction and Brand Breaker all have golden tests. Preview cost equals actual Vigil loss.

### ADR-011 — Fog and glyph accessibility

**Recommendation:** Fog lasts for exactly 3 player Tap actions. It desaturates all hue fills and hides textual hue labels, but preserves the unique high-contrast hue glyph and group boundaries. The glyph is an accessibility cue, not a second color channel to be disabled. Gold Eye reveals the true hue tint and the next two Wall rows while active.

**Why:** Fog should impair fast color scanning without making the board inaccessible or forcing color recognition. A player must never need color alone to identify a group.

**Acceptance tests:** Tests verify the 3-Tap countdown, preserved glyph semantics, reduced-motion/high-contrast modes, and Gold Eye reveal. Manual accessibility review is still required on device.

### ADR-012 — Seal behavior

**Recommendation:** Seal prevents player-initiated Taps and player card effects from targeting or changing cells in the sealed column for 3 complete player turns. It does not stop gravity, normal Wall rise, or enemy-originated board effects. Blocks in the sealed column move vertically with the board; the Seal marker remains attached to the column coordinate and counts down after each enemy phase.

**Why:** Freezing a column against gravity or Wall movement would break the rectangular board and create a separate physics system. Seal should restrict player agency, not suspend the board clock.

**Acceptance tests:** Tap, Pop Shot, Recolor, Fuse, gravity, Wall rise and enemy Stain interactions are tested while Seal is active; countdown is exactly three complete turns.

### ADR-013 — Enemy Vigor and Block

**Recommendation:** Use integer Vigor:

`baseVigor = roundHalfUp(36 × 1.11^fightIndex)`

Apply ×1.6 for elites and ×3.0 for bosses, then round once to the nearest integer (half up). `fightIndex` is the zero-based number of prior combat encounters in the current act. Enemy Block absorbs all player chip/card damage during the player's current action phase and is cleared at the start of the next enemy phase; Block granted by an enemy intent during that phase protects it during the following player phase. Player Block follows the analogous reset at the start of the next player turn.

**Why:** This defines node indexing, rounding and Block lifetime. It prevents platform-specific floating-point or timing differences.

**Acceptance tests:** Golden values around rounding boundaries, act transitions, elite/boss multipliers, enemy Block grant/consume/expiry, and player Block reset are covered. All combat math uses integer state.

### ADR-014 — The Unlit starter deck

**Recommendation:** Define the 12-card starter deck exactly as: 4 Gold Strike, 4 Ash Ward, 2 Pop Shot, 1 Sift, and 1 Ash Alchemy signature. Ash Ward is a neutral Ash card, not a sixth playable hue and not a Gold hue card. Dread remains a Violet curse for spawn weighting. Hollow Crown remains the class signature relic and converts Ash pops to Spark as separately defined.

**Why:** The Unlit is Gold + Ash, not Gold + a missing playable hue. An explicit neutral Ash Ward makes the class identity legible and avoids accidental spawn-weight inflation.

**Acceptance tests:** Starter deck count is exactly 12; card hue metadata is correct; Ash Ward does not affect playable-hue weights; Dread counts Violet; Ash Alchemy and Hollow Crown interactions have golden tests.

### ADR-015 — Effect queue and Echo

**Recommendation:** Use one deterministic FIFO effect queue with monotonic sequence numbers and explicit event categories. Resolve in this order: initiating action; authored primary effects; board mutations; pop/Stone/Brand triggers; relic triggers; enemy reactions; cleanup. Events created by an effect are appended in deterministic authored order. Echo repeats only the last top-level card cast this turn, once; Echo cannot repeat Echo, cannot repeat an already echoed card, and does not recursively repeat nested trigger events.

Set a per-action budget of 128 queued effect events and maximum nesting depth 16. If a budget is exceeded, reject the initiating action transactionally on the working state, emit a deterministic diagnostic event and leave the committed game state unchanged.

**Why:** The queue must be replayable and must not permit recursive infinite effects. Echo should create a deliberate combo, not an unbounded interpreter.

**Acceptance tests:** Golden tests cover nested Pop/Stone/Brand/relic triggers, Echo scope, event priority, insertion order, budget overflow, depth overflow, and transactional rollback. The same seed/action log must produce the same event sequence.

### ADR-016 — Shade scheduling and inheritance

**Recommendation:** A Shade appears only in the next run after a death, at Act 1 row 5. It is built from the immediately previous run's deck snapshot and a bounded strategy profile; it does not literally execute an unrestricted player deck. The Shade's intents remain visible two actions ahead. On victory, return exactly the Cinders carried at the previous death and offer 1 Remnant chosen from up to 3 distinct eligible cards in the dead deck. No relics or passive progression are inherited. Skipping the Shade permanently fades that one Shade and forfeits its recovery reward; no stacking or future interest is created.

**Why:** This makes death meaningful but bounded. It avoids compounding permanent power and keeps the Shade fair and telegraphed.

**Acceptance tests:** No Shade after a win; one Shade after a death; correct row placement; deterministic deck-profile generation; exact Cinders recovery; Remnant eligibility/selection; skip behavior; no relic or passive inheritance.

### ADR-017 — Fast true-ending rules

**Recommendation:** The Fast is available only in Act 3, Hearthkeeper phase 3, and only when the Unlit Covenant is active. The player must complete 3 consecutive full turns in which all 3 ordinary Taps pop groups consisting exclusively of Ash, no card is cast, and no relic/card effect creates a Pop or other Ink-producing board mutation. Prism is not allowed in an Ash-only Fast pop. Any non-Ash pop, card cast or prohibited board mutation resets the counter to 0. During a valid Fast turn, the Hearthkeeper's attack intent resolves against empty air; the Wall still rises normally. On the third valid turn, the true-ending transition resolves before the next boss attack.

**Why:** This makes the “three consecutive turns” testable and prevents an accidental fast ending through cards or relics. The Unlit Covenant is the route key; the Unlit class is not required, so the ending is a player-discovered rule rather than a class lock.

**Acceptance tests:** Tests cover turn boundaries, three consecutive valid turns, reset on each prohibited action, Ash-only group validation, no-card enforcement, Wall behavior, attack miss behavior, and exact transition timing.

### ADR-018 — 15-row map versus node count

**Recommendation:** Use 15 map rows: row 0 is the entrance/starting row with no encounter; rows 1–13 contain the 13 non-boss nodes; row 14 is the boss. The baseline per-act route graph contains 6 normal fights, 1 elite, 2 bonfires, 1 shop, 2 events and 1 treasure, followed by the boss (14 encounter nodes total). A second elite may replace one normal fight on selected higher-risk maps, keeping the encounter-node budget fixed. The generated map may expose optional branches, but every traversable path must reach the boss and must not make all bonfires or the only shop unreachable.

**Why:** The stated node mix totals 14 encounters with one elite. A second elite is a variant, not an extra mandatory node that silently expands the run. A non-encounter entrance row reconciles the 15-row map.

**Acceptance tests:** Property tests generate at least 100,000 map seeds; all paths reach the boss; node counts stay within the declared budget; every map has two bonfires, one shop, one treasure and two events in the graph; route accessibility constraints are verified.

### ADR-019 — iOS at 1.0

**Recommendation:** Ship the M0/M1 prototype on Android first and keep the domain simulation pure Kotlin/JVM with platform adapters. Do not commit to a specific cross-platform implementation before M2. At M2, decide iOS 1.0 only if the team can demonstrate an acceptable shared-domain strategy (Kotlin Multiplatform or another measured option), a viable renderer/audio path, and a funded QA/device matrix. If those gates fail, ship Android 1.0 first and schedule iOS after launch rather than compromising the core loop.

**Why:** The spec calls iOS a 1.0 target, while the architecture decision explicitly gates the engine path at M2. This recommendation preserves the target without forcing premature framework adoption.

**Acceptance tests:** M2 decision memo compares options against actual frame time, audio latency, save/replay parity, developer capacity and iOS delivery cost. The pure domain module must remain free of Android imports.

---

## 4. Non-blocking recommendations

### ADR-021 — Final title and trademark

Keep EMBERFALL as an internal working title only. Do not announce or publish it until a trademark and store-name search is complete. No gameplay work depends on the final title.

### ADR-022 — Four or five hues at launch

Preserve all five playable hues. They are a core identity pillar and support the Spectrum wheel. Reduce onboarding complexity through staged introduction and glyphs, not by removing a hue before evidence exists.

### ADR-023 — Full run vs short crawl

Ship the full ~30-minute run as the primary mode. Do not add a short crawl to M0/M1; consider it only after core retention and completion data show a real need. No meta-currency or energy timer is introduced.

ADR-020 remains resolved by the existing platform-policy check and must be re-verified at store submission.

## 5. Supplemental mobile UX and platform contract

The five-persona first-run report is explicitly a desk-based simulation (evidence class INFERRED / ASSUMED), not human research or observed gameplay. Its cross-persona risks should nevertheless become measurable product gates before external beta. These are not added to the 19 gameplay ADR count; they are supplemental acceptance requirements.

### Input and recovery

- Keep drag-to-target, but add tap-card → tap-target as an equivalent path. Every legal target must be visibly indicated.
- One Undo per turn may reverse the most recent Tap only, and only before any card is cast. It restores the exact pre-Tap board, Ink, Run and Tap count; it does not rewind RNG, enemy actions or prior committed turns.
- Separate card long-press/read from board hold-to-preview. Provide an explicit accessible “card details” action so the same gesture is not the only way to discover rules.

**Acceptance:** first-use control-discovery tests, misdrop/cancel tests, TalkBack/focus tests, and deterministic undo/replay tests.

### Persistence and offline behavior

- Save atomically after every committed player action and phase transition, not only at node boundaries. Resume the exact mid-combat state after backgrounding, process death or device restart.
- Core gameplay, local run saves and seeded runs work fully offline. Daily seeds, leaderboards and replay upload are online features; failures queue for retry and never block a run or destroy local progress.
- An interrupted in-flight action must resolve as either fully committed or fully rolled back—never half-applied.

**Acceptance:** process-kill/resume matrix at tutorial, mid-Tap, card targeting, enemy phase, reward and map; airplane-mode full run; interrupted network sync; save corruption and migration tests.

### Accessibility and input acceptance

- Every hue has a distinct glyph that remains identifiable at actual board-cell size and under Fog. Color is never the only information channel.
- Preserve scalable text, reduced motion, reduced flashing, haptics off, remappable layout, one-handed reach and screen-reader labels/focus order.
- Keep 44 dp as the minimum interactive cell target; use larger targets where the board layout permits. Provide a non-drag card-targeting path.
- Fog may desaturate/hide tint and text labels, but it must not remove the glyph or focus/selection cues needed to act.

**Acceptance:** grayscale and color-vision simulation, enlarged text, reduced motion, one-handed reach, TalkBack, high-contrast and Fog tests on physical devices. Accessibility claims remain unverified until tested with participants who use these features.

### Performance, compatibility and store transparency

- Resolve the documented target-SDK discrepancy before release: product text says target API 35 while the decision register requires API 36+. Recommendation: target the current Play-required API at release (36+ for this baseline) and retain the declared minimum API only after a device/support review.
- Treat 60 fps on a four-year-old mid-range Android device as a measured target, not a source-code claim. Add a lower-end supported device to the matrix and record frame pacing, memory, thermal behavior, battery, startup and crash-free sessions.
- Before store submission, publish the one-time premium price, package size, minimum Android version/device requirements, offline behavior and purchase boundary. No ads, energy timer, random-reward purchase or pay-to-win layer is introduced.

**Acceptance:** physical-device performance report, package-size report, offline verification and store metadata review. SDK target is checked against the current Play policy at submission time.

### First-run reveal and pacing

- Fight 1 remains a deliberately simple board-only lesson, but it must be framed as learning the board—not as the whole game.
- Fight 2 must visibly demonstrate the causal loop: deck composition changes hue distribution → a pop generates Ink → Ink casts a card → the card changes the board or next decision.
- Teach one new keyword at a time. Avoid a full rules dump. Keep map/reward/shop transitions shorter than the combat decisions they support.
- The first-run success criterion is comprehension: a new player can explain why a chosen pop is useful, predict its Ink/Run result, and describe one way the deck changes the board.

**Acceptance:** moderated first-run sessions record time to first legal pop, time to first card cast, prediction accuracy, illegal taps, target errors, menu time vs combat time, and unprompted explanation of the deck-to-board loop. The simulated persona report is not a substitute for these human sessions.

Source: `qa/FIVE_PERSONA_FIRST_RUN_SIMULATION.md` on `qa/five-persona-first-run-audit` (source commit `bbeb5215dbe455e92177756661af65123a70bfa2`), explicitly classified as inferred/assumed.

## 6. Required product-owner sign-off

The 19 blocking decisions remain OPEN / BLOCKING until the product owner explicitly approves or edits this package. The supplemental mobile UX/platform requirements also require product acceptance before external beta. Approval should authorize writing the individual `docs/adr/ADR-NNN-*.md` records and adding golden/property tests. It does **not** authorize implementation of the board engine until the individual ADRs are committed and the EFB-000 baseline has passed independent review.

## 7. M0 implementation boundary after approval

Once EFB-000 is independently accepted and the P0 ADRs are signed:

1. Implement only the headless board/reducer contract first: board generation, Tap group resolution, gravity, Ink, Spectrum, Stone and Wall.
2. Add golden/property tests and deterministic replay before card breadth.
3. Add one class, a 12-card starter deck and one enemy with two-turn intents.
4. Prove the board-only first fight is interesting and understandable before adding the full reward/economy/map layer.
5. Use seeded bot sweeps as balance evidence, not as a substitute for human playtesting.

No production code or game mechanics are changed by this proposal.
