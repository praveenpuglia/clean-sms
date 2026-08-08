# Phase 3 — New Message

Phase 3 replaces the New Message Activity and contact-suggestion row XML with Compose. Android framework work remains in `NewMessageActivity`; the Compose screen receives current values and callbacks only.

## Implementation

- `NewMessageActivity` still owns SENDTO/MMS intent parsing, contact-provider queries, raw-number validation, SIM lookup, SMS sending, sent-provider insertion, toast feedback, inbox refresh, and Activity completion.
- `NewMessageScreen` renders the header, message editor, counters, recipient chips/input, contact suggestions, optional SIM selector, and send action.
- Contact suggestions use a keyed `LazyColumn`. Recipient state uses Compose's observable list directly; no replacement state-holder or navigation layer was added.
- Local contact photos are decoded through `ContentResolver` and `BitmapFactory` on `Dispatchers.IO`; no image-loading dependency was added.
- GSM/UCS-2 limits and multipart behavior are preserved in the pure `smsCounter` function and covered at the exact 160/161 and 70/71 boundaries.
- Backspace removal, chip removal, multi-recipient selection, raw-number entry, Activity recreation, accessible icon labels, system-bar insets, light/dark colors, large text, and landscape were checked on the emulator.
- `activity_new_message.xml`, `item_contact_suggestion.xml`, and `ContactSuggestionAdapter` are deleted.

## Emulator evidence

All captures use the API 37 Pixel 10 AVD at 1080 × 2424 in portrait unless noted:

1. `screenshots/new-message/01-empty-dark.png` — initial editor, disabled Send, and empty GSM counter.
2. `screenshots/new-message/02-raw-number-suggestion-dark.png` — matching local contact plus raw-number action.
3. `screenshots/new-message/03-recipient-and-message-dark.png` — selected recipient, message content, and enabled Send.
4. `screenshots/new-message/04-gsm-multipart-dark.png` — 161-character GSM message with `145` remaining and `2 SMS`.
5. `screenshots/new-message/05-sendto-prefill-light.png` — external SENDTO recipient/body prefill in light mode.
6. `screenshots/new-message/06-large-text-light.png` — empty editor at 1.5× system text.
7. `screenshots/new-message/07-landscape-dark.png` — SENDTO prefill in landscape.
8. `screenshots/new-message/08-multiple-recipients-dark.png` — two recipient chips and horizontally scrolled input.
9. `screenshots/new-message/09-backspace-removes-last-dark.png` — last recipient removed through Backspace.

The emulator has one active subscription, so dual-SIM visibility/toggling cannot be reproduced there. No real SMS was sent; the production send path and provider insertion remain unchanged and the debug tests deliberately stop before the send action.

## Verification

```text
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

PhaseZeroUiRegressionTest
OK (7 tests)

./gradlew assembleRelease
BUILD SUCCESSFUL
```

The Compose regression covers SENDTO prefill, Activity recreation, raw-recipient selection, Backspace removal, send enablement, accessibility labels, and GSM/Unicode multipart counters. A clean post-test logcat scan contains no app crash or UI exception.

`./gradlew lintDebug` reports the same 10 inherited errors as Phase 2 and 160 warnings. No lint error points to Phase 3 code, tests, strings, or evidence; the inherited gate remains scheduled for Phase 6 cleanup.
