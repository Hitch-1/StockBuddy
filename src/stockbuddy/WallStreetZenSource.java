package stockbuddy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WallStreetZen: the "Zen Score" (0-100, overall quality grade) and "Zen Rating" (A = best, F = worst).
 * We first look in data/wallstreetzen.csv (if you saved one), then try the website.
 */
public final class WallStreetZenSource {
    private static Map<String, Map<String, String>> csv;

    private WallStreetZenSource() {}

    /** Forget the cached CSV so a newly imported file is read. */
    public static void reload() { csv = null; }

    public static boolean load(Stock s) {
        if (s.ticker.startsWith("^") || s.ticker.equals("SPY") || s.ticker.equals("QQQ")) return false;
        if (csv == null) csv = Csv.readByTicker(AppPaths.data("wallstreetzen.csv"));
        Map<String, String> row = csv.get(s.ticker);
        if (row != null) {
            s.zenScore = Csv.num(row, Double.NaN, "zen score", "zenscore", "score");
            String r = Csv.text(row, "zen rating", "zenrating", "rating");
            if (r != null && !r.trim().isEmpty()) s.zenRating = r.trim().substring(0, 1).toUpperCase();
            return !Double.isNaN(s.zenScore) || s.zenRating != null;
        }
        for (String exch : new String[]{"nasdaq", "nyse"}) {
            String html = Http.get("https://www.wallstreetzen.com/stocks/us/" + exch + "/" + s.ticker.toLowerCase());
            if (html == null) continue;
            String text = Http.htmlToText(html);
            Matcher m = Pattern.compile("Zen Score\\D{0,30}?(\\d{1,3})\\s*/\\s*100").matcher(text);
            if (!m.find()) m = Pattern.compile("Zen Score\\s*:?\\s*(\\d{1,3})\\b").matcher(text);
            if (m.find(0)) {
                int v = Integer.parseInt(m.group(1));
                if (v <= 100) s.zenScore = v;
            }
            Matcher r = Pattern.compile("Zen Rating\\s*:?\\s*\"?([A-F])\\b").matcher(text);
            if (r.find()) s.zenRating = r.group(1);
            if (!Double.isNaN(s.zenScore) || s.zenRating != null) return true;
        }
        return false;
    }
}
