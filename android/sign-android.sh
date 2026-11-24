#!/usr/bin/env bash
# Signs the app so phones will install it. The first run creates your personal signing key
# (android/stockbuddy-release.jks). KEEP THAT FILE: future updates must be signed with the same key.
set -euo pipefail
A="$(cd "$(dirname "$0")" && pwd)"
KS="$A/stockbuddy-release.jks"
IN="$A/build/StockBuddy-unsigned.apk"
OUT="${1:-$A/build/StockBuddy.apk}"
[ -f "$IN" ] || { echo "Build first: ./build-android.sh"; exit 1; }
if [ ! -f "$KS" ]; then
  read -r -s -p "Choose a password for your new signing key (6+ characters): " PW; echo
  keytool -genkeypair -keystore "$KS" -alias stockbuddy -keyalg RSA -keysize 2048 -validity 10000 \
          -storepass "$PW" -keypass "$PW" -dname "CN=StockBuddy"
else
  read -r -s -p "Password of your signing key: " PW; echo
fi
apksigner sign --ks "$KS" --ks-key-alias stockbuddy --ks-pass "pass:$PW" --key-pass "pass:$PW" --out "$OUT" "$IN"
apksigner verify "$OUT" && echo "Signed: $OUT"
