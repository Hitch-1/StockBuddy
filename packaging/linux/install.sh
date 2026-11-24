#!/usr/bin/env bash
# StockBuddy installer for Linux (no admin/root needed).
# Installs to ~/.local/share/stockbuddy and adds menu entries + Desktop icons.
set -euo pipefail
SRC="$(cd "$(dirname "$0")" && pwd)"
DEST="$HOME/.local/share/stockbuddy"
BIN="$HOME/.local/bin"
APPS="$HOME/.local/share/applications"

echo
echo "  StockBuddy installer"
echo "  --------------------"
mkdir -p "$DEST/data" "$BIN" "$APPS"

ARCH="$(uname -m)"
if [ "$ARCH" = "x86_64" ] && [ -d "$SRC/runtime" ]; then
  echo "  Copying StockBuddy (with its own built-in Java)..."
  rm -rf "$DEST/runtime"
  cp -r "$SRC/runtime" "$DEST/runtime"
  chmod +x "$DEST/runtime/bin/"* "$DEST/runtime/lib/jspawnhelper" 2>/dev/null || true
else
  echo "  Your computer is $ARCH - StockBuddy will use the Java installed on your system."
  if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -qE 'version "(1[7-9]|[2-9][0-9])'; then
    echo "  StockBuddy needs Java 17 or newer. Install it, then run this installer again. For example:"
    echo "      Ubuntu/Debian/Mint:  sudo apt install openjdk-21-jre"
    echo "      Fedora:              sudo dnf install java-21-openjdk"
    echo "      Arch:                sudo pacman -S jre-openjdk"
    exit 1
  fi
fi
cp "$SRC/StockBuddy.jar" "$SRC/stockbuddy.png" "$SRC/uninstall.sh" "$DEST/"
chmod +x "$DEST/uninstall.sh"
for f in "$SRC"/data/*; do
  [ -e "$DEST/data/$(basename "$f")" ] || cp "$f" "$DEST/data/"   # never overwrite your own watchlist/settings
done

cat > "$BIN/stockbuddy" <<'LAUNCH'
#!/bin/sh
# Starts StockBuddy. Options: --demo (practice data), --widget (only the desktop widget), --console (text version)
D="$HOME/.local/share/stockbuddy"
if [ -x "$D/runtime/bin/java" ]; then J="$D/runtime/bin/java"; else J=java; fi
cd "$D" && exec "$J" -jar "$D/StockBuddy.jar" "$@"
LAUNCH
chmod +x "$BIN/stockbuddy"

make_entry() {  # file, name, arguments, comment
  cat > "$1" <<DESK
[Desktop Entry]
Type=Application
Name=$2
Comment=$4
Exec=$BIN/stockbuddy $3
Icon=$DEST/stockbuddy.png
Terminal=false
Categories=Office;Finance;
DESK
  chmod +x "$1"
}
make_entry "$APPS/stockbuddy.desktop" "StockBuddy" "" "Your friendly daily stock helper"
make_entry "$APPS/stockbuddy-widget.desktop" "StockBuddy Widget" "--widget" "StockBuddy desktop widget"
make_entry "$APPS/stockbuddy-practice.desktop" "StockBuddy (practice mode)" "--demo" "StockBuddy with made-up practice data"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" >/dev/null 2>&1 || true

DESKTOP_DIR="$(command -v xdg-user-dir >/dev/null 2>&1 && xdg-user-dir DESKTOP || echo "$HOME/Desktop")"
if [ -d "$DESKTOP_DIR" ]; then
  for f in stockbuddy.desktop stockbuddy-widget.desktop; do
    cp "$APPS/$f" "$DESKTOP_DIR/$f"
    chmod +x "$DESKTOP_DIR/$f"
    command -v gio >/dev/null 2>&1 && gio set "$DESKTOP_DIR/$f" metadata::trusted true 2>/dev/null || true
  done
  echo "  Added StockBuddy icons to your Desktop."
fi

echo
echo "  Done! Open StockBuddy from your apps menu (search \"StockBuddy\") or the Desktop icon."
echo "  You can also type:  stockbuddy        (or: stockbuddy --widget)"
echo "  To remove it later: ~/.local/share/stockbuddy/uninstall.sh"
echo
read -r -p "  Open StockBuddy now? (y/n) " ans || true
case "${ans:-n}" in [Yy]*) (nohup "$BIN/stockbuddy" >/dev/null 2>&1 &) ;; esac
