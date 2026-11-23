# ADR-014 — Unlit Starter Deck

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-005 content implementation  
**Source:** Sections 3–4, 6; existing `GameCatalog.starterDeckIds(THE_UNLIT)` and its ADR-014 comment.

## Decision

The Unlit starts with exactly 12 Gold-frame cards: 4 Gold Strike, 4 Ash Ward, 2 Pop Shot, 1 Sift and 1 Hollow Crown **card** (`unlit_hollow_crown`). Ash Ward is Gold-framed but paints an Ash tile; it contributes Gold to board spawn weighting because card frame hue—not its effect payload—determines the deck hue weight. Dread is a Violet curse and contributes Violet.

The Unlit also starts with the separate signature **Hollow Crown relic** (`relic_hollow_crown`), which grants +1 Spark for every Ash block popped. The starter card and signature relic are separate content entities with separate IDs and effects, despite currently sharing the display name. Do not silently remove either or replace the starter card with Ash Alchemy.

## Rationale and baseline adherence

The exact 12-card multiset is explicitly established in the implementation catalog and tests. The card/relic distinction is a data-model distinction: the starter card creates Ash and grants Guard/Spark; the relic pays Spark when Ash is popped. Both are part of the existing Unlit identity and must be preserved pending a separate content naming review.

## Acceptance tests

- Deck count exactly 12 and exact ID multiset: 4 `unlit_gold_strike`, 4 `unlit_ash_ward`, 2 `unlit_pop_shot`, 1 `unlit_sift`, 1 `unlit_hollow_crown`.
- Every starter card has Gold `frameHue`; Ash Ward paints Ash.
- The separate `relic_hollow_crown` is equipped at run start and pays +1 Spark per Ash block popped.
- Card and relic IDs, categories, tooltips and save serialization remain distinct.
- Dread contributes Violet; no sixth playable hue is introduced.

## Change control

The same-display-name collision is recorded in `engineering/CONTENT_REVIEW_QUEUE.md`; only its presentation/name may be revised after specialist proposal and Product Owner acceptance. The 12-card composition and separate card/relic effects are stable. Reopen only for reproducible defect or explicit Product Owner change.
