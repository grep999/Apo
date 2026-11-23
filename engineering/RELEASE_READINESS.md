# EMBERFALL — Release Readiness & Production Completion Gates

**Programme:** EMBERFALL v1.0  
**Product target:** Premium, portrait-first Android roguelike puzzle deckbuilder  
**Source of truth:** `references/EMBERFALL_GAME_SPEC_v0.2.txt`, accepted Product Owner decisions and accepted ADRs  
**Workflow control plane:** Maestro v0.3.0  
**Checkpoint:** 2026-11-23  
**Overall disposition:** NOT RELEASE READY

> This is the project-level release gate. It records what is proven, what is merely merged, what is implemented but awaiting review, and what remains unbuilt. A merged PR is not automatically a completed Maestro increment. A green board-core build is not an Android game release.

## 1. Status vocabulary

- **MET** — the exact criterion has evidence on the relevant source/candidate.
- **PARTIAL** — implementation or merge exists, but at least one required acceptance/review/evidence gate is absent.
- **BLOCKED** — cannot start or be accepted until a named dependency or decision is satisfied.
- **NOT STARTED** — no implementation evidence.
- **NOT APPLICABLE** — explicitly outside the current increment; never use this to hide a release requirement.

Only the Product Owner may approve a scope change or cut. Only a distinct, independent reviewer may record Mastery. Builders cannot self-approve. No PR merge, protected-branch update, publication, store submission or deployment is authorized by this checklist.

## 2. Verified portfolio checkpoint

| Increment | Evidence-backed state | What is met | What remains |
|---|---|---|---|
| EFB-000 — source baseline | **PARTIAL — merged** | PR #4 merged as `5130215cb0a033345752311903de7f9c08aced4d`; source inventory and baseline documentation exist; E00 validation workflow was recorded as passing | Independent review of the merged tree and matching E00 evidence is still absent. PR #4 has zero submitted review records. |
| EFB-001 — rules/ADR freeze | **PARTIAL — merged** | PR #5 merged as `f9a41aa373d36d41a0c134c8005fb5984fecd2b3`; Product Owner decision baseline and resolved blocking rules ADRs are present; prior ADR validator evidence passed | PR #5 has zero submitted GitHub review records. Preserve the merged decisions; reconcile the review/evidence record without silently reopening accepted rules. |
| EFB-002 — board core | **PARTIAL — candidate verified** | Candidate `3fcb5f2c0ba3ae68690f64342e686f06b8f76061`; exact-candidate Gradle/JUnit and pure-JVM boundary checks passed; Maestro Momentum work items completed; immutable state/results, deterministic rules and randomized invariant coverage delivered | Independent Mastery review is still pending. EFB-000 merged-tree review is still pending. PR #11 remains Draft. No merge/acceptance yet. |
| EFB-003 — turn/combat | **BLOCKED — prepared only** | Grounding packet is prepared in this change | Do not implement until EFB-002 is accepted through Mastery and its dependency gate is clear. |
| EFB-004 through EFB-016 | **NOT STARTED / dependency blocked** | WBS and task definitions exist | No implementation or acceptance evidence yet. |

### Exact EFB-002 evidence

