#!/usr/bin/env bash
# Builds the StockBuddy Android app (unsigned) using Ubuntu/Debian's Android SDK packages:
#   sudo apt install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23 openjdk-8-jdk-headless
# Output: android/build/StockBuddy-unsigned.apk   (sign it with sign-android.sh before installing)
set -euo pipefail
A="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$A")"
B="$A/build"
AJ="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
JAVA8="${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
RL="$A/tools/retrolambda-2.5.7.jar"

if [ ! -f "$RL" ]; then
  mkdir -p "$A/tools"
  echo "Downloading Retrolambda (turns Java lambdas into code Android understands)..."
  curl -fsSL -o "$RL" https://repo1.maven.org/maven2/net/orfjackal/retrolambda/retrolambda/2.5.7/retrolambda-2.5.7.jar
fi

# Shared code used by both the desktop and the phone app (must stay Java 8 compatible)
CORE="Json Http Log Ui Stock Signal Technicals PriceSource StockAnalysisSource WallStreetZenSource StockRoverSource Csv
      RedditSource WorldMarkets Scorer Options Analyzer Advisor DemoData Glossary HtmlView Settings AppPaths Palette
      KeyStats FinnhubSource ChartOptions EconomicCalendar WsbSentiment SecEdgar AlphaVantage CustomCsv WorldStocks TrackRecord
      Alerts PaperPortfolio ReportWriter"
FILES=()
for c in $CORE; do FILES+=("$ROOT/src/stockbuddy/$c.java"); done
while IFS= read -r f; do FILES+=("$f"); done < <(find "$A/src" -name '*.java')

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/rl" "$B/dex"
echo "1/6 Resources..."
aapt package -f -m -J "$B/gen" -M "$A/AndroidManifest.xml" -S "$A/res" -I "$AJ"
echo "2/6 Compiling..."
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$AJ" -d "$B/classes" "${FILES[@]}" "$B/gen/com/stockbuddy/app/R.java"
echo "3/6 Converting lambdas..."
"$JAVA8/bin/java" -Dretrolambda.inputDir="$B/classes" -Dretrolambda.outputDir="$B/rl" \
    -Dretrolambda.classpath="$B/classes:$AJ" -jar "$RL" > "$B/retrolambda.log" 2>&1 || { cat "$B/retrolambda.log"; exit 1; }
echo "4/6 Making Android bytecode..."
dalvik-exchange --dex --min-sdk-version=26 --output="$B/dex/classes.dex" "$B/rl"
echo "5/6 Packaging..."
aapt package -f -M "$A/AndroidManifest.xml" -S "$A/res" -I "$AJ" -F "$B/pkg.apk"
(cd "$B/dex" && aapt add "$B/pkg.apk" classes.dex > /dev/null)
echo "6/6 Aligning..."
zipalign -f -p 4 "$B/pkg.apk" "$B/StockBuddy-unsigned.apk"
rm -f "$B/pkg.apk"
echo "Done: $B/StockBuddy-unsigned.apk  (next: ./sign-android.sh)"
