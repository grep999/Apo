# ADR-007 — Stone Break Order

**Status:** RESOLVED — Product Owner baseline, 2026-11-23  
**Decision authority:** Product Owner  
**Implementation gate:** EFB-000 independent review accepted; then EFB-002/EFB-003 work packets  
**Source:** Section 3.

## Decision

When a qualifying group of four or more pops, inspect the pre-removal board snapshot and mark every Stone orthogonally adjacent to any member of that group. Break all marked Stones in one deterministic batch, then apply vertical gravity once. Newly exposed Stone adjacency does not recursively trigger another break during the same pop.

## Rationale and baseline adherence

The source says Stone breaks when adjacent to a group of four or more. Snapshot batching is the deterministic, non-exploitative interpretation; it avoids free recursive chain reactions not promised by the spec.

## Acceptance tests

Golden boards for one/multiple adjacent Stones, two Stone clusters, Stone exposed only after gravity, and two separate pops; assert exactly one snapshot pass and stable event order.

## Change control

This ADR is stable. Reopen only when (1) a source-of-truth contradiction is found, (2) reproducible test/device evidence demonstrates a material defect, or (3) the Product Owner explicitly changes the product direction. Specialist proposals alone do not reopen it.
