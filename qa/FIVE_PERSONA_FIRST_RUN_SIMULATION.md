# Emberfall — Five-Persona First-Run Simulation

**Type:** Desk-based simulated playtest against the written game specification  
**Date:** 2026-11-22  
**Source branch:** `handoff/emberfall-coding-agent-v1.0`  
**Source commit inspected:** `fe94e61e3d676a57d45aec5f11022c54dbb79b95`  
**Candidate branch:** `qa/five-persona-first-run-audit`  
**Evidence class:** INFERRED / ASSUMED — not observed gameplay, not a build test, and not user research.

## 1. Scope and evidence rules

This report evaluates the first-time experience described in `references/EMBERFALL_GAME_SPEC_v0.2.txt` and the repository's handoff instructions. The remote branch contains design and engineering documents, not a playable Android application. There is no APK, runnable game, Android project, store listing, or executable test suite in this branch. Therefore:

- No persona actually installed or played Emberfall.
- No UI, animation, frame rate, touch accuracy, crash behavior, purchase flow, or save/restore behavior was observed.
- Reactions in quotation marks are **simulated voice-of-customer hypotheses**, not real quotes.
- Device, connection, and personal circumstances below are test-profile assumptions chosen to expose different risks.
- Rules marked OPEN / BLOCKING in `engineering/OPEN_DECISIONS.md` remain unresolved. This report does not silently decide them.

### Documented product facts used

- Android-first, portrait-only, premium game; no ads, energy timers, or random-reward purchases.
- Intended audience spans Slay the Spire / Balatro players and color-puzzle players seeking more depth.
- Core loop: draw 5; make 3 group-pop taps; spend hue-specific Ink on cards; wall rises; enemy intent resolves.
- First fight teaches board pops and chip damage only. Fight 2 introduces Ink and one card type; later fights introduce one keyword at a time.
- Hold a highlighted group to preview size, Ink, and Run result; tap to pop. Drag cards to enemy, board, or self; long-press to read; tap sigil to end turn.
- Optional confirm-Tap mode and one Undo per turn before any card is played.
- Three classes, 12-card starting decks, 7×7 board plus hidden row, five playable hues and three neutral block types.
- Visible enemy intents two steps ahead; map, fights, elites, bonfires, shops, events, treasure, bosses, card rewards, relics, flasks, death and Shade.
- Approximate full run target: 30 minutes; save after every node.
- Accessibility intentions include glyphs per hue, scalable text, remappable layout, reduced motion/flashing, haptics off, and one-handed reach.
- Backend scope is daily seeds, leaderboards and replay validation; no account required. Whether the core game is fully playable offline is not specified.

## 2. Persona findings

## Persona 1 — The systems-first roguelike veteran

**Profile (ASSUMED):** 32-year-old strategy/roguelike regular. Plays 45–90 minute evening sessions on a recent flagship Android phone over stable Wi-Fi. Reads tooltips, experiments with builds, tolerates early difficulty if decisions are meaningful. Expects coherent rules, strategic agency, fair telegraphing, and a reason to replay.

**First-time journey (inferred from spec):**

1. **Discovery / install:** The premium, no-ad positioning is attractive. The repository has no store listing, price, screenshots, or review copy, so the actual conversion pitch cannot be assessed.
2. **First launch / onboarding:** The player expects a concise explanation of the unusual deck-paints-board premise. Fight 1 deliberately withholds Ink and cards. This may feel like a tutorial that hides the differentiator unless the game clearly frames the board-only encounter as a deliberate lesson.
3. **Fight 1:** They test group size, gravity, and the three-tap limit. Hold-to-preview is valuable, but the spec does not state the exact first-board layout, whether legal groups are highlighted continuously, or what feedback teaches the player why one pop is better than another.
4. **Fight 2:** Ink and a card arrive. The player starts connecting hue choice, Ink economy, card effects, and future spawn weights. This is the hook. The same player will immediately probe whether the rules are deterministic and legible.
5. **Progression:** Map branching, card choice, bonfire decisions, shops, relic synergies, and Shade inheritance are attractive. But 18 gameplay decisions are still open/blocking in the decision register, including Prism, Run reset, Stone chain order, Spark payment, and enemy timing. A veteran will notice inconsistencies quickly.
6. **Monetization:** Premium/no-ads is a positive signal. Price and purchase UX are unknown; no in-run monetization is specified.

