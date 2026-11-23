# Product Owner Decision — Clean Core Baseline / Legacy Exclusion

**Recorded:** 2026-11-23  
**Authority:** Product Owner  
**Applies from:** EFB-003 onward

## Decision

EMBERFALL proceeds from the accepted, deterministic pure Kotlin/JVM EFB-002 board core at `6b6e139890a205ead337a94226464a0b5ddd82da`. The old legacy prototype and its implementation artifacts are excluded from the product baseline.

Do not copy, port, adapt, link, package, or depend on legacy prototype source, binaries, assets, libraries, or undocumented behavior. Do not spend further implementation time attempting to clear rights for material that will not be used. The legacy prototype may remain only as a quarantined historical archive outside the clean product source tree; it is not a build input, runtime dependency, design authority, or source of gameplay rules.

## Consequences

1. The accepted EFB-002 board core and the v0.2 specification plus resolved ADRs are the only gameplay implementation baseline.
2. EFB-000 historical provenance/review work is retained for audit, but legacy-source ownership/reuse clearance is no longer a prerequisite for new clean-room implementation.
3. No legacy code or assets are imported. All new code is independently implemented against the accepted contracts.
4. EFB-003 implementation is authorized to proceed on an isolated branch from the exact accepted EFB-002 SHA.
5. Maestro cross-run continuity remains a workflow reliability improvement, not a reason to halt bounded source implementation. Exact-SHA tests, an independent review and Repetition remain required before acceptance/merge.
6. Existing accepted ADRs and Product Owner decisions remain binding. This decision does not reopen them.

## Baseline integrity

- Accepted EFB-002 SHA: `6b6e139890a205ead337a94226464a0b5ddd82da`.
- EFB-003 branch: `feat/efb003-turn-combat-clean`.
- EFB-003 PR: #15, stacked on the accepted EFB-002 candidate.
- No merge, release, or legacy reuse permission is implied.
