<div align="center">

<img src="NON BG ICON (1).png" alt="LAST WAVE Logo" width="140" height="130" />

# LAST WAVE Reimagined

**Modern Android Music Player with a Samsung Music-Inspired Library, Dedicated YouTube Music Section, Listening Insights & Discord Rich Presence.**

<p align="center">
  <i>An independent, community-maintained fork of <a href="https://github.com/Clash-Projects/LastWave-native">LastWave Native</a></i>
</p>

<p align="center">
  <a href="https://github.com/vedhavyasdavuluri0807/LastWave-Reimagined/stargazers">
    <img src="https://img.shields.io/github/stars/vedhavyasdavuluri0807/LastWave-Reimagined?style=for-the-badge&color=ffd0b0&labelColor=2d2d2d" alt="Stars" />
  </a>
  <a href="https://github.com/vedhavyasdavuluri0807/LastWave-Reimagined/network/members">
    <img src="https://img.shields.io/github/forks/vedhavyasdavuluri0807/LastWave-Reimagined?style=for-the-badge&color=ffb4a2&labelColor=2d2d2d" alt="Forks" />
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/Platform-Android%2010+-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=2d2d2d" alt="Platform" />
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/Language-Kotlin%202.4-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=2d2d2d" alt="Kotlin" />
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=2d2d2d" alt="Jetpack Compose" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge&labelColor=2d2d2d" alt="GPL-3.0 License" />
  </a>
</p>

<p align="center">
  <a href="https://t.me/clashprojects">
    <img src="https://img.shields.io/badge/Telegram-Updates%20%26%20Support-24A1DE?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram Support" />
  </a>
  <a href="https://discord.gg/DmyM2p2fMe">
    <img src="https://img.shields.io/badge/Discord-Join%20Community-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord Community" />
  </a>
</p>

</div>

---

## Table of Contents

