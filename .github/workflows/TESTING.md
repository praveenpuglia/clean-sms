# Testing the Release Workflow

## Before First Release - Test Locally

Before pushing your first tag, test the build locally:

```bash
# Test that release build works
./gradlew assembleRelease --stacktrace

# Check that APK was created
ls -lh app/build/outputs/apk/release/
```

If this succeeds, you're ready for automated releases!

## Setting Up Secrets Checklist

- [ ] Go to GitHub Repository Settings
- [ ] Navigate to: Secrets and variables → Actions
- [ ] Click "New repository secret"
- [ ] Add `KEYSTORE_FILE`:
  ```bash
  base64 -i keystore.jks | pbcopy
  ```
  Paste the clipboard content as the secret value
  
- [ ] Add `KEYSTORE_PASSWORD`: `YOUR_PASSWORD_HERE`
- [ ] Add `KEY_ALIAS`: `cleansms`
- [ ] Add `KEY_PASSWORD`: `YOUR_PASSWORD_HERE`

## Test Release (Dry Run)

1. **Create a test tag locally** (don't push yet):
   ```bash
   git tag v0.0.1-test
   ```

2. **Check what would be updated**:
   ```bash
   VERSION="0.0.1-test"
   VERSION_CODE=$((0 * 10000 + 0 * 100 + 1))
   echo "Version: $VERSION"
   echo "Version Code: $VERSION_CODE"
   ```

3. **Delete the test tag**:
   ```bash
   git tag -d v0.0.1-test
   ```

## Your First Real Release

Once you're confident:

```bash
# 1. Commit all changes
git add .
git commit -m "Ready for first release"
git push

# 2. Create and push v1.0.0 tag
git tag v1.0.0
git push origin v1.0.0

# 3. Monitor the build
# Go to: https://github.com/praveenpuglia/clean-sms/actions

# 4. Check the release
# Go to: https://github.com/praveenpuglia/clean-sms/releases
```

## What to Watch For

### In the Actions Tab:
- ✅ Checkout code
- ✅ Set up JDK 21
- ✅ Extract version from tag
- ✅ Update version in build.gradle.kts
- ✅ Decode Keystore
- ✅ Create keystore.properties
- ✅ Build Release APK
- ✅ Create Release
- ✅ Upload APK as artifact

### Expected Artifacts:
- APK file: `CleanSMS-v1.0.0.apk`
- Size: ~5-10 MB (depending on resources)

## Common Issues and Fixes

### Issue: "Keystore not found"
**Fix**: Verify `KEYSTORE_FILE` secret is correctly base64 encoded

### Issue: "Invalid keystore format"
**Fix**: 
```bash
# Re-encode keystore (ensure no line breaks)
base64 -i keystore.jks | tr -d '\n' | pbcopy
```

### Issue: "Wrong password"
**Fix**: Double-check `KEYSTORE_PASSWORD` and `KEY_PASSWORD` secrets

### Issue: "gradlew: Permission denied"
**Fix**: This is handled by the workflow's "Make gradlew executable" step

## Version Code Calculation Examples

The workflow automatically calculates version codes:

| Tag | Version Name | Version Code | Calculation |
|-----|--------------|--------------|-------------|
| v1.0.0 | 1.0.0 | 10000 | 1×10000 + 0×100 + 0 |
| v1.0.1 | 1.0.1 | 10001 | 1×10000 + 0×100 + 1 |
| v1.1.0 | 1.1.0 | 10100 | 1×10000 + 1×100 + 0 |
| v1.2.3 | 1.2.3 | 10203 | 1×10000 + 2×100 + 3 |
| v2.0.0 | 2.0.0 | 20000 | 2×10000 + 0×100 + 0 |

This ensures version codes always increment properly for the Play Store.

## Success Indicators

You'll know it worked when:
1. ✅ GitHub Actions workflow completes successfully (green checkmark)
2. ✅ A new release appears in the Releases page
3. ✅ The APK is attached to the release and downloadable
4. ✅ Release notes are automatically generated
5. ✅ APK is signed with your keystore (verify with: `jarsigner -verify -verbose -certs CleanSMS-v1.0.0.apk`)
