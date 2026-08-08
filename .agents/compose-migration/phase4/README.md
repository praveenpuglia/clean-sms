# Phase 4 — Thread Detail

Phase 4 replaces the complete Thread Detail View hierarchy with Compose. `ThreadDetailActivity` still owns Android framework and SMS-provider work; the Compose screen receives state and callbacks.

## Implementation

- The header, stable-keyed chronological message list, sticky day labels, incoming/outgoing bubbles, spam badge, SIM/delivery indicators, target highlight, and composer are rendered by `ThreadDetailScreen`.
- Existing bottom anchoring, latest/target scrolling, Personal/numeric reply eligibility, call/contact actions, ContentObserver refresh, read marking, SIM selection, sending, sent-provider insertion, feedback, and draft restoration remain in the Activity.
- Message bodies deliberately use the platform `TextView` through `AndroidView`. This preserves native long-press selection and the existing `LinkifyUtil` behavior without a new dependency, including the card-last-four false-positive guard.
- The shared local-contact avatar is reused from Phase 3; no new component or image-loading dependency was introduced.
- `activity_thread_detail.xml`, `composer_bar.xml`, both message-row layouts, the day-row layout, `MessageAdapter`, and `StickyDayHeaderDecoration` are deleted after parity verification.

## Emulator evidence

All captures use the API 37 Pixel 10 AVD at 1080 × 2424:

1. `screenshots/thread/01-personal-bottom-dark.png` — Personal header/call action, incoming/outgoing alignment, SIM/delivery indicators, latest-message anchoring, and composer.
2. `screenshots/thread/02-composer-keyboard-dark.png` — multi-line composer with the keyboard open and live count.
3. `screenshots/thread/03-composer-cleared-dark.png` — composer cleared without sending.
4. `screenshots/thread/04-service-link-dark.png` — service sender, linkified order/URL text, and no reply controls.
5. `screenshots/thread/05-railway-bottom-dark.png` — full railway message rendered as a standard bubble with both URLs and no special preview card.
6. `screenshots/thread/06-railway-settled-dark.png` — railway day label and stable settled position after a scroll gesture.
7. `screenshots/thread/07-spam-badge-dark.png` — numeric sender, call/reply controls, spam badge, and linked phone number.
8. `screenshots/thread/08-transactional-light.png` — light-theme transactional thread with no reply controls.
9. `screenshots/thread/09-large-text-light.png` — 1.5× system text with a readable, non-clipped message bubble.

The emulator exposes one subscription, so dual-SIM toggling cannot be reproduced. No real SMS was sent; the production send path remains unchanged.

## Verification

```text
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL

PhaseZeroUiRegressionTest
OK (8 tests)

./gradlew assembleRelease
BUILD SUCCESSFUL
```

The instrumentation suite checks Personal/service composer eligibility, accessible header actions, target-message positioning, native selectable/linkified text, and the card-last-four link guard. The unit suite also locks the current Today/Yesterday day-label behavior.

A clean post-test logcat scan contains no app crash or Compose/UI exception. `./gradlew lintDebug` reports the same 10 inherited errors as Phase 3 and 137 warnings; no lint error points to Phase 4 code, tests, strings, or evidence. The inherited lint gate remains scheduled for Phase 6 cleanup.
