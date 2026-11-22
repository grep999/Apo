# Emberfall repository instructions

Read `README.md`, `docs/RESTORATION_AUTHORITY_AND_MANDATE.md`, `docs/RESTORATION_STATUS.md`, and `docs/EXECUTION_BLOCKERS.md` before changing the project. The source specification and accepted ADRs govern gameplay; do not infer mechanics from concept art or stale implementation plans.

Keep the deterministic Kotlin engine in `app/src/main/java/com/example/engine/` independent of Android, Compose, persistence and audio. Treat UI as a projection of engine state. Preserve deterministic RNG, transactional commands, persistence/replay contracts and the three canonical classes. Add regression tests for every gameplay or lifecycle change.

Never commit `.env`, API keys, App Check debug tokens, signing keys, keystores, `local.properties`, or build outputs. Use GitHub Actions secrets only for actual secrets. Firebase `google-services.json` is client configuration, not a substitute for restricting API keys in Google Cloud.

Use the committed Gradle wrapper. Required baseline checks are `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug :app:lintRelease`, and `./gradlew :app:assembleDebug`. Release artifacts built with the CI-only key are verification artifacts, not production-signed releases.

Follow the preserved handoff and Maestro materials under `docs/handoff-v1/` where still applicable. Report VERIFIED, OBSERVED, INFERRED, PLANNED and BLOCKED separately. Never claim an unrun check passed; never self-approve a candidate; do not merge or publish without owner authorization.
