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
   - `RELEASE_TOKEN`: a fine-grained personal access token from a repo admin (see below)

4. Create `RELEASE_TOKEN`. `main` requires local signoffs before anything lands, and on a personal
   repo GitHub Actions can't bypass that rule, so the Release workflow pushes its version-bump
   commit with an admin's token:
   - GitHub → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**
   - **Repository access:** Only select repositories → `clean-sms`
   - **Permissions:** Repository → **Contents: Read and write** (nothing else)
   - Pick an expiry and save the token as the `RELEASE_TOKEN` secret. When it expires, releases fail
     at the first step with a clear error; generate a new one and update the secret.

### Creating a Release

Releases are started by hand; nothing runs on push or tag.

1. Make sure everything to ship is merged to `main` (merges require the local signoffs, see `scripts/signoff.sh`).
2. Go to **GitHub Repository → Actions → Release → Run workflow**.
3. Pick the **Version bump**:
   - `auto` (default): derived from Conventional Commits since the last tag. A breaking change (`feat!:` or `BREAKING CHANGE`) bumps major, any `feat:` bumps minor, anything else bumps patch.
   - `patch`, `minor` or `major` to force one.

The workflow then:
- computes the next version from the latest `v*` tag (`versionCode = MAJOR*10000 + MINOR*100 + PATCH`) and fails if that tag already exists
- updates `versionName` and `versionCode` in `app/build.gradle.kts`
- builds the signed release APK and AAB
- only after a successful build: commits `chore: release vX.Y.Z` to `main`, tags `vX.Y.Z` and pushes both
- creates the GitHub Release with generated notes and `CleanSMS-vX.Y.Z.apk` / `.aab` attached

### Version Numbering

Semantic versioning, `vMAJOR.MINOR.PATCH`, chosen by the bump above. Use Conventional Commit messages so `auto` picks the right bump.

### Checking Build Status

1. Go to **GitHub Repository → Actions → Release** and open the run.
2. Once it completes, download the APK/AAB from **Releases**.

## Manual Build (Local)

If you want to build locally:

```bash
./gradlew assembleRelease
# APK will be at: app/build/outputs/apk/release/app-release.apk
```

## Troubleshooting

### If the build fails:
1. Check the Release run's logs in the Actions tab
2. Verify secrets are correctly configured (an expired `RELEASE_TOKEN` fails the first step)
3. Nothing is committed or tagged unless the build succeeded, so just run the workflow again

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
3. Run the Release workflow again with the bump that produces that version

## Next Steps

For Play Store release, see: `PLAY_STORE_GUIDE.md`
