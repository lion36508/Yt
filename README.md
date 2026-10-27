# 🚀 NEXORA - The Ultimate Dual-Engine Video Downloader & Streamer

NEXORA is a completely independent, Open-Source Android application. It does not rely on third-party Android APIs. Instead, it embeds a **Native Linux Termux Environment** directly inside the app to fetch, process, and download media from YouTube, TikTok, Instagram, and X.

## 🧠 Core Architecture (यह काम कैसे करता है?)
The app runs on a strict **2-Engine, 4-Library Architecture** to ensure zero crashes and maximum speed:

### 1. Engine 1: Frontend UI & Live GPU Streamer (Java + Native XML)
* **Direct Server Scraper (No External APIs):** Bypasses standard APIs. It connects directly to `youtubei/v1/player` (ANDROID_VR client) and TikWM to extract raw `.mp4` streams.
* **C++ GPU Playback (VideoView):** No slow WebViews. It uses Android's Native `VideoView` connected directly to Hardware Decoders for zero-lag live streaming.
* **Infinite Horizontal Loop Recycler:** Memory-safe UI. Keeps a maximum of 36 video cards in RAM. As you scroll right, it adds new cards and deletes old ones, preventing storage/RAM overflow.
* **Auto Theme Sync:** Automatically reads Android's `Configuration.UI_MODE_NIGHT_MASK` to switch the entire app between Dark Mode and Light Mode instantly.

### 2. Engine 2: Termux Backend (Native Linux Binaries)
* **Library 1 (yt-dlp):** ARM64 standalone Linux binary embedded in the app. Handles all complex extractions, metadata parsing, and bypassing age/region restrictions natively.
* **Library 2 (FFmpeg):** Processes and merges high-quality video (4K/1080p) with audio entirely in the background.
* **Library 3 (MediaScanner):** Downloads bypass Android restrictions by saving directly to `/Download/`. `MediaScannerConnection` instantly forces the phone's Gallery to index the new video.

## ⚙️ Workflow (सोर्स कोड कैसे जुड़ता है?)
1. **Search Request:** User taps "Search" -> App runs `yt-dlp ytsearch:query` in a background Linux shell via `ProcessBuilder`.
2. **Data Parsing:** JSON output is captured directly from the command line and parsed by Android's `JSONObject`.
3. **Live Play:** User taps a video -> `VideoView` receives the raw stream URL and plays it using device GPU.
4. **Download:** User taps "1080p" -> Shell executes `yt-dlp -f bestvideo+bestaudio --ffmpeg-location ...` and saves the processed `.mp4` straight to the Gallery.

## 📂 Source Code Structure
* `AndroidManifest.xml` - Hardware acceleration, DayNight theme, and Storage permissions.
* `src/main/java/com/lion/downloader/DashboardActivity.java` - The main brain containing Multi-Threading, Shell Executors, and UI logic.
* `res/layout/activity_dashboard.xml` - The dynamic dual-search bar and infinite scroll UI.
* `res/raw/` - Contains the embedded Termux binaries (yt-dlp, ffmpeg).

---
*Developed by lion36508. Free and Open Source under the MIT License.*
