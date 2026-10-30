#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-${SDK_ROOT:+$SDK_ROOT/build-tools/35.0.0}}"
ANDROID_JAR="${ANDROID_JAR:-${SDK_ROOT:+$SDK_ROOT/platforms/android-35/android.jar}}"
KEYSTORE="${KEYSTORE:-${HOME}/.android/debug.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-android}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/bin}"
OBJ_DIR="${OBJ_DIR:-$ROOT_DIR/obj}"
GEN_DIR="${GEN_DIR:-$ROOT_DIR/gen}"
APK_NAME="${APK_NAME:-NEXORA-debug.apk}"

require_file() {
    [[ -f "$1" ]] || { echo "ERROR: required file not found: $1" >&2; exit 2; }
}
require_tool() {
    command -v "$1" >/dev/null 2>&1 || { echo "ERROR: required tool not found: $1" >&2; exit 127; }
}

[[ -n "$BUILD_TOOLS" ]] || { echo "ERROR: set ANDROID_SDK_ROOT or ANDROID_BUILD_TOOLS" >&2; exit 2; }
[[ -n "$ANDROID_JAR" ]] || { echo "ERROR: set ANDROID_SDK_ROOT or ANDROID_JAR" >&2; exit 2; }
require_file "$ANDROID_JAR"
require_file "$KEYSTORE"
for tool in aapt d8 zipalign apksigner; do
    require_file "$BUILD_TOOLS/$tool"
done
require_tool zip
require_tool java
require_tool javac

rm -rf "$OBJ_DIR" "$GEN_DIR" "$OUT_DIR/dex" "$OUT_DIR/app-unsigned.apk" "$OUT_DIR/app-aligned.apk" "$OUT_DIR/$APK_NAME"
mkdir -p "$OBJ_DIR" "$GEN_DIR" "$OUT_DIR/dex"

echo "[*] Compiling Android resources..."
"$BUILD_TOOLS/aapt" package -f -m -J "$GEN_DIR" -M AndroidManifest.xml -S res -I "$ANDROID_JAR"

mapfile -t JAVA_SOURCES < <(
    find src/main/java -name '*.java' -type f ! -name 'R.java'
    find "$GEN_DIR" -name '*.java' -type f
)
[[ "${#JAVA_SOURCES[@]}" -gt 0 ]] || { echo "ERROR: no Java sources found" >&2; exit 2; }

echo "[*] Compiling Java sources with the JDK compiler..."
javac -source 8 -target 8 -proc:none -d "$OBJ_DIR" -classpath "$ANDROID_JAR" "${JAVA_SOURCES[@]}"

echo "[*] Converting bytecode to DEX..."
"$BUILD_TOOLS/d8" --release --lib "$ANDROID_JAR" --output "$OUT_DIR/dex" $(find "$OBJ_DIR" -name '*.class' -type f | sort)

echo "[*] Packaging unsigned APK..."
"$BUILD_TOOLS/aapt" package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT_DIR/app-unsigned.apk"
(cd "$OUT_DIR/dex" && zip -q -u ../app-unsigned.apk classes.dex)

 echo "[*] Aligning and signing APK..."
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT_DIR/app-unsigned.apk" "$OUT_DIR/app-aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --out "$OUT_DIR/$APK_NAME" "$OUT_DIR/app-aligned.apk"
"$BUILD_TOOLS/zipalign" -c -P 4 -v 4 "$OUT_DIR/$APK_NAME" >/dev/null
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_DIR/$APK_NAME"

echo "BUILD SUCCESSFUL: $OUT_DIR/$APK_NAME"
