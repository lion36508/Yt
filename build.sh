#!/system/bin/sh
rm -rf obj/* bin/*
REAL_JAR=$(find /data/data/com.termux/ -name "android.jar" -size +5M 2>/dev/null | head -n 1)

echo "[*] Compiling Base App..."
aapt package -f -m -J src/main/java -M AndroidManifest.xml -S res -I "$REAL_JAR"
ecj -d obj -classpath "$REAL_JAR" -sourcepath src/main/java $(find src/main/java -name "*.java")
d8 --output bin $(find obj -name "*.class") --lib "$REAL_JAR"

aapt package -f -M AndroidManifest.xml -S res -I "$REAL_JAR" -F bin/unsigned.apk
cd bin && aapt add unsigned.apk classes.dex >/dev/null && cd ..

apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --out bin/Base_Downloader_v1.apk bin/unsigned.apk 2>/dev/null

# Push directly to the native Downloads folder
cp -f bin/Base_Downloader_v1.apk ~/storage/downloads/Base_Downloader_v1.apk

echo "================================================="
echo "✅ Build Complete!"
echo "👉 Open your phone's File Manager, go to 'Downloads', and tap 'Base_Downloader_v1.apk'."
echo "================================================="
