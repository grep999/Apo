# ADR-002 — Initial Board And Generation

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 2–3, 14. Existing prototype source inventory records the 35-cell opening as a reusable initialization pattern.

## Decision

Use the existing tested 7×7 initialization pattern as the v0.2 starting topology: rows 0–1 begin empty, rows 2–6 are seeded (35 visible cells), plus one hidden incoming row. Each seeded cell is sampled independently from normalized deck hue weights and neutral-block rules using the run RNG. Do not hand-place patterns or target a group-size histogram. If no legal orthogonal group of size ≥2 exists, retry the entire board up to 32 times on the same deterministic RNG stream; after that, create one adjacent pair using the same hue distribution and emit `BoardGenerationFallback`.

## Rationale and baseline adherence

This carries forward the already-established 35-cell opening topology without carrying forward swap-match-3. Independent sampling protects the deck-paints-board pillar; a rare explicit fallback avoids an unplayable opening without hidden local repairs.

## Acceptance tests

Golden seeded boards; 100,000-seed hue-distribution check; every generated board has a legal group; fallback rate is measured and <0.01%; same seed/deck/rules version produces identical board bytes.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
