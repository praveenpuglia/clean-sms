# Release Process

## Quick Start

### First Time Setup
1. Add GitHub Secrets (one-time setup):
   ```bash
   # Get base64 encoded keystore
   base64 -i keystore.jks | pbcopy
   ```
   
2. Go to: **GitHub Repository → Settings → Secrets and variables → Actions**

3. Add these secrets:
   - `KEYSTORE_FILE`: Paste the base64 string from clipboard
   - `KEYSTORE_PASSWORD`: `THE_PASSWORD_YOU_SET`
   - `KEY_ALIAS`: `cleansms`
   - `KEY_PASSWORD`: `THE_PASSWORD_YOU_SET`

### Creating a Release

```bash
# 1. Ensure your code is committed
git add .
git commit -m "Prepare for release v1.0.0"
git push

# 2. Create and push a version tag
git tag v1.0.0
git push origin v1.0.0

# 3. GitHub Actions will automatically:
#    - Build the signed APK
#    - Update version in build.gradle.kts
#    - Create a GitHub Release
#    - Attach the APK to the release
```

### Version Numbering

Use semantic versioning: `vMAJOR.MINOR.PATCH`

Examples:
- `v1.0.0` - First release
- `v1.0.1` - Bug fix
- `v1.1.0` - New feature
- `v2.0.0` - Major changes/breaking changes

### Checking Build Status

1. Go to: **GitHub Repository → Actions**
2. Find your tag in the workflow runs
3. Once complete, go to: **Releases** to download the APK

### APK Location

After successful build:
- **GitHub Release**: Contains `CleanSMS-v[VERSION].apk`
- **Artifacts**: Also available in the workflow artifacts for 90 days

## Manual Build (Local)

If you want to build locally:

```bash
./gradlew assembleRelease
# APK will be at: app/build/outputs/apk/release/app-release.apk
```

## Troubleshooting

### If the build fails:
1. Check the Actions tab for error logs
2. Verify secrets are correctly configured
3. Ensure tag format is correct (`v*` pattern)

### If you need to delete a tag:
```bash
# Delete locally
git tag -d v1.0.0

# Delete from GitHub
git push origin :refs/tags/v1.0.0
```

### If you need to re-release the same version:
1. Delete the tag (see above)
2. Delete the release on GitHub (Releases → Delete release)
3. Create and push the tag again

## What Gets Updated Automatically

When you push a tag, the workflow will:
- ✅ Update `versionCode` in `app/build.gradle.kts`
- ✅ Update `versionName` in `app/build.gradle.kts`
- ✅ Build a signed release APK
- ✅ Create a GitHub Release with changelog
- ✅ Attach the APK to the release

## Next Steps

For Play Store release, see: `PLAY_STORE_GUIDE.md`
