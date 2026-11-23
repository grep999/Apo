# ADR-017 — Fast True Ending

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-003 combat implementation  
**Source:** Sections 7, 9; existing `GameEngine.startNewRun`, `qualifiesDreadAirTurn`, `handleEndTurn` and ADR-017 decision log.

## Decision

The Fast is available only in Act 3 against the Hearthkeeper when **both** conditions hold: the hero class is `THE_UNLIT` and the Unlit Covenant is active. Each of three consecutive full turns must contain at least one Ash Pop and no non-Ash Pops. The player must cast zero cards, spend no Vigil, use no Flask and trigger no Wall Overflow during each qualifying turn. The player is **not** required to spend all three ordinary Taps; unused Taps are allowed.

On a qualifying turn, non-Defend enemy intents pass through empty air; Defend intents still resolve. The Wall's normal rise still occurs before the Enemy phase. Any failed qualification resets the streak to zero. After the third qualifying Enemy phase, the true-ending victory resolves before the next boss attack.

## Rationale and baseline adherence

The v0.2 spec defines three consecutive Ash-only/no-card turns and says the Unlit Covenant unlocks the route. The existing engine and decision log further establish that the Covenant is exclusive to the The Unlit class and that Vigil burn, Flask use and Overflow disqualify a turn. Preserve those tested conditions. Neither the source nor the engine requires all three Taps to be spent.

## Acceptance tests

- Requires Act 3 Hearthkeeper boss, `THE_UNLIT` hero and active Unlit Covenant.
- At least one Ash Pop per turn; every Pop is Ash; unused Taps are allowed.
- Non-Ash Pop, any card cast, Vigil spent, Flask use or Overflow resets the streak.
- Enemy attacks/board intents pass through on qualifying turns; Defend still resolves.
- Wall rises normally before Enemy phase.
- Three consecutive qualifying turns win after the third Enemy phase.
- Covenant off, wrong class, wrong act or non-boss encounter never triggers the ending.
- Seeded replay produces the same streak and ending transition.

## Change control

This ADR preserves an established code/test contract while clarifying the source's shorthand. Do not remove the class restriction or the no-Vigil/no-Flask/no-Overflow conditions without explicit Product Owner change. The no-all-three-Taps rule is settled and must not be tightened.
