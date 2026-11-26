#!/usr/bin/env bash
# StockBuddy launcher for Mac / Linux.
#   ./run.sh              open the desktop app
#   ./run.sh --demo       practice mode (made-up data)
#   ./run.sh --widget     only the small desktop widget
#   ./run.sh --console    the old text version in the terminal
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1; then
  echo "Java is not installed. Download the free 'JDK 17 or newer' from https://adoptium.net and try again."
  exit 1
fi
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out src/stockbuddy/*.java || exit 1
mkdir -p out/stockbuddy/i18n && cp src/stockbuddy/i18n/*.txt out/stockbuddy/i18n/
jar --create --file StockBuddy.jar --main-class stockbuddy.Main -C out . || exit 1
java -jar StockBuddy.jar "$@"
