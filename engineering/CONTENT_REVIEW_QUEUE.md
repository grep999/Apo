# EMBERFALL — Content Review Queue

**Owner:** Product Owner  
**Baseline:** Game Specification v0.2 + accepted ADRs  
**Rule:** Specialist agents propose; Product Owner integrates. Do not alter the core rules to solve content gaps.

## CR-001 — The Unlit Hollow Crown card/relic name collision

**Verified source:** `GameCatalog.kt` defines starter card `unlit_hollow_crown` and signature relic `relic_hollow_crown`, both displayed as “Hollow Crown”. They are distinct entities and effects.

**Current disposition:** Preserve both IDs and effects. Do not remove the starter card or signature relic. Ask the Narrative/Content Specialist for 3–5 naming/presentation alternatives that preserve the established mechanical identity. Include UI differentiation, lore rationale and localization considerations. No rename until Product Owner approval.

## CR-002 — Ashen Monk and Quarry Witch starter deck count

**Verified source divergence:** The legacy catalog currently defines 10-card Monk and Witch starters, while the v0.2 spec requires 12 cards for every class: 4 Strikes in the first hue, 4 Wards in the second, Pop Shot ×2, Sift and one signature card.

**Current disposition:** v0.2 spec wins. The target implementation must contain 12-card starters. Before EFB-005, the Game Systems Specialist must propose the missing card slots/effects for Monk and Witch, with costs, hue frames, board interactions, counterplay, upgrade hooks and golden-test cases. Do not copy legacy balance numbers without a source-backed rationale.

## CR-003 — The Unlit true-ending eligibility

**Verified source:** Legacy `GameEngine` and `DECISION_LOG.md` require both `THE_UNLIT` and the Unlit Covenant; the v0.2 spec identifies the Covenant as the route unlock. ADR-017 preserves the established class + Covenant contract and clarifies turn qualification.

**Disposition:** No open product decision. Implement ADR-017 exactly; do not silently broaden eligibility to other classes.

## CR-004 — v0.2 content scale versus legacy catalog

Legacy content counts are not launch targets. v0.2 targets 360 cards, 150 relics, 36 regular enemies, 12 elites, 3 core bosses, 50 events and 400 codex entries. The first vertical slice is intentionally much smaller. Content specialists must produce coherent, testable slices rather than bulk filler.

## Specialist deliverable format

Every proposal must include: ADR compatibility; player fantasy; mechanical role; cost/rarity or encounter tier; effect text; interaction matrix; exploit risks; accessibility/readability; narrative hook; art/audio brief; acceptance tests; rejected alternatives; provenance for any external research.
