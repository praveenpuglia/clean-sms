# CLAUDE.md - Clean SMS Project Guide

## Project Overview

Clean SMS is a privacy-focused Android SMS messaging client written in Kotlin. It provides intelligent message categorization using TRAI headers (Indian SMS standard), OTP management, and spam detection with a modern Material Design 3 UI.

**Current Version**: 1.1.4
**Min SDK**: 33 (Android 13) | **Target SDK**: 36 (Android 15)

## Tech Stack

- **Language**: Kotlin 2.2.10
- **Build System**: Gradle 9.3.1 with Kotlin DSL (AGP 9.1.0, JDK 21 toolchain)
- **UI**: Material Design 3 with dynamic colors (Material You)
- **Architecture**: Standard Android with Activities, Receivers, and Services
- **Key Dependencies**: libphonenumber (phone parsing), AndroidX, Material Components

## Project Structure

```
app/src/main/java/com/praveenpuglia/cleansms/
├── MainActivity.kt              # Main screen with tabs (Messages/OTPs)
├── ThreadDetailActivity.kt      # Conversation view
├── ComposeActivity.kt           # Message composition
├── NewMessageActivity.kt        # New message creation
├── SettingsActivity.kt          # App settings
├── CategoryClassifier.kt        # Message categorization engine (TRAI headers)
├── IncomingSmsReceiver.kt       # Incoming SMS handling & notifications
├── Message.kt / ThreadItem.kt   # Data models
└── *Adapter.kt                  # RecyclerView adapters
```

## Development Workflow

### Prerequisites
- Android device connected via USB with developer mode enabled
- ADB configured and device visible (`adb devices` shows your device)

### Build & Install Commands

```bash
# Install debug APK on connected device (primary workflow command)
./gradlew installDebug

# Build debug APK without installing
./gradlew assembleDebug

# Build release APK (signed)
./gradlew assembleRelease

# Build App Bundle for Play Store
./gradlew bundleRelease

# Clean build
./gradlew clean

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

### Auto-Install After Changes

After making code changes, run this to automatically build and install on connected phone:
```bash
./gradlew installDebug
```

For faster iteration, you can also use:
```bash
# Quick reinstall (skips some checks)
./gradlew installDebug --offline
```

### Live Reload / Hot Reload

Native Android doesn't support true hot reload like Flutter. Options for faster iteration:

1. **Apply Changes** (Android Studio): Use `Ctrl+Alt+F10` to apply code changes without full reinstall (works for method body changes)

2. **Apply Code Changes** (Android Studio): Use `Ctrl+F10` for structural changes

3. **Automatic rebuild on file save**: Use file watcher with gradle:
   ```bash
   # Watch and rebuild (in a separate terminal)
   ./gradlew assembleDebug --continuous
   # Then manually install when ready
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Quick ADB Commands

```bash
# Check connected devices
adb devices

# Install APK directly
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall app
adb uninstall com.praveenpuglia.cleansms

# View logs from app
adb logcat | grep -i cleansms

# Launch app after install
adb shell am start -n com.praveenpuglia.cleansms/.MainActivity

# Full workflow: build, install, and launch
./gradlew installDebug && adb shell am start -n com.praveenpuglia.cleansms/.MainActivity
```

### Seeding test messages (debug builds only)

[DebugSeedReceiver](app/src/debug/java/com/praveenpuglia/cleansms/DebugSeedReceiver.kt) populates the inbox with ~56 curated messages covering every TRAI category, all 4 OTP detection strategies, OTP false-positive guards (PNRs, order numbers, monetary-only), Airtel-SPAM-prefixed spam, and a mix of read/unread. Useful for UI testing on a fresh emulator.

One-time setup on a new device:
```bash
./gradlew installDebug
# Grant the SMS role so the app has WRITE_SMS (required to insert into the SMS provider)
adb shell cmd role add-role-holder android.app.role.SMS com.praveenpuglia.cleansms 0
```

Seed or re-seed (clears its own prior rows, leaves real messages alone):
```bash
adb shell am broadcast -a com.praveenpuglia.cleansms.DEBUG_SEED
```

Seeded rows are tagged via `service_center=CLEAN_SMS_DEBUG_SEED`. The receiver only exists in the `debug` source set, so release builds are unaffected.

## Project Principles

Enduring rules that govern all changes. PR descriptions should call out any deviations.

