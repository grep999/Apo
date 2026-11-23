#!/usr/bin/env python3
"""Validate the product-owner-resolved EFB-001 ADR contract."""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
register = (ROOT / "engineering/OPEN_DECISIONS.md").read_text(encoding="utf-8")
resolution = (ROOT / "engineering/EFB-001_RULES_ADR_PROPOSALS.md").read_text(encoding="utf-8")
wbs = json.loads((ROOT / "engineering/IMPLEMENTATION_WBS.json").read_text(encoding="utf-8"))
adr_dir = ROOT / "docs/adr"

blocking_ids = [f"ADR-{i:03d}" for i in range(1, 20)]
expected = {
    "ADR-001": "turn-phases", "ADR-002": "initial-board-and-generation",
    "ADR-003": "hue-weighting", "ADR-004": "prism-resolution",
    "ADR-005": "spectrum-run", "ADR-006": "ash-pop",
    "ADR-007": "stone-break-order", "ADR-008": "wall-and-overflow",
    "ADR-009": "spark-payment", "ADR-010": "brand-cost",
    "ADR-011": "fog-and-glyphs", "ADR-012": "seal-column",
    "ADR-013": "enemy-vigor-and-block", "ADR-014": "unlit-starter-deck",
    "ADR-015": "effect-queue-and-echo", "ADR-016": "shade-inheritance",
    "ADR-017": "fast-true-ending", "ADR-018": "map-graph",
    "ADR-019": "ios-and-engine-path", "ADR-020": "google-play-target-api",
    "ADR-022": "five-playable-hues", "ADR-023": "full-run-scope",
}
missing, invalid = [], []
for adr_id, slug in expected.items():
    path = adr_dir / f"{adr_id}-{slug}.md"
    if not path.exists():
        missing.append(str(path.relative_to(ROOT)))
        continue
    body = path.read_text(encoding="utf-8")
    if "**Status:** RESOLVED — Product Owner baseline" not in body:
        invalid.append(f"{adr_id}: missing Product Owner resolution status")
    for marker in ("## Decision", "## Rationale and baseline adherence", "## Acceptance tests", "## Change control"):
        if marker not in body:
            invalid.append(f"{adr_id}: missing {marker}")
for adr_id in blocking_ids:
    tail = register.split(f"| {adr_id} |", 1)[-1].splitlines()[0]
    if f"| {adr_id} |" not in register or "RESOLVED —" not in tail:
        invalid.append(f"{adr_id}: register does not mark resolved")
assert not missing, f"missing ADR files: {missing}"
assert not invalid, f"invalid ADR contract: {invalid}"
fast = (adr_dir / "ADR-017-fast-true-ending.md").read_text(encoding="utf-8").lower()
assert "three consecutive full turns" in fast
assert "not required to spend all three ordinary taps" in fast
assert "hero class is `the_unlit` and the unlit covenant is active" in fast
assert "spend no vigil" in fast and "use no flask" in fast and "overflow" in fast
assert "swap-match-3 is legacy-only" in resolution.lower()
assert "EFB-000 independent review" in resolution
epics = {e["id"]: e for e in wbs["epics"]}
assert epics["EFB-001-RULES-ADR"]["dependencies"] == ["EFB-000-BASELINE"]
assert "EFB-002-BOARD-CORE" in epics
unlit = (adr_dir / "ADR-014-unlit-starter-deck.md").read_text(encoding="utf-8").lower()
assert "unlit_hollow_crown" in unlit and "relic_hollow_crown" in unlit
assert "exactly 12 gold-frame cards" in unlit and "hollow crown" in unlit
result = {
    "status": "PRODUCT_OWNER_RESOLVED_IMPLEMENTATION_GATED",
    "resolved_blocking_adrs": len(blocking_ids),
    "additional_resolved_adrs": ["ADR-020", "ADR-022", "ADR-023"],
    "non_blocking_open": ["ADR-021"],
    "missing": missing,
    "invalid": invalid,
    "implementation_gate": "EFB-000 independent review accepted",
}
out = ROOT / "artifacts/efb-001-adr-resolution-validation.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
