# ADR-009 — Spark Payment

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Sections 2–4.

## Decision

Colored card pips require matching colored Ink. Generic/colorless pips may be paid with any colored Ink or Spark. Spark never pays a colored pip. Payment is selected explicitly in the card payment preview. When an effect must auto-pay, spend Spark first, then the least abundant eligible colored Ink, with Spectrum wheel order as tie-break.

## Rationale and baseline adherence

This preserves the spec's distinction between colored and colorless costs and prevents Spark from erasing hue identity. Deterministic auto-payment is needed for replay and bots; player override is retained where the UI offers a choice.

## Acceptance tests

Payment matrix for every hue, Spark-only generic, mixed sources, insufficient balance, player override, deterministic auto-payment and preview/result parity.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
