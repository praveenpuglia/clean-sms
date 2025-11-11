# GitHub Actions - Release Workflow

## Overview
This workflow automatically builds a signed release APK when you push a version tag to GitHub.

## Setup Instructions

### 1. Configure GitHub Secrets
Navigate to: **Repository → Settings → Secrets and variables → Actions**

Add the following secrets:

| Secret Name | Value | How to Get |
|------------|-------|------------|
| `KEYSTORE_FILE` | Base64 encoded keystore | Run: `base64 -i keystore.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | Your keystore password | From your keystore.properties |
| `KEY_ALIAS` | Your key alias | From your keystore.properties |
| `KEY_PASSWORD` | Your key password | From your keystore.properties |

### 2. Create and Push a Release Tag

```bash
# Create a tag (e.g., for version 1.0.0)
git tag v1.0.0

# Push the tag to GitHub
git push origin v1.0.0
```

### 3. What Happens Next

The GitHub Action will:
1. ✅ Extract the version from the tag (e.g., `v1.0.0` → `1.0.0`)
2. ✅ Generate a version code (e.g., `1.0.0` → `10000`, `1.2.3` → `10203`)
3. ✅ Update `versionCode` and `versionName` in `app/build.gradle.kts`
4. ✅ Build a signed release APK
5. ✅ Create a GitHub Release with the APK attached
6. ✅ Generate release notes automatically

## Version Numbering Scheme

- **Tag format**: `v[MAJOR].[MINOR].[PATCH]` (e.g., `v1.2.3`)
- **Version Name**: Same as tag without 'v' (e.g., `1.2.3`)
- **Version Code**: `MAJOR * 10000 + MINOR * 100 + PATCH`
  - `v1.0.0` → Version Code `10000`
  - `v1.2.3` → Version Code `10203`
  - `v2.0.0` → Version Code `20000`

## APK Output

The APK will be named: `CleanSMS-v[VERSION].apk`

Example: `CleanSMS-v1.0.0.apk`

## Monitoring the Build

1. Go to your repository on GitHub
2. Click on the **Actions** tab
3. You'll see the "Build and Release APK" workflow running
4. Once complete, check the **Releases** section for your new release

## Troubleshooting

### Build fails with keystore error
- Verify all secrets are correctly set
- Ensure `KEYSTORE_FILE` is properly base64 encoded
- Check that passwords match your actual keystore

### Version not updating
- Ensure your tag follows the `v*` pattern (e.g., `v1.0.0`)
- Check the workflow logs to see the extracted version

### APK not attached to release
- Check the workflow logs for errors in the "Create Release" step
- Verify `GITHUB_TOKEN` permissions are correct (should be automatic)
