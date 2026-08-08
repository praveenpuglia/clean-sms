# Jetpack Compose Migration Plan

## Objective

Replace the app's View-based screen UI with Jetpack Compose without changing its visual design, behavior, navigation, SMS handling, permissions, privacy properties, or supported Android versions.

The migration is intentionally incremental. Compose and Views may coexist while a screen is being migrated, but each completed screen must have one production implementation. We will not maintain permanent duplicate View and Compose versions behind a feature flag.

## Non-goals

The migration must not introduce any of the following unless separately approved:

- A visual redesign or revised information architecture.
- Navigation Compose or changes to Activity names, intent filters, or task behavior.
- ViewModel, repository, dependency-injection, or database architecture rewrites.
- Changes to SMS classification, OTP detection, spam detection, contact enrichment, sending, deletion, or notification behavior.
- New permissions, analytics, network calls, or transmission of message content.
- Min SDK, target SDK, compile SDK, Kotlin, AGP, or Java toolchain upgrades.
- Opportunistic bug fixes or product changes discovered during migration.

## Existing UI Surface

### Active screens

1. `MainActivity`
   - Default-SMS onboarding.
   - Optional All tab, OTP tab, and five category tabs.
   - Thread, OTP, and chronological message lists.
   - Search, unread-only filtering, selection, select-all, and deletion.
   - Contact/avatar actions, unread indicators, spam indicators, SIM indicators, and new-message FAB.
2. `SettingsActivity`
   - Theme, font, All-tab, default-tab, and promotional-notification settings.
   - Terms, privacy policy, version information, and debug-only OTP notification control.
3. `NewMessageActivity`
   - External `sms:`, `smsto:`, `mms:`, and `mmsto:` intent handling.
   - Contact suggestions, raw phone numbers, multiple recipients, removable chips, message input, GSM/Unicode part counting, SIM selection, and sending.
4. `ThreadDetailActivity`
   - Contact header and actions, message bubbles, selectable/linkified message text, spam/SIM/delivery indicators, sticky dates, target-message highlighting, composer visibility, SIM selection, and sending.

### View implementation scheduled for removal

- Activity layouts: `activity_main`, `activity_settings`, `activity_new_message`, `activity_thread_detail`.
- Embedded layouts: onboarding, composer, tabs, and pager pages.
- Row layouts: threads, OTPs, search results, contact suggestions, message bubbles, and day indicators.
- RecyclerView adapters and `StickyDayHeaderDecoration` after their owning screens are migrated.
- The unused `ComposeActivity` and `activity_compose.xml` after confirming no callers.

### Required XML exception

`notification_otp.xml` remains. OTP notifications use Android `RemoteViews`, whose custom content is defined by a supported View layout. This is system UI outside the Activity screen migration and replacing it would change notification behavior.

Drawable, color, font, string, style, and other value XML resources are not screen layouts and remain available to Compose where useful.

## Compatibility Contract

The following are frozen unless a difference is explicitly reviewed and approved:

### Visual

- Content, ordering, dimensions, padding, alignment, typography, weights, colors, shapes, icons, dividers, badges, and animations.
- Static light/dark palettes and Material You dynamic color behavior.
- Google Sans Flex, Google Sans Code, and system font selections.
- Status/navigation bar handling, keyboard resizing, and safe-area padding.
- Normal and large system font scales.

### Navigation and lifecycle

- Back behavior from normal, search, and selection modes.
- Activity destinations, extras, external intents, and returning to prior state.
- Default-SMS role flow, runtime permission flow, and battery-optimization flow.
- Refresh behavior after settings changes, contact changes, received SMS, sent SMS, deletion, and returning from another Activity.

### Inbox

- Enabled tabs, order, default tab, horizontal paging, tab reselect scroll-to-top, unread dots, and muted Promotions indicator.
- Thread/OTP/All row content, ordering, click targets, avatar actions, long-press selection, multi-page selection count, select-all, and deletion confirmation.
- Unread-only filter animation and results.
- Search entry/exit, debounce, result ordering/highlighting, target-message navigation, and returning to the active search.
- OTP copy behavior and feedback.

### New message

- Intent prefill, keyboard focus, contact matching, raw number validation, duplicate prevention, multi-recipient selection, chip removal, and Backspace removal.
- GSM and Unicode character/part counting.
- Send-button enablement, SIM visibility/default/toggle animation, multipart sending, sent-provider insertion, toast wording, inbox refresh, and finish behavior.

### Thread detail

- Header name/number/avatar, saved/add-contact action, Personal-only call action, and composer eligibility.
- Incoming/outgoing alignment, spam badge, SIM indicator, delivery tick, link handling, and long-press text selection.
- Day grouping, sticky day label, bottom anchoring, automatic latest-message scroll, target-message scroll/highlight, and observer-driven reload.
- Composer counter, keyboard focus extra, SIM behavior, sending, clearing, feedback, and read-state update.

### Notifications and background components

