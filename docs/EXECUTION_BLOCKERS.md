# Emberfall completion execution blockers

Prepared 2026-11-22 against the supplied `emberfall-8.zip` snapshot.

## Environment facts

- JDK available in the execution container: OpenJDK 21.
- Project-declared Gradle distribution: 9.3.1 (`gradle/wrapper/gradle-wrapper.properties`).
- `gradle` executable: absent.
- `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`: absent.
- Cached Gradle distribution / wrapper JAR: not found.
- Android SDK platform/build-tools: not installed or discoverable.
- `adb`, `sdkmanager`, and Android emulator: unavailable.
- Outbound DNS/network: unavailable (`services.gradle.org` could not be resolved).
- Release keystore and signing credentials: not supplied.
- Local Git repository: initialized from the supplied archive; no upstream remote or authoritative branch was supplied.

## Consequence

The requested Gradle test, lint, debug build, release build, APK/AAB signing, installability check, and device scenarios could not be executed. The existing historical report of 86 passing tests is not evidence for the modified source snapshot. The modified source now contains 87 declared JUnit tests, but those tests are **not executed**. The persistence request queue is still unbounded; its accepted writes are drained on ViewModel teardown and prior write failures are reported by the explicit flush barrier, but a sustained producer/slow-storage imbalance still needs bounded/coalesced queue design and stress testing.

## Required external completion steps

1. In a network-enabled Android development environment, run `gradle wrapper --gradle-version 9.3.1` and commit the generated wrapper scripts/JAR.
2. Install Android SDK Platform 36 (extension 36.1) and compatible build tools; configure `local.properties` locally (do not commit machine-specific paths).
3. Run `./gradlew :app:testDebugUnitTest --console=plain` and `./gradlew :app:lintDebug`.
4. Run `./gradlew :app:assembleDebug` and install the APK on API 24+ devices/emulators.
5. Configure release signing through `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in the environment or secrets manager; do not put credentials in source control.
6. Run release build/lint, inspect the R8 mapping and dependency reports, install the signed release APK, and verify the AAB with bundletool/Play Console internal testing.
7. Complete real-device lifecycle, backup/device-transfer, accessibility, audio, large-screen, and long-session validation.

The GitHub Actions workflow was extended to run debug/release lint and debug/release packaging with an ephemeral CI-only signing key. This is a future verification path, not evidence of a completed run.

Until those steps pass, the release verdict remains **NOT RELEASE READY**.
