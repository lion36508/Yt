# NEXORA - Direct Media Player & Downloader

NEXORA is an open-source Android application for playing and saving direct media URLs that the user owns or is licensed to download. YouTube links are handed to the official YouTube app or browser; NEXORA does not bypass platform access controls or extract protected streams.

## 🧠 Core Architecture (यह काम कैसे करता है?)
The app uses a compact Android-native architecture. The notes below describe implemented behavior and avoid performance guarantees:

### 1. Android UI and native media player (Java + XML)
* **Direct URL playback:** Native `VideoView` plays HTTP(S) media URLs supplied by the user or a permitted provider.
* **Playback engine:** The app uses Android `VideoView` / `MediaPlayer`. Android selects the decoder; the separate C++ helper is not connected to this player.
* **Bounded feed window:** The current Activity maintains 12 feed slots. This is a custom horizontal feed, not a `RecyclerView`; memory and performance still require device testing.
* **Auto Theme Sync:** Automatically reads Android's `Configuration.UI_MODE_NIGHT_MASK` to switch the entire app between Dark Mode and Light Mode instantly.

### 2. Direct media downloader
* **Atomic downloads:** Files are written to a `.part` file and renamed only after a complete HTTP success response, preventing corrupt videos from appearing in the gallery.
* **Modern gallery output:** Android 10+ uses a pending `MediaStore.Downloads` row and publishes it only after the stream completes; older devices use the legacy `.part` + `MediaScannerConnection` path.
* **Clear errors:** HTTP failures, unsupported schemes, and interrupted downloads are reported without leaving partial files behind.

## ⚙️ Workflow (सोर्स कोड कैसे जुड़ता है?)
1. **Search Request:** Search results are displayed as metadata cards.
2. **YouTube links:** Opening a YouTube card launches the official YouTube app/browser.
3. **Direct playback:** A permitted HTTP(S) media URL is played by the native Android player.
4. **Download:** Only validated HTTPS direct media URLs are accepted. Android 10+ writes to `Download/NEXORA/` through scoped `MediaStore`; older devices use an atomic `.part` file.

## 📂 Source Code Structure
* `AndroidManifest.xml` - Hardware acceleration, DayNight theme, and Storage permissions.
* `src/main/java/com/lion/downloader/DashboardActivity.java` - The main UI, validated direct playback, scoped-storage download, and gallery logic.
* `res/layout/activity_dashboard.xml` - The dynamic dual-search bar and infinite scroll UI.
* `res/raw/` - Contains only UI media bundled with the app.

---
*Developed by lion36508. Free and Open Source under the MIT License.*

## ⚠️ Disclaimer & Ethical Use (उपयोग के सख्त नियम)
This project is **strictly for educational, research, and developer purposes** only. It is **not** created for any business, commercial, or profitable organization. 

This is a free and Open-Source project. It can be used by anyone, but **strictly for good and ethical purposes**. Any use of this project for illegal, unauthorized, or "dark" activities is strictly prohibited. The developer is not responsible for any misuse of this tool.

## 🧗‍♂️ Major Challenges Faced & Solutions (प्रोजेक्ट की मुख्य चुनौतियाँ और समाधान)

इस प्रोजेक्ट को बनाते समय हमें कई कठिन तकनीकी चुनौतियों का सामना करना पड़ा, जिन्हें हमने सफलतापूर्वक हल किया:

1. **Reliable direct media handling:**
   - Direct URLs are played by Android's native player and downloaded to a temporary file before being finalized.

2. **Platform compliance:**
   - **Challenge:** Platforms can reject unofficial extraction, and protected URLs expire quickly.
   - **Solution:** NEXORA no longer attempts to defeat those controls. It uses official YouTube playback and supports stable direct URLs for content the user is authorized to access.

3. **Android storage and gallery indexing:**
   - **Challenge:** नए एंड्रॉइड वर्जन में legacy public-path writes और `WRITE_EXTERNAL_STORAGE` से `SecurityException` तथा missing gallery entries हो सकती थीं।
   - **Solution:** Android 10+ uses a pending **`MediaStore.Downloads`** item with `RELATIVE_PATH`, closes and publishes it only after a complete stream, and deletes the row on failure. Legacy devices retain the atomic `.part` path.

4. **Memory Management in Infinite Horizontal Scrolling:**
   - **Challenge:** एक ही लाइन में अनगिनत वीडियो कार्ड लोड करने से मोबाइल की रैम (RAM) फुल होने और ऐप क्रैश होने का खतरा था।
   - **Solution:** हमने **Memory-Safe Infinite Loop Recycler** बनाया जो अधिकतम 36 कार्ड्स को ही रैम में रखता है। जैसे-जैसे यूजर दाईं तरफ स्क्रॉल करता है, नए कार्ड्स जुड़ते जाते हैं और पीछे के पुराने कार्ड्स अपने-आप डिलीट हो जाते हैं, जिससे स्टोरेज या रैम कभी फुल नहीं होती।

## 🔮 Future Challenges & Roadmap (भविष्य की चुनौतियाँ और सुधार)

जैसे-जैसे वीडियो स्ट्रीमिंग और एंड्रॉइड टेक्नोलॉजी आगे बढ़ रही है, हमारे सामने कुछ बड़े चैलेंजेस आने वाले हैं। यदि आप एक डेवलपर हैं, तो हम इन समस्याओं को हल करने में आपका स्वागत करते हैं:

1. **Official-platform handoff:**
   - YouTube playback and account-controlled actions should remain inside the official YouTube app/browser.

2. **Modern Android storage:**
   - The scoped `MediaStore` migration is now implemented; remaining work is instrumentation coverage on physical Android API levels.

3. **Performance Optimization for Infinite Loop Recycler (रैम और बैटरी ऑप्टिमाइज़ेशन):**
   - **Challenge:** जब यूजर वीडियो की लंबी सूची को लगातार स्क्रॉल करता है, तो थंबनेल बिटमैप्स (Bitmaps) लोड होने के कारण रैम (RAM) की खपत बढ़ सकती है।
   - **Help Needed:** थंबनेल कैचिंग (Thumbnail Caching) और लेजी लोडिंग (Lazy Loading) को और अधिक कुशल बनाने के लिए कंट्रीब्यूशन की आवश्यकता है।

---

### 💡 How to Contribute & Ask for Help (योगदान कैसे करें और मदद कैसे लें?)
यदि आप इन चैलेंजेस को हल करने में हमारी मदद करना चाहते हैं या कोई नया फीचर जोड़ना चाहते हैं, तो कृपया इन चरणों का पालन करें:
1. इस रिपॉजिटरी को **Fork** करें।
2. अपनी एक नई ब्रांच बनाएं (`git checkout -b feature/AmazingFeature`)।
3. अपने बदलावों को कमिट करें (`git commit -m 'Add some AmazingFeature'`)।
4. अपनी ब्रांच को पुश करें (`git push origin feature/AmazingFeature`)।
5. एक **Pull Request (PR)** ओपन करें। हमारा ऑटोमैटिक बॉट आपके कोड का स्वागत करेगा! 

आपके सहयोग और सुझावों के लिए बहुत-बहुत धन्यवाद! ❤️