### Material Design 3 & Theming
- Use Material Components views (`MaterialToolbar`, `MaterialCardView`, `TextInputLayout`/`TextInputEditText`, `Chip`, `MaterialButton`, `FloatingActionButton`). No plain `EditText` unless justified.
- Typography follows the M3 type scale (Title Medium 16sp for primary text, Body Medium 14sp for metadata, Body Small 12sp for timestamps, Label Small 11sp for badges).
- Icons: 24dp standard, 16dp only for inline metadata. **All icons/drawables must be theme-aware** — use `app:tint="?attr/colorOnSurface"` (or appropriate theme attribute), never `@android:color/white` or other hardcoded colors.
- Reference theme attributes (`?attr/colorSurface`, `?attr/colorPrimary`) for semantic colors — no inline color literals.
- Dynamic color (Material You) on Android 12+; static M3 palette as fallback. Dark theme must reach parity.
- Use M3 shape tokens; avoid arbitrary corner radii. Elevation only where semantically meaningful.

### UX & Interaction
- **No implicit navigation side-effects**: sending a message does NOT auto-open the thread view. User stays in context.
- OTP detection must be high precision — avoid aggressive heuristics that yield false positives. Filter monetary amounts.
- Every send action gives immediate feedback (toast/snackbar) and reflects message state visually where possible.
- Compose flow: supports multiple recipients; backspace on empty input removes last chip; raw numbers not in contacts are treated as valid recipients.
- Accessibility: 48dp minimum touch targets, content descriptions on all icon-only buttons (Back, Send, FAB, Delete), TalkBack support, chips/text fields handle large font scaling.

### Architecture & Performance
- Separation of concerns: Activities/Fragments render and handle input; data access (ContentResolver queries) belongs in repository-style helpers as complexity grows.
- Defensive queries: always null/empty-check cursor columns; close cursors with `use { }` blocks.
- OTP detection and thread enrichment run off the UI thread (coroutines / structured concurrency — no ad hoc thread spawning).
- Lazy/incremental loading for contacts and large lists; debounce/batch on-device queries.
- `RecyclerView` with stable IDs and `ListAdapter`/`DiffUtil` for dynamic sets.

### Code Style
- Idiomatic Kotlin; data classes, extension functions sparingly, avoid long parameter lists.
- Catch specific exceptions — no broad `catch (Exception)` unless wrapping/annotating and rethrowing.
- Logging: structured tags, no PII (don't log full phone numbers, SMS bodies, or OTP values — use placeholders/shortened forms).
- Strings in `strings.xml` with placeholders; don't concatenate user data with static phrases.

### Privacy & Permissions
- Read minimal data required — don't prefetch full SMS bodies where metadata suffices.
- No external network calls for SMS/content enrichment without explicit user opt-in. Never transmit messages or OTPs off-device.
- Runtime permissions requested only when needed; features requiring the Default SMS Role must degrade gracefully when not granted.

### Build & Release
- Use debug builds for iterative development (`./gradlew installDebug`). After install, auto-relaunch with `adb shell am start -n com.praveenpuglia.cleansms/.MainActivity` to verify.
- Reserve release builds for final testing or production.
- Builds must be lint-clean (or suppression justified). Deprecated APIs (e.g. `SmsManager.getDefault()`) tracked for replacement.

## Key Architecture Patterns

### Message Categorization (`CategoryClassifier.kt`)
- Parses TRAI headers (format: `XY-HEADER-SFX`)
- Categories: PERSONAL, PROMOTIONAL, TRANSACTIONAL, SERVICE, GOVERNMENT, UNKNOWN
- Uses caching via `CategoryStorage` for performance

### OTP Extraction
- High-precision proximity-based detection
- Filters monetary amounts to avoid false positives
- Supports "is XXXX" pattern recognition

### Spam Detection (`SpamDetector.kt`)
- Detects Airtel SPAM prefix
- Extracts clean message body

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests (on device)
./gradlew connectedAndroidTest

# Test with coverage
./gradlew testDebugUnitTest
```

## Release Process

1. Create a git tag: `git tag v1.2.0`
2. Push tag: `git push origin v1.2.0`
3. GitHub Actions automatically builds and creates a release

See [RELEASE_GUIDE.md](RELEASE_GUIDE.md) for detailed instructions.

## Important Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | App-level build config, version management |
| `gradle/libs.versions.toml` | Dependency version catalog |
| `app/src/main/AndroidManifest.xml` | Permissions and component declarations |
| `keystore.properties` | Signing credentials (local only, not in git) |

## Permissions Used

- `READ_SMS`, `SEND_SMS`, `RECEIVE_SMS` - Core SMS functionality
- `READ_CONTACTS` - Contact name/photo display
- `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` - MMS support
- `POST_NOTIFICATIONS` - Message notifications

## Common Tasks

### Adding a new message category
1. Add enum value in `MessageCategory.kt`
2. Update classification logic in `CategoryClassifier.kt`
3. Update UI in relevant adapters

### Modifying notification behavior
- Edit `IncomingSmsReceiver.kt` for incoming SMS notifications
- Edit `OtpCopyReceiver.kt` for OTP-related notifications

### Updating UI themes
- Light theme: `res/values/themes.xml`
- Dark theme: `res/values-night/themes.xml`
- Colors: `res/values/colors.xml`
