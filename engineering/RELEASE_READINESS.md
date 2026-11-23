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
| EFB-000 — source baseline | **HISTORICAL ARCHIVE — legacy excluded by Product Owner** | PR #4 remains recorded as merged; validator run 45265389152 pins the historical handoff and prototype snapshots and reports no license files. Product Owner has now explicitly excluded the legacy prototype and all associated source/assets from the clean product baseline. | Retain EFB-000 provenance and validator evidence for audit only. No rights clearance is sought for excluded material; no legacy source/assets may enter the product. EFB-000 is not a dependency of clean-room EFB-003. |
| EFB-001 — rules/ADR freeze | **PARTIAL — merged, validator only** | PR #5 merged as `f9a41aa373d36d41a0c134c8005fb5984fecd2b3`; Product Owner decision baseline and resolved blocking rules ADRs are present; exact-head ADR validation run 45180407668 passed | No EFB-001 Maestro workflow artifact or submitted GitHub review exists. Preserve accepted decisions; reconcile the review/evidence record without silently reopening rules. |
| EFB-002 — board core | **ACCEPTED BY PRODUCT OWNER** | PR #14 candidate `6b6e139890a205ead337a94226464a0b5ddd82da`; exact-head Gradle/JUnit, pure-JVM and ADR validation pass; half-Ink payout correction and independent hue-weight golden tests are included. Product Owner accepted after the single requested feedback round and waived another independent Mastery round. Repetition is recorded. | PR remains draft. This is Product Owner acceptance, not an independent Mastery submission. The separate EFB-000 legacy-rights question is retired by explicit exclusion; Maestro continuity remains a governance improvement, not a code gate. |
| EFB-003 — turn/combat | **IN PROGRESS — clean-core candidate PR #15** | Branch `feat/efb003-turn-combat-clean`, candidate code SHA `fbb8b07c134b77825cedac3f65389be13f11fd15`; deterministic turn/combat reducer, player resources, two-intent forecast, ten intent contracts, Wall/Seal integration, Card Pop seam and versioned replay added. Legacy prototype excluded. | Exact-head Gradle/JUnit and pure-JVM checks, independent Mastery and Repetition remain pending. Earlier CI found two issues which were corrected in later candidate revisions. |
| EFB-004 through EFB-016 | **NOT STARTED / dependency blocked** | WBS and task definitions exist | No implementation or acceptance evidence yet. |

### Exact EFB-002 evidence

