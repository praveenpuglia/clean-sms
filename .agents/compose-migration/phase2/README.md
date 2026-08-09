# Phase 2 — Default-SMS onboarding

Phase 2 replaces `screen_setup_default_sms.xml` with a Compose subtree hosted by the existing `MainActivity`. Activity navigation, the SMS-role request, battery-optimization request, permission request, and onboarding persistence remain owned by `MainActivity`.

## Implementation

- `activity_main.xml` keeps the existing inbox View hierarchy and now hosts onboarding in a lifecycle-aware `ComposeView`.
- `OnboardingScreen` receives only `OnboardingUiState` and three callbacks for the framework actions.
- The original content, ordering, dimensions, fixed Continue footer, optional background step, disabled states, completion ticks, dynamic colors, fonts, and large-text scrolling are preserved.
- The adaptive launcher icon is rendered from its existing vector background and foreground layers because Compose does not directly decode adaptive-icon XML through `painterResource`.
- All onboarding text is moved to `strings.xml`, and the three actions have stable Compose test tags.
- `screen_setup_default_sms.xml` is deleted after parity verification.

## Visual verification

Onboarding was checked on the API 37 Pixel 10 AVD from true first run through both system setup steps and inbox entry, in dark/light themes and large text. The temporary screenshot assets were removed after migration completion.

## Verification

```text
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

PhaseZeroUiRegressionTest
OK (7 tests)

./gradlew assembleRelease
BUILD SUCCESSFUL
```

The onboarding regression now uses Compose semantics for the title, logo, required action, and Continue enabled state. The full seven-scenario suite passes on the emulator. A clean post-fix logcat scan contains no app crash or UI exception.

`./gradlew lintDebug` reports 10 inherited errors and 187 warnings, down from Phase 1's 12 errors because deleting the onboarding XML also removes two legacy `android:tint` findings. No lint error points to the Phase 2 Kotlin, test, strings, or host-layout changes. The remaining inherited lint gate is scheduled for Phase 6 cleanup.
