#!/usr/bin/env bash
set -euo pipefail

project="$(cd "$(dirname "$0")" && pwd)"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
tools="$sdk/build-tools/35.0.1"
android_jar="$sdk/platforms/android-35/android.jar"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v javac >/dev/null || ! javac -version >/dev/null 2>&1; then
    echo "Для сборки нужен JDK 17 или новее (см. JAVA_HOME)." >&2
    exit 1
fi
if [[ ! -x "$tools/aapt2" || ! -f "$android_jar" ]]; then
    echo "Нужны Android SDK Platform 35 и Build-Tools 35.0.1 (см. ANDROID_SDK_ROOT)." >&2
    exit 1
fi

src="$project/app/src/main"
mkdir -p "$project/.build"
work=$(mktemp -d "$project/.build/build.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
mkdir "$work/generated" "$work/classes" "$work/dex"

"$tools/aapt2" compile --dir "$src/res" -o "$work/resources.zip"
"$tools/aapt2" link --manifest "$src/AndroidManifest.xml" -I "$android_jar" \
    --java "$work/generated" -o "$work/unsigned.apk" "$work/resources.zip"
java_files=()
while IFS= read -r -d '' file; do java_files+=("$file"); done \
    < <(find "$src/java" "$work/generated" -name '*.java' -type f -print0)
javac -encoding UTF-8 --release 8 -classpath "$android_jar" \
    -d "$work/classes" "${java_files[@]}"
jar cf "$work/classes.jar" -C "$work/classes" .
"$tools/d8" --release --min-api 26 --lib "$android_jar" \
    --output "$work/dex" "$work/classes.jar"
(cd "$work/dex" && zip -q -0 "$work/unsigned.apk" ./*.dex)
"$tools/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"

umask 077
key="$project/.signing/attendance.p12"
pass="$project/.signing/password"
if [[ ! -e "$key" && ! -e "$pass" ]]; then
    mkdir -p "$project/.signing"
    openssl rand -hex 24 > "$work/password"
    keytool -genkeypair -keystore "$work/attendance.p12" -storetype PKCS12 \
        -storepass:file "$work/password" -alias attendance -keyalg RSA -keysize 2048 \
        -validity 10000 -dname "CN=Group Attendance"
    mv "$work/attendance.p12" "$key"
    mv "$work/password" "$pass"
fi
if [[ ! -f "$key" || ! -f "$pass" ]]; then
    echo "Нет полного комплекта ключа подписи в .signing; восстанови его из копии." >&2
    exit 1
fi

"$tools/apksigner" sign --ks "$key" --ks-key-alias attendance --ks-pass "file:$pass" \
    --v4-signing-enabled false --out "$work/Attendance.apk" "$work/aligned.apk"
"$tools/apksigner" verify "$work/Attendance.apk"
"$tools/zipalign" -c 4 "$work/Attendance.apk"
chmod 644 "$work/Attendance.apk"
mv "$work/Attendance.apk" "$project/Attendance.apk"
echo "Готово: $project/Attendance.apk"