**What works (hypothesis):**
- The deck-to-board feedback loop has a distinctive strategic promise.
- Two-step enemy intents support planning rather than surprise damage.
- Shade can make a failed run feel narratively and mechanically consequential.

**Confusion / frustration risks:**
- If the first fight feels like a generic color-pop tutorial, it delays the game's unique proposition.
- Ambiguous rules will be interpreted as bugs or unfair RNG, not as onboarding gaps.
- The listed content targets (360 cards, 150 relics) are aspirational, not evidence of shipped depth.

**Simulated reaction:** “The idea is strong. Show me the deck changing the board early, then give me exact rules. I don't want to lose because Prism or the Run reset behaved differently from what the preview implied.”

**Return likelihood:** **Medium–high if rule execution is consistent; low if ambiguity reaches combat.** The concept is appealing, but this audience has a low tolerance for opaque resolution.

**Real test to run:** Ask the player to explain why a chosen pop is optimal, predict its Ink and Run result, and predict the enemy's next action before committing.

---

## Persona 2 — The short-session commuter

**Profile (ASSUMED):** 27-year-old casual puzzle player with limited roguelike experience. Plays 3–8 minute bursts on a mid-range Android phone while commuting, often with interruptions and variable 4G. Wants quick comprehension, satisfying actions, reliable resume, and progress that survives interruptions.

**First-time journey:**

1. **Discovery / install:** Premium may be acceptable if the listing communicates a complete game and the 30-minute run target does not sound like a 30-minute uninterrupted commitment. Store copy and price are absent.
2. **First launch:** They want to start playing immediately. No launch flow, consent screens, or tutorial presentation is documented; these cannot be judged.
3. **Fight 1:** Board-only onboarding is a sensible cognitive ramp. The three-tap constraint may be easy to understand if the UI visibly counts remaining taps. A 7×7 board with five hues plus neutral block types may still look busy to a novice.
4. **Fight 2:** Introducing Ink and one card type is a reasonable staged reveal. But if the first card requires dragging to one of three target surfaces, the player may not infer the valid target without affordances.
5. **Between fights:** Map, card reward, bonfire, shop, and events create interesting choices but can make a short session feel like several menus between the satisfying board interactions.
6. **Interruption / return:** Saving after every node is promising. The spec does not explicitly define mid-combat persistence, process-death recovery, or behavior when the app is backgrounded mid-turn. Do not equate node saves with mid-fight resume.
7. **Connectivity:** Daily seeds, leaderboards, and replay validation imply online features, but offline behavior and retry semantics are unspecified.

**What works (hypothesis):**
- Portrait one-thumb layout and lower-third reach are aligned with commuter play.
- Staged tutorial reduces initial rules load.
- Node-level saves support stopping between encounters.

**Confusion / frustration risks:**
- A 30-minute run may conflict with short bursts unless nodes are independently resumable and session length is communicated.
- A commute interruption during combat could lose a turn or progress if only node boundaries are saved.
- Map/reward/shop navigation may dominate the time available for actual play.

**Simulated reaction:** “I like the puzzle, but I need to put my phone away in 20 seconds. If I come back and the fight reset, I'm not doing another run.”

**Return likelihood:** **Medium, conditional on interruption-safe resume and low menu friction.** The loop could fit short sessions, but current persistence claims only guarantee node-level saves.

**Real test to run:** Background the app at multiple points (tutorial, mid-turn, card targeting, reward, map), kill the process, relaunch, and verify exactly what state is restored.

---

## Persona 3 — The impatient, high-tempo casual player

**Profile (ASSUMED):** 19-year-old mobile-first player accustomed to fast feedback and simple controls. Plays 5–15 minute sessions on an older entry-level Android device, frequently switches apps, taps quickly, and abandons games after repeated friction. Wants immediate clarity, responsive interactions, and visible payoff.

**First-time journey:**

