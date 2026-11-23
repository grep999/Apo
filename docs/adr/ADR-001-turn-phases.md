# ADR-001 — Turn Phases

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 2, 4, 13.

## Decision

Draw 5 → Weave → Cast → Wall → Enemy → Cleanup. Weave starts with three Tap actions. Cards/relics may add or remove Taps through explicit effects; they do not reopen arbitrary player input after Cast. Card effects that pop a group do not consume a Tap unless their definition explicitly says so.

## Rationale and baseline adherence

The v0.2 spec gives this order and three base Taps. Strict phases keep the board, card economy and Wall cadence legible. Interleaving is not silently introduced.

## Acceptance tests

A fourth ordinary Tap is rejected; modifiers adjust the Tap budget exactly; Cast cannot return to Weave; Wall and enemy phases resolve once; phase transition and replay tests cover every legal command.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
