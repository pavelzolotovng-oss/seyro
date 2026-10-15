# SEYRO Lite 0.7.12

Native Android VPN client written in Java. The active development branch is `feature/profile-subscription-link`.

## Current status

- VLESS + REALITY profiles loaded from a subscription URL.
- TCP and XHTTP transports are supported; the selected transport is shown in the app.
- Android VPN traffic is routed through HEV tun2socks to the local Xray SOCKS inbound.
- Profile/subscription URL is stored locally using Android SharedPreferences.
- Profile selection supports automatic and manual modes.
- The profile dialog supports pasting a link from the clipboard and saving or cancelling changes.
- Version: `0.7.12` (`versionCode 12`).
- Application ID: `com.seyro.lite`.
- Minimum Android: Android 10 (API 29).
- Compile/target SDK: Android 35.

TCP and XHTTP were manually checked on a device for build 67. Every new build still needs to be checked before distribution.

## Build from GitHub Actions

The workflow is [Build SEYRO Lite APK](https://github.com/pavelzolotovng-oss/seyro-lite/actions/workflows/android-apk.yml). It builds `app-debug.apk` and uploads the artifact as `seyro-lite-debug-apk` for 14 days.

To build manually:

1. Open the repository's **Actions** tab.
2. Select **Build SEYRO Lite APK**.
3. Choose branch `feature/profile-subscription-link` and press **Run workflow**.
4. Open the successful run and download the `seyro-lite-debug-apk` artifact.

The workflow downloads the required Xray and HEV AAR libraries, then builds with Gradle 8.9 and JDK 17.

## Persistent APK signing

The workflow signs every debug APK with a persistent keystore so that future APKs can be installed as updates when the signing certificate remains the same and `versionCode` increases.

Add these **repository Actions secrets** in **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: base64 contents of the private `seyro-release.jks` keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias (currently `seyro-release`).
- `ANDROID_KEY_PASSWORD`: private-key password.

Never commit the keystore, base64 value, or passwords to Git. Keep an encrypted backup of `seyro-release.jks`; future updates must use the same keystore and key.

If the workflow reports that a secret is empty, verify that all four names above are configured as **Repository secrets** (not only Environment secrets) and then start a new workflow run. Existing runs do not receive secrets added after that run starts.

Changing from the old debug certificate to this persistent signing key requires a one-time uninstall/reinstall. After migration, test installing a new APK over the installed app before relying on in-place updates.

## Local Android Studio build

Open this folder in Android Studio and sync Gradle. The current build script expects the signing environment variables described above. Do not use a random/generated keystore for each build, because that prevents normal APK updates.

## Security notes

Subscription links may be credentials: do not paste them into public issues or logs. The signing keystore and its passwords are separate from the VPN subscription link.
