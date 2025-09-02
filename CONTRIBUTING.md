# Contributing

Thank you for contributing to SmartTube. Please use the stable flavor by default and keep changes focused and well‑tested.

## Prerequisites
- Android SDK (SDK 34) and JDK 11–14.
- ADB in PATH and at least one Android TV or emulator connected.
- Initialize submodules: `git submodule update --init`.

## Build & Run (Stable)
- Assemble: `./gradlew :smarttubetv:assembleStstableDebug`
- Install to one device: `./gradlew :smarttubetv:installStstableDebug`
- Output: `smarttubetv/build/outputs/apk/ststable/<buildType>/SmartTube_stable_<version>_<abi>.apk`

## Quick Deploy to TVs
- Linux/macOS: `bash scripts/deploy_to_tvs.sh ststableDebug`
- Windows: `powershell -File scripts/deploy_to_tvs.ps1 -Variant ststableDebug`
The scripts auto‑select ABI, use short timeouts, and force‑stop/uninstall if needed.

## Testing
- Unit tests: `./gradlew :smarttubetv:testStstableDebugUnitTest :common:testStstableDebugUnitTest`
- Instrumented (device required): `./gradlew :smarttubetv:connectedStstableDebugAndroidTest`

## Code Style
- Kotlin/Java 8 sources, 4‑space indent, trim trailing whitespace. Optimize imports.
- Names: Classes PascalCase; members camelCase; constants UPPER_SNAKE_CASE.

## Pull Requests
- Use short, imperative commit subjects; group related changes.
- Include a summary, rationale, screenshots for UI changes, and affected flavors.
- Ensure `./gradlew build` is green and tests pass.

## Flavor Policy
- Default to `ststable`. Avoid `stbeta` unless explicitly requested.
- Place flavor‑specific overrides under `smarttubetv/src/<flavor>/`.

For more details, see AGENTS.md (Repository Guidelines) and BUILD_AND_DEPLOY.md.
