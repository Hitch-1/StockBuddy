package stockbuddy;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.List;

/**
 * Shows price alerts as real desktop notifications:
 *  - Windows (and most Linux desktops): a system-tray notification
 *  - Linux without a tray: notify-send
 *  - macOS: the built-in notification centre (osascript)
 * Returns true when at least one notification was shown.
 */
public final class DesktopNotify {
    private static TrayIcon tray;

    private DesktopNotify() {}

    public static boolean show(String title, List<String> lines) {
        String text = String.join("\n", lines);
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
                if (tray == null) {
                    tray = new TrayIcon(App.icons().get(1), "StockBuddy");
                    tray.setImageAutoSize(true);
                    SystemTray.getSystemTray().add(tray);
                }
                tray.displayMessage(title, text, TrayIcon.MessageType.INFO);
                if (os.contains("win")) return true;   // Windows shows tray messages as proper notifications
            }
        } catch (Throwable ignored) { /* no tray - try the system's own notifier below */ }
        try {
            if (os.contains("mac")) {
                String safe = text.replace("\\", "").replace("\"", "'");
                new ProcessBuilder("osascript", "-e", "display notification \"" + safe + "\" with title \"" + title.replace("\"", "'") + "\"").start();
                return true;
            }
            if (!os.contains("win")) {
                new ProcessBuilder("notify-send", "-a", "StockBuddy", "-u", "critical", title, text).start();
                return true;
            }
        } catch (Throwable ignored) { /* notify-send not installed */ }
        return tray != null;
    }
}