- [Project Overview](#project-overview)
- [Screenshots](#screenshots)
- [Key Features](#key-features)
  - [Local Music Library (Samsung Music-Inspired)](#local-music-library-samsung-music-inspired)
  - [Playback & Mini-Player](#playback--mini-player)
  - [Dedicated YouTube Music Section](#dedicated-youtube-music-section)
  - [YouTube Music Download Policy](#youtube-music-download-policy)
  - [Performance Optimizations](#performance-optimizations)
  - [Listening Insights & Scrobbling](#listening-insights--scrobbling)
- [Technology Stack](#technology-stack)
- [Requirements & Build Instructions](#requirements--build-instructions)
- [Installation & Usage](#installation--usage)
- [Testing & Known Limitations](#testing--known-limitations)
- [Open-Source License & Credits](#open-source-license--credits)

---

## Project Overview

**LAST WAVE Reimagined** is an Android music player built with **Kotlin** and **Jetpack Compose**. It provides a sleek, dark-themed local music library inspired by the classic Samsung Music interface—featuring a clean tab bar, pure black aesthetic, album grids, and docked playback controls—while maintaining its own independent architecture and identity.

> [!NOTE]
> **Independent Project Notice:**
> LAST WAVE Reimagined is an independent open-source project. It is **not** an official Samsung product, nor is it affiliated with, sponsored by, or endorsed by Samsung Electronics Co., Ltd.

The app features a custom **LAST WAVE launcher icon** rendered in vibrant neon green, cyan, and dark teal on a transparent background, complete with Android adaptive launcher assets (`ic_launcher` and `ic_launcher_round`).

---

## Screenshots

<div align="center">
  <img src="Screenshot/screenshot_1.png" width="31%" style="border-radius: 14px; margin: 4px;" alt="Screenshot 1" />
  <img src="Screenshot/screenshot_2.png" width="31%" style="border-radius: 14px; margin: 4px;" alt="Screenshot 2" />
  <img src="Screenshot/screenshot_3.png" width="31%" style="border-radius: 14px; margin: 4px;" alt="Screenshot 3" />
  <br/>
  <br/>
  <img src="Screenshot/screenshot_4.png" width="31%" style="border-radius: 14px; margin: 4px;" alt="Screenshot 4" />
  <img src="Screenshot/screenshot_5.png" width="31%" style="border-radius: 14px; margin: 4px;" alt="Screenshot 5" />
  <img src="Screenshot/screenshot_6.png" width="31%" style="border-radius: 14px; margin: 4px;" alt="Screenshot 6" />
</div>

> [!TIP]
> To update or add new screenshots of the Samsung Music-inspired library or YouTube Music section, place replacement PNG files in the `Screenshot/` directory and update the references above.

---

## Key Features

### Local Music Library (Samsung Music-Inspired)
- **Dedicated Library Categories:** Browse local device audio across 6 tabs:
  - **Tracks:** Complete listing of all indexed audio files with duration, artist, and playback status.
  - **Albums:** 2-column album card grid with high-resolution artwork and track counts.
  - **Artists:** Artist directory with track totals and categorized browsing.
  - **Playlists:** Create, rename, delete, and manage local playlists.
  - **Folders:** Navigate storage directories directly to access audio collections.
  - **Favorites:** Instant access to marked favorite tracks.
- **Sorting Options:** Sort items by **Date Added**, **Title**, **Artist**, **Track Count**, or **Duration** with Ascending and Descending toggles.
- **Batch Actions:** Quick **Play All** and **Shuffle** controls directly from library headers.

### Playback & Mini-Player
- **Docked Mini-Player:** Floats seamlessly above bottom navigation showing current artwork, track title, artist name, play/pause, next track, and playback progress.
- **Continuous Local Audio Engine:** Powered by AndroidX Media3 ExoPlayer with lockscreen media controls, notification playback, and Bluetooth/headset integration.
- **Queue Management:** Uninterrupted playback queue progression when selecting tracks from any tab.

### Dedicated YouTube Music Section
- **Direct Menu Access:** Reachable from the top-right overflow menu (`⋮` → **YouTube Music**) in the main app shell.
- **Home Header & Quick Actions:** Includes YouTube Music branding, a search action, and one-tap navigation shortcuts for **Home**, **Explore**, **Library**, **Download**, and **Open App**.
- **Official App & Browser Fallback:** Seamlessly opens the official YouTube Music application (`com.google.android.apps.youtube.music`) if installed, falling back to `https://music.youtube.com/` in the browser if the app is unavailable.
- **Safe Authentication:**
  - Login access is housed strictly inside this section (never on first launch).
  - Uses official Google-supported authentication flows via browser/official app.
  - **Never** collects Google passwords or account credentials in custom LAST WAVE forms.
- **State & Playback Preservation:** Returning from the YouTube Music section immediately restores the local library and keeps active audio playing without interruption.

### YouTube Music Download Policy
- **Official Compliance:** The Download action in the YouTube Music section provides clear guidance directing users to the official YouTube Music app and built-in offline downloads (subject to account eligibility and YouTube Premium).
- **Integrity Safeguards:**
  - Never scrapes audio streams without authorization.
  - Never attempts to bypass DRM or YouTube Premium restrictions.
  - Never displays simulated or fake download progress.
- **Separation:** YouTube Music offline download information is kept completely separate from any authorized local-file export or download functionality.

### Performance Optimizations
- **Artwork Downsampling & Caching:** Uses Coil with exact target dimensions and hardware bitmap allocation (`allowHardware(true)`), drastically lowering memory consumption on long lists.
- **Stable Lazy Layout Keys:** Lazy list and grid items use unique, stable keys (`key = { it.id }`) and content types to avoid redundant item allocations during scrolling.
- **Recomposition Isolation:** Mini-player and library rows observe `chromeState` (track metadata and play/pause state) rather than high-frequency millisecond progress tickers (`progressState`), eliminating unnecessary full-screen recompositions during playback.
- **Optimized Scanning:** Caches indexed library metadata in memory to eliminate repeated disk reads and filesystem scans while scrolling.

### Listening Insights & Scrobbling
- **Listening Insights Dashboard:** In-depth playback analytics featuring listening duration, total play counts, unique tracks, and unique artists across **Today**, **7D**, **30D**, and **All Time** timeframes.
- **Offline Local Statistics:** Tracks real local playback data completely offline without requiring an external Last.fm connection.
- **Discord Rich Presence:** Live music status updates on Discord via IPC with dynamic artwork, accurate track progress, instant pause clearing, and persistent background playback tracking.
- **Last.fm Scrobbler:** Built-in scrobbler integration for external music tracking.

---

## Technology Stack

| Component | Technology / Library | Version |
|:---|:---|:---|
| **Language** | Kotlin | `2.4.10` |
| **UI Framework** | Jetpack Compose (BOM) / Material 3 Expressive | `2026.06.01` |
| **Android SDK Baseline** | Compile SDK: `37` \| Target SDK: `35` \| Min SDK: `29` (Android 10+) | Android 10+ |
| **Build Tools** | Android Gradle Plugin (AGP) / Gradle KSP | AGP `9.1.1` / KSP `2.3.10` |
| **Dependency Injection** | Dagger Hilt | `2.59.2` |
| **Audio Engine** | AndroidX Media3 ExoPlayer (Core, HLS, DASH) | `1.2.1` |
| **Audio Codecs & Native** | Jellyfin Media3 FFmpeg Decoder / Google Oboe | `1.2.1+1` / `1.10.0` |
| **Image Loading** | Coil Compose | `2.7.0` |
| **Local Persistence** | Room Database / Jetpack DataStore Preferences | Room `2.7.1` / DataStore `1.1.1` |
| **Networking & Parsers** | Retrofit / OkHttp / Kotlinx Serialization / Jsoup | Retrofit `2.11.0` / OkHttp `4.12.0` |
| **Unit Testing** | JUnit 4 / Robolectric / Google Truth / MockK / Coroutines Test | JUnit `4.13.2` / Robolectric `4.12.2` |

---

## Requirements & Build Instructions

### Prerequisites
- **JDK:** OpenJDK 17 or higher
- **Android SDK:** Platforms 35 and 37 installed via Android Studio SDK Manager
- **CMake & NDK:** CMake 3.22.1+ and Android NDK (for native C++ audio components)
- **Git**

### Build Commands (Windows PowerShell)

```powershell
git clone https://github.com/vedhavyasdavuluri0807/LastWave-Reimagined.git
cd LastWave-Reimagined
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

> [!NOTE]
> On Linux or macOS, substitute `.\gradlew.bat` with `./gradlew`.

### Generated Artifact Location
The debug APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Installation & Usage

1. **Install Debug APK:**
   - Transfer `app/build/outputs/apk/debug/app-debug.apk` to an Android device running Android 10 (API 29) or higher, or install via ADB:
     ```powershell
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```
2. **Grant Media Permissions:**
   - On Android 13+ (API 33+), grant **Music and audio** (`READ_MEDIA_AUDIO`) permission when prompted.
   - On Android 10–12 (API 29–32), grant storage permission (`READ_EXTERNAL_STORAGE`) so the app can scan and index your local audio files.
3. **Browse Local Music:**
   - Tap through the top tab bar to browse **Tracks**, **Albums**, **Artists**, **Playlists**, **Folders**, or **Favorites**.
   - Use the sort dropdown to arrange tracks by title, artist, date added, or duration.
4. **Playback:**
   - Tap any song to start playback. Control playback using the docked mini-player at the bottom or the system notification.
5. **Access YouTube Music:**
   - Tap the overflow menu (`⋮`) in the top right corner and select **YouTube Music**.
   - Use shortcuts to browse Home, Explore, or Library, check download requirements, or launch the official YouTube Music app.

---

## Testing & Known Limitations

### Verified Unit Test Results
- **Test Command:** `.\gradlew.bat testDebugUnitTest`
- **Result:** **598 unit tests passed, 0 failures, 0 skipped (100% success rate)**
- **Test Suite Duration:** ~37 seconds
- **Verification Notice:** *All verification was performed via the automated Robolectric / JVM unit-test suite and Gradle APK compilation. No physical-device testing has occurred.*

### Known Limitations
- **YouTube Music Protected Content:** LAST WAVE Reimagined does not download, stream-rip, or decrypt DRM-protected YouTube Music audio. Offline playback of YouTube Music content must be handled through the official YouTube Music app and requires an active YouTube Premium subscription.
- **Media Indexing:** Local library content depends on the Android MediaStore index; files hidden with `.nomedia` or stored in inaccessible app-private folders will not appear in the library.
- **Minimum Android Version:** Devices below Android 10 (API 29) are not supported.

---

## Open-Source License & Credits

### License
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for the full license terms.

### Project & Upstream Attribution
- **LAST WAVE Reimagined** is maintained by [vedhavyasdavuluri0807](https://github.com/vedhavyasdavuluri0807).
- Forked from **[LastWave Native](https://github.com/Clash-Projects/LastWave-native)**, originally created and developed by [Duxtami](https://github.com/duxtami) and [Ajisth](https://github.com/ajisth69).
- Upstream community support: [Telegram @clashprojects](https://t.me/clashprojects) and [Discord Community](https://discord.gg/DmyM2p2fMe).

### Design & Trademark Disclaimers
- The user interface is inspired by Samsung Music's design aesthetics. LAST WAVE Reimagined is an independent project and is **not an official Samsung application**, nor is it affiliated with, endorsed by, or sponsored by Samsung Electronics Co., Ltd.
- YouTube and YouTube Music are registered trademarks of Google LLC. LAST WAVE Reimagined is not affiliated with Google LLC.
- All other trademarks, service marks, and company names are the property of their respective owners.
