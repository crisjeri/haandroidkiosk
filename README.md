# HA Android Kiosk

HA Android Kiosk is a lightweight, local Android kiosk application for displaying a Home Assistant dashboard. It was developed primarily for the Samsung Galaxy Tab A 10.1 LTE (SM-T585) running LineageOS 19.1 / Android 12, but the main application can also work on other Android tablets that meet the requirements below.

The application uses the system Android WebView and performs all camera-based motion detection locally. It contains no advertisements, analytics, cloud services, or Google Play Services dependency.

[Česká dokumentace](README_CS.md)

## Features

- Full-screen Home Assistant WebView with JavaScript, cookies, DOM storage, media playback, and WebSocket support
- HTTPS support and explicitly configurable local HTTP access
- Navigation restricted to the configured Home Assistant host
- TLS errors are rejected instead of bypassed
- Local network outage indication and automatic reload with exponential backoff
- Low-resolution front-camera motion detection at approximately 1–3 FPS
- Configurable `ACTIVE → DIMMED → SCREEN_OFF` display state machine
- Optional Device Admin integration for immediate screen locking
- Foreground service and automatic startup after boot
- Support for use as the default home application and Android Lock Task Mode
- PIN-protected settings with temporary lockout after repeated failed attempts
- Four fixed screen orientations independent of the orientation sensor
- Diagnostic screen with application, network, camera, WebView, battery, and service information

Camera frames are never stored, displayed, or transmitted. Motion detection processes only a downsampled luminance channel locally on the tablet.

## Requirements

### Build environment

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools 36
- Internet access to Google Maven and Maven Central when dependencies are downloaded for the first time

The application has a minimum SDK level of 26 and targets SDK level 36.

### Tablet

- Android 8.0 or newer
- A current Android System WebView implementation
- A front-facing camera for motion detection
- USB debugging for ADB installation

Device Admin, default-launcher selection, background camera access, and screen-off behavior may differ between Android versions and ROMs.

## Building

The repository includes the Gradle wrapper. Build and test the debug version with:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run Android lint with:

```bash
./gradlew lintDebug
```

The generated debug APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Signed release build

Create a private signing key and define the following project-specific properties in the user-level `~/.gradle/gradle.properties` file:

```properties
HA_KIOSK_STORE_FILE=/absolute/path/to/ha-kiosk-release.jks
HA_KIOSK_STORE_PASSWORD=keystore-password
HA_KIOSK_KEY_ALIAS=ha-kiosk
HA_KIOSK_KEY_PASSWORD=key-password
```

Protect the file and build the release APK:

```bash
chmod 600 ~/.gradle/gradle.properties
./gradlew assembleRelease
```

The signed APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

The release build fails with a descriptive error when the signing properties or keystore are missing. Debug builds remain available without release signing configuration. Never commit the keystore or its passwords to the repository, and keep a secure backup: future updates of `cz.hykel.hakiosk` must use the same signing key.

## Installation

Enable **Developer options** and **USB debugging** on the tablet, connect it to the computer, and accept the computer fingerprint on the device.

Verify the connection and install the APK:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Start the application from ADB if necessary:

```bash
adb shell am start -n cz.hykel.hakiosk/.MainActivity
```

On first launch, create a PIN containing at least four digits and enter the Home Assistant URL. Local unencrypted `http://` access must be enabled explicitly. Android requests the camera permission through its standard permission dialog.

## Opening Settings

Place two fingers anywhere on the kiosk screen and hold them still for approximately two seconds. Enter the configured PIN when prompted. The gesture observes touches without blocking normal Home Assistant controls.

There is no default PIN. After five failed attempts, PIN verification is blocked for 30 seconds.

## Default Launcher

The application can act as the Android home application. Press the system Home button and select **HA Kiosk → Always**. This is generally more reliable after boot than attempting to open an activity directly from `BOOT_COMPLETED`.

The default launcher can later be changed in Android's default-app settings.

## Device Admin and Screen Control

Without Device Admin, the application dims its window, removes `KEEP_SCREEN_ON`, and waits for the Android system screen timeout. For example, the timeout can be configured to 30 seconds with:

```bash
adb shell settings put system screen_off_timeout 30000
adb shell settings get system screen_off_timeout
```

With Device Admin enabled, the application can call `DevicePolicyManager.lockNow()` after the configured delay. It cannot bypass a PIN, password, pattern, or secure keyguard. Automatic wake directly into the kiosk therefore requires a device without a secure lock screen or a properly provisioned dedicated-device setup.

