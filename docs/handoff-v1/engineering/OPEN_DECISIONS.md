# EMBERFALL — Open Decisions Register

Status meanings: **OPEN / BLOCKING** = do not implement dependent behavior without a signed decision; **PROPOSED** = recommended answer for prototype testing, not approved; **RESOLVED** = accepted decision with ADR reference.

| ID | Decision | Why it matters | Recommendation to test | Status |
|---|---|---|---|---|
| ADR-001 | Strict phases vs interleaved Tap/card play | Determines combat state machine and card/board pacing | Keep source order Draw → Weave → Cast for M0; prototype interleaving as a separate variant before approval | OPEN / BLOCKING |
| ADR-002 | Initial board fill and topology | Distribution alone does not create interesting groups | Weighted random baseline plus measured topology metrics; do not silently bias generation | OPEN / BLOCKING |
| ADR-003 | Hue-weight formula with curses/neutral cards | The Unlit and curse counts alter every future board | Formalize eligible-card rules and exact normalization; preserve Dread's Violet count | OPEN / BLOCKING |
| ADR-004 | Prism group interpretation | A wild block may connect ambiguous hues | Define deterministic resolution for single/multiple Prism and mixed-color contacts | OPEN / BLOCKING |
| ADR-005 | First Spectrum pop and Run reset | Multipliers depend on start/reset semantics | Specify Run state at turn start and after out-of-order pop with table-driven examples | OPEN / BLOCKING |
| ADR-006 | Ash chip damage | Ash pays no Ink but pop formula also defines chip | Confirm `n-1` chip applies to Ash | OPEN / BLOCKING |
| ADR-007 | Stone chain break order | Can create free multi-breaks and trigger ordering exploits | Define whether one pop can trigger iterative Stone checks and exact queue order | OPEN / BLOCKING |
| ADR-008 | Wall orientation and overflow | Affects softlocks and board pressure | Define visible/hidden row coordinates, insertion and overflow trigger | OPEN / BLOCKING |
| ADR-009 | Spark and generic cost | Spec's “1 any Ink” and Spark differ | Define resource payment order and whether Spark pays only colorless cost | OPEN / BLOCKING |
| ADR-010 | Brand penalty semantics | Could be 2 damage per block or per pop | Choose and show in pop preview before launch | OPEN / BLOCKING |
| ADR-011 | Fog and glyph accessibility | Hue hiding conflicts with color-independent identification | Decide which information is hidden while retaining accessible interaction cues | OPEN / BLOCKING |
| ADR-012 | Seal behavior | Board lock can interact with card-generated effects | Define whether Seal prevents taps, gravity, card effects, wall insertion or all | OPEN / BLOCKING |
| ADR-013 | Enemy Vigor and Block | Balance curve and damage math currently incomplete | Define integer rounding and reset timing in a balance ADR | OPEN / BLOCKING |
| ADR-014 | The Unlit starter deck | Gold + Ash does not map cleanly to normal colored-hue starter recipe | Define Ash-card classification and exact 12-card list | OPEN / BLOCKING |
| ADR-015 | Effect queue and Echo | Recursive effects can become infinite | Deterministic priorities, one Echo scope, effect budget and loop guard | OPEN / BLOCKING |
| ADR-016 | Shade scheduling and inheritance | Prior deck as intents must be bounded and fair | Generate a strategy-profile Shade from last deck; define relic inheritance/reward math | OPEN / BLOCKING |
| ADR-017 | Fast true-ending rules | Three turns of Ash-only/no-card actions require explicit transition semantics | Define all checks, phase timing, failure handling and Unlit route prerequisite | OPEN / BLOCKING |
| ADR-018 | 15-row map versus node count | The listed per-act node mix may not fit every route | Define node graph constraints and expected traversed nodes; use validator | OPEN / BLOCKING |
| ADR-019 | iOS at 1.0 | Native Android renderer may constrain shared code | Defer to M2 evidence; retain pure domain module and platform adapters | OPEN / BLOCKING |
| ADR-020 | Launch target SDK | Source target 35 is stale for new Play submissions | Set target API 36+ at release, verify policy again before submission | RESOLVED by platform policy check; re-verify at launch |
| ADR-021 | Final title/trademark | EMBERFALL is working title | Trademark search before public reveal | OPEN |
| ADR-022 | Four or five hues at launch | Five is core differentiator; four could simplify onboarding | Preserve five; teach progressively; test comprehension before changing count | PROPOSED |
| ADR-023 | Full run vs short crawl | Full run target is ~30 min; short crawl is post-launch idea | Ship full run first; short crawl only after core retention evidence | PROPOSED |

Every resolution must create `docs/adr/ADR-NNN-*.md`, link the source lines/sections, explain alternatives, define consequences and add tests. Do not amend a decision silently.
