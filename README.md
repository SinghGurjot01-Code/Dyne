# Dyne — Dynamic Island for Android

Native Kotlin + Jetpack Compose app. A black pill is drawn with `WindowManager` (`TYPE_APPLICATION_OVERLAY`) around the front camera cutout and expands for music, notifications, charging, calls, timer, torch, volume and Bluetooth. No WebView, no root, no INTERNET permission, no analytics.

## Build

Requirements: Android Studio (Narwhal or newer), JDK 17, Android SDK 36.

1. Open this folder in Android Studio and let Gradle sync (it uses its own Gradle; `gradle/wrapper/gradle-wrapper.properties` pins 8.11.1. Run `gradle wrapper` once if you want `./gradlew`).
2. **Build > Build APK(s)**, or from a terminal: `./gradlew assembleDebug`
3. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
4. Release APK: copy `keystore.properties.example` to `keystore.properties`, fill it in, run `./gradlew assembleRelease` -> `app/build/outputs/apk/release/app-release.apk`. Without a keystore the release build is unsigned.

## First-run setup (Motorola g67 Power 5G, Android 16)

1. Install and open Dyne.
2. **Access** tab > *Display over other apps* > allow. The island appears.
3. **Access** tab > *Notification access* > allow. This powers notifications **and** music (Android only shares other apps' media sessions with apps holding notification access). If the switch is greyed out because you sideloaded: App info > three-dot menu > **Allow restricted settings**, then retry.
4. Turn on **Calls** / **Bluetooth** in the Features tab; Android asks for those permissions only then.
5. Optional: unrestricted battery (Access tab) so Motorola's battery manager does not kill the service.
6. If the island is a few pixels off the camera: **Look** tab > Horizontal/Vertical.

## How it works

| Piece | Where |
|---|---|
| Overlay service (foreground, `specialUse`) | `overlay/DyneOverlayService.kt` |
| Window creation, cutout geometry, resize without jumps | `overlay/OverlayWindowManager.kt` |
| Compact/expanded state machine | `overlay/IslandController.kt` |
| Event queue + priority | `events/IslandEventManager.kt` |
| Island UI + spring animations | `island/` |
| One class per integration | `integrations/*` |
| Settings (Material 3) | `settings/`, `MainActivity.kt` |

Cutout position comes from `Display.getCutout()` (`DisplayCutout.boundingRects`) and the window is centred on the cutout's real centre; there is no hard-coded coordinate. If no cutout is reported, it falls back to top-centre and you can nudge it in settings. The island is hidden in landscape and while the screen is off.

**Priority.** Calls > timer done > timer > music > torch > charging > notifications > Bluetooth > volume. Deliberate deviation from a strict ladder: short-lived events (notification, charging plug-in, Bluetooth, volume, timer done) are shown for their timeout even over music/timer, otherwise a notification could never appear while music plays. Calls always win. Tap the compact island to expand it; tap outside to collapse. Swipe sideways on the expanded island to cycle through active events; swipe up to dismiss a temporary one. The idle expanded panel includes torch, volume, Bluetooth settings and timer shortcuts. Appearance sliders trigger a brief blue preview so size and position can be judged against dark wallpapers.

## Status of every feature

**Build status:** version 1.1.0 includes reliable compact-window tap handling, a temporary blue appearance preview during size/position adjustments, and idle quick actions for torch, volume, Bluetooth settings and timer. The app still requires physical-device testing.

| Feature | Status | Test |
|---|---|---|
| Overlay, cutout positioning, tap-to-expand, tap-outside collapse, swipe gestures | Updated in 1.1.0; device test pending | Tap compact pill; tap outside; swipe across expanded pill; swipe up to dismiss |
| Music (artwork, progress, play/pause/next/prev) | Implemented, not tested | Play in Spotify/YouTube Music |
| Notifications (app filter, dedupe, dismiss, lock-screen privacy) | Implemented, not tested | Send a WhatsApp message |
| Charging (plug/unplug/full) | Implemented, not tested | Plug in a charger |
| Calls (state, duration, answer/end) | Implemented, not tested | Call the phone; answer/end buttons need the "answer calls" permission |
| Timer (pause/resume/reset, alarm, reboot restore) | Implemented, not tested | Start 1 min, background the app, reboot with a running timer |
| Torch (toggle, state sync with quick settings) | Implemented, not tested | Toggle from island and from the shade |
| Volume | Implemented, not tested | Press volume keys |
| Bluetooth (on/off, connect/disconnect, name) | Implemented, not tested | Connect earbuds |
| Settings, permission status, deep links and high-contrast live appearance preview | Updated in 1.1.0; device test pending | Adjust size/position sliders; blue preview appears briefly |

**Not implemented, because of Android limits**
- Rapid-charging label / wattage: Android has no reliable public flag, so Dyne does not guess.
- Caller name/number: needs call-log level access; the island says "Incoming call".
- Showing above the lock screen: overlay windows are not drawn over the keyguard.
- Landscape: the island is hidden rather than repositioned.
- Volume detection uses the `VOLUME_CHANGED_ACTION` broadcast, which is unofficial but widely used (there is no public API).
- Bluetooth only reports connections that happen while Dyne is running.
- Notification dismissal from the island works only for notifications Android lets the listener cancel.

## Privacy

No INTERNET permission, no analytics, no ads. Notification/media/call data is read only to draw the island and is held in memory only. Each sensitive permission is requested only from the screen of the feature that needs it, with the reason shown.
