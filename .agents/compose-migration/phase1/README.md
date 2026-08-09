# Phase 1 — Compose foundation and Settings

Phase 1 introduces Jetpack Compose without changing the app's Activity-based navigation. `SettingsActivity` is the first complete Compose screen; the old `activity_settings.xml` and its popup-menu XML are removed.

## Implementation

- Kotlin Compose compiler plugin `2.2.10`, matching the project Kotlin version.
- Compose BOM `2026.06.00`, Material 3, and Activity Compose `1.13.0` on `compileSdk 36`.
- Preview tooling remains compile-only so it is available to source/IDE tooling without entering the runtime APK.
- Compose UI instrumentation support is limited to `androidTest`.
- `CleanSmsTheme` preserves the static light/dark palettes, Android 12+ dynamic color, all three app font choices, Material typography, and system theme behavior.
- Settings preserves theme/font recreation, default-tab selection and ordering, optional All visibility, the All-to-OTP fallback, promotional-notification persistence, links, version display, Back behavior, and the debug/release source-set split.
- The debug random-OTP control is now a composable. The release implementation remains a no-op.
- System-bar insets, 48 dp Back target, switch semantics, merged row click targets, and stable Compose test tags cover accessibility and automation.

Dependency choices follow the official [Compose setup guide](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler), [Compose BOM guidance](https://developer.android.com/develop/ui/compose/bom), and [Activity release notes](https://developer.android.com/jetpack/androidx/releases/activity).

## Visual verification

Settings was checked on the API 37 Pixel 10 AVD in dark/light themes, all supported fonts, large text, optional-tab states, and the default-tab menu. The temporary screenshot assets were removed after migration completion.

## Verification

```text
./gradlew testDebugUnitTest assembleDebug compileReleaseKotlin
BUILD SUCCESSFUL

./gradlew connectedDebugAndroidTest
Finished 6 tests on Pixel_10(AVD) - 17
BUILD SUCCESSFUL

./gradlew installDebug
Installed on 1 device
BUILD SUCCESSFUL

./gradlew assembleRelease
BUILD SUCCESSFUL
```

The Settings regression scenario now asserts Compose semantics and still verifies persistence plus the All-default fallback. The other five Phase 0 scenarios remain green. Theme recreation produced no crash or UI exception in a clean logcat scan.

`./gradlew lintDebug` remains red with exactly the same 12 inherited errors recorded in Phase 0: three notification-permission checks, two suspicious-indent findings, one protected permission, and six legacy XML `android:tint` findings. Phase 1 briefly introduced a Compose resource-access lint error while the debug control was migrated; that error was fixed, leaving no new lint error.

## Performance checkpoint

The minified release APK grows from about 4.4 MiB at the Phase 0 commit to 5.8 MiB with the Compose runtime. On this AVD, the unminified debug APK's `am start -W` result became dominated by first-load Compose dex overhead even though the inbox is still View-based; same-session samples were roughly 4.5–5.1 seconds versus 0.5–0.6 seconds for the exact Phase 0 debug APK. This debug-only measurement is not accepted as a production benchmark. The release build is R8-shrunk and merges Compose startup profiles, but Phase 2 should add a repeatable release-like Macrobenchmark before using cold-start numbers as a merge gate.