1. **Discovery / install:** They decide quickly from icon, screenshots, rating, size, and price. None of those store assets or metadata are in the handoff branch.
2. **First launch:** Any lengthy exposition is a likely exit point. The spec defines what is taught, not how much text or how many steps the tutorial uses.
3. **Fight 1:** Three taps per turn may feel restrictive unless each pop has strong feedback and the player understands why the limit exists. Hold-to-preview is useful but requires patience; optional confirm-Tap could feel like an extra step if enabled by default.
4. **Fight 2:** Drag-and-drop card targeting can be slower and less forgiving than tap-to-select then tap-target. The spec mandates drag, but does not document a tap alternative or misdrop recovery.
5. **Enemy turn / wall rise:** The wall rising every turn is a clear pressure mechanic on paper. If its animation delays the next action, this persona will perceive sluggishness. Animation timing is not specified.
6. **Progression:** Reward picks, shops, and map nodes may feel like interruptions if the player wants another quick board interaction.
7. **Device performance:** A 60 fps target on a four-year-old mid-range phone is documented as a target, not a measured result. It does not prove smooth performance on an older entry-level device.

**What works (hypothesis):**
- Tap-to-pop is immediately familiar.
- The three-action turn creates a clear rhythm.
- Visible enemy intent can make the next action feel urgent.

**Confusion / frustration risks:**
- Long-press, drag, and hold gestures may be undiscovered or conflict with fast touch habits.
- Mis-taps on 44 dp cells can be costly; undo only applies to the last Tap before any card is played.
- Repeated menus or lengthy animations may break momentum.

**Simulated reaction:** “Why do I have to drag this? Let me tap the card, tap where it goes, and get back to the board.”

**Return likelihood:** **Low–medium.** The basic interaction is approachable, but input friction and pacing could lose this audience before the deeper system becomes rewarding.

**Real test to run:** Observe first-use control discovery without coaching. Count wrong targets, cancelled drags, accidental pops, time-to-first-successful-card, and time spent waiting on transitions.

---

## Persona 4 — The accessibility-conscious, one-handed player

**Profile (ASSUMED):** 41-year-old player with reduced color discrimination and mild hand-mobility constraints. Uses a mid-range Android phone one-handed, text enlarged, motion reduced, haptics disabled. Plays 15–25 minute sessions. Wants independent hue recognition, reliable touch targets, and settings that work consistently.

**First-time journey:**

1. **Discovery / install:** Accessibility claims in the listing would matter, but the store listing is unavailable. The spec's accessibility commitments are encouraging but unverified.
2. **First launch:** They look for text scaling, reduced motion, flashing reduction, haptics, and remappable layout. The spec says these are supported intentions; it does not define where settings live or whether they are available before the tutorial.
3. **Fight 1:** Unique glyphs per hue are essential. The player must distinguish Ember, Tide, Moss, Gold, Violet, Ash, Stone, and Prism without relying on color alone. Fog hides hues for three taps, so the remaining glyph and interaction cues need explicit definition.
4. **Board interaction:** 44 dp cells are a documented starting size. On a 7×7 board, small targets and accidental neighboring-group selection are risks, especially one-handed. Hold-to-preview may be difficult for users with limited dexterity.
5. **Cards:** Dragging cards to enemy/board/self is a potential motor barrier. Long-press to read may conflict with the long-press gesture used elsewhere.
6. **Progression:** One-handed reach is a stated requirement for hand and End Turn in the lower third. The map, shop, and event layouts are not specified enough to verify the same reachability outside combat.

**What works (hypothesis):**
- Glyphs plus hue, scalable text, reduced motion, and haptics controls are the right requirements.
- Portrait mode and lower-third reach can support one-handed play.

**Confusion / frustration risks:**
- “Unique glyph per hue” is not enough unless glyphs remain visually distinct at actual board-cell scale and under Fog.
- Long-press for preview/read and drag-to-target need accessible alternatives.
- No contrast, minimum target-size, screen-reader, or focus-order acceptance criteria are specified.

**Simulated reaction:** “Don't tell me the colors are accessible. Let me identify every block when the screen is dim, the hues are hidden, and my text is enlarged.”

**Return likelihood:** **Unknown / conditional.** The spec acknowledges accessibility but does not provide implementation evidence or a complete interaction alternative set.

**Real test to run:** Test with grayscale/color-vision simulation, enlarged text, reduced motion, one-handed reach, TalkBack, and actual users with relevant access needs. Verify Fog does not erase information required to act.

---

## Persona 5 — The cautious budget-device owner

**Profile (ASSUMED):** 54-year-old player who enjoys thoughtful puzzles but is not a genre specialist. Uses a 5-year-old low-cost Android phone with limited free storage, intermittent mobile data, battery concerns, and modest patience for technical problems. Prefers one clear upfront purchase over ads, subscriptions, or pay-to-win systems.

