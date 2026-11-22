> **[NON-AUTHORITATIVE PROVISIONAL LOG — SUPERSEDED BY `docs/RESTORATION_AUTHORITY_AND_MANDATE.md`]**  
> Preserved as historical evidence of prior builder assumptions only. No provisional choice recorded below overrides owner-approved sources, `ART_BASELINE_LOCK.md` v0.4, or `docs/RESTORATION_AUTHORITY_AND_MANDATE.md`.

# EMBERFALL — ADR Conflicts, Source Gaps & Decision Log

**Date:** 2026-11-21  
**Active Maestro Workflows:** `EFB-RULES-CORRECTIVE-01`, `EFB-ART-UI-BASELINE-01`  
**Canonical Traceability:** `docs/RESTORATION_TRACEABILITY.csv`, `docs/RESTORATION_STATUS.md`

---

## 1. Architecture Decision (Resolved Owner/Platform Baseline)
- **Context:** Historical Java 8 roadmap (`Master_Implementation_Roadmap`) vs AI Studio Android Kotlin/Compose template.
- **Decision (Recorded):** Retain Kotlin 2.x + Jetpack Compose + Room + DataStore inside the Android app while enforcing strict pure-domain isolation: `com.example.engine` contains **0 Android, Compose, or Room imports** and executes deterministically on the pure JVM.

## 2. Corrective Resolutions Applied in `EFB-RULES-CORRECTIVE-01`
1. **Ashen Monk Vigil Burn (P0.1):** Removed un-canonical repeated free `vigilPool` counterattack; added `vigilCost` and `vigilBurnMax`/`damagePerBurnedVigil` single-hit burst consumption.
2. **Brand / Fog / Seal (P0.2):**
   - `Brand` (`ADR-010`): Marks board tiles (`Tile.isBranded`); each marked block popped deducts `2` from `vigil` (shortfall from `run.hp`), previewed before tap.
   - `Fog` (`ADR-011`): Sets `fogTapsRemaining = 3`, concealing board hues and Ink counts for 3 Taps while preserving all glyphs; `Gold Eye` restores visibility immediately.
   - `Seal` (`ADR-012`): Locks `board.sealedColumns` for 3 turns against taps, gravity, refill, targeted effects, and Wall rise.
3. **Wall Overflow (P0.3):** `ADR-008` exact predicate (`unsealed top row has no Empty cell`) -> `-8 HP (Vigil)`, burn top 2 rows to `TileKind.Empty`, `-1 Flask`, skip rise (Guard does NOT mitigate overflow).
4. **Echo (P0.4):** `ADR-015` repeats only `CardDef.primaryEffect` once; costs/secondary effects/draw/exhaust do not repeat.
5. **Unlit Covenant & Dread Ending (P0.5):** Enforced `THE_UNLIT`-only Covenant eligibility + 3 consecutive Ash-only, 0-card turns without overflow vs Act III Boss (`Hearthkeeper` strikes empty air while qualifying; any card play, non-Ash pop, or overflow resets streak).
6. **Command Transition Guards (P0.6):** `GameEngine.validateTransition` blocks out-of-order `ChooseNode`, `OpenRewards`, `FinishRewards`, `Campfire*`, `Shop*`, `ChooseEventOption`, and `LeaveNode` calls transactionally.
7. **Content, Scaling, Events & Board Fairness (P1):**
   - Exact `ADR-014` 12-card Gold-frame starter (`4 Gold Strike`, `4 Ash Ward`, `2 Pop Shot`, `1 Sift`, `1 Hollow Crown`); Monk/Witch starter card stats remain explicitly `[PROVISIONAL]` pending `Design Bible v0.2`.
   - `ADR-013` node HP formula `round(36 * 1.11^node)` (`x1.6` Elite, `x3.0` Boss).
   - `ADR-005` Spectrum `Run 0` start, wheel advance, Prism advance, and `Run 0` evaluation before reset.
   - `MapNodeType.EVENT` + `EventScreen` + DAG route-composition validator (`validateMap`).
   - Deterministic deck-accounted no-move reshuffle (zero silent tile injection).

## 3. UI & Persistence Baseline (`EFB-ART-UI-BASELINE-01`)
- Extracted authentic painterly class portraits (`ef_class_ashen_monk.webp`, `ef_class_quarry_witch.webp`, `ef_class_the_unlit.webp`) from `class_roster_painterly_1__23f2a842.png`; aligned `Color.kt` to `ART_BASELINE_LOCK v0.4`.
- Stale unreferenced JPGs (`img_class_{pyromancer,cinderknight,ashweaver}.jpg`) remain inert/unreferenced in `res/drawable/` due to container binary delete restriction (`delete_file` charset constraint).
- `EmberfallDatabase` upgraded cleanly to v4 with explicit `Migration`s (`1->2->3->4`, destructive fallback removed); `EmberfallRepository` serializes writes with `Mutex`; `RunSnapshotCodec` v3 validates `TileKind` strictly and migrates v2 snapshots cleanly.
