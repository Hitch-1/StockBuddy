package stockbuddy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Stock Rover is a paid research site that requires a login and has no public data feed,
 * so the program can't download it by itself. Instead, export a screener or portfolio
 * from Stock Rover as CSV and save it as data/stockrover.csv. We read the fair value
 * and the quality / value / growth scores from it.
 */
public final class StockRoverSource {
    public static Path FILE = AppPaths.data("stockrover.csv");
    private static Map<String, Map<String, String>> rows;

    private StockRoverSource() {}

    /** Forget the cached CSV so a newly imported file is read. */
    public static void reload() { rows = null; }

    public static boolean available() { return Files.exists(FILE); }

    public static java.util.Set<String> tickers() {
        if (rows == null) rows = Csv.readByTicker(FILE);
        return rows.keySet();
    }

    public static boolean load(Stock s) {
        if (rows == null) rows = Csv.readByTicker(FILE);
        Map<String, String> r = rows.get(s.ticker);
        if (r == null) return false;
        s.fairValue = Csv.num(r, Double.NaN, "fair value", "fairvalue", "intrinsic value");
        if (Double.isNaN(s.fairValue)) {
            double mos = Csv.num(r, Double.NaN, "margin of safety");
            double px = Csv.num(r, Double.NaN, "price", "last price");
            if (!Double.isNaN(mos) && !Double.isNaN(px)) s.fairValue = px / (1 - mos / 100.0);
        }
        s.roverQuality = Csv.num(r, Double.NaN, "quality score", "quality");
        s.roverValue = Csv.num(r, Double.NaN, "value score", "value");
        s.roverGrowth = Csv.num(r, Double.NaN, "growth score", "growth");
        if (s.name == null) s.name = Csv.text(r, "company", "name", "company name");
        return true;
    }
}