**First-time journey:**

1. **Discovery / install:** Premium/no ads/no energy timers/no random-reward purchases is a strong fit. But price, app size, minimum device requirements, supported Android versions, and refund expectations are not stated in the listing because no listing is present.
2. **Install / first launch:** The spec targets Android API 24 and target API 35 in its product section, while the open-decisions register says target API 36+ for release. This inconsistency must be resolved in the actual project. Storage footprint and install time are unknown.
3. **Fight 1:** A simple board-only tutorial may be manageable. Small text, complex color effects, or excessive animation could make it harder, but no runtime evidence exists.
4. **Fight 2 onward:** Cards, hue-specific Ink, Run order, enemy intents, and neutral block rules accumulate. The tutorial's one-keyword-at-a-time ramp helps, but the player may need a persistent glossary and replayable lessons.
5. **Progression / monetization:** Premium is understandable and respectful. The exact price and whether purchase is required before trying the game are unknown. No recurring monetization is specified.
6. **Connectivity:** No account is required; daily seeds, leaderboards, and replay validation are backend features. The spec does not say whether an offline player can start and complete a full run or what happens if connectivity drops.
7. **Performance / battery:** The performance goal is 60 fps on a four-year-old mid-range phone, not a verified result on this older low-cost device. Battery and thermal behavior are unmeasured.

**What works (hypothesis):**
- Clear premium model avoids pressure mechanics.
- No account requirement reduces onboarding friction.
- A persistent run and staged teaching could suit deliberate play.

**Confusion / frustration risks:**
- Unclear offline support is a significant trust risk for intermittent connectivity.
- Large asset bundles or heavy visual effects may exclude older hardware; package size and performance are unknown.
- Too many new rules can overwhelm a player who does not already know deckbuilders.

**Simulated reaction:** “I'll pay once if the game is complete. But tell me how big it is, whether it works offline, and whether my phone can run it before I buy.”

**Return likelihood:** **Medium if compatibility, offline behavior, and teaching are explicit; low if the game stutters or connectivity blocks a run.**

**Real test to run:** Install on the oldest supported physical device with constrained storage and intermittent connectivity. Measure install size/time, launch time, memory, frame pacing, battery, and full-run offline behavior.

---

## 3. Cross-persona issues ranked by retention impact

Rank is a hypothesis based on likely breadth and severity, not measured frequency.

| Rank | Issue | Personas affected | Retention impact | Evidence status |
|---:|---|---|---|---|
| 1 | Core rules and P0 ambiguities can make outcomes feel inconsistent (Prism, Run reset, Stone order, Spark, enemy timing, etc.) | 1, 2, 4, 5 | Critical: undermines trust in the central puzzle | OPEN / BLOCKING decisions documented |
| 2 | The deck-paints-board hook may arrive too late if the first fight feels disconnected from the full game | 1, 2, 3, 5 | High: delays the reason to keep playing | Tutorial sequence documented; player reaction inferred |
| 3 | Touch interaction and gesture discoverability (hold, long-press, drag, undo) | 2, 3, 4 | High: repeated input errors create immediate friction | Controls documented; implementation unverified |
| 4 | Interruption-safe persistence is not guaranteed by “save after every node” | 2, 3, 5 | High for mobile usage: lost progress causes abandonment | Mid-combat resume unspecified |
| 5 | Accessibility is a requirement list, not yet measurable acceptance criteria | 4, 2, 5 | High for affected players; broad usability impact | Spec requirements documented; implementation unverified |
| 6 | Performance and compatibility targets are not test evidence | 3, 4, 5 | High if frame pacing, heat, or crashes fail | Targets documented; no device evidence |
| 7 | Offline behavior, package size, and premium price are unspecified | 2, 5 | Medium–high: purchase/install uncertainty | Not present in inspected handoff |
| 8 | Map/reward/shop/menu pacing may interrupt short-session flow | 2, 3 | Medium: session friction | Structure documented; pacing unmeasured |

## 4. Prioritized changes and verification work

### P0 — Before interpreting any playtest as valid

