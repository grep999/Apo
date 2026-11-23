#!/usr/bin/env python3
"""Run exact-candidate EFB-002 gates through Maestro GateRunner and bind evidence to the SHA."""
from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

from maestro.checks import CheckSpec, GateRunner

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "artifacts"
ARTIFACTS.mkdir(parents=True, exist_ok=True)
sha = (os.environ.get("CANDIDATE_SHA") or os.environ.get("GITHUB_SHA", "")).strip()
if not re.fullmatch(r"[0-9a-f]{40}", sha):
    raise SystemExit("GITHUB_SHA must be an exact 40-character commit SHA")
actual = __import__("subprocess").run(
    ["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True
).stdout.strip()
if actual != sha:
    raise SystemExit(f"checkout SHA mismatch: expected {sha}, got {actual}")

boundary_code = (
    "from pathlib import Path; import re,sys; "
    "files=list(Path('src/main/kotlin').rglob('*.kt')); "
    "pattern=re.compile(r'android\\.|androidx\\.|compose\\.'); "
    "bad=[f'{p}:{i}:{line.strip()}' for p in files for i,line in enumerate(p.read_text().splitlines(),1) if pattern.search(line)]; "
    "print('pure JVM boundary PASS' if not bad else '\\n'.join(bad)); "
    "sys.exit(1 if bad else 0)"
)
specs = [
    CheckSpec(
        name="gradle-junit-exact-candidate",
        command=["gradle", "--no-daemon", "clean", "test"],
        required=True,
        category="test",
        timeout=900,
    ),
    CheckSpec(
        name="pure-jvm-boundary",
        command=[sys.executable, "-c", boundary_code],
        required=True,
        category="lint",
        timeout=60,
    ),
]
runner = GateRunner(
    ROOT,
    runner_identity=f"github-actions/{os.environ.get('GITHUB_RUN_ID', 'unknown')}",
    timeout=900,
)
results = runner.run(specs)
report = {
    "candidate_sha": sha,
    "runner_identity": runner.runner_identity,
    "checks": results,
}
(ARTIFACTS / "efb002-gate-report.json").write_text(
    json.dumps(report, indent=2) + chr(10), encoding="utf-8"
)

packet_path = ROOT / "maestro/packets_v03/EFB-002-BOARD-CORE.momentum.json"
packet = json.loads(packet_path.read_text(encoding="utf-8"))
packet["candidate_ref"] = f"git:{sha}"
packet["outcome"] = (
    "Exact candidate Gradle/JUnit and pure-JVM checks were executed by Maestro GateRunner; "
    "see checks for VERIFIED outcomes, exit codes, runner identity, ran_on_sha and log hashes."
)
gate_names = {item["name"] for item in results}
packet["checks"] = [
    item for item in packet.get("checks", []) if item.get("name") not in gate_names
] + results
packet_path.write_text(json.dumps(packet, indent=2) + chr(10), encoding="utf-8")

print(json.dumps(report, indent=2))
if any(item["required"] and item["outcome"] != "passed" for item in results):
    raise SystemExit("required EFB-002 GateRunner check failed")
