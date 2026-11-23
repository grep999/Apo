#!/usr/bin/env python3
"""Validate that EFB-001 covers every blocking ADR without resolving it."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
register = (ROOT / "engineering/OPEN_DECISIONS.md").read_text(encoding="utf-8")
proposal = (ROOT / "engineering/EFB-001_RULES_ADR_PROPOSALS.md").read_text(encoding="utf-8")
wbs = json.loads((ROOT / "engineering/IMPLEMENTATION_WBS.json").read_text(encoding="utf-8"))

rows = [line for line in register.splitlines() if line.strip().startswith("| ADR-")]
blocking = []
for row in rows:
    cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
    if len(cells) >= 5 and cells[-1] == "OPEN / BLOCKING":
        blocking.append(cells[0])

proposal_sections = {}
matches = list(re.finditer(r"^### (ADR-\d{3})\b.*$", proposal, flags=re.MULTILINE))
for index, match in enumerate(matches):
    end = matches[index + 1].start() if index + 1 < len(matches) else len(proposal)
    proposal_sections[match.group(1)] = proposal[match.start():end]

missing = [adr for adr in blocking if adr not in proposal_sections]
incomplete = [
    adr for adr in blocking
    if adr in proposal_sections
    and ("**Recommendation:**" not in proposal_sections[adr]
         or "**Acceptance tests:**" not in proposal_sections[adr])
]
assert not missing, f"blocking ADRs absent from proposal: {missing}"
assert not incomplete, f"blocking ADRs lack recommendation/tests: {incomplete}"
assert len(blocking) == 19, f"expected 19 blocking ADRs, found {len(blocking)}"
assert "EFB-001-RULES-ADR" in [e["id"] for e in wbs["epics"]]
assert "EFB-000-BASELINE" in [e["id"] for e in wbs["epics"]]

for adr in blocking:
    assert adr in proposal_sections
    assert "OPEN / BLOCKING" in register, "register no longer records blocking decisions"

adr_dir = ROOT / "docs/adr"
resolved_files = sorted(adr_dir.glob("ADR-0[01][0-9]-*.md")) if adr_dir.exists() else []
assert not resolved_files, (
    "This proposal branch must not silently create final ADR resolutions: "
    + ", ".join(p.name for p in resolved_files)
)

result = {
    "status": "PROPOSAL_ONLY",
    "blocking_adr_count": len(blocking),
    "covered_adr_count": len(proposal_sections),
    "missing_blocking_adrs": missing,
    "incomplete_blocking_adrs": incomplete,
    "final_adr_files_created": len(resolved_files),
    "wbs_dependency": "EFB-001-RULES-ADR depends on EFB-000-BASELINE",
}
out = ROOT / "artifacts/efb-001-adr-proposal-validation.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
