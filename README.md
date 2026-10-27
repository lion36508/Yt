# 🚀 NEXORA - Universal Video Downloader & Streamer

**NEXORA** ek powerful, ad-free, aur 100% Open Source Android application hai. Yeh app Termux ke native backend (yt-dlp + FFmpeg) aur Android ke native GPU hardware decoder ka use karke multiple platforms se videos ko stream aur download karta hai.

## 🌟 Key Features
- **Dual Engine Architecture**: Frontend UI ke liye Native Android aur backend processing ke liye Termux Core Engine.
- **Multi-Platform Support**: YouTube, TikTok, Instagram Reels, aur X (Twitter) se direct video fetch aur download.
- **Live GPU Streaming**: Bina video download kiye direct YouTube/TikTok server se HD streaming (Native VideoView).
- **Infinite Scrolling**: Memory-safe loop recycler jo bina RAM ya storage bhare unlimited videos dikhata hai.
- **Auto Dark/Light Theme**: Phone ke system UI ke hisaab se app ka color aur background auto-adjust hota hai.
- **Direct Gallery Sync**: Background downloader videos ko seedhe `/Download/NEXORA` mein save karta hai aur `MediaScanner` ke zariye instantly phone gallery mein bhejta hai.
- **No Third-Party App Required**: YouTube app ya browser ki koi zaroorat nahi.

## 🛠️ Architecture & Libraries
- **Language:** Java (Native Android SDK)
- **Downloader Core:** yt-dlp & FFmpeg (Linux ARM64 Standalone Binaries)
- **Streaming:** Android HardwareCodec (C++ GPU Decoder)
- **APIs:** Piped API (YouTube Raw Streams) & TikWM API (TikTok/Insta Streams)

## 🤝 Open Source
This project is open-source and free to use, modify, and distribute. Developed by **lion36508**.
