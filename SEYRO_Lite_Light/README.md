# SEYRO Lite — Android VPN client

Native Android application written in Java.

> **Branch status:** the `main` branch still contains the early UI prototype. The current working implementation, version **0.7.12** (`versionCode 12`), is being developed on [`feature/profile-subscription-link`](https://github.com/pavelzolotovng-oss/seyro-lite/tree/feature/profile-subscription-link/SEYRO_Lite_Light). Use that branch when building the current VPN client; do not use the older `main` APK as the current release.

## Current development version (0.7.12)

The implementation on `feature/profile-subscription-link` includes:

- VLESS + REALITY subscription profiles.
- TCP and XHTTP transports, with automatic or manual profile selection.
- Android VPN tunnel routed via HEV tun2socks to the local Xray SOCKS inbound.
- Locally stored subscription link, clipboard paste, and save/cancel controls.
- Connection state, selected transport, and debug log screen.

TCP and XHTTP were manually checked on a device with build 67. The signed 0.7.12 build must still be installed and checked on-device, including update-in-place behavior, before it is treated as verified.

## Build the current development version

1. Open the [Build SEYRO Lite APK workflow](https://github.com/pavelzolotovng-oss/seyro-lite/actions/workflows/android-apk.yml).
2. Select **Run workflow**.
3. Choose branch `feature/profile-subscription-link`.
4. Start the workflow and wait for a green check.
5. Download artifact `seyro-lite-debug-apk`.

The workflow uses JDK 17 and Gradle 8.9, downloads the required Xray and HEV AAR files, builds the debug APK, and keeps the artifact for 14 days.

## Persistent APK signing

To build signed APKs that can be updated in place, the development branch expects four **repository Actions secrets**:

- `ANDROID_KEYSTORE_BASE64` — base64 contents of `seyro-release.jks`.
- `ANDROID_KEYSTORE_PASSWORD` — keystore password.
- `ANDROID_KEY_ALIAS` — currently `seyro-release`.
- `ANDROID_KEY_PASSWORD` — private-key password.

Configure them under **Settings → Secrets and variables → Actions → Repository secrets**. Existing workflow runs do not receive secrets added after they start; start a new run after adding or correcting secrets.

Never commit the keystore, Base64 value, or passwords to Git. Back up `seyro-release.jks` securely. Future updates must use this same key and a higher `versionCode`.

The initial move from the previous automatically generated debug certificate to the persistent key requires one uninstall/reinstall. After the migration, explicitly test installing a newer APK over the existing app without uninstalling.

## Application information (current development branch)

- Application ID: `com.seyro.lite`
- Version name: `0.7.12`
- Version code: `12`
- Minimum Android: Android 10 (API 29)
- Compile SDK / target SDK: 35

The `main` branch remains an earlier prototype until the development branch is merged.
