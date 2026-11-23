# EMBERFALL — Product Decision Register

**Updated:** 2026-11-23  
**Owner:** Product Owner  
**Status meanings:** RESOLVED = accepted ADR exists; OPEN = non-blocking follow-up; BLOCKING = dependent implementation must not proceed.

| ID | Decision | Product Owner disposition | Status |
|---|---|---|---|
| ADR-001 | Turn phases | Draw → Weave (3 base Taps) → Cast → Wall → Enemy → Cleanup | RESOLVED — [ADR-001](../docs/adr/ADR-001-turn-phases.md) |
| ADR-002 | Initial board fill and topology | 35-cell seeded opening; deck-weighted sampling; bounded deterministic legal-pop fallback | RESOLVED — [ADR-002](../docs/adr/ADR-002-initial-board-and-generation.md) |
| ADR-003 | Hue-weight formula | Spec formula; explicit card frame hue; Dread Violet; five normalized hues | RESOLVED — [ADR-003](../docs/adr/ADR-003-hue-weighting.md) |
| ADR-004 | Prism group interpretation | Single base-hue group; deterministic direct-Prism tie break; no cross-hue bridge | RESOLVED — [ADR-004](../docs/adr/ADR-004-prism-resolution.md) |
| ADR-005 | Spectrum Run | First pop ×1; next hue increments; out-of-order resets and re-anchors | RESOLVED — [ADR-005](../docs/adr/ADR-005-spectrum-run.md) |
| ADR-006 | Ash chip damage | Ash n−1 chip, zero base Ink; Hollow Crown Spark separate | RESOLVED — [ADR-006](../docs/adr/ADR-006-ash-pop.md) |
| ADR-007 | Stone break order | Pre-removal snapshot, one deterministic batch, one gravity pass | RESOLVED — [ADR-007](../docs/adr/ADR-007-stone-break-order.md) |
| ADR-008 | Wall and Overflow | Row orientation fixed; 8 Vigil, top-two-row burn, one Flask, skip rise, preserve incoming row | RESOLVED — [ADR-008](../docs/adr/ADR-008-wall-and-overflow.md) |
| ADR-009 | Spark and generic costs | Spark only pays generic/colorless; explicit source preview; deterministic auto-pay | RESOLVED — [ADR-009](../docs/adr/ADR-009-spark-payment.md) |
| ADR-010 | Brand penalty | 2 Vigil per branded block popped; shortfall HP; preview required | RESOLVED — [ADR-010](../docs/adr/ADR-010-brand-cost.md) |
| ADR-011 | Fog and glyphs | 3 Tap actions; hue/Ink concealment; glyph and selection cues preserved | RESOLVED — [ADR-011](../docs/adr/ADR-011-fog-and-glyphs.md) |
| ADR-012 | Seal behavior | Frozen column for three complete turns; player effects, gravity and Wall insertion blocked | RESOLVED — [ADR-012](../docs/adr/ADR-012-seal-column.md) |
| ADR-013 | Enemy Vigor and Block | 36×1.11^fightIndex; elite/boss multipliers; integer rounding and Block lifetime | RESOLVED — [ADR-013](../docs/adr/ADR-013-enemy-vigor-and-block.md) |
| ADR-014 | Unlit starter deck | 4 Gold Strike, 4 Gold-framed Ash Ward, 2 Pop Shot, 1 Sift, 1 Hollow Crown card; separate Hollow Crown signature relic | RESOLVED — [ADR-014](../docs/adr/ADR-014-unlit-starter-deck.md) |
| ADR-015 | Effect queue and Echo | Deterministic bounded FIFO; one top-level Echo; transactional overflow | RESOLVED — [ADR-015](../docs/adr/ADR-015-effect-queue-and-echo.md) |
| ADR-016 | Shade inheritance | One next-run Shade after death; bounded profile; Cinders + one Remnant; no stacking | RESOLVED — [ADR-016](../docs/adr/ADR-016-shade-inheritance.md) |
| ADR-017 | Fast true ending | The Unlit class + Unlit Covenant; three turns with ≥1 Ash Pop, no non-Ash Pop/card/Vigil/Flask/Overflow; unused Taps allowed | RESOLVED — [ADR-017](../docs/adr/ADR-017-fast-true-ending.md) |
| ADR-018 | Map graph | 15 rows; 14 encounters; second elite replaces normal; all paths reach boss | RESOLVED — [ADR-018](../docs/adr/ADR-018-map-graph.md) |
| ADR-019 | iOS at 1.0 | Android first; iOS/gamepad target retained; M2 measured architecture gate | RESOLVED — [ADR-019](../docs/adr/ADR-019-ios-and-engine-path.md) |
| ADR-020 | Google Play target SDK | API 36+ for new app/update submissions as of 2026-08-31; recheck at release | RESOLVED — [ADR-020](../docs/adr/ADR-020-google-play-target-api.md) |
| ADR-021 | Final title/trademark | EMBERFALL remains internal working title pending trademark/IP search | OPEN — non-blocking; no public reveal |
| ADR-022 | Four or five hues | Preserve all five playable hues; teach progressively | RESOLVED — [ADR-022](../docs/adr/ADR-022-five-playable-hues.md) |
| ADR-023 | Full run vs short crawl | Full ~30-minute run; no short mode before post-launch evidence | RESOLVED — [ADR-023](../docs/adr/ADR-023-full-run-scope.md) |

## Implementation gate

EFB-001 product decisions are resolved in this candidate. EFB-002 board implementation remains gated on EFB-000 independent review and acceptance. No decision in this register authorizes importing the legacy swap-match-3 loop.
