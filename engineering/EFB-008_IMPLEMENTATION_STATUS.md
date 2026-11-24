# EFB-008 — Android Playable Combat Shell

**Status:** IMPLEMENTED — Android CI pending  
**Branch:** `feat/efb008-android-playable-shell`  
**Base:** `feat/efb007-enemy-encounters`

## Delivered

- Added a native Android application module using Kotlin 1.9.24, Android Gradle Plugin 8.7.3 and Jetpack Compose.
- Portrait-only Android app, min API 24, target API 35.
- Class select uses canonical Ashen Monk → Quarry Witch → The Unlit order and displays each signature relic.
- Starts a deterministic seeded Cinderling combat from the selected class deck and class relic.
- Initial board is generated from the selected deck's actual hue profile.
- Combat screen renders 49 board cells with distinct glyphs for five playable hues and Ash/Stone/Prism, Vigil/Block/Ink/Spark/Taps, enemy health and the next two intents.
- Input dispatches through CardEngine commands: Tap/Pop, End Weave, card play with board targeting, Chisel Wand, End Cast, Wall rise, enemy resolution and turn cleanup.
- Wall rows are generated deterministically from the deck hue profile.
- No Android or Compose imports were added to the game-core module.

## Explicit limits

This is the first playable combat shell, not the complete game. Run map, rewards, events, bonfires, Shade, boss phases, save/resume, audio, production art package integration, accessibility audit and device performance profiling remain outstanding. The app currently keeps the session in memory and uses a temporary code-defined visual skin; it does not claim the approved graphics bundle has been imported.

## Verification

`.github/workflows/efb008-android.yml` runs the full Kotlin/JVM test suite and builds `:android-app:assembleDebug` on the exact SHA.
