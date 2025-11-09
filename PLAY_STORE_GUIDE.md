# Clean SMS - Google Play Store Submission Guide

## ✅ Technical Requirements (Completed)

### 1. App Bundle Generated
- **Location**: `app/build/outputs/bundle/release/app-release.aab`
- **Size**: 3.8 MB
- **Signed**: Yes, with your keystore
- **ProGuard/R8**: Enabled (code shrinking and obfuscation)

### 2. Build Commands
```bash
# Build App Bundle (for Play Store)
./gradlew bundleRelease

# Build APK (for testing/sharing)
./gradlew assembleRelease
```

## 📋 Play Store Submission Checklist

### Step 1: Google Play Console Setup ($25 one-time fee)

1. **Sign up**: https://play.google.com/console/
2. **Pay registration fee**: $25 USD (one-time)
3. **Complete account details**: Developer name, contact info
4. **Accept agreements**: Developer Distribution Agreement

### Step 2: Create Privacy Policy (REQUIRED)

Your app uses sensitive permissions (SMS, Contacts), so Google requires a privacy policy.

**What to include**:
- What data you collect (SMS messages, contacts, phone numbers)
- Why you collect it (to provide SMS messaging functionality)
- How you store it (locally on device only)
- That you don't share data with third parties
- User rights (data deletion, access)

**Options**:
1. Use a privacy policy generator (e.g., https://app-privacy-policy-generator.nisrulz.com/)
2. Host it on GitHub Pages (free)
3. Use a simple website/blog
4. Example template provided below

**Privacy Policy Template** (customize as needed):
```markdown
# Privacy Policy for Clean SMS

Last updated: November 7, 2025

Clean SMS ("the App") is committed to protecting your privacy. This policy explains how we handle your information.

## Information Collection and Use

The App requires the following permissions to function:
- **SMS Permissions**: To read, send, and receive text messages
- **Contacts Permissions**: To display contact names and phone numbers
- **Phone Permissions**: To handle phone number formatting

### Data Storage
All data remains on your device. We do not:
- Collect or upload your messages to any server
- Share your data with third parties
- Track your usage or behavior
- Store data on external servers

### Data Usage
Your SMS messages and contacts are only used to:
- Display your message threads
- Send and receive SMS messages
- Show contact names in conversations
- Detect and display OTP codes in notifications

## Security
All data is stored locally on your Android device using Android's secure storage mechanisms.

## Changes to This Policy
We may update this policy. Changes will be posted with an updated revision date.

## Contact Us
For questions about this privacy policy, contact: [your-email@example.com]
```

### Step 3: Prepare App Store Assets

#### Required Graphics:

1. **App Icon** (already have)
   - 512x512 PNG
   - No alpha channel
   - Extract from `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` and upscale

2. **Feature Graphic** (REQUIRED)
   - 1024x500 PNG or JPG
   - Showcases your app's main feature
   - Use a design tool (Canva, Figma) or hire a designer

3. **Screenshots** (minimum 2, maximum 8)
   - Phone: 320-3840px on longest side
   - Take screenshots of:
     - Messages list view
     - Thread detail view
     - New message composition
     - OTP notification
   - Use Android Studio's Device Manager to get clean screenshots

4. **Optional but Recommended**:
   - Video (YouTube): 30s-2min demo
   - Tablet screenshots (if supporting tablets)

#### App Store Listing Text:

**App Name** (30 chars max):
```
Clean SMS
```

**Short Description** (80 chars max):
```
A clean, ad-free SMS app with Material Design and smart OTP detection
```

**Full Description** (4000 chars max):
```
Clean SMS is a modern, lightweight SMS messaging app built with Material You design principles. Enjoy a clutter-free texting experience without ads or unnecessary features.

✨ KEY FEATURES:

📱 Material Design 3
• Beautiful, adaptive interface that matches your device theme
• Smooth animations and modern UI components
• Light and dark mode support

💬 Core Messaging
• Send and receive SMS messages
• Thread-based conversations
• Contact integration with names and photos
• Multiple recipient support

🔢 Smart OTP Detection
• Automatically detects one-time passwords
• Quick copy button in notification
• No need to open the app

🎯 Clean & Simple
• No ads, ever
• No analytics or tracking
• All data stays on your device
• Lightweight and fast

⚡ Quick Actions
• Compose new messages easily
• Send messages from contacts app
• Material You chip-based recipient selection

🔒 Privacy First
• No data collection
• No internet permission required
• Your messages never leave your device
• Open source (link to GitHub)

Perfect for users who want a straightforward, privacy-respecting SMS app without bloat.

---
Requires Android 13 or higher.
This is an SMS app - requires SMS permissions to function.
```

**Categorization**:
- App Category: Communication
- Content Rating: Everyone
- Target Age: All ages

### Step 4: Upload to Play Console

1. **Create New App**:
   - Go to Play Console → Create App
   - Choose name: "Clean SMS"
   - Default language: English (US)
   - App/Game: App
   - Free/Paid: Free

2. **Set Up Store Listing**:
   - Upload graphics (icon, feature graphic, screenshots)
   - Enter descriptions
   - Add privacy policy URL
   - Categorize your app

3. **Content Rating**:
   - Complete the questionnaire
   - Should get "Everyone" rating

4. **Target Audience**:
   - Age: All ages
   - Appeals to children: No

5. **Data Safety**:
   - Does your app collect or share user data? **NO**
   - Select required permissions and explain usage:
     - SMS: "Required to send and receive text messages"
     - Contacts: "To display contact names"
     - Phone: "For phone number handling"

6. **App Access**:
   - All functionality available without login: **YES**

7. **Upload App Bundle**:
   - Go to Production → Create Release
   - Upload `app-release.aab`
   - Set release notes (what's new)

8. **Pricing & Distribution**:
   - Free
   - Select countries (or worldwide)
   - Confirm content guidelines compliance

9. **Submit for Review**:
   - Review everything
   - Submit for review
   - Wait 1-3 days for approval

### Step 5: Post-Launch

1. **Monitor**:
   - Check Play Console for crashes
   - Respond to user reviews
   - Monitor ratings

2. **Updates**:
   - Increment `versionCode` and `versionName` in `build.gradle.kts`
   - Build new bundle: `./gradlew bundleRelease`
   - Upload to Play Console as new release

## 🔐 Important Reminders

- **Backup your keystore**: `keystore.jks` and `keystore.properties`
  - Without these, you CANNOT update your app
  - Store in a secure location (password manager, secure cloud storage)

- **API Level Requirements**:
  - Current: minSdk = 33 (Android 13)
  - This limits your audience to ~40% of Android devices
  - Consider lowering to minSdk = 26 (Android 8) for wider reach

- **Testing**:
  - Use Internal Testing track first
  - Add testers via email
  - Test thoroughly before production release

## 📊 Version Management

Current version:
- Version Code: 1
- Version Name: "1.0"

Update in `app/build.gradle.kts` for each release:
```kotlin
versionCode = 2  // Increment for each release
versionName = "1.1"  // User-facing version
```

## 🚀 Quick Commands

```bash
# Build for Play Store
./gradlew bundleRelease

# Build for direct sharing
./gradlew assembleRelease

# Clean build
./gradlew clean bundleRelease

# Check bundle size
ls -lh app/build/outputs/bundle/release/
```

## 📞 Support Resources

- Google Play Console: https://play.google.com/console/
- Play Console Help: https://support.google.com/googleplay/android-developer
- App Bundle Guide: https://developer.android.com/guide/app-bundle
- Play Store Policies: https://play.google.com/about/developer-content-policy/

---

Good luck with your launch! 🎉
