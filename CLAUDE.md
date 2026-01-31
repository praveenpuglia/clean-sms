# CLAUDE.md - Clean SMS Project Guide

## Project Overview

Clean SMS is a privacy-focused Android SMS messaging client written in Kotlin. It provides intelligent message categorization using TRAI headers (Indian SMS standard), OTP management, and spam detection with a modern Material Design 3 UI.

**Current Version**: 1.1.4
**Min SDK**: 33 (Android 13) | **Target SDK**: 36 (Android 15)

## Tech Stack

- **Language**: Kotlin 2.0.21
- **Build System**: Gradle 8.13.0 with Kotlin DSL
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
