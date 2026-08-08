# Phase 6 — Final cleanup

## Result

The Activity UI migration is complete. The project has no `setContentView`, `findViewById`, `AndroidView`, RecyclerView, ViewPager, or Activity/row/page XML UI remaining. `notification_otp.xml` is intentionally retained because Android custom notifications require platform `RemoteViews`.

Cleanup included:

- Removed the unused `ComposeActivity`, its manifest entry, and `activity_compose.xml`.
- Removed the RecyclerView dependency and unused dimension resources.
- Removed the inactive duplicate incoming-SMS receiver while retaining its shared SMS-role/contact helpers in `ContactEnrichment.kt`.
- Replaced the final thread-message `AndroidView` bridge with selectable Compose text and Compose URL annotations, while preserving Android link detection and the card-last-four phone-link guard.
- Added notification permission guards, removed an invalid protected permission request, and documented the justified `RemoteViews` tint lint suppression.
- Updated `AGENTS.md` to describe the actual Compose architecture and conventions.

AppCompat and Material Components remain because the Activity/window theme and night-mode behavior still depend on them; removing them would be a separate, risky theme migration with no user-facing benefit.

## Final verification

- `testDebugUnitTest`: passed.
- `lintDebug`: passed with zero errors.
- `assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed.
- `assembleRelease`: passed, including R8/resource shrinking and release signing validation.
- API 37 emulator instrumentation: 8/8 tests passed.
- Manual emulator checks: Compose web/phone links, text-selection toolbar, card-last-four guard, OTP `RemoteViews` notification, dark theme, 1.0x text scale, and crash/exception logcat scan.
- Performance testing was not repeated, per the explicit performance-test waiver after the large debug-seed pass.
- No build was installed on the physical phone during this phase.

## Screenshots

- `screenshots/thread`: Compose links and native text selection.
- `screenshots/notifications`: retained OTP `RemoteViews` notification.
