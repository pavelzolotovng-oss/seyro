# SEYRO sing-box runtime integration

## Current state
- The SEYRO Lite app uses `com.github.singbox-android:libbox:1.14.3` as a runtime dependency.
- `SeyroVpnService.kt` starts and stops sing-box through `CommandServer`; the former Xray/HEV service and AAR dependencies are removed from this app.
- `SeyroPlatformInterface.kt` implements the Android platform bridge and creates the Android TUN interface from sing-box `TunOptions`.
- `SingBoxConfigBuilder.java` converts SEYRO's generated VLESS Xray JSON into sing-box JSON for TCP, gRPC, WebSocket, and HTTPUpgrade.
- GitHub Actions successfully compiled and published the first ARM64 APK. Build success confirms compilation/package creation, not a verified live VPN connection.
- The application ID is `com.seyro.lite`, intended to update the existing SEYRO Lite installation rather than install a second app. The APK must be signed with the same release key as the installed app.

## Known limitations before production
- XHTTP is not supported by sing-box 1.14.3's V2Ray transport set; profiles using XHTTP must report a clear unsupported-transport error or use a supported transport.
- Physical-device verification is still required for VPN permission, TUN routing, DNS, and a real VLESS + REALITY endpoint.
- Before treating this as a stable release, verify VPN permission, TUN routing, DNS, and a real VLESS + REALITY endpoint on the target device.
