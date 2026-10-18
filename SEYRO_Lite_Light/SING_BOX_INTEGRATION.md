# Sing-box integration status

This branch is the isolated SEYRO TEST app.

## Current state
- `com.github.singbox-android:libbox:1.14.3` is on the compile classpath only.
- Xray's `libv2ray.aar` and HEV are still runtime dependencies because the current `SeyroVpnService` still imports and starts them.
- Do not change libbox to `implementation` while libv2ray is also packaged: both AARs export Go binding classes such as `go.Seq` and `go.Universe`.

## Required next changes before packaging libbox
1. Add a sing-box-specific VPN service implementing `io.nekohasekai.libbox.PlatformInterface` and `CommandServerHandler`.
2. Implement `openTun(TunOptions)` using Android `VpnService.Builder`, returning the established TUN file descriptor.
3. Initialize libbox and start `CommandServer.startOrReloadService(config, OverrideOptions())`.
4. Route the test app's start/stop actions to the sing-box service and convert the active profile to sing-box JSON.
5. Once no runtime code references `libv2ray` or HEV, remove both AARs from the test APK and change libbox to `implementation`.
6. Build and test VLESS + REALITY on a physical device before describing this APK as a working sing-box client.

The current APK is not yet a sing-box runtime build.
