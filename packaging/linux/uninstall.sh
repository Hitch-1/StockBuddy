#!/usr/bin/env bash
# Removes StockBuddy, its menu entries, Desktop icons and "start with computer" entry.
read -r -p "Remove StockBuddy and all its settings, watchlist and reports? (y/n) " ans
case "$ans" in [Yy]*) ;; *) exit 0 ;; esac
DESKTOP_DIR="$(command -v xdg-user-dir >/dev/null 2>&1 && xdg-user-dir DESKTOP || echo "$HOME/Desktop")"
rm -f "$HOME/.local/bin/stockbuddy" \
      "$HOME/.local/share/applications/stockbuddy.desktop" \
      "$HOME/.local/share/applications/stockbuddy-widget.desktop" \
      "$HOME/.local/share/applications/stockbuddy-practice.desktop" \
      "$DESKTOP_DIR/stockbuddy.desktop" "$DESKTOP_DIR/stockbuddy-widget.desktop" \
      "$HOME/.config/autostart/stockbuddy-widget.desktop"
rm -rf "$HOME/.local/share/stockbuddy"
echo "StockBuddy has been removed."
