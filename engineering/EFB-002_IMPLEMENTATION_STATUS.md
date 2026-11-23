# EFB-002 — Board Core

**Disposition:** ACCEPTED BY PRODUCT OWNER  
**Accepted candidate:** `6b6e139890a205ead337a94226464a0b5ddd82da`  
**PR:** #14, draft; acceptance does not itself authorize merge.

## Accepted scope

Pure Kotlin/JVM deterministic 7×7 board core: immutable board state, orthogonal Tap/Pop, Prism/Ash/Stone behavior, vertical-only gravity, Seal, Wall/Overflow, Spectrum, deck-hue weighting, deterministic RNG streams, replay and state digest.

## Product Owner review disposition

The Product Owner reviewed the remediation feedback, confirmed that one review round was sufficient, accepted the corrected candidate and waived any further EFB-002 independent Mastery round. This owner disposition is recorded at https://github.com/grep999/Apo/pull/14#issuecomment-7114277343.

## Exact-head verification

All three runs passed on the accepted SHA:
- Board Core Gradle/JUnit: run 45265105730.
- Maestro GateRunner: run 45265105467.
- EFB-001 ADR validator: run 45265105426.

The half-Ink precision issue is corrected by retaining integer half-units through Spectrum and rounding once, half-up. Hue-weight tests use independent golden expected values.

## Next increment

EFB-003 is implemented clean-room on top of this core. The old legacy prototype and its assets are explicitly excluded; see `engineering/PRODUCT_OWNER_DECISION_CLEAN_CORE.md`. Do not reopen EFB-002 without new reproducible evidence.
