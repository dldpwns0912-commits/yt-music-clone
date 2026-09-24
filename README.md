# YouTube Music Clone (Android / Kotlin Jetpack Compose)

A production-grade, architectural-first YouTube Music clone for Android built with **Kotlin**, **Jetpack Compose (Material Design 3)**, **AndroidX Media3 (ExoPlayer & MediaSession)**, **Room Database**, **WorkManager**, and **AndroidX Glance**.

---

## 🛠 Tech Stack & Architecture

- **Language**: Kotlin 2.1.0
- **UI Framework**: Jetpack Compose + Material Design 3 (Dynamic Ambient Glow, Shared Transitions)
- **Architecture**: MVVM + Clean Architecture (Unidirectional Data Flow)
- **Dependency Injection**: Google Dagger Hilt
- **Media Engine**: AndroidX Media3 (ExoPlayer, MediaLibraryService for Android Auto, LoudnessEnhancer Normalizer, Audio Crossfade)
- **Local Persistence & Caching**: Room Database with automated LRU Storage Manager
- **Background Tasks**: AndroidX WorkManager (Parallel/Constrained Audio Downloader)
- **Audio Extraction**: Dynamic yt-dlp Extraction Pipeline with InnerTube Fallback & Auto-Updater
- **System Integration**: AndroidX Glance (Home Screen AppWidget) & Android Quick Settings Tile

---

## 🚀 Key Features & Implementation Milestones

### Step 1: Project Setup & Design System
- Modern Gradle version catalogs (`libs.versions.toml`) configuring Android 15 (API 35, minSdk 26).
- Custom YouTube Music Material 3 dark/pure-black theme with high-contrast accent tokens and typography.
- Dagger Hilt application bootstrapping and core dependency graph configuration.

### Step 2: yt-dlp Extraction Pipeline & Auto-Update Engine
- Dynamic yt-dlp executable pipeline with GitHub Releases version checking.
- Resilient fallback strategy to InnerTube client API to ensure unthrottled streaming playback.
- Format selection parser prioritizing optimal audio streams (Opus/AAC 128k/192k/320k).

### Step 3: Room Database, Smart Cache & WorkManager Downloader
- Normalized Room relational schema for tracks, playlists, playback history, and offline storage.
- Storage Manager supporting max cache sizing, automated LRU eviction, and JSON backup/restore.
- WorkManager background audio downloader with exponential retry policy and network constraint handling.

### Step 4: Media3 Audio Engine & Android Auto Integration
- `MusicPlaybackService` inheriting `MediaLibraryService` for seamless background playback and Android Auto car head-unit navigation.
- Gapless pre-buffering of the upcoming queue track for near-instant zero-latency transitions.
- Smooth audio crossfading (fade-in / fade-out) between consecutive tracks.
- Studio-grade volume normalization via Android `LoudnessEnhancer` and `Equalizer` audio session binding.

### Step 5: Core Presentation & Motion Experience
- `PlayerViewModel` and reactive UI state pipeline powered by Kotlin Coroutines & StateFlow.
- Dynamic Ambient Glow: Real-time album art color extraction via AndroidX Palette API with blurred gradient backdrop.
- Fluid bidirectional expandable Mini-Player to Full-Player transition with physics-based spring gestures.

### Step 6: Feature Screens & Rich Interactions
- Instant search screen with debounced search suggestions, query history, and quick-filter chips.
- Timestamp-synced interactive lyrics with automated smooth-scrolling and tap-to-seek support.
- Queue management with smooth drag-and-drop item reordering and swipe-to-remove.
- 5-band Equalizer and BassBoost adjustment interface.
- Storage management dashboard for cache clearing and JSON playlist export/import.

### Step 7: System Integration & Polish
- **Home Screen Widget (AndroidX Glance)**:
  - Live album art, track title, and artist display directly on the launcher.
  - Interactive playback control buttons (Play/Pause toggle, Previous, Next).
  - Deep-link tap handling launching directly into the playback screen.
- **Quick Settings Tile (`OfflineModeTileService`)**:
  - One-tap status bar toggle for "Offline Only Mode".
  - Proactively blocks network streaming requests and enforces local-only playback to save mobile data.

---

## 📁 Project Structure

```
app/src/main/java/com/ytmusic/
├── core/
│   ├── database/        # Room Database, DAOs, Entities, TypeConverters
│   ├── extractor/       # yt-dlp & InnerTube extraction pipeline
│   ├── media/           # Media3 service, ExoPlayer manager, effects, crossfade
│   ├── storage/         # Storage manager, cache management, JSON backup
│   └── system/          # Quick Settings Tile (OfflineModeTileService)
├── feature/
│   ├── home/            # Home screen, recommendations, history
│   ├── player/          # Mini-Player, Full-Player, Lyrics, Equalizer
│   ├── search/          # Search UI, suggestions, history
│   ├── library/         # Downloaded tracks, playlists, storage settings
│   └── widget/          # Glance Home Screen Widget & Action Callback
├── ui/
│   ├── theme/           # Color tokens, Typography, Shape, M3 Theme
│   └── components/      # Reusable UI elements (CoverArt, AmbientGlow)
└── MainActivity.kt
```

---

## 📦 Building & Testing

To compile and build the debug APK:
```bash
./gradlew assembleDebug
```
