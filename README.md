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

## ⚠️ Disclaimer & Ethical Use (उपयोग के सख्त नियम)
This project is **strictly for educational, research, and developer purposes** only. It is **not** created for any business, commercial, or profitable organization. 

This is a free and Open-Source project. It can be used by anyone, but **strictly for good and ethical purposes**. Any use of this project for illegal, unauthorized, or "dark" activities is strictly prohibited. The developer is not responsible for any misuse of this tool.

## 🧗‍♂️ Major Challenges Faced & Solutions (प्रोजेक्ट की मुख्य चुनौतियाँ और समाधान)

इस प्रोजेक्ट को बनाते समय हमें कई कठिन तकनीकी चुनौतियों का सामना करना पड़ा, जिन्हें हमने सफलतापूर्वक हल किया:

1. **SELinux & Native Linux Binary Execution (Android Sandboxing):**
   - **Challenge:** Android का सुरक्षा तंत्र (SELinux) सीधे ऐप के अंदर `yt-dlp` या `FFmpeg` जैसी लिनक्स बाइनरी फाइलों को सीधे एक्जीक्यूट करने से रोकता है (`Permission Denied` एरर)।
   - **Solution:** हमने बाइनरी फाइलों को ऐप के इंटरनल स्टोरेज (`getFilesDir()`) में एक्सट्रैक्ट करके उनका `executable(true)` परमिशन सेट किया और Termux जैसी नेटिव `HOME` व `PATH` एनवायरनमेंट वेरिएबल्स के साथ `ProcessBuilder` के ज़रिए शेल शिफ़्ट किया।

2. **Bypassing Third-Party API Reliance & Server Blocks:**
   - **Challenge:** बाहरी सार्वजनिक APIs (जैसे Piped API) अक्सर स्लो हो जाते हैं या ज्यादा ट्रैफिक की वजह से डाउन हो जाते हैं।
   - **Solution:** हमने थर्ड-पार्टी APIs पर निर्भरता खत्म करके सीधे **YouTube InnerTube Server API (`ANDROID_VR` Client)** और **Desktop User-Agent Parsers** को इंटीग्रेट किया, जिससे ऐप सीधे सर्वर से रॉ `.mp4` स्ट्रीम और असली थंबनेल बिना किसी रुकावट के खींचता है।

3. **Android 13+ Storage Restrictions & Gallery Indexing:**
   - **Challenge:** नए एंड्रॉइड वर्जन में `WRITE_EXTERNAL_STORAGE` परमिशन ब्लॉक होने के कारण डाउनलोड की गई फाइलें सीधे गैलरी में नहीं दिखती थीं।
   - **Solution:** हमने `DownloadManager` के साथ-साथ Android की **`MediaScannerConnection`** लाइब्रेरी जोड़ी, जो वीडियो डाउनलोड होते ही सिस्टम को फोर्स करती है कि वह फाइल को तुरंत फोन की मुख्य गैलरी और ऐप के 'My Videos' सेक्शन में इंडेक्स करे।

4. **Memory Management in Infinite Horizontal Scrolling:**
   - **Challenge:** एक ही लाइन में अनगिनत वीडियो कार्ड लोड करने से मोबाइल की रैम (RAM) फुल होने और ऐप क्रैश होने का खतरा था।
   - **Solution:** हमने **Memory-Safe Infinite Loop Recycler** बनाया जो अधिकतम 36 कार्ड्स को ही रैम में रखता है। जैसे-जैसे यूजर दाईं तरफ स्क्रॉल करता है, नए कार्ड्स जुड़ते जाते हैं और पीछे के पुराने कार्ड्स अपने-आप डिलीट हो जाते हैं, जिससे स्टोरेज या रैम कभी फुल नहीं होती।