- OTP notification appearance, copy action, channels, priority, receiver behavior, and debug notification control.
- SMS/MMS receivers and services remain independent of Compose.

## Delivery Strategy

Each phase uses conventional commits and must be independently reviewable and revertible. Prefer one PR per phase after Phase 0. A phase is not considered complete until its tests and emulator evidence pass.

### Phase 0: Establish the regression baseline

No production UI changes.

Execution status (2026-08-08): the clean-state/manual baseline, 65-screenshot manifest, six-scenario emulator regression suite, and PR emulator job are in place. Unit tests, debug assembly, installation, and API 37 instrumented tests pass. The phase remains blocked on the existing lint gate: `lintDebug` reports 12 errors in pre-existing production/debug files; none are in the Phase 0 changes. See `compose-migration/baseline/README.md` for evidence and exact reproduction steps.

1. Reset the existing emulator to a true first run:
   - Clear the app package data.
   - Remove the app from the default SMS role if needed.
   - Reinstall the current debug APK.
   - Do not seed messages until onboarding evidence is complete.
2. Capture onboarding from its first frame through completion:
   - Initial screen.
   - Default-SMS role system prompt.
   - Step 1 completed.
   - Battery-optimization system prompt.
   - Step 2 completed when supported.
   - Continue enabled and resulting empty inbox.
3. Grant the test permissions, seed the curated debug data, and capture every app UI state listed in the screenshot inventory below.
4. Add instrumented behavioral regression tests for non-destructive flows and deterministic seeded-data flows.
5. Replace the placeholder instrumented test name with product-specific test classes.
6. Add an emulator-backed instrumented-test job to PR CI while retaining the fast unit-test job.
7. Record baseline build, startup, and seeded-list rendering checks.

#### Phase 0 screenshot inventory

Screenshots are stored below `.agents/compose-migration/baseline/` with a manifest recording emulator identity, build commit, theme, font, font scale, and setup commands.

Onboarding and permissions:

- Onboarding initial/top.
- Onboarding scrolled middle and bottom/footer.
- Default-SMS role chooser.
- Onboarding after default-SMS role is granted.
- Battery-optimization request.
- Onboarding after background exemption, when supported.
- Empty inbox immediately after Continue.
- Permission-denied/degraded UI where it can be reproduced safely.

Main inbox:

- Every enabled tab: OTPs, Personal, Transactions, Services, Promotions, Government, and optional All.
- Top, middle, and bottom of every scrollable list that exceeds one viewport.
- Unread indicators and muted Promotions tab.
- Unread-only filter enabled and cleared.
- Search empty, search with results/highlighting, search with no results, and search clear.
- Selection mode with one item, mixed/multiple items where supported, select-all, delete confirmation, and cancellation.
- Overflow menu and delete dialog.
- Empty-list state for any category that exposes one.

Thread detail:

- Personal thread header, top/history, bottom/latest, composer, keyboard open, and call action visibility.
- Alphanumeric/service thread with hidden composer.
- Numeric non-contact thread with composer.
- Incoming and outgoing bubbles, spam badge, SIM indicator, delivery status, selectable text, web/email/phone links, day labels, and sticky header.
- Search-target highlight state.
- Contact/add-contact path and relevant system screen where safe.

New message:

- Initial focus/keyboard state.
- Contact suggestions at top and after scrolling.
- Raw-number suggestion.
- One recipient, multiple recipients, chip removal, and Backspace-removal state.
- Empty, valid, and disabled/enabled send states.
- GSM single-part, GSM multipart, Unicode single-part, and Unicode multipart counters.
- External SENDTO recipient/body prefill.
- SIM selector on devices/configurations where multiple subscriptions are available.
- Success/failure feedback only where sending can be tested without contacting a real person.

Settings:

- Top, middle, and bottom of the scrollable screen.
- Light, dark, and system theme selections.
- Sans, monospace, and system font selections.
- All-tab enabled/disabled and its effect on the default-tab menu.
- Every default-tab menu option and selected state.
- Promotional notification toggle.
- About links and debug-only section.
- Debug OTP notification and notification shade copy control.

Configuration variants:

- Light and dark mode for every primary screen.
- All three app fonts on representative screens.
- At least one representative pass at the largest usable system font scale.
- Material You dynamic color on the emulator configuration used for the baseline.
- Portrait throughout; landscape only where the Activity currently supports a usable landscape layout.

#### Phase 0 automated test inventory

- App launch and first-run setup visibility.
- Settings preference persistence and dependent behavior (`All` default fallback).
- Main tab presence/order/default selection, unread filter, search entry/exit, and back priority.
- Seeded OTP copy and selection-mode behavior.
- Thread opening with the expected header/composer visibility and target message.
- New-message send enablement, recipients, raw number, chip removal, message counters, and SENDTO prefill.
- Accessibility names for every icon-only action and primary control.

System role dialogs, notification shade behavior, real SMS delivery, dual-SIM sending, contact-app screens, and battery-optimization screens remain emulator/manual checks because they cross application boundaries.

