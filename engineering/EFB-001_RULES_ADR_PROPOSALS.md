# EFB-001 — Product Owner Rules ADR Resolution Package

**Status:** PRODUCT OWNER RESOLVED — implementation remains gated by EFB-000 independent review  
**Authority:** `references/EMBERFALL_GAME_SPEC_v0.2.txt`, explicit product-owner delegation, accepted ADRs under `docs/adr/`  
**Purpose:** Close the 19 OPEN/BLOCKING rules decisions with executable contracts and acceptance tests.

## Resolution

All 19 blocking decisions have been resolved in individual ADRs. ADR-020 (Google Play target API), ADR-022 (five hues) and ADR-023 (full run) are also recorded. ADR-021 remains an open, non-blocking legal/name task; EMBERFALL is internal working title only.

The resolution preserves the established product direction:
- Connected-group Tap/Pop is canonical. Swap-match-3 is legacy-only.
- Five playable hues, three acts, approximately 30-minute full run, Android-first portrait delivery.
- Draw → Weave (3 base Taps) → Cast → Wall → Enemy → Cleanup.
- Deck-driven hue spawn, Ink economy, vertical-only gravity and visible two-intent enemy telegraphing.
- True ending requires The Unlit class plus the Unlit Covenant, and three consecutive turns with ≥1 Ash Pop, no non-Ash Pop, no card, no Vigil spend, no Flask and no Overflow. All three Taps need not be spent.

## Governance reconciliations

- The proposal's original ADR-012 recommendation to allow gravity and Wall movement through Seal was rejected. Existing engine contract and tests establish a frozen-column behavior; see ADR-012.
- The original ADR-017 requirement to spend all three Taps per turn was rejected as stricter than the source. The existing class restriction and no-Vigil/no-Flask/no-Overflow checks are preserved. See ADR-017.
- The Unlit starter deck preserves the established 12-card Gold-frame list including a Hollow Crown card, plus the separate Hollow Crown signature relic; see ADR-014.
- Existing tested Overflow penalty of one Flask is explicitly carried forward alongside the v0.2 8-Vigil/two-row/skip-rise rule; see ADR-008.
- The existing 35-cell opening topology is retained while replacing the legacy swap-match-3 interaction; see ADR-002.

## Implementation authorization boundary

This package closes decision ambiguity; it does not close EFB-000. EFB-002 board-core implementation starts only after the independent EFB-000 review accepts the exact baseline SHA. The implementation must follow the ADRs and add golden/property tests before card breadth or full content.

## Specialist contributions

New content and system proposals are to be authored by bounded specialist roles under `engineering/PRODUCT_OWNER_GOVERNANCE.md`. Proposals are not accepted until Product Owner review reconciles them against the ADR set. No simulated or unreceived subagent work is represented as evidence.
