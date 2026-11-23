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
board_packet_path = ROOT / "maestro/packets_v03/EFB-002-BOARD-CORE.grounding.json"
board_packet = json.loads(board_packet_path.read_text(encoding="utf-8"))
content_packet_path = ROOT / "maestro/packets_v03/EFB-CONTENT-001-SPECIALIST-PROPOSALS.grounding.json"
content_packet = json.loads(content_packet_path.read_text(encoding="utf-8"))

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
spectrum = (adr_dir / "ADR-005-spectrum-run.md").read_text(encoding="utf-8").lower()
assert "round-half-up" in spectrum and "ties go to the higher integer" in spectrum
assert "(baseink * (2 + run) + 1) / 2" in spectrum
assert any("round-half-up exactly once at final payout" in item for item in board_packet["acceptance"])
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
assert board_packet["current_status"] == "BLOCKED_PENDING_EFB000_INDEPENDENT_REVIEW"
assert board_packet["no_implementation_authorized"] is True
assert "EFB-000-BASELINE: independent review accepted" in board_packet["dependencies"]
assert "Do not implement or route live combat through SwapCells" in " ".join(board_packet["constraints"])
# Dependency evidence must resolve through accepted PR merge commits, not moving
# branch-head SHAs embedded in this packet. This prevents stale/self-referential
# candidate pins from authorizing work against an obsolete baseline.
board_sources = " ".join(board_packet["sources"])
assert "PR #4 accepted merge commit" in board_sources
assert "PR #5 accepted merge commit" in board_sources
for stale_sha in ("93a4b27360c043b702624d7d317e7c682a0e7031", "3a4a5ae5092f38e058dd3210a05a68f163c73c49"):
    assert stale_sha not in board_sources + " ".join(board_packet["constraints"])
assert content_packet["current_status"] == "WAITING_FOR_SPECIALIST_AGENT_OUTPUT"
assert content_packet["no_content_accepted"] is True
assert content_packet["no_implementation_authorized"] is True
assert len(content_packet["specialist_roles"]) == 3
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
