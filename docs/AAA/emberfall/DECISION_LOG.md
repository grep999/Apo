# EMBERFALL — Decision Log & Traceability Register

**Updated:** 2026-11-21  
**Governing Maestro Workflows:** `EFB-RULES-CORRECTIVE-01`, `EFB-ART-UI-BASELINE-01`

---

## 1. Classification of Mechanics & Content

| Category | Definition |
|---|---|
| **[CANONICAL]** | Verified directly against `Master_Implementation_Roadmap`, `ART_BASELINE_LOCK v0.4`, `EMBERFALL_emberfall-2_review`, and ADRs |
| **[OWNER-DECIDED / PROVISIONAL]** | Chosen cleanly where `Design Bible v0.2` / `CH01` are absent from the repository, awaiting upstream source import |
| **[BLOCKED]** | Cannot be finalized verbatim until missing upstream documents are supplied |

---

## 2. Rules Traceability Matrix (P0 & P1)

| Rule / ADR | Source Contract | Model Field | Engine Function | UI Projection | Test Coverage |
|---|---|---|---|---|---|
| **P0.1 Ashen Monk Vigil Burn** | `HeroClass.ASHEN_MONK` burns Vigil for single-hit power; no infinite multi-hit counterattack exploit | `PlayerCombatState.vigil`, `CardDef.vigilCost`, `CardDef.vigilBurnMax`, `CardDef.damagePerBurnedVigil` | `GameEngine.handleCastCard` burns Vigil cleanly; enemy attack loop no longer deals unconsumed `vigilPool` | Card badges show `Vigil` burn / requirement | `Ashen Monk Vigil burn spends pool cleanly and multi-hit enemy never triggers repeated Vigil counter` |
| **P0.2 Brand (ADR-010)** | Marks board blocks; each marked block popped costs 2 Vigil (shortfall hits HP); previewed before tap | `Tile.isBranded`, `GroupResolution.brandedCoords`, `GroupResolution.brandVigilCost` | `BoardEngine.brandTiles`, `BoardEngine.groupAt`, `GameEngine.handleTapCell` | Crimson/gold Brand rune ring + `-2V` corner indicator + preview cost | `ADR-010 Brand marks board blocks previews 2 Vigil per block and charges Vigil before HP` |
| **P0.2 Fog (ADR-011)** | Veils board hue colors & Ink pools for 3 Taps while preserving glyphs; Gold Eye restores colors | `PlayerCombatState.fogTapsRemaining`, `CardDef.clearsFog`, `EnemyIntent.InflictFog` | `GameEngine.handleTapCell` decrements Taps; `Gold Eye` resets to 0 | `CombatArenaScreen` veils tile hues & Ink numbers (`?`) keeping glyphs visible | `ADR-011 Fog conceals colors and Ink pools for 3 Taps preserving glyphs until Gold Eye clears` |
| **P0.2 Seal (ADR-012)** | Locks a board column for 3 turns against taps, gravity, targeted effects, and Wall rise | `BoardState.sealedColumns`, `EnemyIntent.SealColumn`, `CardDef.unsealColumns` | `BoardEngine.groupAt`, `consumeAndCascade`, `riseIncomingRow`, `paintTiles`, `shatterStones`, `recolourTile` | Iron lock frame & `SEALED Nt` badge on column | `ADR-012 Seal locks column against taps gravity paint and Wall rise for 3 turns` |
| **P0.3 Wall Overflow (ADR-008)** | When top row has no empty cell: lose 8 Vigil (HP), burn top 2 rows (`Empty`), lose 1 Flask, skip rise (no Guard mitigation) | `TileKind.Empty`, `WallRiseOutcome.overflowTriggered`, `wallSkippedDueToOverflow` | `BoardEngine.riseIncomingRow`, `GameEngine.handleEndTurn` | Empty top sockets shown; incoming strip preserved on skip | `ADR-008 Wall overflow full top row burns top 2 rows deducts 8 HP and 1 Flask and skips rise` |
| **P0.4 Echo (ADR-015)** | Repeats only ONE primary card effect once; ignores Echo-of-Echo | `CardDef.primaryEffect: PrimaryEffect`, `PrimaryEffect` enum | `GameEngine.applyPrimaryEchoEffect` | Echo badge on card & single Echo log entry | `ADR-015 Echo card matrix repeats only primary effect once without duplicating costs or secondary effects` |
| **P0.5 Unlit Covenant & Dread (ADR-017)** | `THE_UNLIT` only; 3 consecutive Ash-only + 0-card turns vs Act III Boss; Hearthkeeper attacks fall on empty air; disqualifiers reset streak | `RunState.unlitCovenantActive`, `RunState.dreadConsecutiveAshTurns`, `CombatState.ashPoppedThisTurn`, `nonAshPoppedThisTurn`, `cardsPlayedThisTurn` | `GameEngine.handleEndTurn` evaluates after Enemy phase & checks reset triggers | Covenant switch locked to `THE_UNLIT`; streak banner in Act III Boss | `ADR-017 Unlit Covenant 3 consecutive Ash-only no-card turns trigger Dread true ending and resets break streak` |
| **P0.6 Command Guards** | Strict transactional transition guards reject out-of-order commands cleanly | `CommandRejectionReason`, `RunState.inNonCombatNode`, `activeEvent` | `GameEngine.validateTransition` | UI binds legal transitions only | `Command API rejects out-of-order progression reward shop campfire and event commands` |
| **P1.1 Unlit Starter (ADR-014)** | Exact 12-card Gold-frame starter (`4 Gold Strike`, `4 Ash Ward (Gold frame)`, `2 Pop Shot`, `1 Sift`, `1 Hollow Crown`) | `GameCatalog.starterDeckIds(THE_UNLIT)` | `GameEngine.startNewRun` | Codex + Deck modal | `ADR-014 Unlit starter has exact 12 Gold-frame card breakdown` |
| **P1.5 Node Scaling (ADR-013)** | `round(36 * 1.11^node)` (`x1.6` Elite, `x3.0` Boss) | `CampaignGenerator.scaledEnemyHp` | `GameEngine.startCombat` | Bestiary + Enemy HUD | `ADR-013 enemy HP scaling matches round(36 * 1.11^node) with Elite and Boss multipliers` |
| **P1.6 Event Nodes & Routes** | 15-row spire with `EVENT` nodes & O(V+E) DAG path-composition validation | `MapNodeType.EVENT`, `EventDef`, `RunState.activeEvent` | `CampaignGenerator.validateMap`, `GameEngine.handleChooseEventOption` | `EventScreen` (`AppDestination.Event`) | `Map generator enforces Event nodes and route composition bounds across all paths` |
| **UI & Persistence (P1)** | Defeat->RunSummary, serialized Mutex writes, Room migrations v1->v4 (no destructive drop), strict `RunSnapshotCodec` fail on unknown tiles, generic Ink picker, painterly WebPs | `EmberfallDatabase` v4, `RunSnapshotCodec` v3, `EmberfallRepository.writeMutex` | `EmberfallViewModel`, `EmberfallNavHost` | `CombatArenaScreen`, `ClassSelectScreen`, `EventScreen` | Robolectric & Room migration tests |

