#!/usr/bin/env bash
# Builds a stand-alone StockBuddy app in the "dist" folder (people who use it don't need Java installed).
cd "$(dirname "$0")"
rm -rf out build dist && mkdir -p out build
javac -d out src/stockbuddy/*.java || exit 1
jar --create --file build/StockBuddy.jar --main-class stockbuddy.Main -C out . || exit 1
jpackage --type app-image --name StockBuddy --input build --main-jar StockBuddy.jar --dest dist \
         --description "Beginner-friendly daily stock helper" || exit 1
echo "Done! Your app is in: dist/StockBuddy"
