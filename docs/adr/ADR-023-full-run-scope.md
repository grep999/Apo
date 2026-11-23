# ADR-023 — Full Run Scope

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 1, 7, 16, 19.

## Decision

The primary run is the full approximately 30-minute, three-act run. Do not add a short-crawl mode, energy timer or meta-currency to M0/M1. Consider a shorter mode only after post-launch retention and completion data demonstrate a concrete need.

## Rationale and baseline adherence

The full run is a core roguelike pacing target. A short mode before the core loop is validated would split scope and obscure whether the main experience works.

## Acceptance tests

Run-length telemetry and moderated completion tests; no short mode in M0/M1; no meta-currency or energy gating; revisit only with post-launch evidence.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
