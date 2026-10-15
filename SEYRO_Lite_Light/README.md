# SEYRO Lite 0.3 — Light

Native Android Java prototype with a refreshed light dashboard, clearer connection and subscription cards, and profile settings for a permanent SEYRO subscription link.

## Included
- Redesigned light dashboard cards and improved spacing.
- Profile dialog accepts and locally stores an `https://`, `http://`, or `vless://` link using Android SharedPreferences.
- Settings, subscription, and help screens explain current connection state.
- Build is intentionally transparent: storing a link does not mean a VPN tunnel is active.

## Not yet implemented
- VLESS + REALITY core and Android `VpnService` integration.
- Fetching and parsing a subscription URL.
- SEYRO API authentication, subscription status, traffic usage, and server selection.

The Connect action must not be treated as a working VPN until the engine and API are integrated.

## Build APK
Open this folder in Android Studio, allow Gradle sync, then choose Build > Build APK(s).

Application ID: `com.seyro.lite`
Version: `0.3-light`
Minimum Android: 8.0 (API 26)
