#!/usr/bin/env python3
"""Validate the docs-led Emberfall v0.2 handoff and inventory the legacy prototype."""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY_REF = "origin/implementation/emberfall-latest-source"
EXPECTED_LEGACY_COMMIT = "e3610946ecf4caf91f0e1d2a232af714b88e65c2"
EXPECTED_MAESTRO_COMMIT = "e5247d950fd48a8e1e07acec68ffd04d1445ba55"


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        raise SystemExit(f"REQUIRED FILE MISSING: {path}")
    return p.read_text(encoding="utf-8")


def git_show(ref: str, path: str) -> str:
    proc = subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode:
        raise SystemExit(f"Cannot inspect {ref}:{path}: {proc.stderr.strip()}")
    return proc.stdout


def git_sha() -> str:
    p = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True)
    return p.stdout.strip()


def main() -> None:
    required = [
        "references/EMBERFALL_GAME_SPEC_v0.2.txt",
        "engineering/CODING_AGENT_ROADMAP.md",
        "engineering/IMPLEMENTATION_WBS.json",
        "engineering/OPEN_DECISIONS.md",
        "maestro/EMBERFALL_TASKS_V2.json",
        "maestro/CORE_PACKET_TEMPLATES_V03.json",
        "maestro/bootstrap_current_maestro.sh",
    ]
    hashes = {
        path: hashlib.sha256(read(path).encode("utf-8")).hexdigest()
        for path in required
    }

    wbs = json.loads(read("engineering/IMPLEMENTATION_WBS.json"))
    tasks = json.loads(read("maestro/EMBERFALL_TASKS_V2.json"))
    templates = json.loads(read("maestro/CORE_PACKET_TEMPLATES_V03.json"))
    spec = read("references/EMBERFALL_GAME_SPEC_v0.2.txt")
    decisions = read("engineering/OPEN_DECISIONS.md")
    bootstrap = read("maestro/bootstrap_current_maestro.sh")

    assert wbs["project_id"] == "EMBERFALL"
    assert wbs["maestro_version"] == "0.3.0"
    assert tasks["project_id"] == "EMBERFALL"
    assert tasks["maestro_version"] == "0.3.0"
    assert tasks["schema_version"] == 2
    assert EXPECTED_MAESTRO_COMMIT in bootstrap
    assert "Tap" in spec and "Wall" in spec

    epics = wbs["epics"]
    epic_ids = [e["id"] for e in epics]
    task_ids = [t["task_id"] for t in tasks["tasks"]]
    assert len(epic_ids) == len(set(epic_ids)), "duplicate WBS epic IDs"
    assert set(epic_ids) == set(task_ids), "WBS and Maestro task IDs diverge"
    assert epics[0]["id"] == "EFB-000-BASELINE"
    assert epics[1]["id"] == "EFB-001-RULES-ADR"
    assert epics[1]["dependencies"] == ["EFB-000-BASELINE"]
    for epic in epics:
        missing = set(epic.get("dependencies", [])) - set(epic_ids)
        assert not missing, f"{epic['id']} has unknown dependencies: {sorted(missing)}"

    adr_rows = [line for line in decisions.splitlines() if line.strip().startswith("| ADR-")]
    status_counts = Counter(line.split("|")[-2].strip() for line in adr_rows)
    assert len(adr_rows) == 23, f"expected 23 ADR rows, found {len(adr_rows)}"
    assert status_counts["OPEN / BLOCKING"] == 19
    assert status_counts["RESOLVED by platform policy check; re-verify at launch"] == 1
    assert status_counts["OPEN"] == 1
    assert status_counts["PROPOSED"] == 2

    # The handoff branch is intentionally docs-led. Absence of app source is a
    # recorded applicability result, not a failed Android build.
    handoff_has_app = (ROOT / "app/src/main").exists()
    handoff_has_gradle = (ROOT / "settings.gradle.kts").exists() and (ROOT / "build.gradle.kts").exists()
    assert not handoff_has_app and not handoff_has_gradle, (
        "Handoff branch unexpectedly contains Android source/build files; update E00 applicability."
    )

    legacy_readme = git_show(LEGACY_REF, "README.md")
    legacy_catalog = git_show(LEGACY_REF, "app/src/main/java/com/example/engine/GameCatalog.kt")
    legacy_progression = git_show(LEGACY_REF, "app/src/main/java/com/example/engine/ProgressionSystem.kt")
    legacy_contract = git_show(LEGACY_REF, "docs/AAA/emberfall/ENGINE_CONTRACT.md")
    legacy_commit = subprocess.run(
        ["git", "rev-parse", LEGACY_REF],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    assert legacy_commit == EXPECTED_LEGACY_COMMIT, (
        f"legacy prototype ref drifted: expected {EXPECTED_LEGACY_COMMIT}, got {legacy_commit}"
    )
    legacy_tree = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", LEGACY_REF],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout.splitlines()

    def is_license_path(path: str) -> bool:
        name = Path(path).name.upper()
        return name in {"LICENSE", "COPYING", "NOTICE"} or name.startswith(("LICENSE.", "COPYING.", "NOTICE."))

    legacy_license_files = sorted(path for path in legacy_tree if is_license_path(path))
    handoff_license_files = sorted(
        str(path.relative_to(ROOT))
        for path in ROOT.rglob("*")
        if path.is_file() and ".git" not in path.parts and is_license_path(str(path))
    )

    card_count = len(re.findall(r"CardDef\(", legacy_catalog))
    relic_count = len(re.findall(r"RelicDef\(", legacy_catalog))
    support_count = len(re.findall(r'id = "sup_[^"]+"', legacy_progression))
    artifact_count = len(re.findall(r'id = "art_[^"]+"', legacy_progression))
    has_wrapper_script = "gradlew" in legacy_tree
    has_wrapper_jar = "gradle/wrapper/gradle-wrapper.jar" in legacy_tree
    has_gradle_properties = "gradle/wrapper/gradle-wrapper.properties" in legacy_tree
    has_android_tests = any(p.startswith("app/src/androidTest/") for p in legacy_tree)
    legacy_is_swap_match = (
        "Match-3 board" in legacy_readme
        or "SwapCells" in legacy_contract
        or "swap" in legacy_contract.lower()
    )
    assert card_count == 65
    assert relic_count == 22
    assert support_count == 8
    assert artifact_count == 8
    assert has_gradle_properties
    assert not has_wrapper_script and not has_wrapper_jar
    assert legacy_is_swap_match

    result = {
        "project_id": "EMBERFALL",
        "baseline_sha": git_sha(),
        "handoff_branch_android_source": handoff_has_app,
        "handoff_branch_gradle_build": handoff_has_gradle,
        "wbs_epic_count": len(epics),
        "maestro_task_count": len(task_ids),
        "adr_row_count": len(adr_rows),
        "adr_status_counts": dict(status_counts),
        "handoff_license_files": handoff_license_files,
        "handoff_source_reuse_authorization": (
            "NO_GRANT_VISIBLE_RIGHTS_CONFIRMATION_REQUIRED"
            if not handoff_license_files
            else "LICENSE_FILES_PRESENT_MANUAL_CONTENT_AND_OWNERSHIP_REVIEW_REQUIRED"
        ),
        "legacy_prototype": {
            "ref": LEGACY_REF,
            "commit": legacy_commit,
            "license_files": legacy_license_files,
            "source_reuse_authorization": (
                "NO_GRANT_VISIBLE_RIGHTS_CONFIRMATION_REQUIRED"
                if not legacy_license_files
                else "LICENSE_FILES_PRESENT_MANUAL_CONTENT_AND_OWNERSHIP_REVIEW_REQUIRED"
            ),
            "card_definitions": card_count,
            "relic_definitions": relic_count,
            "support_definitions": support_count,
            "artifact_definitions": artifact_count,
            "has_gradle_wrapper_properties": has_gradle_properties,
            "has_gradlew": has_wrapper_script,
            "has_gradle_wrapper_jar": has_wrapper_jar,
            "has_android_instrumentation_tests": has_android_tests,
            "uses_swap_match_rules": legacy_is_swap_match,
        },
        "maestro_pinned_commit": EXPECTED_MAESTRO_COMMIT,
        "file_sha256": hashes,
        "android_build_gate": "NOT_APPLICABLE_NO_ANDROID_SOURCE" if not handoff_has_app else "APPLICABLE",
        "evidence_class": "SOURCE_INVENTORY_VALIDATION",
    }

    out = ROOT / "artifacts/e00-validation.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
