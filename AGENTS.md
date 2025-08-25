# Repository Guidelines

## Project Structure & Module Organization
- Modules: `smarttubetv` (app), `common` (shared core), `chatkit`, `leanback-1.0.0`, `fragment-1.1.0`, `filepicker-lib`, and vendored `exoplayer-amzn-2.10.6`. External submodules: `MediaServiceCore`, `SharedModules`.
- App sources: `smarttubetv/src/main/{java,res,assets}` with product flavors in `smarttubetv/src/<flavor>` (e.g., `stbeta`, `ststable`, `storig`, etc.) overriding manifests/resources.
- Build config lives in root `build.gradle`, module `build.gradle`, and `gradle.properties`. Images/docs in `images/`. Helper scripts in `scripts/`.

## Build, Test, and Development Commands
- Build all: `./gradlew clean build` (Windows: `gradlew.bat`).
- Assemble APKs per flavor: `./gradlew :smarttubetv:assembleStbetaRelease` (or `assembleStstableRelease`). APKs output to `smarttubetv/build/outputs/apk/<flavor>/<buildType>/` and are ABI‑split.
- Install to device/emulator (debug): `./gradlew :smarttubetv:installStbetaDebug`.
- Unit tests: `./gradlew :smarttubetv:testStbetaDebugUnitTest` and `./gradlew :common:testStbetaDebugUnitTest`.
- Instrumented tests (device required): `./gradlew :smarttubetv:connectedStbetaDebugAndroidTest`.

## Coding Style & Naming Conventions
- Languages: Kotlin + Java 8 (see `compileOptions`). Use 4‑space indentation, trim trailing whitespace, and optimize imports.
- Names: Classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`. Android resources lowercase_with_underscores (e.g., `ic_play_arrow.xml`).
- Flavors: Keep `st*` prefix (e.g., `stbeta`) and place overrides under `smarttubetv/src/<flavor>/`.
- Formatting/lint: Use Android Studio formatter; run `./gradlew lint` selectively when needed.

## Testing Guidelines
- Frameworks: JUnit 4 and Robolectric for unit tests; AndroidX Test + Espresso for UI/instrumented tests.
- Layout: Unit tests in `src/test/java`; instrumented tests in `src/androidTest/java`. Name files `SomethingTest.kt`.
- Scope: Prefer small, deterministic unit tests for `common` and view‑model logic; reserve instrumented tests for UI flows.

## Commit & Pull Request Guidelines
- Commits: Short, imperative subjects; optional scope prefix when relevant (e.g., `exoplayer:`, `video loader:`). Group related changes.
- PRs: Include summary, rationale, and screenshots for UI changes; link issues; note affected flavors; ensure `./gradlew build` and tests pass.

## Security & Configuration Tips
- Crashlytics/Google Services apply only when `google-services.json` is present and for select flavors (`stbeta`, `strtarmenia`). Do not commit secrets.
- Output naming: APKs follow `SmartTube_<flavor>_<version>_<abi>.apk`. Verify correct flavor/ABI before distributing.
- Environment: Android Studio (SDK 34) or CLI with JDK 11+, Android SDK set via `ANDROID_HOME`.