Before uninstalling the application, disable Device Admin in the application or run:

```bash
adb shell dpm remove-active-admin cz.hykel.hakiosk/.DeviceAdminReceiverImpl
adb uninstall cz.hykel.hakiosk
```

## Lock Task Mode

Calling `startLockTask()` without Device Owner provisioning normally enables only Android screen pinning, which the user can exit. A fully locked kiosk requires the application to be allowlisted by a Device Owner on a managed or freshly provisioned device.

Device Owner provisioning can require a factory-reset or newly prepared device. Back up the tablet before attempting it.

## Android System WebView Performance

HA Android Kiosk renders Home Assistant through the active system WebView, not through Firefox or the standalone Chrome browser. Older Android versions and alternative ROMs may include an outdated WebView that renders modern Home Assistant dashboards much more slowly than Firefox, even when Chrome itself is current.

The active provider and version are shown on the diagnostic screen and can also be checked with:

```bash
adb shell dumpsys webviewupdate
adb shell pm list packages | grep -E 'webview|chrome'
```

If the active WebView is outdated:

1. Update or install **Android System WebView** from Google Play.
2. Open **Settings → System → Developer options → WebView implementation**.
3. Confirm that Android selected the newly installed provider.
4. Restart HA Android Kiosk or reboot the tablet.

Updating Chrome does not necessarily update WebView. Some LineageOS and AOSP-based systems use a separate `com.android.webview` or `com.google.android.webview` package. If a compatible provider is unavailable from Google Play, install a newer ROM build that includes an updated WebView. Installing an arbitrary WebView APK is not recommended because package signatures, CPU architecture, and Android-version compatibility must match.

## Battery Diagnostics

The diagnostic screen reads battery capacity and charging status through standard Android APIs.

The device-specific service in [`baterry/`](baterry/) can additionally publish the Samsung kernel value `batt_slate_mode`. This integration is optional. When the service is not installed, is stopped, cannot access the kernel interface, or has not refreshed its status for three minutes, the application displays charging control as unavailable and does not display a stale `batt_slate_mode` value.

The charging-control service is specific to the Samsung SM-T585 kernel and requires a rooted or `userdebug` LineageOS build. See [`baterry/README.md`](baterry/README.md) before using it.

## Logs and Diagnostics

The diagnostic screen reports the application and Android versions, WebView provider, network state, camera state, foreground-service state, battery state, last detected motion, Device Admin state, and the latest WebView loading error.

View application logs with:

```bash
adb logcat -s HaKiosk.Main HaKiosk.MotionService HaKiosk.Camera HaKiosk.Screen HaKiosk.Boot
```

Or restrict logcat to the application process:

```bash
adb shell pidof cz.hykel.hakiosk
adb logcat --pid="$(adb shell pidof cz.hykel.hakiosk)"
```

Logs intentionally exclude page contents, cookies, authentication tokens, and camera frames.

## Android and ROM Limitations

- Android 12 restricts starting activities and foreground services from the background.
- A camera foreground service must display a persistent notification.
- Android 13 and newer may additionally require notification permission.
- A regular application cannot directly power off the display or unlock a secure keyguard.
- Camera access while the screen is off or locked depends on the ROM, camera driver, and power-management configuration.
- Disable aggressive battery optimization for HA Android Kiosk if the ROM repeatedly terminates its foreground service.
- System updates can change WebView, camera, Device Admin, and background-execution behavior.

## Project Structure

- `MainActivity` — full-screen kiosk, hidden settings gesture, and window brightness
- `WebViewController` — WebView security, navigation, loading, and retry handling
- `MotionDetectionService` — foreground-service lifecycle and motion processing
- `CameraController` / `MotionDetector` — Camera2 capture, downsampling, and image-change detection
- `ScreenStateMachine` / `ScreenController` — dimming, wake-up, timeout, and locking behavior
- `SettingsActivity` / `PreferencesRepository` / `PinManager` — configuration and PIN protection
- `DiagnosticsActivity` — local runtime diagnostics without sensitive web content
- `BootReceiver` / `DeviceAdminReceiverImpl` — Android system integrations
- `baterry/` — optional device-specific battery charging-control service

## Tests

Local unit tests cover URL validation, motion detection, screen-state transitions, retry delays, PIN hashing and verification, and optional battery-control status parsing:

```bash
./gradlew testDebugUnitTest
```
