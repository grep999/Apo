# Emberfall

Emberfall is a single-player Android puzzle-board roguelike deckbuilder. Its core experience combines a deterministic 7×7 elemental match-3 board, turn-based card casting, three playable classes, a three-Act branching campaign, local run persistence, and an offline procedural audio engine. An optional Firebase AI Logic soundtrack path is present in the current source snapshot.

## Project structure

- `app/src/main/java/com/example/engine/` — pure-JVM game rules, board engine, campaign generation, content catalogs, and deterministic simulation.
- `app/src/main/java/com/example/ui/` — Jetpack Compose screens, navigation, components, and ViewModel.
- `app/src/main/java/com/example/data/` — Room persistence, snapshot codec, and DataStore preferences.
- `app/src/main/java/com/example/audio/` — procedural audio, SFX, and optional generated soundtrack support.
- `app/src/main/res/` — runtime artwork, typography, themes, and Android resources.
- `app/src/test/` — JVM and Robolectric regression tests.
- `docs/RESTORATION_AUTHORITY_AND_MANDATE.md` — controlling design and restoration authority.
- `docs/RESTORATION_STATUS.md` — restoration status and latest completion delta.
- `docs/EXECUTION_BLOCKERS.md` — exact build/device/signing verification blockers for this handoff.

## Toolchain

- JDK 21 for the current Gradle/Android toolchain.
- Gradle 9.3.1 (declared in `gradle/wrapper/gradle-wrapper.properties`).
- Android SDK Platform 36, extension 36.1, and matching build tools.
- Android Studio or a configured command-line Android SDK.

**Wrapper bootstrap:** the original source archive omitted `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`. The GitHub import workflow generates and commits the pinned Gradle 9.3.1 wrapper before running the build. If bootstrapping manually, use:

```bash
gradle wrapper --gradle-version 9.3.1
```

Then run:

```bash
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

## Release signing

The release build reads signing configuration from environment variables:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_PASSWORD`

The key alias is `upload`. Supply production credentials through a secrets manager or protected environment; never commit keystores, passwords, App Check debug tokens, or private keys. The CI workflow creates an ephemeral verification-only key and its artifacts are not production upload artifacts.

## Product and data behavior

- One-tap board firing defaults to **OFF**; explicit saved player preferences are retained.
- Run progression is stored locally in Room; player preferences use DataStore.
- Backup rules include the Room database and DataStore preferences while excluding generated soundtrack cache files.
- The core board/game engine and procedural audio path operate offline. The optional generated soundtrack path requires the configured Firebase project and network access.

## Verification status

The supplied environment had no Gradle executable, wrapper JAR/distribution, Android SDK, ADB, emulator, network/DNS, release keystore, or signing credentials. Therefore the modified source has not been built, its 87 declared tests have not been executed, and no APK/AAB has been generated or device-verified. See `docs/EXECUTION_BLOCKERS.md` before treating this source as releasable.
