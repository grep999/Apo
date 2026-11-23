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
| True ending | Three consecutive Ash-only/no-card turns; Unlit Covenant unlocks route | Spec §§7, 9; ADR-017 |
| Onboarding | Fight 1 board-only; later fights progressively add Ink/cards/keywords | Spec §§13, 16 |
| Store target | API 36+ at submission from 2026-08-31 | Official policy; ADR-020 |

## Product Owner clarifications

- Initial board reuses the existing 35-cell opening topology (rows 2–6 filled), but all live interaction is group-pop.
- Prism resolves into one base group and never bridges different hues.
- Stone breaks in one snapshot batch; no recursive same-action chain.
- Seal retains the established frozen-column contract from engine and tests.
- Overflow retains the established one-Flask penalty alongside the explicit 8-Vigil/two-row/skip-rise behavior; it is now explicit in ADR-008.
- The Unlit starter deck is exactly 12 cards; Ash Alchemy is the signature card, Hollow Crown the signature relic.
- The Fast does not require all three Taps; at least one Ash Pop per qualifying turn, no non-Ash Pop and no card cast.
- The Unlit Covenant is the route gate, not the The Unlit hero class.

## Explicit non-goals

No swap-match-3 live combat, no horizontal board slide, no short-crawl mode in M0/M1, no fourth playable hue, no meta-currency, no ads/energy model, no mandatory account/network dependency, no premature iOS renderer choice, no unbounded Echo or recursive effect chains.
