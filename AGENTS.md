# Repository Guidelines

## Project Structure & Module Organization
- `smarttubetv/`: Android TV app module (product flavors under `src/{stbeta,ststable,storig,…}`; shared code in `src/main`).
- `common/`: shared UI/logic resources used by the app.
- `exoplayer-amzn-2.10.6/`: bundled ExoPlayer modules.
- `fragment-1.1.0/`, `leanback-1.0.0/`: patched AndroidX modules.
- `SharedModules/`, `MediaServiceCore/`: Git submodules; run `git submodule update --init`.
- `images/`, `scripts/`, `.github/`: assets, helper scripts, CI metadata.

## Build, Test, and Development Commands
- Prereqs: OpenJDK ≤ 14, Android SDK, ADB device/emulator.
- Initialize modules: `git submodule update --init`.
- Install debug (example flavor): `./gradlew clean :smarttubetv:installStorigDebug` (requires `adb devices`).
- Assemble releases: `./gradlew :smarttubetv:assembleStbetaRelease` or `:smarttubetv:assembleStstableRelease`.
- Unit tests: `./gradlew test`.
- Instrumented tests: `./gradlew connectedAndroidTest` (emulator/device required).
- Notes: Firebase/Crashlytics is enabled only for `Stbeta`/`Strtarmenia` tasks when `google-services.json` exists in `smarttubetv/`.

## Coding Style & Naming Conventions
- Language: Java 8 (`sourceCompatibility/targetCompatibility 1.8`).
- Indentation: 4 spaces; use AndroidX imports.
- Resources: snake_case (e.g., `ic_play.xml`, `activity_main.xml`).
- Flavors: all start with `st...` (e.g., `stbeta`, `ststable`, `storig`). Keep flavor-specific resources under `src/<flavor>/res`.
- Lint: Android Lint configured; release lint checks may be relaxed for vendor issues.

## Testing Guidelines
- Frameworks: JUnit + Robolectric for unit; AndroidX Test + Espresso for instrumented.
- Location: `src/test/java` and `src/androidTest/java` in relevant modules.
- Naming: end with `Test` (e.g., `PlayerControllerTest.java`).
- Scope: cover new logic and critical flows (playback, navigation, network fallbacks). Aim to keep tests device-agnostic where possible.

## Commit & Pull Request Guidelines
- Commits: concise, imperative mood. Optional scope prefix (e.g., `exoplayer: fix codec selection`, `video loader: buffering fix`). Group related changes.
- PRs: include summary, motivation, and testing notes (device/emulator, flavor). Link issues, attach logs or screenshots for UI changes. Keep diffs focused; note any impacts to flavors or submodules.

## Security & Configuration Tips
- Do not commit secrets; place `google-services.json` locally if needed.
- Verify JDK (≤ 14) to avoid runtime issues.
- Ensure submodules (`SharedModules`, `MediaServiceCore`) are initialized and up to date.
