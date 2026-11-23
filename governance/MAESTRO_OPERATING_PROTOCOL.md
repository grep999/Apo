# Maestro Operating Protocol — EMBERFALL

## Status

Maestro is adopted as the workflow governance model for the EMBERFALL engineering program. The repository contains Maestro-compatible work definitions and operating instructions. It does not claim that agent subprocesses were launched or that the upstream native SQLite runtime was executed in the authoring environment.

## Lifecycle

| Stage | Purpose | Exit condition |
|---|---|---|
| GROUNDING | Establish sources, constraints, dependencies, risks and next increment. | Packet accepted; assumptions visible; required grounding items completed. |
| MOMENTUM | Build one isolated candidate increment. | Candidate artifact and changed paths recorded; checks bound to exact candidate. |
| MASTERY | Independent reviewer challenges candidate against acceptance criteria. | Reviewer identity differs from builder; findings/evidence recorded; verdict justified. |
| REPETITION | Compare expected and actual result, retain lessons and update durable state. | Lessons, state updates, evidence and next decision recorded; return to Grounding. |

## Workflow topology

- Portfolio workflow: shared experience contract, volume ownership, glossary and cross-volume decisions.
- Feature workflows: one bounded implementation increment per workflow, not one workflow for the entire game.
- Every candidate is reviewed by artifact hash and Git SHA, never only by a narrative summary.

## Roles

| Maestro role | EMBERFALL responsibility |
|---|---|
| ORCHESTRATOR | Maintain stage, dependencies, candidate identity, evidence register and decisions. |
| ARCHITECT | Preserve experience architecture and resolve system ownership/interfaces. |
| RESEARCHER | Verify platform, accessibility, genre, production and provenance claims. |
| DOMAIN_SPECIALIST | Art, Android UX, content, animation/VFX/audio or narrative expertise. |
| BUILDER | Produce implementation candidate. |
| INDEPENDENT_REVIEWER | Challenge exact candidate; must not be builder. |

Role assignment is not proof of execution. An actual agent assignment requires an adapter acceptance and observable run evidence.

## Evidence classes

VERIFIED: reproducible check/inspected artifact on exact candidate.  
OBSERVED: directly inspected result not yet reproducibly gated.  
INFERRED: reasoned conclusion.  
ASSUMED: self-report or unconfirmed inference.  
PLANNED: future work.  
BLOCKED: work cannot proceed without a named dependency/authorization.  
SUPERSEDED: replaced by a newer approved result.

A reviewer PASS cannot rely on ASSUMED evidence for mandatory criteria. Generated mockups remain exploratory until verified against device screenshots and implementation behavior.

## Feature workflow

1. Ground against source spec, repository baseline and decisions.
2. Confirm ownership/dependencies; create work items for research, architecture, implementation and gates.
3. Build one bounded candidate in an isolated worktree.
4. Run applicable deterministic gates and capture output hashes.
5. Bind candidate_ref, candidate SHA and changed paths to Momentum.
6. Submit to Mastery; independent reviewer inspects exact artifact.
7. On FAIL return to Momentum; on BLOCKED record dependency.
8. After accepted review, update durable state and Repetition record.

## Approval boundaries

No recruitment, spending, public release, store submission, licensing purchase, merge to protected branch, deployment or destructive action is authorized by this protocol. The 100-person visual production structure is a hypothetical capacity model, not a hiring claim.
