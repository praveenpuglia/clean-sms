# Phase 0 Regression Baseline

This directory records the View-based UI regression baseline used before Compose production code was introduced. The temporary screenshot assets were removed after the migration completed; behavioral tests are the durable regression gate.

## Baseline identity

| Property | Value |
| --- | --- |
| Source commit | `ebafad6a294bcb2d577bb5ae906091d9872d56e7` |
| Working branch | `agent/compose-migration` |
| App | Clean SMS debug, version `2.0.1` (`20001`) |
| Emulator | Android Studio `Pixel_10` AVD (`emulator-5554`) |
| Android | 17 / API 37 |
| Display | 1080 × 2424, 420 dpi |
| Normal font scale | 1.0 |
| Large-text pass | 1.5 |
| Primary appearance | Dark, Google Sans Flex |
| Additional appearances | Light; Google Sans Code; system font |
| Orientation | Portrait, plus representative landscape main/compose states |
| Seed fixture | 59 debug-owned SMS rows plus debug contacts |

## Reproducing the baseline

### True clean first run

The one-time capture used a fully empty emulator SMS provider, not merely an app-data reset:

```bash
adb shell cmd role remove-role-holder android.app.role.SMS com.praveenpuglia.cleansms 0
adb shell pm clear com.praveenpuglia.cleansms
adb shell pm clear com.android.providers.telephony
adb reboot
./gradlew installDebug
adb shell am start -n com.praveenpuglia.cleansms/.MainActivity
```

Clearing the telephony provider is destructive to every SMS/MMS row on that emulator. It must only be used on a disposable emulator. It is not part of ordinary migration verification.

On this API 37 image, accepting the SMS role automatically granted `READ_SMS`, `SEND_SMS`, `READ_CONTACTS`, `READ_PHONE_STATE`, and `POST_NOTIFICATIONS`. There was therefore no separate runtime-permission prompt after Continue. The role-not-held onboarding screen and disabled Continue button are the recorded degraded state.

### Safe repeat runs

The debug receiver can now clear only its own rows without immediately reseeding:

```bash
adb shell am broadcast \
  -n com.praveenpuglia.cleansms/.DebugSeedReceiver \
  -a com.praveenpuglia.cleansms.DEBUG_SEED \
  --ez clear true \
  --ez seed false
```

Seed or reseed after onboarding:

```bash
adb shell cmd role add-role-holder android.app.role.SMS com.praveenpuglia.cleansms 0
adb shell pm grant com.praveenpuglia.cleansms android.permission.WRITE_CONTACTS
adb shell am broadcast \
  -n com.praveenpuglia.cleansms/.DebugSeedReceiver \
  -a com.praveenpuglia.cleansms.DEBUG_SEED
```

The receiver performs provider/contact work synchronously and took roughly nine seconds on this AVD. Do not force-stop the app until the broadcast reports completion.

## Visual baseline

The migration was manually checked against temporary captures covering onboarding, every inbox tab, search, selection, thread detail, new message, settings, notifications, light/dark themes, fonts, large text, and representative landscape states. Those image assets were removed after the migration completed.

## Automated baseline

`PhaseZeroUiRegressionTest` replaces the Android Studio placeholder test and covers six independent scenarios:

1. Onboarding visibility and enabled/disabled/accessibility state after a fresh onboarding preference.
2. Settings persistence, promotional notifications, and the All-default fallback to OTP.
3. External SENDTO prefill plus GSM and Unicode multipart boundaries.
4. Raw-recipient selection, send enablement, and Backspace chip removal.
5. Seeded tabs, search/back priority, and a Personal thread with reply/call controls.
6. A Service thread with reply and call controls hidden.

System role dialogs, battery prompts, notification shade behavior, real SMS delivery, contact-app destinations, and dual-SIM sending remain manual because they cross app/process boundaries.

The suite passed on the existing API 37 emulator:

```text
Finished 6 tests on Pixel_10(AVD) - 17
BUILD SUCCESSFUL in 34s
```

AndroidX Test was moved from JUnit extension 1.1.5 / Espresso 3.5.1 to the stable 1.3.0 / 3.7.0 test stack because 3.5.1 cannot inject input on API 37. No production dependency changed.

PR CI retains the fast unit-test job and adds an API 35 emulator job. The emulator test report is uploaded even when the job fails.

## Verification status

Passed:

```text
./gradlew testDebugUnitTest assembleDebug
BUILD SUCCESSFUL

./gradlew connectedDebugAndroidTest
Finished 6 tests on Pixel_10(AVD) - 17
BUILD SUCCESSFUL

./gradlew installDebug
Installed on 1 device
BUILD SUCCESSFUL
```

`./gradlew lintDebug` is not green at the baseline commit. It reports 12 errors in files untouched by Phase 0: three notification permission checks, two suspicious-indent findings, one protected-permission declaration, and six legacy XML `android:tint` findings. No error points to the new regression test, debug seed control, workflow, plan, or manifest. These inherited errors must be resolved separately before Phase 0 can satisfy the plan's lint-clean acceptance gate; they were not suppressed with a lint baseline because that would hide real debt.

## Performance checkpoint

One post-install seeded cold start was recorded with `am start -W`:

```text
LaunchState: COLD
TotalTime: 1474 ms
WaitTime: 1584 ms
```

The first nine rendered frames included asynchronous inbox startup work and reported 8 janky frames. This tiny sample is not a benchmark; it is a warning threshold. Later phases should compare the same command and investigate a material regression rather than optimizing to this single run.

## Known coverage boundaries

- API 37 automatically grants SMS-role permissions, so a standalone runtime permission dialog was not reproducible.
- The AVD has one active/no usable dual-SIM configuration.
- No real recipient was contacted and no send action was pressed.
- External Terms/Privacy/Contacts destinations were not navigated beyond their app-owned launch controls.
- Behavioral assertions are the durable CI gate.