1. **Resolve gameplay P0s that affect player-visible outcomes.** At minimum: initial board topology, Prism grouping, first Spectrum pop/reset, Ash chip, Stone chain order, wall orientation/overflow, Spark payment, Brand penalty, Fog information, Seal behavior, enemy Vigor/Block timing, and effect queue/Echo limits. Each resolution needs an ADR and table-driven tests.
2. **Make the tutorial teach the actual differentiator early.** Keep fight 1 simple, but explicitly frame it as learning the board; make fight 2 visibly demonstrate the board → Ink → card → board-spawn feedback loop. Do not add a full rules dump.
3. **Define input recovery and alternatives.** Specify tap-to-select/tap-target as an alternative to drag, clear legal-target affordances, cancellation, and what exactly Undo can reverse. Validate gesture conflicts between preview and card reading.

### P1 — Before external beta

4. **Specify persistence guarantees.** Decide and test whether state is saved mid-combat, on background, on process death, and after interrupted transitions—not only after a node.
5. **Turn accessibility claims into acceptance criteria.** Define glyph legibility at 44 dp, minimum touch targets, scalable-text layout limits, Fog behavior, TalkBack/focus behavior, and non-drag alternatives.
6. **Set real device gates.** Test 60 fps/frame pacing on the stated four-year-old mid-range target and at least one lower-end supported device. Measure memory, thermal behavior, battery, startup, and crash-free sessions. Do not infer device performance from the target statement.
7. **Document offline and network degradation.** State what works offline, what syncs later, and how failed leaderboard/replay calls affect the run. A failed network request must not silently destroy local progress.
8. **Publish compatibility and purchase facts.** Confirm min/target SDK, app size, device support, premium price, and the exact purchase gate before store testing.

### P2 — Tune after the first external cohort

9. Measure tutorial completion, time to first successful pop, time to first card cast, illegal taps, undo use, card-target errors, fight abandonment, return after interruption, and first-run completion.
10. Measure map/reward/shop time against combat time. Reduce unnecessary transitions if menus dominate the short-session experience.
11. Run seeded balance sweeps and human playtests separately. Bot win rates and pick rates are not substitutes for comprehension, delight, or perceived fairness.

## 5. Concrete first-run test protocol

Run this protocol with at least five real participants matching the profiles above. Do not coach unless they are blocked; record the point and exact wording of any intervention.

| Checkpoint | Observe / record | Pass evidence |
|---|---|---|
| Store page | Can they state genre, core hook, price, offline expectations, and device requirements? | Accurate expectation before install |
| First launch | Time to first playable input; skipped/read tutorial content; settings discovery | No unexplained dead end; first action within a predeclared target |
| First pop | Can they identify a legal group and predict the result? | Correct prediction without coaching |
| Preview | Do they discover hold-to-preview? Does it conflict with other gestures? | Preview used correctly and cancelled safely |
| Fight 1 end | Can they explain the three-tap limit and board pressure? | Correct mental model |
| Fight 2 | Can they explain Ink and cast the introduced card? | Successful cast with correct target |
| Enemy intent | Can they predict next intent and choose a counter? | Correct intent interpretation |
| Progression | Can they distinguish card reward, bonfire, shop, event, relic, and Flask choices? | Understands consequences before choosing |
| Interruption | Background, process kill, relaunch, network loss | No unexplained loss or corrupted run |
| Return decision | Ask “Would you open this tomorrow? Why/why not?” | Verbatim reason recorded; do not lead the answer |

### Minimum event instrumentation (privacy-conscious)

Use anonymous, opt-in event data only. Capture:
- `tutorial_step_started/completed`
- `first_legal_pop`, `invalid_tap`, `preview_used`, `undo_used`
- `first_card_cast`, `card_target_cancelled`, `card_target_error`
- `fight_started/completed/abandoned`
- `node_saved`, `resume_success/failure`
- `network_request_failed` with feature category only
- `session_duration`, `return_within_24h`

Do not collect personal identifiers or infer sensitive traits. Ask participants directly about accessibility needs only when recruiting an accessibility study and with appropriate consent.

## 6. Limitations and next gate

This is a **spec-based design simulation**, not a test execution report. No APK was installed; no Android emulator, physical device, Gradle task, instrumentation test, or game interaction was run. No retention rate or usability score can honestly be claimed from this exercise.

The remote handoff branch does not contain playable Android source. The next executable gate is to bring the actual app source and assets into a repository/workspace with a reproducible Gradle build, then run deterministic engine tests, Android tests, and the five-person physical-device protocol above.

**Decision:** The design has a testable central hook, but it is not ready for a real first-time-user verdict until the P0 rules are resolved and a playable build is available.
