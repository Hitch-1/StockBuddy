#!/usr/bin/env bash
# Builds the downloadable StockBuddy-<version>.zip with Windows, Linux and Android installers.
# Run on Linux x86_64 with JDK 17+. The Android app must be built and signed first
# (android/build-android.sh, then android/sign-android.sh) - otherwise it is left out.
set -euo pipefail
VERSION="${VERSION:-1.5}"
P="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$P")"
B="$ROOT/build/release"
NAME="StockBuddy-$VERSION"
OUT="$B/$NAME"

# A fresh default watchlist (never your own one)
default_watchlist() {
  printf '%s\n' "# Your watchlist: one ticker per line. Lines starting with # are ignored." \
    AAPL MSFT NVDA AMZN GOOGL META TSLA AMD AVGO JPM V KO PEP JNJ WMT COST XOM DIS NFLX INTC PLTR BRK-B UNH BAC > "$1"
}

rm -rf "$B"
mkdir -p "$B/classes" "$OUT"
echo "1/5 Compiling the desktop app..."
javac -Xlint:none --release 17 -encoding UTF-8 -d "$B/classes" "$ROOT"/src/stockbuddy/*.java
mkdir -p "$B/classes/stockbuddy/i18n" && cp "$ROOT"/src/stockbuddy/i18n/*.txt "$B/classes/stockbuddy/i18n/"
jar --create --file "$B/StockBuddy.jar" --main-class stockbuddy.Main -C "$B/classes" .

echo "2/5 Making a small built-in Java for Linux..."
jlink --add-modules java.base,java.desktop,jdk.crypto.ec,jdk.charsets,jdk.accessibility \
      --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$B/runtime-linux-x64"

echo "3/5 Windows folder..."
W="$OUT/Windows"
mkdir -p "$W/data"
cp "$B/StockBuddy.jar" "$P/icons/stockbuddy.ico" "$P/windows/"* "$W/"
cp "$ROOT/data/"*.sample.csv "$W/data/"
default_watchlist "$W/data/watchlist.txt"

echo "4/5 Linux folder..."
L="$OUT/Linux"
mkdir -p "$L/data"
cp "$B/StockBuddy.jar" "$P/icons/stockbuddy.png" "$P/linux/"* "$L/"
cp -r "$B/runtime-linux-x64" "$L/runtime"
cp "$ROOT/data/"*.sample.csv "$L/data/"
default_watchlist "$L/data/watchlist.txt"

echo "5/5 Android folder..."
cp "$P/README - START HERE.txt" "$OUT/"
if [ -f "$ROOT/android/build/StockBuddy.apk" ]; then
  mkdir -p "$OUT/Android"
  cp "$P/android/"* "$ROOT/android/build/StockBuddy.apk" "$OUT/Android/"
else
  echo "   (no signed APK found - leaving Android out of this download)"
  python3 - "$OUT/README - START HERE.txt" <<'PY'
import sys, re
p = sys.argv[1]
t = open(p, newline='').read()
t = re.sub(r"ANDROID \(Android 8\.0 or newer\)\r\n-+\r\n.*?(?=\r\n\r\nTIPS FOR BEGINNERS)",
           "ANDROID\r\n-------\r\nThe Android app is not included in this download yet. Its code is in\r\n"
           "\"Source code/android\"; once it has been built and signed it can be added.",
           t, flags=re.S)
t = t.replace("the Android home-screen widget can each", "the Android home-screen widget can each")
open(p, "w", newline='').write(t)
PY
fi

mkdir -p "$OUT/Source code"
cp -r "$ROOT/src" "$ROOT/README.md" "$ROOT/run.sh" "$ROOT/run.bat" "$ROOT/make-app.sh" "$ROOT/make-app.bat" "$OUT/Source code/"
mkdir -p "$OUT/Source code/android"
cp -r "$ROOT/android/src" "$ROOT/android/res" "$ROOT/android/AndroidManifest.xml" "$ROOT/android/build-android.sh" "$ROOT/android/sign-android.sh" "$OUT/Source code/android/"
# only the samples and the default watchlist - never your own files (Stock Rover export, Reddit posts, portfolio, settings...)
mkdir -p "$OUT/Source code/data"
cp "$ROOT/data/"*.sample.csv "$OUT/Source code/data/"
printf '%s\n' "# Your watchlist: one ticker per line. Lines starting with # are ignored." AAPL MSFT NVDA AMZN GOOGL META TSLA JPM KO WMT > "$OUT/Source code/data/watchlist.txt"

python3 - "$B" "$NAME" <<'PY'
import os, sys, zipfile, stat
base, name = sys.argv[1], sys.argv[2]
src = os.path.join(base, name)
dest = os.path.join(base, name + ".zip")
with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for root, dirs, files in os.walk(src):
        dirs.sort()
        for f in sorted(files):
            full = os.path.join(root, f)
            arc = os.path.relpath(full, base)
            info = zipfile.ZipInfo.from_file(full, arc)
            info.compress_type = zipfile.ZIP_DEFLATED
            mode = os.stat(full).st_mode
            if f.endswith(".sh") or "/runtime/bin/" in arc or arc.endswith("jspawnhelper"):
                mode |= stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH
            info.external_attr = (mode & 0xFFFF) << 16
            with open(full, "rb") as fh:
                z.writestr(info, fh.read(), compresslevel=9)
print("Created", dest, round(os.path.getsize(dest) / 1e6, 1), "MB")
PY