---

## 3. Explicit Provisional Items Awaiting `Design Bible v0.2` & `CH01`
1. **Ashen Monk & Quarry Witch Starter Cards (`P1.2`):** Exact canonical card names/damage numbers require `Design Bible v0.2`. Current 10-card Monk (`Ember/Moss` + Vigil burn) and Witch (`Tide/Violet` + Paint/Seal-cleanse) starters are tagged `[PROVISIONAL]`.
2. **Exact Numeric Constants (`P1.10`):** Ash Ward/Gold Strike damage values, Event option tuning numbers, and Combat Board base formula (`count * 2 + groupBonus`) are tagged `[PROVISIONAL]` until upstream Design Bible import.
3. **Room Schema (`UI-P1.9`):** `EmberfallDatabase` persists `active_run`, `shade_deck`, and `run_history` only. Per canonical no-meta-currency design, there is no separate meta-unlocks/currency table.


## 4. P0 Reward Selection Guard Follow-up — 2026-11-23

**Decision:** A support reward cannot be consumed by selecting an already socketed support or an invalid catalog ID. The command guard rejects both before mutation; a full socket set still requires an explicit valid replacement slot.

**Rationale:** The progression helper already treats duplicate/unknown support IDs as no-ops. Accepting those commands at the engine boundary would nevertheless mark the reward as picked, silently discarding the choice. Validation must reject the command atomically instead.

**Verification:** Regression tests cover duplicate and unknown support IDs, unchanged RunState on rejection, and the existing explicit replacement path.
