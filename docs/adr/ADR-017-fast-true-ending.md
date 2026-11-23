# ADR-017 — Fast True Ending

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 7, 9.

## Decision

The Fast is available in Act 3 against the Hearthkeeper when the Unlit Covenant is active. Complete three consecutive full turns with at least one Pop each turn; every Pop during those turns must consist only of Ash; cast zero cards during each qualifying turn. The player is not required to spend all three ordinary Taps. Any non-Ash Pop, card cast or prohibited Ink-producing board mutation resets the streak. The Wall still rises. Hearthkeeper attack intents during valid Fast turns resolve against empty air. The true-ending transition occurs after the third qualifying enemy phase and before the next boss attack. The Unlit hero class is not required; the Covenant is the unlock condition.

## Rationale and baseline adherence

The source says three consecutive turns of Ash-only Pops and no cards; it does not say all three Taps must be spent. The Unlit Covenant unlocks the route; the class and Covenant are separate concepts. This closes both ambiguities without making the challenge stricter than written.

## Acceptance tests

Tests for 1+ Ash Pop per turn, unused Taps allowed, multiple Ash Pops, non-Ash reset, card reset, effect-generated Pop reset, Wall still rises, attacks miss, exact phase timing, Covenant on/off and non-Unlit classes.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
