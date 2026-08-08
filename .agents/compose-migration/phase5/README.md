# Phase 5 — Main inbox

## Result

`MainActivity` now renders the header, tabs, pager, thread/OTP/All lists, search, unread filter, selection mode, delete dialog, and FAB entirely with Compose. SMS/contact queries, classification, OTP extraction, deletion, and Activity navigation remain in `MainActivity`.

The legacy pager adapter, three RecyclerView adapters, eight layouts, the tab layout, and the overflow menu XML were removed after parity checks passed.

## Emulator verification

- Target: `emulator-5554` (API 37)
- Unit tests: `testDebugUnitTest` passed, including deterministic inbox date formatting.
- Instrumented tests: 8/8 passed on the emulator.
- Builds: debug, debug-test, and minified signed release APKs passed.
- Focused inbox coverage: enabled tab order/default, unread dots/filter, OTP copy, long-press selection, delete dialog cancellation, search/back behavior, and Personal thread navigation.
- Manual coverage: horizontal paging through every enabled tab; All, OTP, thread, and search rows; overflow/unread state; selection/delete dialog; dark/light themes; 1.3x system text; FAB; settled pager transitions.
- Logcat: no matching app crash or exception entries after the manual pass.
- Device state restored to dark theme and 1.0x system text.

`lintDebug` improved from the inherited Phase 4 result to 6 errors/107 warnings. The remaining errors are pre-existing non-inbox findings and are reserved for the final cleanup gate.

## Screenshots

The `screenshots/main` directory contains:

- OTP, Personal, Transactions, Services, Promotions, Government, and All tabs.
- Overflow menu and unread-only filter.
- Search results with the keyboard.
- Selection mode and delete confirmation.
- Representative light-theme and large-text states.
