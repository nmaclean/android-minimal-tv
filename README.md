# MinimalTV

A minimal, 10-foot Android TV home launcher built with Jetpack Compose for TV in a Midnight-Navy & Electric-Cyan design system, featuring installed media app launching, TV input switching, voice search, a live Home Assistant dashboard, and an in-app GitHub Releases self-updater.

## Features

- **Midnight-Navy & Electric-Cyan TV UI**
  - Collapsible left navigation rail (`Search`, `Apps`, `Inputs`, `Smart Home`, `Settings`) with `2.dp` `#00E5FF` D-pad focus rings and `focusRestorer()` support.
  - **Apps**: 4-column grid of installed TV/media apps (`TVHeadend`, `YouTube`, `Jellyfin`, `Netflix`, `Prime Video`, `Disney+`, `Apple TV`, `BBC iPlayer`, `Plex`, `Kodi`, `VLC`) with category filter pills (`All`, `Live TV`, `Streaming`, `Media Players`), a live clock header, and a compact Home Assistant glance strip.
  - **Inputs**: Lists hardware HDMI/AV and TV Input Framework (TIF) inputs via `TvInputManager` for direct input switching, with a fallback to the system inputs picker.
  - **Search**: D-pad-friendly search across installed apps and YouTube without triggering mobile companion keyboards until explicitly clicked.
  - **Smart Home**: 2×2 Home Assistant dashboard cards (Solar & Battery, Temps & Lights, EV, and phone battery levels) plus live weather.
  - **Voice Assistant**: Hardware `ASSIST` / `VOICE_ASSIST` key handling for YouTube voice search.

- **In-App Settings, Encrypted Storage & Local Web Setup**
  - Stores the Home Assistant URL and Long-Lived Access Token in `EncryptedSharedPreferences` (falling back to standard `SharedPreferences` if keystore hardware is unavailable).
  - Includes a **Test Connection** check against `GET <ha_url>/api/` with HTTP status and latency reporting.
  - **On-Demand Web Config Server**: Toggle **Start Web Config** / **Stop Web Config** in Settings to host a temporary local HTTP form (`http://<device-ip>:8080`) for pasting the Home Assistant URL and token from a phone or PC browser, syncing live back to the TV screen.

- **Self-Updater & Automated Releases**
  - Checks `nmaclean/android-minimal-tv` GitHub Releases (`releases/latest`) using SemVer comparison, displays release notes, and downloads/installs APKs via `FileProvider` (with Android `canRequestPackageInstalls()` permission handling).

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), `activity-compose`
- **Security**: `androidx.security:security-crypto` (`EncryptedSharedPreferences`)
- **Min / Target SDK**: Android 10 (API 29) / API 37
- **Build**: Gradle (Kotlin DSL), AGP 9.4.1, JDK 17+

## Building & Testing

```bash
# Run unit tests and build debug APK
./gradlew testDebugUnitTest assembleDebug

# Build release APK (defaults to versionName=1.0.0, versionCode=10000)
./gradlew assembleRelease
```

### Deploying to Chromecast / Android TV via ADB

```bash
adb connect <CHROMECAST_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.nmaclean.minimaltv/.MainActivity
```

*(Optional)* To replace the stock Google TV Home screen:

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
# Restore later with:
# adb shell pm enable com.google.android.apps.tv.launcherx
```

## Releases & CI

Pushing a `v*` tag (e.g. `v1.0.0`) triggers [.github/workflows/release.yml](.github/workflows/release.yml), which derives `versionName` and `versionCode` from the tag, runs `testDebugUnitTest` and `assembleRelease`, and publishes the APK to GitHub Releases.

Optional repository secrets for release signing:
- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

If `KEYSTORE_BASE64` is not configured, the release build falls back to debug signing.
