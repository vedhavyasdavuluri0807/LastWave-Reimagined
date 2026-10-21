# Builds

- **Release Artifact:** `LastWave-v4.3.2-release.apk`
  - Variant: `release` (`:app:assembleRelease`)
  - Optimizations: R8 code shrinking, dead-code elimination, resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`)
- **Debug Artifact:** `LastWave-v4.3.2-debug.apk`
  - Variant: `debug` (`:app:assembleDebug`)
  - Uncompressed full DEX with all debug symbols, no R8 shrinking
- **Version:** `4.3.2` (versionCode `27`)
- **Features:** Listening Insights dashboard (Room v16 migration), Offline Local Listening Statistics, Discord Rich Presence lifecycle & stale-progress fixes
- **Toolchain:** Gradle 9.3.1, OpenJDK 17, Android compileSdk 37, targetSdk 35, minSdk 29
