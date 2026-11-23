# ADR-019 — Ios And Engine Path

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 1, 14, 16.

## Decision

Android is the M0/M1 implementation target. Keep the simulation/domain engine pure Kotlin/JVM and platform-independent. iOS and gamepad remain 1.0 product targets, but the cross-platform implementation choice is made at M2 using measured frame time, audio latency, save/replay parity, engineering capacity and device-QA cost. Do not force a cross-platform renderer before the evidence gate.

## Rationale and baseline adherence

This preserves the spec's iOS/gamepad 1.0 target and its explicit M2 architecture gate while keeping the first vertical slice focused.

## Acceptance tests

M2 decision memo compares native Android + shared domain, Kotlin Multiplatform and measured alternative; domain module contains no Android imports; device/audio/replay metrics included.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
