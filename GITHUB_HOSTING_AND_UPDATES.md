# GPS Camera — GitHub hosting and update setup

## What is included
- GitHub Actions CI builds a debug APK for pushes and pull requests.
- Tagging a version like `v1.9.0` triggers a signed APK build and publishes it as a GitHub Release.
- The app's Settings page has **Check for updates**. It checks the latest public GitHub Release and opens that release page if a newer version exists.
- Android will always ask the user to approve APK installation. Android apps cannot silently self-install updates through this mechanism.

## 1. Create the repository
1. Sign in to GitHub and create a **public** repository named `gps-camera` (or choose another name).
2. Do not initialize it with a README if you plan to push this project as-is.
3. Before the first push, edit `app/src/main/java/com/gatha/gpscamera/GitHubUpdateChecker.kt` and set `OWNER` to your GitHub username and `REPOSITORY` to the exact repository name.
4. Push the project to the repository's `main` branch.

## 2. Protect the signing key
The release APK must be signed with the **same keystore/key** used by the version users already have installed, otherwise Android will refuse to install it as an update.
Never commit the `.jks`/`.keystore` file or passwords.

In GitHub repository → Settings → Secrets and variables → Actions, add these repository secrets:
- `ANDROID_KEYSTORE_BASE64`: base64 encoding of your existing keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias.
- `ANDROID_KEY_PASSWORD`: key password.

Windows PowerShell can encode the existing keystore without uploading it to this chat:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\to\your-existing-keystore.jks')) | Set-Clipboard
```
Paste the clipboard value into the `ANDROID_KEYSTORE_BASE64` secret. Do not paste the value into an issue, chat, source file, or commit.

If you do not have the existing signing key, do not publish a release intended to update the already-installed app until the signing situation is resolved.

## 3. Publish a release
1. Update `versionCode` (always increase it) and `versionName` in `app/build.gradle.kts`.
2. Commit and push those changes.
3. Create and push a version tag matching the app version, for example:
```bash
git tag v1.9.0
git push origin v1.9.0
```
4. GitHub Actions builds a signed release APK and attaches it to a GitHub Release.
5. Open the published release and confirm `app-release.apk` is attached.

The updater compares the app's `versionName` with the latest GitHub Release tag. Keep the tag and app version aligned (for example `versionName = "1.9.0"`, release tag `v1.9.0`). Only publish a release after you have built and tested it on your phone.

## Important limitation
This is an update checker plus a user-approved install flow, not silent installation. The app opens the release page; the user downloads the APK and confirms installation in Android. Direct in-app downloading/installing would require additional download, file-provider, package-installer, and unknown-app-install permission flows.
