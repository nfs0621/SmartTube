# Repository Guidelines

## Project Structure & Module Organization
- Modules: `smarttubetv` (app), `common` (shared core), `chatkit`, `leanback-1.0.0`, `fragment-1.1.0`, `filepicker-lib`, vendored `exoplayer-amzn-2.10.6`. External submodules: `MediaServiceCore`, `SharedModules`.
- App sources: `smarttubetv/src/main/{java,res,assets}`. Product flavors under `smarttubetv/src/<flavor>` (e.g., `stbeta`, `ststable`, `storig`) override manifests/resources.
- Assets/docs: `images/`. Helper scripts: `scripts/`.
- Tests: unit in `src/test/java`; instrumented in `src/androidTest/java` per module.

## Build, Test, and Development Commands
- Build all: `./gradlew clean build` — compiles, runs unit tests, and lints.
- Assemble stable APKs: `./gradlew :smarttubetv:assembleStstableDebug` (or `assembleStstableRelease`). Output: `smarttubetv/build/outputs/apk/ststable/<buildType>/` (ABI‑split).
- Install stable (debug): `./gradlew :smarttubetv:installStstableDebug` to a connected device.
- Fast multi‑device install (stable): `bash scripts/deploy_to_tvs.sh ststableDebug` or `powershell -File scripts/deploy_to_tvs.ps1 -Variant ststableDebug`.
- Unit tests: `./gradlew :smarttubetv:testStstableDebugUnitTest` and `./gradlew :common:testStstableDebugUnitTest`.
- Instrumented tests: `./gradlew :smarttubetv:connectedStstableDebugAndroidTest` (device required).

## Coding Style & Naming Conventions
- Languages: Kotlin + Java 8 (`compileOptions`). Indentation: 4 spaces; trim trailing whitespace; optimize imports.
- Names: Classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Android resources: `lowercase_with_underscores` (e.g., `ic_play_arrow.xml`).
- Flavors: keep `st*` prefix (e.g., `stbeta`); overrides live under `smarttubetv/src/<flavor>/`.
- Lint/format: use Android Studio formatter; run `./gradlew lint` before PRs.

## Testing Guidelines
- Frameworks: JUnit 4 + Robolectric (unit); AndroidX Test + Espresso (UI/instrumented).
- Layout: place unit tests in `src/test/java`; instrumented in `src/androidTest/java`.
- Naming: `SomethingTest.kt`/`.java`. Favor small, deterministic unit tests in `common` and view‑model logic; reserve instrumented tests for UI flows.

## Commit & Pull Request Guidelines
- Commits: short, imperative subjects; optional scope (e.g., `exoplayer:`, `video loader:`). Group related changes.
- PRs: include summary, rationale, and screenshots for UI changes; link issues; note affected flavors; ensure `./gradlew build` and tests pass.

## Security & Configuration Tips
- Enable Crashlytics/Google Services only when `google-services.json` is present and only for select flavors (`stbeta`, `strtarmenia`). Never commit secrets.
- APK naming: `SmartTube_<flavor>_<version>_<abi>.apk`. Verify flavor/ABI before distributing.
- Environment: Android Studio (SDK 34) or CLI with JDK 11+; set Android SDK via `ANDROID_HOME`.

## Flavor Policy
- Use the stable flavor by default: `ststable`. Avoid `stbeta` unless explicitly requested.
# Repository Guidelines

## Project Structure & Module Organization
- Modules: `smarttubetv` (app), `common` (shared core), `chatkit`, `leanback-1.0.0`, `fragment-1.1.0`, `filepicker-lib`, vendored `exoplayer-amzn-2.10.6`. External submodules: `MediaServiceCore`, `SharedModules`.
- App sources: `smarttubetv/src/main/{java,res,assets}`. Product flavors under `smarttubetv/src/<flavor>` (e.g., `ststable`, `stbeta`, `storig`) override manifests/resources.
- Assets/docs: `images/`. Helper scripts: `scripts/`.
- Tests: unit in `src/test/java`; instrumented in `src/androidTest/java` per module.

## Build, Test, and Development Commands
- Build all: `./gradlew clean build` — compiles, runs unit tests, and lints.
- Assemble stable APKs: `./gradlew :smarttubetv:assembleStstableDebug` (or `assembleStstableRelease`). Output: `smarttubetv/build/outputs/apk/ststable/<buildType>/` (ABI-split).
- Install stable (debug): `./gradlew :smarttubetv:installStstableDebug` to a connected device.
- Fast multi-device install: `bash scripts/deploy_to_tvs.sh ststableDebug` or `powershell -File scripts/deploy_to_tvs.ps1 -Variant ststableDebug`.
- Unit tests: `./gradlew :smarttubetv:testStstableDebugUnitTest` and `./gradlew :common:testStstableDebugUnitTest`.
- Instrumented tests: `./gradlew :smarttubetv:connectedStstableDebugAndroidTest` (device required).

## Coding Style & Naming Conventions
- Languages: Kotlin + Java 8. Indentation: 4 spaces; trim trailing whitespace; optimize imports.
- Names: Classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Resources: `lowercase_with_underscores` (e.g., `ic_play_arrow.xml`).
- Flavors: keep `st*` prefix; overrides in `smarttubetv/src/<flavor>/`.
- Lint/format: Android Studio formatter; run `./gradlew lint` before PRs.

## Testing Guidelines
- Frameworks: JUnit 4 + Robolectric (unit); AndroidX Test + Espresso (UI/instrumented).
- Layout: unit in `src/test/java`; instrumented in `src/androidTest/java`.
- Naming: `SomethingTest.kt`/`.java`. Prefer small, deterministic unit tests in `common` and view-model logic; reserve instrumented tests for UI flows.

## Commit & Pull Request Guidelines
- Commits: short, imperative subjects; optional scope (e.g., `exoplayer:`, `video loader:`). Group related changes.
- PRs: include summary, rationale, and screenshots for UI changes; link issues; note affected flavors; ensure `./gradlew build` and tests pass.

## Security & Configuration Tips
- Only enable Crashlytics/Google Services when `google-services.json` is present and only for select flavors (`stbeta`, `strtarmenia`). Never commit secrets.
- APK naming: `SmartTube_<flavor>_<version>_<abi>.apk`; verify flavor/ABI before distributing.
- Environment: Android Studio (SDK 34) or CLI with JDK 11+; set Android SDK via `ANDROID_HOME`.

## Flavor Policy
- Use `ststable` by default. Avoid `stbeta` unless explicitly requested.
