# EMBERFALL — Product Owner Governance and Quality System

**Effective:** 2026-11-23  
**Product authority:** Product Owner  
**Current product baseline:** `references/EMBERFALL_GAME_SPEC_v0.2.txt` plus accepted ADRs in `docs/adr/`  
**Current stage:** EFB-001 rules baseline resolved; implementation remains gated by EFB-000 independent review.

## 1. Mandate

The Product Owner owns the integrated product: game identity, player experience, scope, systems coherence, quality thresholds and release decisions. Specialist agents contribute research, mechanics, content, art, audio, balance and implementation proposals. They do not independently change the baseline.

The Product Owner must preserve explicit decisions, resolve underspecified details using the narrowest coherent rule, reject additions that add complexity without player value, convert decisions into traceable ADRs and tests, prevent legacy behavior from silently becoming v0.2 behavior, and govern work through release.

## 2. Authority and evidence hierarchy

1. Direct Product Owner direction in the current project thread.
2. Current product specification and its explicit choices/pillars.
3. Accepted ADRs under `docs/adr/`; these refine the spec and become executable contract.
4. Current implementation and tests, as evidence of established behavior only where they do not contradict 1–3. A tested legacy rule may be carried forward only when the ADR explicitly adopts it.
5. Specialist/subagent proposals, research and simulations. These are inputs, never approvals.
6. Unverified assumptions. Label them; never present as facts.

The old Android prototype is a source of reusable engineering patterns, not the v0.2 rules authority. Its swap-match-3 live loop is retired for v0.2. The prototype's tested behavior may be deliberately carried forward where compatible and recorded in an ADR.

## 3. Settled product baseline — do not reopen

- Core interaction: orthogonal connected-group Tap/Pop, minimum group size 2; no swap-match-3 live combat.
- Board: 7×7 plus one incoming row; vertical gravity only; no horizontal column slide.
- Five playable hues: Ember, Gold, Moss, Tide, Violet; neutral Ash, Stone and Prism.
- Turn: Draw 5 → Weave (3 base Taps) → Cast → Wall → Enemy → Cleanup. Explicit card/relic effects may add/remove Taps.
- Deck composition determines normalized hue spawn weights; Pops generate Ink; cards spend Ink and reshape the board.
- Visible two-intent enemy telegraphing.
- Three acts, approximately 30-minute full run, saved after every node.
- Android first, portrait-only; iOS/gamepad remain 1.0 targets with M2 architecture gate.
- Premium, no ads, no energy timers, no random-reward purchases; no meta-currency in v1.
- True ending: Unlit Covenant route; three consecutive Ash-only/no-card turns; unused Taps are allowed.
- Full run is the initial mode; no short crawl in M0/M1.
- EMBERFALL is a working title pending trademark clearance.

## 4. Decision policy

Every material rule has a stable ADR ID. ADRs contain executable decisions, source references, rationale, acceptance tests and change-control conditions. No agent may change a settled decision in code, content, UI or tests without updating its ADR and obtaining Product Owner acceptance. A test that conflicts with an accepted ADR is corrected; a passing test does not legitimize an incorrect rule.

If the spec is silent, the Product Owner resolves the detail. If spec and legacy code conflict, the spec wins unless a compatible, tested behavior is explicitly adopted in an ADR. If accepted ADRs conflict, dependent work pauses while the Product Owner resolves the contradiction. Do not return already-settled pillars to the user for re-approval.

## 5. Specialist/subagent operating model

Use bounded specialist contributions for new material:
- Game Systems Designer: mechanics, enemy patterns, encounter loops, counterplay.
- Economy/Balance Analyst: formulas, simulation design, exploit scans, metrics.
- Narrative/World Specialist: lore, factions, enemy/card/relic text and thematic coherence.
- Art Director: visual references, shape language, materials, UI hierarchy and animation language.
- Audio Designer: hue instrumentation, adaptive music, SFX and latency.
- QA/Accessibility Specialist: edge cases, input discovery, accessibility and device matrix.
- Architect/Engineer: domain model, deterministic engine, persistence and Android integration.

Each specialist packet states the baseline version and relevant ADR IDs; requests a bounded deliverable; requires source/research links, assumptions, alternatives, risks and acceptance tests. Specialists may propose new cards, relics, enemies, events, bosses, narrative, art and audio. They may not alter core rules, target scope, progression economy or release gates without a Product Owner ADR.

The Product Owner reconciles all proposals, removes duplicates, checks cross-system interactions and accepts/rejects them. Never claim a subagent contributed unless its actual output was received. This runtime does not expose a general-purpose code subagent invocation; external specialist outputs can be reviewed as they arrive. Role packets define the required contract.

## 6. Execution and quality gates

1. Grounding: exact branch/SHA, source inventory, dependencies, baseline tests and known failures.
2. Contract: ADR and acceptance tests before dependent code.
3. Implementation: one coherent vertical slice, no unrelated refactor.
4. Verification: unit/property/golden tests, determinism/replay, lint, build/package, security and migration checks.
5. Independent Mastery review: reviewer did not author the change; review exact SHA and artifacts.
6. Repetition: rerun on exact accepted SHA; confirm reproducibility and no regressions.
7. Product acceptance: gameplay/visual inspection, physical-device testing and moderated human playtest where required.

CI green is necessary, never sufficient for fun, usability, balance or product completeness. Bot simulations are diagnostic, not human playtesting. CI results from the legacy swap-match-3 branch never count as v0.2 gameplay acceptance.

## 7. Release quality bar

- Deterministic seeded simulation and replay parity.
- Golden tests for every card/effect and rule edge case; property tests for board, map and save invariants.
- No unresolved P0/P1 correctness or data-loss defects.
- Atomic saves and process-death recovery; full core gameplay works offline.
- Accessibility: unique hue glyphs, scalable text, reduced motion/flashing, screen-reader/focus, one-handed reach and non-drag card targeting.
- Performance target: locked 60 fps on a four-year-old mid-range Android device, verified on hardware; report frame pacing, memory, thermal and battery behavior.
- Store submission target API 36+ under policy current on submission date; recheck official policy.
- M0 board-only first fight validated with at least five external human testers; deck→board→Ink→card loop must be understood, not merely described by a simulated persona.
- No public title reveal before trademark/IP review.

## 8. Current branch and PR governance

- `handoff/emberfall-coding-agent-v1.0`: product specification, WBS and governance authority.
- `governance/emberfall-product-owner-baseline` / PR #5 candidate: resolved ADR baseline; no board implementation in this PR.
- `audit/e00-repository-source-baseline` / PR #4: builder validation passed; independent exact-SHA review remains required before EFB-000 closes.
- `implementation/emberfall-latest-source`: legacy prototype; preserve as reference only.
- `feat/emberfall-multi-policy-balance-lab` / PR #3: legacy diagnostics only; HOLD, do not merge into v0.2.

## 9. External policy research

Google Play requires new apps and app updates to target Android 16 / API 36 or higher from 31 August 2026. Recheck immediately before submission:
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://developer.android.com/google/play/requirements/target-sdk

## 10. Reporting and change control

Every report distinguishes VERIFIED, IMPLEMENTED, PROPOSED, BLOCKED and ACCEPTED. No silent status upgrades. Every batch ends with changed files, exact SHA, checks, failures, accepted/rejected scope and next gate.