- Candidate: `3fcb5f2c0ba3ae68690f64342e686f06b8f76061`
- [Board-core Gradle/JUnit workflow](https://github.com/grep999/Apo/actions/runs/45209604945) — PASS.
- [Maestro lifecycle/GateRunner workflow](https://github.com/grep999/Apo/actions/runs/45209604883) — PASS.
- Maestro workflow: `b856a095-5d26-4aeb-8c52-82f36dcde6e6`; stage `MASTERY`; status `ACTIVE`; revision 5.
- Three Momentum work items are complete. Independent Mastery work item is pending; assignment status is `PROPOSED`, not assigned or completed.
- Evidence artifact: `emberfall-efb-002-maestro-evidence`, artifact ID `14834588092`, SHA-256 `bb177ce72c13fdf5d7a56aead8274511367ffd8909fe0b67b818c8550226ff83`.
- Required exact-SHA checks: Gradle/JUnit `VERIFIED`, exit 0, log SHA-256 `56a36ffb013d79fec41ae2d7623bd4e90b49d159c95f5b56e8610ee99e71e3bb`; pure-JVM boundary `VERIFIED`, exit 0, log SHA-256 `ce4db7edbc0b3f58a04e8a857be2076d906aea037be04da98da8f4d452d6c2c3`.
- The board workflow proves the pure Kotlin/JVM module only. It does not prove Android compilation, gameplay usability, device performance or release readiness.

## 3. Hard gates before feature implementation can proceed

- [ ] Independent EFB-000 review of merged tree `5130215cb0a033345752311903de7f9c08aced4d`, including the matching E00 workflow artifact. Review must inspect source classification, legacy/v0.2 rule separation, ADR inventory, toolchain applicability and evidence provenance.
- [ ] Independent EFB-002 Mastery review of exact candidate `3fcb5f2c0ba3ae68690f64342e686f06b8f76061`, including changed paths, tests, exact-SHA evidence, deterministic invariants, ADR compliance and edge cases.
- [ ] Reviewer identity must be different from the builder. A proposed assignment or unsubmitted request is not a review.
- [ ] Resolve every review finding: fix it, or record an explicit Product Owner disposition with rationale and risk owner. Unresolved blocking findings prevent acceptance.
- [ ] Record EFB-002 Repetition: expected vs actual, durable lessons, accepted state, next increment and evidence references.
- [ ] Only then mark EFB-002 accepted and authorize EFB-003 implementation.

## 4. Production work breakdown — required before v1.0

Each increment receives its own Maestro workflow, task-specific Grounding packet, bounded Momentum work items, exact candidate, project-specific GateRunner checks, independent Mastery and Repetition record. No giant all-project agent prompt. The task dependencies below are hard dependencies, not suggestions.

### EFB-003 — Turn/combat state machine

- [ ] Implement explicit Draw → Weave → Cast → Wall → Enemy → Cleanup phases.
- [ ] Implement player Vigil, Block, Flask count/cap and healing rules.
- [ ] Implement deterministic enemy state, intent queue, targets and numeric preview of the next two intents.
- [ ] Guarantee previewed intent equals the intent resolved; no hidden attacks.
- [ ] Implement Strike, Ash Row, Stone Seed, Stain, Fog, Seal, Brand, Surge, Snatch and Curse behaviors as specified.
- [ ] Implement damage, Block absorption/reset, Delay, board-target validation, death and cleanup order.
- [ ] Make all invalid actions transactional: no partial state, resource or RNG mutation.
- [ ] Add command/reducer API, pure preview, emitted game events and deterministic replay fixtures.
- [ ] Test phase ordering, enemy intent forecasting, status durations, lethal damage, Flask edge cases, board attacks, invalid targets and multi-turn sequences.
- [ ] Independent Mastery + Repetition before acceptance.

### EFB-004 — Data-driven card/effect engine

- [ ] Versioned typed schemas for card ID, hue/frame, type, rarity, cost, tags, targeting, effects and upgrades.
- [ ] Deterministic payment across colored Ink and Spark; reject unaffordable casts atomically.
- [ ] One ordered effect resolver/trigger queue; explicit priority and stable ordering.
- [ ] Implement Pop, Recolor, Fuse, Spawn, Shatter, Draw, Tap, Delay, Exhaust, Retain and Echo.
- [ ] Bound recursion, Echo, trigger depth, effect count and per-action work; detect cycles and fail safely.
- [ ] No arbitrary code execution in content definitions.
- [ ] Golden test for every vertical-slice card and every relevant effect interaction pair.
- [ ] Validate preview, payment, execution and emitted events agree.
- [ ] Independent Mastery + Repetition before acceptance.

### EFB-005 — Classes and starter decks

- [ ] Implement Ashen Monk, Quarry Witch and The Unlit with distinct mechanical identities.
- [ ] Preserve exact Product Owner starter-deck decisions and separate signature relic identity.
- [ ] Validate all three 12-card decks, hue weights, relic effects and unlock/selection data.
- [ ] Prove class identity through actual play patterns, not just different card names.
- [ ] Resolve any remaining naming/content review items without changing accepted mechanical decisions.
- [ ] Independent content/design review, tests, Mastery and Repetition.

### EFB-006 — Enemies, elites and bosses

- [ ] Data-driven enemy definitions, deterministic behavior and explicit intent state.
- [ ] Every enemy exposes two readable upcoming intents with target and numeric effect.
- [ ] Implement M1 target: 8 regular enemies, 3 elites and one three-phase boss.
- [ ] Implement board-changing attacks with legal target validation and preview parity.
- [ ] Expand to 36 regular enemies, 12 elites and 3 core bosses for the full catalogue.
- [ ] Add 3 optional Covenant bosses only if retained in the approved 1.0 scope.
- [ ] Boss phases must create distinct board problems and strategic adaptations, not inflated health bars.
- [ ] Test every intent, phase transition, status, lethal outcome and replay.
- [ ] Independent Mastery + Repetition.

### EFB-007 — Run map and progression

- [ ] Three acts with 15-row branching maps; validate node reachability and legal transitions.
- [ ] Implement fights, elites, bonfires, shops, treasure, events, rewards and node completion.
- [ ] Implement Cinders, card rewards, upgrades/removal, relics, Flask progression, Covenants and Ascension.
- [ ] Preserve approximately 30-minute run target without intrusive countdown/energy systems.
- [ ] Save after every node and every critical transition.
- [ ] Test route generation, dead ends, reward validity, economy bounds and complete-run paths.
- [ ] Independent Mastery + Repetition.

### EFB-008 — Death, Shade and narrative

- [ ] Persist the dead deck identity and required run record.
- [ ] Generate the bounded Shade encounter at the specified next-run location.
- [ ] Shade behavior must reflect the prior deck while remaining readable and bounded.
- [ ] Award Cinders and one Remnant according to accepted rules.
- [ ] Implement codex, NPC secrets, narrative flags and endings.
- [ ] Verify the Fast/true-ending requirements exactly; prevent accidental or repeatable completion.
- [ ] Test death/restart, save recovery, Shade generation and ending flags.
- [ ] Editorial/lore review plus independent Mastery.

### EFB-009 — Android app and combat UX

- [ ] Create the actual Android application shell; the accepted handoff baseline currently has no Android app.
- [ ] Keep simulation pure Kotlin/JVM; UI renders state and dispatches commands only.
- [ ] Build the combat screen first: torch-lit corridor, board/slate, hand/HUD, enemy tells and readable action feedback.
- [ ] Implement tap, long-press, drag/drop, target preview, undo, cancel, confirm-tap mode and End Turn.
- [ ] Implement navigation for map, reward, shop, bonfire, event, Shade, boss, death, settings and codex.
- [ ] Ensure no legacy SwapCells/match-3 route is reachable in live play.
- [ ] Screenshot fixtures for every key screen and state.
- [ ] Test 360×800, 390×844 and 412×915, safe areas, rotation lock and larger text.
- [ ] Device/emulator interaction tests and independent UX review.

### EFB-010 — Art and asset pipeline

- [ ] Approve one coherent art direction grounded in the existing visual baseline and real-world material references.
- [ ] Integrate assets through manifests with source, creator, license, hash, dimensions, scale and usage metadata.
- [ ] Build atlases, 9-slice frames, animation clips, environment layers and resolution variants.
- [ ] Preserve five distinct hue glyphs and neutral Ash/Stone/Prism silhouettes; never rely on color alone.
- [ ] Keep gameplay values, numbers and critical text live/localized, not baked into raster art.
- [ ] Add event-driven lighting/VFX tied to actual simulation events.
- [ ] Validate missing assets, licensing, atlas bounds, memory and fallback rendering.
- [ ] Art-direction review and independent Mastery.

### EFB-011 — Audio, lighting and haptics

- [ ] Hue-to-note mapping and pop voicing; adaptive ambient layers tied to board/run state.
- [ ] Card material sounds, enemy telegraphs, act ambience, boss transitions and UI feedback.
- [ ] Haptics mapped to meaningful events, with reduced-intensity and disable controls.
- [ ] Master/music/SFX/ambience/haptics settings persist.
- [ ] Measure audio onset, input latency and synchronization on physical Android devices.
- [ ] Ensure mute/reduced-motion/accessibility settings do not hide gameplay information.
- [ ] Audio mix review and independent Mastery.

### EFB-012 — Saves, migrations and replay

- [ ] Atomic versioned saves after every node and critical state transition.
- [ ] Include rules, content and RNG version identifiers, seed, class, options and action log.
- [ ] Crash recovery, interrupted-write recovery, backup/restore and corruption handling.
- [ ] Migration tests for every supported prior save schema.
- [ ] Same version + seed + actions must reproduce identical state hashes at every checkpoint.
- [ ] Incompatible replay versions must be rejected or routed to a compatible interpreter; never silently replay with changed rules/content.
- [ ] Test process death, low storage, app upgrade, interrupted save and repeated resume.
- [ ] R2 independent review and Mastery.

### EFB-013 — Balance laboratory

- [ ] Greedy and lookahead bot policies with reproducible seeds and versioned configurations.
- [ ] PR smoke sweeps plus formal 3,000 seeded runs per class/build.
- [ ] Track win rate, run length, turns, damage, Ink use, archetype pick rate, dead cards, Flask use, Overflow and boss phase outcomes.
- [ ] Test Monochrome, Spectrum, Demolition, Ash Warden and Vigil-burner archetypes.
- [ ] Exploit scans: infinite Ink, Echo loops, free Pops, unbounded Taps/Block, Wall cancellation, resource loops and Shade exploits.
- [ ] No single archetype above the specified 45% pick-rate target without an explicit Product Owner disposition.
- [ ] Publish before/after balance reports; all balance changes versioned.
- [ ] Independent balance review and Mastery.

### EFB-014 — Full launch content

- [ ] Deliver 360 cards (270 class, 70 neutral, 20 curses), or an explicitly approved cut list.
- [ ] Deliver 150 relics, 36 regular enemies, 12 elites, 3 core bosses, optional Covenant bosses, 50 events and 400 codex entries, or approved cuts.
- [ ] Validate unique IDs, schema, effect parameters, rarity/pool consistency, references and localization keys.
- [ ] Every card/effect has a golden behavior test; every enemy intent and boss phase has coverage.
- [ ] Editorial review for originality, clarity, tone and consistency; no copied protected text/art/names.
- [ ] Content completeness and localization reports are green.
- [ ] Independent content Mastery.

### EFB-015 — Optional backend

- [ ] Only begin after offline runs, saves and replay validation are stable.
- [ ] Daily seed distribution, leaderboard submission and replay validation only.
- [ ] No mandatory account; offline play must never depend on service availability.
- [ ] Rate limits, abuse controls, replay size caps, server resource limits and privacy documentation.
- [ ] Backend outage must not block local play or invalidate local saves.
- [ ] Security/privacy review and R2 Mastery.

### EFB-016 — QA, performance and release hardening

- [ ] Device matrix includes minimum supported API, current target API, low/mid-range and common screen sizes.
- [ ] Verify 60 fps target on a four-year-old mid-range device using frame-time percentiles, not average FPS alone.
- [ ] Measure startup/load time, memory, battery, thermal behavior, input latency and audio latency.
- [ ] Complete UI, integration, property, fuzz, screenshot, accessibility, migration and replay suites.
- [ ] No P0/P1 defects; no blocker bugs; no major placeholders in the primary flow.
- [ ] Offline first-run and complete offline run pass.
- [ ] Accessibility: content descriptions, focus order, scalable text, contrast, hue glyphs, reduced motion and haptic/audio controls.
- [ ] Store target API verified immediately before submission; source spec says API 35, while Product Owner baseline records API 36+ from 2026-08-31 policy. Reconfirm official Play policy at submission and update the ADR if needed.
- [ ] Privacy, data safety, crash reporting, telemetry consent, retention and support/restore processes approved.
- [ ] Trademark/title search and IP/license review complete; EMBERFALL remains a working title until cleared.
- [ ] Premium purchase configuration, restore flow, store listing, screenshots, icon and support materials verified.
- [ ] 99.5% crash-free target supported by pre-release test evidence and a monitored launch plan; no fabricated live reliability claim.
- [ ] Final release-candidate Mastery and Repetition accepted.
- [ ] Human Product Owner explicitly authorizes publication/submission.

## 5. Milestone exit gates

### M-1 — Source discovery and baseline
- [ ] Source inventory, provenance and license risks recorded.
- [ ] Reproducible build and pinned toolchain.
- [ ] Maestro v0.3.0 pinned and its tests/contracts pass.
- [ ] Architecture and ADR index current.

### M0 — Core prototype
- [ ] Group-pop board, sample 12-card deck, hue-weighted spawn, Ink, chip, Run and rising Wall.
- [ ] One enemy with visible intents.
- [ ] Headless simulation CLI and deterministic replay.
- [ ] At least five external human testers understand the deck-paints-board loop.
- [ ] Testers can complete a session without unexplained softlocks; board is enjoyable without full progression.

### M1 — Vertical slice
- [ ] Act 1 for one class at target art/audio quality.
- [ ] 60 cards, 25 relics, 8 enemies, 3 elites and one boss.
- [ ] Map, bonfire, shop, rewards and Shade.
- [ ] Complete Android combat UX.
- [ ] 60 fps on target device; complete 15–20 minute session without crashes.
- [ ] Board/deck interaction is understandable and repeatably fun.
- [ ] Save/reload works.

### M2 — Engine/platform gate
- [ ] Profile native architecture and renderer on real devices.
- [ ] Record frame-time, build complexity and renderer maturity.
- [ ] Assess iOS delivery estimate and shared-core cost.
- [ ] Product Owner approves measured stay-native or cross-platform decision; no port for fashion.

### M3 — Alpha
- [ ] All three acts and all three classes playable end-to-end.
- [ ] Economy, enemy and boss content substantially populated.
- [ ] Bot sweep and balance dashboard operational.
- [ ] Death/Shade and true-ending routes implemented.
- [ ] All acts completable; no blocker bugs; no major primary-flow placeholders.
- [ ] Deterministic save/replay stable.

### M4 — Beta
- [ ] Content complete or explicit approved cut list.
- [ ] Accessibility and localization pipeline pass.
- [ ] Daily seed/replay service only if approved.
- [ ] Device matrix, migrations, content validation and balance report pass.
- [ ] Zero P0/P1 defects.

### M5 — Gold / launch
- [ ] Release candidate built from a tagged, reproducible source SHA.
- [ ] Privacy/legal/IP/trademark and store-policy review complete.
- [ ] Premium purchase and restore tested.
- [ ] Offline run verified.
- [ ] Crash reporting policy, telemetry consent, support/restore process and monitoring ready.
- [ ] 99.5% crash-free target evidence and monitoring plan reviewed.
- [ ] Final independent Mastery + Repetition PASS.
- [ ] Explicit human publication approval.

## 6. Release-wide non-negotiable invariants

- Deck hue composition changes board spawn weights; the board is the energy engine, not a side puzzle.
- Every Tap/Pop has a meaningful resource and combat consequence; cards reshape the board.
- Every threat is readable two intents ahead; no untelegraphed damage.
- Death returns as a bounded Shade encounter; a failed run contributes to the world.
- The corridor, board, cards, light and sound belong to one torch-lit crypt.
- Five hues have distinct glyphs; Ash, Stone and Prism are distinguishable without color.
- Premium, no ads, no energy timers, no randomized paid rewards, no v1 meta-currency.
- Offline-first; optional backend cannot block local play.
- No live swap-match-3; no horizontal board sliding.
- No unbounded Echo/recursive effects; no hidden randomness; no nondeterministic state transitions.
- No scope cuts, rule changes or release claims without recorded Product Owner decision.

## 7. Current next actions — ordered

1. Obtain independent EFB-000 merged-tree review and exact evidence reconciliation.
2. Obtain independent EFB-002 Mastery review for SHA `3fcb5f2c0ba3ae68690f64342e686f06b8f76061`.
3. Resolve review findings and complete EFB-002 Repetition; accept EFB-002 only after both gates.
4. Ground and execute EFB-003 turn/combat in its own Maestro workflow.
5. Build EFB-004 card/effect engine, then classes/enemies, then the first playable M0 loop.
6. Deliver M1 vertical slice before scaling content or polishing secondary screens.
7. Progress through M2–M5 with the exit gates above; keep this checklist updated from evidence, not optimism.

**Current release decision: HOLD — no release candidate exists.**