### Phase 1: Compose foundation and Settings

1. Add the Compose compiler plugin matching the existing Kotlin version.
2. Enable Compose and add the smallest stable dependency set compatible with `compileSdk 36`:
   - Compose BOM.
   - Material 3.
   - Activity Compose.
   - Tooling preview/debug tooling.
   - Compose UI instrumentation test support.
3. Add `CleanSmsTheme` using the existing static palettes, dynamic color behavior, typography, fonts, and shapes.
4. Convert `SettingsActivity` as the first complete screen.
5. Preserve the debug/release source-set behavior without injecting Views into the Compose hierarchy.
6. Delete `activity_settings.xml` only after parity evidence passes.

Do not create a separate design-system module or speculative component library. Extract a shared composable only after a second real caller needs it.

### Phase 2: Default-SMS onboarding

1. Convert onboarding into a Compose subtree hosted by the existing `MainActivity`.
2. Keep role requests, battery settings intents, permission checks, and persisted completion state in the Activity.
3. Validate every system-return transition and empty-inbox transition.
4. Remove `screen_setup_default_sms.xml` after parity passes.

### Phase 3: New Message

1. Convert the whole Activity content using `setContent`.
2. Use `LazyColumn` for contact suggestions and Compose chips/inputs for recipients.
3. Keep the existing intent parsing, contact query, validation, SMS counting, SIM lookup, send, and provider insertion behavior.
4. Use platform image decoding for local contact photos; do not add an image-loading library solely for local URIs.
5. Delete the Activity/contact row layouts and `ContactSuggestionAdapter` after parity passes.

### Phase 4: Thread Detail

1. Convert header, message list, message rows, sticky dates, target highlight, and composer.
2. Use stable message IDs and preserve current chronological order and bottom anchoring.
3. Preserve selectable text and web/email/phone link behavior, including the card-last-four false-positive guard.
4. Keep the existing ContentObserver, query, sending, SIM, contact, read-state, and refresh behavior.
5. Delete the Activity/composer/message/day layouts, `MessageAdapter`, and `StickyDayHeaderDecoration` after parity passes.

### Phase 5: Main inbox

1. Convert the header, tabs, horizontal pager, all list types, search mode, unread filter, selection state, dialog, and FAB.
2. Keep current SMS/contact queries, categorization, OTP extraction, fuzzy search, deletion, and Activity navigation unchanged.
3. Use stable list keys and preserve reselect-to-top and target-message navigation.
4. Delete the main/page/tab/row layouts, the pager adapter, and the thread/OTP/search adapters after parity passes.

### Phase 6: Final cleanup

1. Remove the unused legacy `ComposeActivity` and `activity_compose.xml`.
2. Remove RecyclerView/ViewPager dependencies only when no remaining production or debug code uses them.
3. Remove Material View/AppCompat dependencies only if doing so does not alter Activity theme, night mode, dynamic colors, dialogs, notifications, or lifecycle behavior. Keeping a dependency is preferable to bundling a second risky migration.
4. Keep `notification_otp.xml` and its `RemoteViews` callers.
5. Update `AGENTS.md` from View-specific UI rules to Compose equivalents.
6. Run the complete final verification matrix.

## State and Architecture Rules During Migration

- Activities continue to own Android framework operations, permissions, Activity Results, ContentResolver access, observers, intents, and SMS sending.
- Composables receive display state and event callbacks. Introduce only the state objects required for the screen currently being migrated.
- Preserve existing formatting and decision logic. Move it out of an adapter only when the adapter is being removed.
- Do not fix unrelated behavior during migration. Record discoveries separately.
- Do not introduce Navigation Compose, DI, a new persistence layer, or an image-loading dependency.
- Do not leave commented-out XML/View implementations or permanent runtime migration switches.

## Acceptance Gate for Every Phase

Required commands:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew installDebug
```

Required emulator checks:

1. Clear/reseed only the debug-owned SMS rows as appropriate for the test.
2. Run the phase-specific automated test scenarios.
3. Repeat the relevant screenshot inventory and compare with Phase 0.
4. Check light/dark/system theme and all applicable fonts.
5. Check large text, content descriptions, touch targets, keyboard, back behavior, and rotation/recreation.
6. Check logcat for crashes and UI-related exceptions without logging SMS bodies, phone numbers, or OTP values.
7. Compare cold start with `am start -W` and list rendering with `dumpsys gfxinfo` for material regressions.

A phase must not merge with an unapproved behavior difference, visible regression, accessibility regression, privacy change, or meaningful performance regression.

## Rollback Strategy

- Each screen migration is a separate conventional commit and preferably a separate PR.
- The old implementation is deleted only in the same phase after its replacement passes the parity gate.
- Git history is the rollback mechanism; no permanent dual-implementation feature flag is added.
- If a Compose API cannot match a required behavior, stop that screen's phase and document the exact mismatch before changing the product contract.
