package stockbuddy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where StockBuddy keeps its files (watchlist, CSV imports, settings, reports).
 * If there is a "data" folder next to where the program was started, that is used.
 * Otherwise (e.g. when started from a desktop shortcut) a "StockBuddy" folder in your home folder.
 */
public final class AppPaths {
    private static Path base;

    private AppPaths() {}

    public static synchronized Path base() {
        if (base == null) {
            String custom = System.getProperty("stockbuddy.home");
            if (custom != null) base = Paths.get(custom);
            else if (Files.isDirectory(Paths.get("data")) || Files.exists(Paths.get("src", "stockbuddy"))) base = Paths.get("").toAbsolutePath();
            else base = Paths.get(System.getProperty("user.home"), "StockBuddy");
        }
        return base;
    }

    public static Path data(String file) { return base().resolve("data").resolve(file); }

    public static Path reports() { return base().resolve("reports"); }
}
