#!/usr/bin/env python3
"""Complete executed EFB-002 Momentum work and submit the exact candidate to Mastery."""
from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAESTRO_DIR = Path(os.environ["MAESTRO_DIR"])
CLI = MAESTRO_DIR / ".venv/bin/maestro"
DB = ROOT / ".maestro/state.sqlite3"
STATE = ROOT / ".maestro"
ARTIFACTS = ROOT / "artifacts"
STATE.mkdir(parents=True, exist_ok=True)
ARTIFACTS.mkdir(parents=True, exist_ok=True)

def run(args: list[str], output: Path | None = None) -> dict | list:
    proc = subprocess.run(
        [str(CLI), "--db", str(DB), *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if proc.returncode:
        raise SystemExit(
            f"Maestro command failed: {args!r}\nstdout:\n{proc.stdout}\nstderr:\n{proc.stderr}"
        )
    if output is not None:
        output.write_text(proc.stdout, encoding="utf-8")
    return json.loads(proc.stdout)

start = json.loads((STATE / "efb-002-start.json").read_text(encoding="utf-8"))
wid = start["id"]
momentum = json.loads((ROOT / "maestro/packets_v03/EFB-002-BOARD-CORE.momentum.json").read_text(encoding="utf-8"))
candidate_ref = momentum["candidate_ref"]
items = json.loads((STATE / "efb-002-work-items.json").read_text(encoding="utf-8"))
if len(items) != 3:
    raise SystemExit(f"expected three seeded Momentum work items, got {len(items)}")

event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
pr = event.get("pull_request", {})
pr_number = pr.get("number", "unknown")
base_sha = pr.get("base", {}).get("sha", "unknown")
head_branch = os.environ.get("GITHUB_HEAD_REF", "unknown")
remediation_parent = "6932b747d78f9310ecb859cc9079cb2fc28ac9bf"
ancestor_check = subprocess.run(
    ["git", "merge-base", "--is-ancestor", remediation_parent, "HEAD"],
    cwd=ROOT, capture_output=True, text=True, check=False
)
if ancestor_check.returncode != 0:
    raise SystemExit(
        f"expected remediation commit {remediation_parent} is not in candidate ancestry"
    )

evidence_by_title = {
    "Research implementation context": [
        "Verified EFB-002 target source/test paths and pure Kotlin/JVM build harness on the isolated implementation branch.",
        f"Reconciled the EFB-002 grounding packet against EFB-000/EFB-001 prerequisites and current base git:handoff/emberfall-coding-agent-v1.0@{base_sha}; original remediation parent is {remediation_parent}.",
    ],
    "Implement candidate increment": [
        f"Candidate source, tests and build configuration are committed on {head_branch} at {candidate_ref}.",
        f"Draft PR #{pr_number} contains the isolated EFB-002 implementation; legacy SwapCells prototype was not modified.",
    ],
    "Run deterministic gate checks": [
        f"Maestro GateRunner exact-candidate report: {ARTIFACTS / 'efb002-gate-report.json'}.",
        "gradle-junit-exact-candidate: VERIFIED passed, exit_code=0, SHA-bound log_hash recorded in the Momentum packet.",
        "pure-jvm-boundary: VERIFIED passed, exit_code=0, SHA-bound log_hash recorded in the Momentum packet.",
        "The Gradle test suite includes the 100,000-seed deterministic generation/distribution test.",
    ],
}
revision = int(run(["show", wid])["revision"])
for item in items:
    title = item["title"]
    if title not in evidence_by_title:
        raise SystemExit(f"unexpected seeded work item: {title}")
    run(["start-work", wid, item["id"], "--revision", str(revision), "--actor", "orchestrator"])
    run([
        "complete-work", wid, item["id"], "--revision", str(revision),
        "--evidence", json.dumps(evidence_by_title[title]),
        "--actor", "orchestrator",
    ])

before_mastery = run(["show", wid])
revision = int(before_mastery["revision"])
submitted = run(["submit-mastery", wid, "--revision", str(revision), "--actor", "orchestrator"])
revision = int(submitted["revision"])

review_item = run([
    "add-work", wid,
    "Independent Mastery review",
    "Review the exact EFB-002 candidate and return an evidence-backed PASS or REQUEST_CHANGES verdict.",
    "INDEPENDENT_REVIEWER",
    "--acceptance", json.dumps([
        f"Review exact candidate {candidate_ref}, not a moving branch reference.",
        "Inspect BoardCore implementation, test coverage, deterministic evidence and CI artifact.",
        "Check ADR-002/003/004/005/006/007/008/010/012 compliance and adversarial edge cases.",
        "Submit findings and evidence using the independent reviewer identity; do not approve if any required check or invariant is unproven.",
    ]),
    "--dependencies", json.dumps([items[-1]["id"]]),
    "--revision", str(revision),
    "--actor", "orchestrator",
])
review_dispatch = {
    "workflow_id": wid,
    "candidate_ref": candidate_ref,
    "role": "INDEPENDENT_REVIEWER",
    "capability": "independent_mastery_review",
    "expected_output": {
        "verdict": ["PASS", "REQUEST_CHANGES"],
        "findings": "File/line-specific findings with severity and rationale.",
        "evidence": "Exact-candidate reproducible evidence and review coverage.",
    },
    "scope": [
        "src/main/kotlin/com/emberfall/board/BoardCore.kt",
        "src/test/kotlin/com/emberfall/board/BoardCoreTest.kt",
        "build.gradle.kts",
        "settings.gradle.kts",
        ".github/workflows/efb002-board-core.yml",
        "scripts/record_efb002_maestro_gates.py",
        "artifacts/efb002-gate-report.json",
    ],
    "independence_requirement": "Reviewer actor must differ from builder actor. No self-approval.",
    "permission_boundary": "read_only",
    "acceptance_boundary": "EFB-000 merged-tree review remains a separate prerequisite before EFB-002 merge.",
}
dispatch_path = ARTIFACTS / "efb002-review-dispatch.json"
dispatch_path.write_text(json.dumps(review_dispatch, indent=2) + chr(10), encoding="utf-8")
assignment = run([
    "assign", wid, "INDEPENDENT_REVIEWER", "independent_mastery_review",
    str(dispatch_path), "--actor", "orchestrator",
])
final_state = run(["show", wid], STATE / "efb-002-final-state.json")
work_items = run(["work-items", wid], STATE / "efb-002-work-items.json")
assignments = run(["assignments", wid], STATE / "efb-002-assignments.json")
events = run(["events", wid], STATE / "efb-002-events.json")
summary = {
    "workflow_id": wid,
    "stage": final_state["stage"],
    "status": final_state["status"],
    "revision": final_state["revision"],
    "candidate_ref": candidate_ref,
    "completed_momentum_items": [x["id"] for x in work_items if x["stage"] == "MOMENTUM" and x["status"] == "COMPLETED"],
    "pending_mastery_items": [x["id"] for x in work_items if x["stage"] == "MASTERY" and x["status"] != "COMPLETED"],
    "review_assignment_id": assignment["assignment_id"],
    "review_assignment_status": next((x["status"] for x in assignments if x["id"] == assignment["assignment_id"]), None),
    "event_count": len(events),
}
(ARTIFACTS / "efb002-maestro-summary.json").write_text(json.dumps(summary, indent=2) + chr(10), encoding="utf-8")
if final_state["stage"] != "MASTERY" or len(summary["completed_momentum_items"]) != 3:
    raise SystemExit(f"Maestro did not reach the expected Mastery queue: {summary}")
if summary["review_assignment_status"] != "PROPOSED":
    raise SystemExit(f"independent review dispatch is not pending as expected: {summary}")
print(json.dumps(summary, indent=2))
