#!/usr/bin/env python3
"""Run Gradle/content checks through the current Maestro GateRunner API."""
from __future__ import annotations
import argparse
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = ROOT
MAESTRO_DIR = Path(os.environ.get("MAESTRO_DIR", str(ROOT.parent / "maestro"))).resolve()
if not (MAESTRO_DIR / "maestro" / "checks.py").is_file():
    raise SystemExit(
        f"Maestro source checkout not found at {MAESTRO_DIR}; "
        "run bootstrap_current_maestro.sh first or set MAESTRO_DIR."
    )
sys.path.insert(0, str(MAESTRO_DIR))
from maestro.checks import GateRunner, CheckSpec

DEFAULT = PROJECT_ROOT / "maestro" / "gates.template.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=["m0", "android-slice", "release"], required=True)
    parser.add_argument("--config", type=Path, default=DEFAULT)
    parser.add_argument("--out", type=Path, default=Path(".maestro/gate-results.json"))
    args = parser.parse_args()

    config = json.loads(args.config.read_text(encoding="utf-8"))[args.profile]
    specs = [
        CheckSpec(
            name=item["name"],
            command=item["command"],
            required=item["required"],
            category=item["category"],
            timeout=item["timeout"],
        )
        for item in config
    ]
    results = GateRunner(ROOT).run(specs)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(results, indent=2), encoding="utf-8")
    for result in results:
        print(
            f"{result['name']}: {result['outcome']} exit={result['exit_code']} "
            f"sha={result['ran_on_sha']} log={result['log_hash']}"
        )
    return 0 if all((not result["required"]) or result["outcome"] == "passed" for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
