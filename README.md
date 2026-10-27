# 🚀 NEXORA - Multi-Engine Android Video Streamer & Downloader (`Yt`)

**NEXORA** is a custom-built Android video streaming and downloading application compiled natively via Termux toolchains.

## ✨ Key Features
- **Dual Search Architecture:** Separate Direct Link (URL) bar and Server Keyword Search bar.
- **Multi-Platform Support:** YouTube, TikTok, Instagram Reels, and X video feeds.
- **Infinite Loop Horizontal Scrolling:** Memory-safe infinite card recycling without filling device storage.
- **Auto Day/Night Theme:** Automatically syncs with Android system Dark Mode and Light (White) Mode.
- **Native Playback & Gallery Sync:** Direct stream playback in `VideoView` and instant gallery indexing via `MediaScannerConnection`.

## 🛠️ Project Structure
- `AndroidManifest.xml` — App permissions, DayNight theme & hardware acceleration config.
- `src/main/java/com/lion/downloader/` — Core Java activities & multi-library engine logic.
- `res/` — Custom XML layouts, vector icons, and neon/light UI drawables.
- `bin/NEXORA_App.apk` — Signed installable Android package.
