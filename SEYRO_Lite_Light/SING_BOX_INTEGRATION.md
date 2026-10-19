# SEYRO sing-box runtime integration

## Current state
- The isolated test app uses `com.github.singbox-android:libbox:1.14.3` as a runtime dependency.
- `SeyroVpnService.kt` starts and stops sing-box through `CommandServer`; the former Xray/HEV service and AAR dependencies are removed from this test branch.
- `SeyroPlatformInterface.kt` implements the Android platform bridge and creates the Android TUN interface from sing-box `TunOptions`.
- `SingBoxConfigBuilder.java` converts SEYRO's generated VLESS Xray JSON into sing-box JSON for TCP, gRPC, WebSocket, and HTTPUpgrade.
- GitHub Actions successfully compiled and published the first ARM64 test APK. Build success confirms compilation/package creation, not a verified live VPN connection.
- The test package ID is `com.seyro.lite.singboxtest`, so it installs separately and does not replace the user's current SEYRO Lite installation.

## Known limitations before production
- XHTTP is not supported by sing-box 1.14.3's V2Ray transport set; profiles using XHTTP must report a clear unsupported-transport error or use a supported transport.
- Physical-device verification is still required for VPN permission, TUN routing, DNS, and a real VLESS + REALITY endpoint.
- Do not publish this test APK as a production update until those checks pass and the production package/signing configuration is intentionally migrated.
