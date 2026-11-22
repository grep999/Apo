# Execution Evidence — Emberfall v5

Target archive: `/mnt/data/emberfall-5.zip`  
SHA-256: `bdb84a6b1febad79d3a79948199fcffbdafbbcb67c663ad7c13d485abb6cc75e`

## Freshly executed

1. `kotlinc -Werror app/src/main/java/com/example/engine/*.kt -d /tmp/ef5build/engine.jar` — PASS.
2. Compiled v5 `EmberfallEngineTest.kt` against that engine and invoked all `@Test` methods with a local JUnit-compatible shim/reflection runner — 37 PASS, 0 FAIL.
3. `kotlinc -Werror app/src/main/java/com/example/audio/MinimalTechnoSynth.kt -d /tmp/ef5build/audio.jar` — PASS.
4. Compiled and ran `MinimalTechnoSynthTest.kt` — 3 PASS, 0 FAIL.

Combined: 40/40 pure JVM tests pass.

## Not executed

- Gradle `:app:testDebugUnitTest`.
- Android app compilation / `assembleDebug`.
- Robolectric / Compose tests.
- Room migration integration tests.
- Instrumented Android test.
- Emulator/device audio playback and focus lifecycle.

Reason: archive has no `gradlew` or `gradle-wrapper.jar`; no Gradle executable or Android SDK is configured in the available environment. No `.github/workflows` CI definition is included in the archive.

The local JUnit shim is only a lightweight test invocation adapter; it is not a substitute for Gradle, Robolectric, AndroidX, Room, or device execution.
