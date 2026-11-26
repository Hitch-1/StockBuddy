#!/usr/bin/env bash
# Builds a stand-alone StockBuddy app in the "dist" folder (people who use it don't need Java installed).
cd "$(dirname "$0")"
rm -rf out build dist && mkdir -p out build
javac -encoding UTF-8 -d out src/stockbuddy/*.java || exit 1
mkdir -p out/stockbuddy/i18n && cp src/stockbuddy/i18n/*.txt out/stockbuddy/i18n/
jar --create --file build/StockBuddy.jar --main-class stockbuddy.Main -C out . || exit 1
jpackage --type app-image --name StockBuddy --input build --main-jar StockBuddy.jar --dest dist \
         --description "Beginner-friendly daily stock helper" || exit 1
echo "Done! Your app is in: dist/StockBuddy"
