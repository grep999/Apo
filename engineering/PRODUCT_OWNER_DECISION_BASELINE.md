# EMBERFALL — Product Owner Decision Baseline

**Date:** 2026-11-23  
**Authority:** Product Owner  
**Governing source:** `references/EMBERFALL_GAME_SPEC_v0.2.txt`

## Settled by source or owner direction

| Area | Product baseline | Disposition |
|---|---|---|
| Core verb | Connected-group Tap/Pop; swap-match-3 is not the v0.2 loop | Spec §§1, 3, 14 |
| Board | 7×7, orthogonal groups ≥2, vertical gravity only, one incoming row | Spec §§2–3 |
| Turn | Draw 5 → Weave (3 base Taps) → Cast → Wall → Enemy → Cleanup | Spec §2 |
| Hues | Five playable hues; Ash/Stone/Prism neutral | Spec §§2–3 |
| Deck/board | Card frame hue weights board spawns; Pops generate Ink; cards spend Ink and reshape board | Spec §§1–4 |
| Enemy information | Two intents shown ahead | Spec §§1, 5 |
| Run | Three acts, about 30 minutes, saved after every node | Spec §§1, 7 |
| Platform | Android first, portrait; iOS/gamepad 1.0 target, M2 architecture gate | Spec §§1, 14, 16 |
| Monetization | Premium; no ads, energy timer or random-reward purchases; no meta-currency in v1 | Spec §§1, 7, 18 |
| True ending | The Unlit class + Unlit Covenant; three consecutive turns with ≥1 Ash Pop, no non-Ash Pop, no card, no Vigil spend, no Flask and no Overflow; unused Taps allowed | Existing engine contract + spec §§7, 9; ADR-017 |
| Onboarding | Fight 1 board-only; later fights progressively add Ink/cards/keywords | Spec §§13, 16 |
| Store target | API 36+ at submission from 2026-08-31 | Official policy; ADR-020 |

## Product Owner clarifications

- Initial board reuses the existing 35-cell opening topology (rows 2–6 filled), but all live interaction is group-pop.
- Prism resolves into one base group and never bridges different hues.
- Stone breaks in one snapshot batch; no recursive same-action chain.
- Seal retains the established frozen-column contract from engine and tests.
- Overflow retains the established one-Flask penalty alongside the explicit 8-Vigil/two-row/skip-rise behavior; it is now explicit in ADR-008.
- The Unlit starter deck is exactly 12 Gold-frame cards: 4 Gold Strike, 4 Ash Ward, 2 Pop Shot, 1 Sift, 1 Hollow Crown card; the separate Hollow Crown signature relic is also equipped. Preserve both IDs/effects; naming review is queued.
- The Fast does not require all three Taps; at least one Ash Pop per qualifying turn, no non-Ash Pop, no card, no Vigil spend, no Flask and no Overflow.
- Both The Unlit hero class and the Unlit Covenant are required, as established in the engine and decision log.

## Explicit non-goals

No swap-match-3 live combat, no horizontal board slide, no short-crawl mode in M0/M1, no fourth playable hue, no meta-currency, no ads/energy model, no mandatory account/network dependency, no premature iOS renderer choice, no unbounded Echo or recursive effect chains.
