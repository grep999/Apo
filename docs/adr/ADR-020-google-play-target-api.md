# ADR-020 — Google Play Target Api

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Google Play policy, checked 2026-11-23.

## Decision

For a new Google Play app or app update submitted from 31 August 2026 onward, target Android 16 / API 36 or higher. Recheck the official Play policy immediately before submission; do not hard-code a permanent future target number into the product spec. Keep minSdk and targetSdk as separate decisions.

## Rationale and baseline adherence

Current official Google Play policy requires API 36+ for new apps and updates as of 31 August 2026. This supersedes the spec's older target 35 for store submission, without changing the minimum supported Android version.

## Acceptance tests

CI asserts targetSdk meets the policy snapshot used for the release; release checklist rechecks official policy and behavior changes; minSdk compatibility matrix remains independently tested.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
