package stockbuddy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Turns "show the desktop widget when my computer starts" on or off (Windows, Mac and Linux). */
public final class Autostart {
    private Autostart() {}

    private static final String OS = System.getProperty("os.name", "").toLowerCase();

    private static Path file() {
        String home = System.getProperty("user.home");
        if (OS.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData == null ? home : appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "StockBuddyWidget.vbs");
        }
        if (OS.contains("mac")) return Path.of(home, "Library", "LaunchAgents", "com.stockbuddy.widget.plist");
        return Path.of(home, ".config", "autostart", "stockbuddy-widget.desktop");
    }

    public static boolean isEnabled() { return Files.exists(file()); }

    /** Returns null on success, or a friendly error message. */
    public static String set(boolean on) {
        try {
            Path f = file();
            if (!on) { Files.deleteIfExists(f); return null; }
            String java = Path.of(System.getProperty("java.home"), "bin", OS.contains("win") ? "javaw.exe" : "java").toString();
            String where = new File(App.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            boolean jar = where.toLowerCase().endsWith(".jar");
            String[] cmd = jar ? new String[]{java, "-Dstockbuddy.home=" + AppPaths.base(), "-jar", where, "--widget"}
                    : new String[]{java, "-Dstockbuddy.home=" + AppPaths.base(), "-cp", where, "stockbuddy.Main", "--widget"};
            Files.createDirectories(f.getParent());
            if (OS.contains("win")) {
                StringBuilder line = new StringBuilder();
                for (String c : cmd) line.append(line.length() == 0 ? "" : " ").append("\"").append(c).append("\"");
                Files.writeString(f, "CreateObject(\"WScript.Shell\").Run \"" + line.toString().replace("\"", "\"\"") + "\", 0, False\r\n");
            } else if (OS.contains("mac")) {
                StringBuilder args = new StringBuilder();
                for (String c : cmd) args.append("    <string>").append(c.replace("&", "&amp;").replace("<", "&lt;")).append("</string>\n");
                Files.writeString(f, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
                        + "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist version=\"1.0\"><dict>\n  <key>Label</key><string>com.stockbuddy.widget</string>\n"
                        + "  <key>ProgramArguments</key><array>\n" + args + "  </array>\n  <key>RunAtLoad</key><true/>\n</dict></plist>\n");
            } else {
                StringBuilder exec = new StringBuilder();
                for (String c : cmd) exec.append(exec.length() == 0 ? "" : " ").append("\"").append(c).append("\"");
                Files.writeString(f, "[Desktop Entry]\nType=Application\nName=StockBuddy widget\nExec=" + exec + "\nX-GNOME-Autostart-enabled=true\n");
            }
            return null;
        } catch (Exception e) {
            return "Sorry, I couldn't set that up automatically: " + e.getMessage();
        }
    }
}
