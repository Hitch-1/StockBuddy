package stockbuddy;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Remembers your choices (skin, widget position...) between runs, in data/settings.properties. */
public final class Settings {
    private static final Path FILE = AppPaths.data("settings.properties");
    private static final Properties P = new Properties();
    static {
        try (Reader r = Files.newBufferedReader(FILE)) { P.load(r); } catch (Exception ignored) {}
    }

    private Settings() {}

    public static String get(String k, String def) { return P.getProperty(k, def); }
    public static int getInt(String k, int def) { try { return Integer.parseInt(P.getProperty(k)); } catch (Exception e) { return def; } }
    public static boolean getBool(String k, boolean def) { String v = P.getProperty(k); return v == null ? def : Boolean.parseBoolean(v); }

    public static synchronized void set(String k, Object v) {
        P.setProperty(k, String.valueOf(v));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { P.store(w, "StockBuddy settings"); }
        } catch (Exception ignored) {}
    }
}
