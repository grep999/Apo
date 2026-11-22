# EMBERFALL — Restoration Authority & Production Mandate

**Document status:** Controlling restoration instruction for the current `emberfall-2` source snapshot  
**Prepared:** 21 November 2026  
**Mirror source:** `public/assets/aistudio/EMBERFALL_RESTORATION_AUTHORITY_AND_MANDATE_71ac56e3.md`  
**Purpose:** Stop design drift, restore the original EMBERFALL, and drive the project through a controlled, verifiable production sequence.  
**This document supersedes:** the current AI Studio implementation plan and the provisional choices recorded in `docs/ADR_CONFLICTS_AND_DECISIONS.md`, wherever they conflict with this mandate or an owner-approved source.

---

## Governing Rule

> **The original design defines the game. The code is allowed to change; the game is not allowed to change without owner approval.**

- Canonical classes (order immutable): **Ashen Monk** (`Ember/Moss`, `Prayer Beads`), **Quarry Witch** (`Tide/Violet`, `Chisel Wand`), **The Unlit** (`Gold/Ash`, `Hollow Crown`).
- Canonical playable hues: **Ember, Tide, Moss, Gold, Violet** (Spectrum wheel order: **Ember → Gold → Moss → Tide → Violet → Ember**).
- Canonical neutrals: **Ash, Stone, Prism** (plus `Empty` Wall headroom sockets).
- Strict prohibition on inventing missing rules, substituting mechanics, adding meta-currency, cloud/online backends, iOS, or marking provisional/draft values as `LOCKED`.
- Full controlling text is preserved verbatim in `public/assets/aistudio/EMBERFALL_RESTORATION_AUTHORITY_AND_MANDATE_71ac56e3.md`.

## Phase Execution Gates (G0–G7)

1. **Gate G0 (Freeze, Preserve & Truth):** Record baseline & authority table; mark old plan superseded & `ADR_CONFLICTS_AND_DECISIONS.md` non-authoritative; publish `RESTORATION_STATUS.md` & `RESTORATION_TRACEABILITY.csv`.
2. **Gate G1 (Design Contract & Source Recovery):** Import accepted `SCOPE.md`, `ADR-001..ADR-023`, detailed Design Bible volumes, and canonical content catalogs; block any rule whose authoritative source is missing.
3. **Gate G2 (Engine Restoration):** Deterministic pure-JVM domain engine (`com.example.engine` with 0 Android/Compose/Room imports) matching accepted rules & golden tests.
4. **Gate G3 (Classes & Content):** Restore exact Monk, Witch, and Unlit identities, signatures, starters, cards, relics, and enemies without invention.
5. **Gate G4 (Campaign):** 15-row branching map with `EVENT` nodes, O(V+E) route validation, 3 Acts, Act I Row 5 Shade, and ADR-017 true ending.
6. **Gate G5 (Android Integration):** Jetpack Compose projection & command surface, canonical painterly portraits (`ART_BASELINE_LOCK.md` v0.4), Fog/Seal/Brand/Generic-Ink UI, defeat->RunSummary routing.
7. **Gate G6 (Persistence & Replay):** Mutex-serialized Room writes, non-destructive explicit migrations (`1->4`), strict `RunSnapshotCodec` v3 validation & v2->v3 migration, deterministic seed+command replay.
8. **Gate G7 (Balance, UX, Accessibility & Release):** Independent QA, multi-seed sweeps, clean reproducible Android build, legal/title check.

## Required Owner-Level Decisions (Section 11)

1. **Architecture:** Confirm whether the original Java 8 lightweight Android architecture remains binding or whether the current Kotlin + Compose shell (with pure-JVM `com.example.engine`) is an approved migration.
2. **Missing Source Pack:** Provide/import accepted `docs/AAA/emberfall/SCOPE.md`, `docs/adr/ADR-001.md`…`ADR-023.md`, detailed Design Bible volumes (beyond the v0.2 outline), and approved Monk/Witch starter & full content catalogs.
3. **Live Git Repository Access:** Provide a live `.git` checkout/remote if branch creation, commit SHAs, and remote pushes are required (the current AI Studio workspace has no `.git` directory).
