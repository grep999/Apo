# Maestro v0.3 Operating Guide for EMBERFALL

## Upstream lock

- Repository: https://github.com/grep999/maestro
- Version: 0.3.0
- Observed main commit: e5247d950fd48a8e1e07acec68ffd04d1445ba55
- Engine blob: bb98d37b43a951f7de368c271896fb03ac31846e
- CLI blob: 90ba6c49aeb34ca298047cc238e3e4cbb155dc0e
- Coding helpers: 1cec7ed4bb9b36c32e2999324c3d6a66f458fa1e
- GateRunner: 7dd138b453833287325c2eeb5616f011ea7d204b
- Task schema v2: 15b4050fd6392cf8e6bd16a2099383a9ed349124
- Handoff schema v2: e92582b45827c8efcc04d2860d5143b90c303e06

## Guarantees enforced by current upstream engine

- Optimistic revision guard and append-only event history.
- Current-cycle Grounding/Momentum/Mastery packet requirements.
- Builder actor recorded at candidate delivery; review() rejects the same actor as reviewer.
- Reviewer can bind to exact candidate_ref.
- PASS requires at least one required check and all required checks passed.
- If workflow requires verified checks (R1+ from coding_start), PASS requires evidence_class=VERIFIED, exit_code=0 and a non-empty log_hash for every required check.
- Repetition requires accepted current-cycle Mastery and clears stale packets on the next cycle.

Actor string comparison is a useful workflow guard, not identity cryptography. Record actual adapter model identity and prefer separate agents/vendors for Builder and Independent Reviewer.

## CLI commands available in inspected main

coding-start, coding-seed-work, begin-momentum, assign, assignment, assignments, deliver, submit-mastery, review, learn, run-checks, adapters, adapter-health, show, list, events, add-work, start-work, complete-work, work-items, cancel, unblock.

The README sample flow is illustrative; the CLI source is authority for exact argument names. Verify maestro --help after installing the pinned commit.

## Workflow policy

- One workflow per coherent engineering increment, not one giant workflow for the entire game.
- A project portfolio document tracks all increments; each increment has its own task v2 packet and Maestro workflow.
- Use R0 for editorial/doc-only changes, R1 for ordinary feature work, R2 for save migration/backend/replay integrity/security-sensitive changes, R3 only where a destructive/production operation requires explicit human control.
- No PR/merge/publish/deploy before an authorized human approves.
- Echo adapter is for adapter health and smoke tests only.

## CLI availability after bootstrap

The bootstrap script creates an isolated environment under the sibling checkout (../maestro/.venv). A new terminal does not inherit that shell activation. Either activate ../maestro/.venv/bin/activate before using the CLI or let create_maestro_workflows.py resolve the pinned executable automatically. Set MAESTRO_DIR if the checkout lives elsewhere.

The workflow creation helper runs official coding-start and then applies the matching task-specific Grounding packet from maestro/packets_v03/, preserving the real Git baseline SHA. It checkpoints each workflow immediately after creation. Ordinary reruns refuse duplicates; after an interrupted batch, inspect .maestro/workflow_ids.json and use python maestro/create_maestro_workflows.py --resume to finish only ungrounded records. Preview with python maestro/create_maestro_workflows.py --dry-run. Add engineering/GITIGNORE_FRAGMENT.txt to the game repository before writing local runtime state.

## Feature loop

1. coding-start with the real Git workspace and risk.
2. Grounding review: source, dependencies, acceptance and next increment explicit.
3. begin-momentum, then coding-seed-work or add tailored work items.
4. Builder assignment in isolated worktree; record exact base/candidate SHA and model identity.
5. Run custom GateRunner checks that actually apply to Gradle/Kotlin; default Python preset is not appropriate.
6. Deliver Momentum packet with candidate reference, changed artifacts, checks and risks.
7. Submit Mastery; an independent reviewer inspects the exact candidate and records PASS/FAIL/BLOCKED/CONDITIONAL.
8. If PASS/CONDITIONAL, produce Repetition packet and learn.
9. Update task state, release notes, known gaps and portfolio index.

## Independent review helper

Before review, prepare the Momentum packet with committed candidate_sha, candidate_ref (git:<sha>), builder_identity and a non-empty candidate_sha256 map of relative paths to SHA-256 digests. The candidate worktree must be clean and the local .maestro/ state must be ignored by Git. The helper checks HEAD equality and each declared file hash, writes an immutable review attestation under .maestro/reviews/, then invokes the current engine review() API.

Example:
    python maestro/record_independent_review.py --workflow-id <WORKFLOW_ID> --revision <REV> --reviewer-identity <independent-agent-identity> --candidate-ref git:<candidate-sha> --verdict PASS --findings '[]' --evidence '["review:diff-report","gate:unit-test-log-hash"]' --independence-attestation 'Separate reviewer model/vendor and operator-confirmed independence'

The helper does not cryptographically authenticate the reviewer; record a real agent/model identity and have the orchestrator independently verify that it differs from the builder. If Mastery is rejected, retain the immutable attempted-review record and correct the candidate or review setup rather than editing the record.

## Build-gate guidance

Suggested custom Gradle gates, enabled only when their tasks exist:
- ./gradlew --no-daemon :game-core:test
- ./gradlew --no-daemon :game-content:validateContent
- ./gradlew --no-daemon :android-app:lintDebug
- ./gradlew --no-daemon :android-app:assembleDebug
- ./gradlew --no-daemon :balance-sim:smokeTest

Real device/emulator tests must be executed separately and attached as evidence; a successful Gradle build does not prove touch behavior, audio latency or 60 fps.
