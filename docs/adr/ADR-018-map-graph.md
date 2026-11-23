# ADR-018 — Map Graph

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 7, 14.

## Decision

Each act map has 15 rows: row 0 is the entrance with no encounter, rows 1–13 contain 13 non-boss encounter nodes, row 14 is the boss. Baseline graph budget: 6 normal fights, 1 elite, 2 bonfires, 1 shop, 2 events, 1 treasure and 1 boss (14 encounter nodes). Higher-risk variants may replace one normal fight with a second elite; they do not add a mandatory encounter. Every reachable path leads to the boss and route generation must preserve access to required service nodes.

## Rationale and baseline adherence

This reconciles the stated 15-row map with the listed encounter mix and prevents accidental scope inflation.

## Acceptance tests

Property tests across 100,000 seeds; every route reaches boss; exact graph node budget; required bonfire/shop/event/treasure availability; second elite replaces normal; no dead ends or unreachable mandatory nodes.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
