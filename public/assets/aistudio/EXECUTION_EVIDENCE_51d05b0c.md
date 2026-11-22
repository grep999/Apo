# EMBERFALL v4 — Execution Evidence

Target archive SHA-256:
`fca382562bf08b8c086158950a69b169d03ffa4679370909117a9fa975593d7d`

## Environment
- Java: 21
- Kotlin/JVM: 1.9.0
- Gradle: not installed
- Android SDK: not configured
- Gradle wrapper scripts/JAR: absent
- GitHub Actions workflow in uploaded archive: absent

## Compilation
- 9 pure engine Kotlin files compiled with `kotlinc -Werror`: PASS
- Pure `MinimalTechnoSynth.kt` compiled with `kotlinc -Werror`: PASS

## Test execution
- `EmberfallEngineTest.kt`: 30/30 PASS
- `MinimalTechnoSynthTest.kt`: 3/3 PASS
- `ExampleUnitTest.kt`: 1/1 PASS
- Total executed: 34/34 PASS

Tests were compiled against a local minimal JUnit-compatible annotation/assertion shim and invoked via reflection. This is not an official Gradle/JUnit execution.

Not run:
- 9 Robolectric/Compose tests
- 1 Android instrumented test
- Android app build
- Room/Compose integration
- Real device SoundPool/AudioTrack playback

## Adversarial probes
1. Enemy SealColumn(3) -> post-cleanup value 2.
2. Brand Vigil shortfall reduces player HP to zero on same Tap that kills enemy -> engine returns COMBAT_WON, gameOver=null.
3. Sparse board with six sealed columns -> rescue returns didReshuffle=true but hasAnyValidMove=false.
4. Branded Ash pop spends Vigil while Dread streak is 2 -> streak remains 2 and next EndTurn awards Dread ending.

## Static checks
- 10 XML files parsed.
- 4 JSON files parsed.
- 0 missing R.drawable/string/font references.
- 0 Android/AndroidX/Room imports in pure engine.
- 65 unique cards; 22 unique relics; 6 unique remnants; 20 unique enemies; 12 unique events.
- All 22 relic IDs have non-catalog code references.
