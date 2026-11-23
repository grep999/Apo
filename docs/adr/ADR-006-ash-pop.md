# ADR-006 — Ash Pop

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 3–4, 6.

## Decision

Any orthogonally connected Ash group of at least two blocks can be popped. It deals `n-1` chip damage after enemy Block and pays zero Ink. Hollow Crown adds its separately defined Spark payout per Ash block; it does not change base Ink payout.

## Rationale and baseline adherence

The spec explicitly states Ash pops in groups, pays no Ink, and that pop chip damage is n−1. The relic's Spark is an explicit exception, not a change to Ash's base economy.

## Acceptance tests

Golden groups of size 2, 3 and 8; zero Ink; chip equals n−1 after Block; Hollow Crown Spark count; Brand interaction; no Ash group below size 2.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
