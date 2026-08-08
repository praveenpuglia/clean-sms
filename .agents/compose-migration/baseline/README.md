# Phase 0 Regression Baseline

This directory freezes the existing View-based UI before any Compose production code is introduced. The screenshots are reference evidence, not pixel-golden tests: device/system UI changes can move pixels even when app behavior is unchanged.

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
| Screenshot count | 65 PNGs |

The screenshots contain only curated fake seed data. The role chooser, battery prompt, keyboard, and notification shade are Android system UI and are intentionally included where they form part of the user flow.

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

## Screenshot manifest

### Onboarding (`screenshots/onboarding`)

- `01-initial-dark.png`: genuine clean first frame; SMS role absent; Continue disabled.
- `02-default-sms-role-chooser.png`: Android role chooser before selection.
- `03-default-sms-role-selected.png`: Clean SMS selected in the role chooser.
- `04-default-sms-granted.png`: required step complete; Continue enabled.
- `05-battery-optimization-dialog.png`: Android background-exemption prompt.
- `06-both-steps-complete.png`: both setup checks complete.
- `07-empty-inbox-after-onboarding-dark.png`: first inbox with zero provider rows.

The complete onboarding content fits one portrait viewport on this device, so separate top/middle/bottom crops would duplicate the same content.

### Main inbox (`screenshots/main`)

- `01`–`03`: OTP top, middle, and bottom.
- `04`–`05`: Personal top and bottom.
- `06`–`07`: Transactions top and bottom.
- `08`–`09`: Services top and bottom.
- `10`–`11`: Promotions top and bottom.
- `12`–`13`: Government top and bottom.
- `14`: overflow menu.
- `15`: unread-only filter.
- `16`: empty search with keyboard.
- `17`: RATNADEEP results and term highlighting.
- `18`: no-results search.
- `19`–`21`: one, multiple, and select-all selection states.
- `22`: destructive confirmation dialog; Cancel was used and no data was deleted.
- `23`: optional All tab visible and Promotions notifications muted.
- `24`–`25`: All top and bottom.
- `26`: 1.5× system text.
- `27`: verified light-theme OTP screen.
- `28`: landscape OTP screen.

### Thread detail (`screenshots/thread`)

- `01`: Personal header, incoming bubbles, SIM marker, call action, and composer.
- `02`: focused composer and keyboard.
- `03`: GSM multipart counter at 161 characters.
- `04`: native text selection handles and Copy/Share/Select-all toolbar.
- `05`: alphanumeric service thread with a linkified URL and no reply composer.
- `06`–`07`: long `VM-IRSMSa-G` railway SMS, including the complete plain message bubble and both URLs; no railway-specific card.
- `08`: Personal thread at 1.5× system text.
- `09`: verified light-theme Personal thread.
- `10`: Airtel SPAM badge and current message-body presentation.

### New message (`screenshots/new-message`)

- `01`: initial focus, contacts overlay, disabled send, and keyboard.
- `02`: valid raw-number suggestion.
- `03`: one recipient chip.
- `04`: contact search result.
- `05`: multiple recipient chips.
- `06`: body entered and send enabled.
- `07`: empty recipient-input Backspace removed the last chip.
- `08`: external `smsto:` recipient and body prefill.
- `09`: SENDTO at 1.5× system text.
- `10`: representative landscape SENDTO state.

GSM and Unicode single/multipart counter boundaries are enforced by instrumentation tests. A real send was intentionally not performed, and this AVD exposes only one subscription, so no dual-SIM screenshot exists.

### Settings (`screenshots/settings`)

- `01`: top/default dark state using Google Sans Flex.
- `02`: default-tab menu with all seven destinations, including optional All.
- `03`: All disabled and promotional notifications disabled.
- `04`: complete lower content, About links, version, privacy/terms, and debug control.
- `05`: All enabled while promotional notifications remain disabled.
- `06`: light theme with Google Sans Flex.
- `07`: light theme with Google Sans Code.
- `08`: light theme with system font.
- `09`: dark Settings at 1.5× system text.

### Notifications (`screenshots/notifications`)

- `01-random-otp-dark.png`: debug OTP custom notification in the expanded shade, including its copy affordance. Other emulator system notifications remain visible because the shade itself is part of the captured state.

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

`./gradlew lintDebug` is not green at the baseline commit. It reports 12 errors in files untouched by Phase 0: three notification permission checks, two suspicious-indent findings, one protected-permission declaration, and six legacy XML `android:tint` findings. No error points to the new regression test, debug seed control, workflow, plan, manifest, or screenshots. These inherited errors must be resolved separately before Phase 0 can satisfy the plan's lint-clean acceptance gate; they were not suppressed with a lint baseline because that would hide real debt.

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
- Screenshots are manual reference evidence; behavioral assertions are the durable CI gate.
