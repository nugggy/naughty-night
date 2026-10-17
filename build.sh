#!/usr/bin/env bash
# Builds NaughtyNight.apk using the raw Android SDK tools (no Gradle).
# Version comes from the VERSION file (e.g. 1.2.3 -> versionCode 10203).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
OUT=build
VERSION="$(tr -d '[:space:]' < VERSION)"
IFS=. read -r MA MI PA <<< "${VERSION%%-*}"
CODE=$(( MA * 10000 + MI * 100 + ${PA:-0} ))

# Signing key stays local. Keep naughtynight.keystore and .keystore-pass backed up:
# without them you can't ship updates to installed copies.
KS=naughtynight.keystore
KS_PASS="${NN_KS_PASS:-$(cat .keystore-pass 2>/dev/null || true)}"
if [ ! -f "$KS" ]; then
  [ -n "$KS_PASS" ] || { KS_PASS="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"; printf '%s' "$KS_PASS" > .keystore-pass; }
  keytool -genkeypair -keystore "$KS" -alias nn -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Naughty Night" -noprompt
fi

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
"$BT/aapt2.exe" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2.exe" link -o "$OUT/base.apk" -I "$JAR" --manifest app/AndroidManifest.xml \
  --version-name "$VERSION" --version-code "$CODE" --replace-version \
  -A app/assets --java "$OUT/gen" "$OUT/res.zip"
javac -nowarn -Xlint:-options -Xlint:-deprecation -source 11 -target 11 -classpath "$JAR" -d "$OUT/classes" \
  $(find app/src "$OUT/gen" -name '*.java')
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 24 --lib "$JAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
python - <<PY
import zipfile
with zipfile.ZipFile("$OUT/base.apk","a",zipfile.ZIP_DEFLATED) as z: z.write("$OUT/dex/classes.dex","classes.dex")
PY
"$BT/zipalign.exe" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --out NaughtyNight.apk "$OUT/aligned.apk"
java -jar "$BT/lib/apksigner.jar" verify NaughtyNight.apk
echo "Built NaughtyNight.apk v$VERSION (code $CODE)"