- Current candidate: PR #14, `6b6e139890a205ead337a94226464a0b5ddd82da`; original remediation commit `6932b747d78f9310ecb859cc9079cb2fc28ac9bf` is preserved in ancestry.
- [Board-core Gradle/JUnit workflow](https://github.com/grep999/Apo/actions/runs/45265105730) — PASS.
- [Maestro lifecycle/GateRunner workflow](https://github.com/grep999/Apo/actions/runs/45265105467) — PASS.
- [EFB-001 ADR validator](https://github.com/grep999/Apo/actions/runs/45265105426) — PASS.
- Maestro workflow snapshot: `18096733-d9eb-4ce6-bac5-c3c77a947e60`; stage `MASTERY`; status `ACTIVE`; revision 5. Three Momentum items complete; review assignment `9cb25ee3-3a1e-43fd-99e4-3eb9fde2a81f` remains `PROPOSED`, not assigned.
- Evidence artifact: `emberfall-efb-002-maestro-evidence`, artifact ID `14861514908`, SHA-256 `02d107084f1ea96a750e378d557224af8556c5534eca2c0a202263fd02043f00`.
- Required exact-SHA checks: Gradle/JUnit `VERIFIED`, exit 0, log SHA-256 `568e0a0033596669f33015617774f4f7f5d7d607c6f204345991ad57971f28fa`; pure-JVM boundary `VERIFIED`, exit 0, log SHA-256 `ce4db7edbc0b3f58a04e8a857be2076d906aea037be04da98da8f4d452d6c2c3`.
- The Product Owner pre-review found integer truncation of half-Ink before Spectrum; this is fixed using exact half-units and final half-up rounding. Golden tests now cover 9/11/13-cell payouts and hue-weight formula values.
- These checks prove the pure Kotlin/JVM board module only. They do not prove Android compilation, gameplay usability, device performance or release readiness.

## 3. Maestro workflow integrity — must fix before scaling

The current artifacts expose a process gap, not merely a code gap:

- **EFB-000:** native Maestro workflow remains ACTIVE / GROUNDING at revision 1, but its source/rights follow-up is now historical audit only. Product Owner excluded the legacy prototype and all associated assets; no reuse clearance is sought and this is not an EFB-003 dependency.
- **EFB-001:** the ADR validator workflow passed, but there is no native EFB-001 Maestro workflow artifact. Its static task remains INBOX in the seed task register.
- **EFB-002:** Product Owner accepted exact candidate `6b6e139890a205ead337a94226464a0b5ddd82da` after one feedback round and explicitly waived another independent Mastery round. The proposed assignment was not dispatched; this acceptance is not represented as independent review. Repetition is recorded. EFB-003 is now underway from this exact base.
- **EFB-003:** clean-room candidate PR #15 is based on accepted EFB-002 SHA `6b6e139890a205ead337a94226464a0b5ddd82da`. Prior candidate `8dd0a03ceae047a72d783eeee81df037952ac18c` passed exact workflow run 45273205685 (Gradle/JUnit and pure-JVM boundary); final code candidate `fbb8b07c134b77825cedac3f65389be13f11fd15` is queued in run 45273418357. Exact-SHA CI is verified. Independent Mastery and Repetition remain outstanding.
- **State continuity:** the EFB-002 GitHub Actions workflow currently invokes `coding-start` on every push. That creates a new Maestro workflow/database per run rather than resuming one durable workflow for the increment. The latest artifact proves one run, not a persistent multi-commit lifecycle.

Required control-plane fixes:
- [x] Product Owner excluded the legacy prototype and all associated source/assets from the clean product baseline. No legacy code/assets may be copied or redistributed; rights clearance is not pursued for excluded material. Preserve EFB-000 validator evidence for historical audit.
- [ ] Reconcile EFB-001's merged rules increment into Maestro state; do not change accepted ADR content.
- [ ] Persist/retrieve the same Maestro database and workflow ID across CI runs, or use a deliberate one-run-per-candidate policy with explicit cycle linkage. Do not silently create unrelated workflow IDs on every push.
- [ ] Configure an actual independent-review adapter and prove assignment/dispatch is observable. `PROPOSED` is not `ASSIGNED`, and an echo adapter is not an independent reviewer.
- [ ] Require exact candidate SHA, changed-path hashes, VERIFIED GateRunner evidence and reviewer identity inequality before Mastery PASS.
- [ ] Export the final state DB, event log, assignments, review attestation and Repetition packet as durable artifacts.

EFB-003 clean-room implementation is underway on PR #15 from accepted EFB-002 SHA `6b6e139890a205ead337a94226464a0b5ddd82da`. Do not merge or accept it until exact-SHA tests, independent Mastery and Repetition pass. Maestro continuity/reviewer-dispatch improvements remain governance work, not a source implementation blocker.

## 4. Hard gates before feature implementation can proceed

Review dossiers are prepared under `maestro/reviews_v03/`: EFB-000 merged tree, EFB-001 resolved rules and EFB-002 exact implementation candidate. They identify the precise commits, artifacts and review scope.

- [ ] Independent EFB-001 rules review using `maestro/reviews_v03/EFB-001-RULES-ADR.review-input.json` (recommended governance reconciliation; do not reopen accepted decisions without evidence).
- [x] Legacy prototype/source/assets explicitly excluded by Product Owner; no EFB-000 license/ownership clearance required for excluded material. Retain merged-tree provenance dossier as a historical record only.
- [x] EFB-002 Product Owner acceptance of exact candidate `6b6e139890a205ead337a94226464a0b5ddd82da`; further independent Mastery round explicitly waived by Product Owner and recorded in PR #14.
- [ ] Reviewer identity must be different from the builder. A proposed assignment or unsubmitted request is not a review.
- [ ] Resolve every review finding: fix it, or record an explicit Product Owner disposition with rationale and risk owner. Unresolved blocking findings prevent acceptance.
- [x] Record EFB-002 Repetition: expected vs actual, durable lessons, accepted state, next increment and evidence references (`maestro/packets_v03/EFB-002-BOARD-CORE.repetition.json`).
- [x] EFB-003 clean-core implementation authorized by Product Owner. Exact-SHA CI, independent Mastery and Repetition remain required before EFB-003 acceptance.

## 5. Production work breakdown — required before v1.0

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

## 6. Milestone exit gates

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

## 7. Release-wide non-negotiable invariants

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

## 8. Current next actions — ordered

1. Complete EFB-003 exact-candidate Gradle/JUnit and pure-JVM verification; fix all findings.
2. Obtain independent Mastery review of exact EFB-003 candidate SHA and complete Repetition.
3. Start EFB-004 card/effect engine on the accepted combat API. Continue Maestro state continuity and reviewer-dispatch improvements as parallel governance work.
7. Build EFB-004 card/effect engine, then classes/enemies, then the first playable M0 loop.
8. Deliver M1 vertical slice before scaling content or polishing secondary screens.
9. Progress through M2–M5 with the exit gates above; keep this checklist updated from evidence, not optimism.

**Current release decision: HOLD — no release candidate exists.**