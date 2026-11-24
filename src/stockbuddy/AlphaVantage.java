package stockbuddy;

import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

/**
 * Alpha Vantage (free key from alphavantage.co): news sentiment - is today's news about a company
 * mostly positive or negative? The free plan only allows about 25 requests a DAY, so StockBuddy
 * asks about a few stocks (your watchlist first) and remembers the answers for the rest of the day.
 */
public final class AlphaVantage {
    public static final int DEFAULT_PER_DAY = 8;
    private static final Path CACHE = AppPaths.data("alphavantage-cache.properties");
    public static volatile String lastStatus = "Not set up";
    private static volatile boolean limitHit;

    private AlphaVantage() {}

    public static String key() {
        String k = Settings.get("alphavantage.key", "").trim();
        if (k.isEmpty()) { String env = System.getenv("ALPHAVANTAGE_API_KEY"); k = env == null ? "" : env.trim(); }
        return k;
    }

    public static boolean enabled() { return !key().isEmpty(); }

    public static void setKey(String k) { Settings.set("alphavantage.key", k == null ? "" : k.trim()); }

    /** Checks a key. Returns null if fine, or a friendly error. */
    public static String test(String k) {
        String body = Http.request("GET", "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=IBM&apikey=" + enc(k), null, null, null);
        if (body == null) return "Alpha Vantage couldn't be reached. Check your internet connection.";
        if (body.contains("Global Quote")) return null;
        if (body.contains("Information") || body.contains("Note")) return "Alpha Vantage says the daily limit is reached or the key is invalid. The key is saved; try again tomorrow.";
        return "Alpha Vantage didn't accept the key.";
    }

    /** Adds news sentiment to up to `max` stocks (cached for today). */
    public static void load(List<Stock> stocks, int max) {
        if (!enabled()) { lastStatus = "Not set up - add your free key from alphavantage.co (optional)"; return; }
        Properties cache = readCache();
        String today = LocalDate.now().toString();
        if (!today.equals(cache.getProperty("date"))) { cache.clear(); cache.setProperty("date", today); }
        int asked = 0, used = 0;
        limitHit = false;
        for (Stock s : stocks) {
            if (used >= max) break;
            if (s.ticker.contains(".") || s.ticker.startsWith("^")) continue;
            String hit = cache.getProperty("news." + s.ticker);
            if (hit == null && !limitHit) {
                hit = fetch(s.ticker);
                asked++;
                if (hit != null) cache.setProperty("news." + s.ticker, hit);
            }
            if (hit != null && apply(s, hit)) used++;
        }
        writeCache(cache);
        lastStatus = used > 0 ? "OK - news sentiment for " + used + " stocks" + (asked == 0 ? " (remembered from earlier today)" : "")
                : limitHit ? "Daily limit reached (the free plan allows about 25 requests a day) - try again tomorrow"
                : "Could not get news sentiment - check your key and internet";
    }

    /** Returns "score|count|headline1|headline2" or null. */
    private static String fetch(String ticker) {
        String body = Http.request("GET", "https://www.alphavantage.co/query?function=NEWS_SENTIMENT&limit=50&tickers=" + enc(ticker) + "&apikey=" + enc(key()), null, null, null);
        if (body == null) return null;
        try {
            Object root = Json.parse(body);
            if (Json.path(root, "feed") == null) {
                if (Json.path(root, "Information") != null || Json.path(root, "Note") != null) limitHit = true;
                return null;
            }
            double sum = 0, weights = 0;
            int n = 0;
            StringBuilder heads = new StringBuilder();
            for (Object article : Json.list(Json.path(root, "feed"))) {
                for (Object ts : Json.list(Json.path(article, "ticker_sentiment"))) {
                    if (!ticker.equalsIgnoreCase(String.valueOf(Json.path(ts, "ticker")))) continue;
                    double rel = parse(Json.path(ts, "relevance_score"), 0.5), sc = parse(Json.path(ts, "ticker_sentiment_score"), Double.NaN);
                    if (Double.isNaN(sc)) continue;
                    sum += sc * rel; weights += rel; n++;
                    if (n <= 2) heads.append('|').append(String.valueOf(Json.path(article, "title")).replace('|', '/'));
                }
            }
            if (n == 0) return "NaN|0";
            return (sum / weights) + "|" + n + heads;
        } catch (Exception e) {
            Log.debug("Alpha Vantage unreadable for " + ticker + ": " + e);
            return null;
        }
    }

    private static boolean apply(Stock s, String cached) {
        String[] p = cached.split("\\|");
        try {
            double sc = Double.parseDouble(p[0]);
            if (Double.isNaN(sc)) return false;
            s.newsScore = sc;
            s.newsCount = Integer.parseInt(p[1]);
            for (int i = 2; i < p.length; i++) s.newsHeadlines.add(p[i]);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double parse(Object o, double def) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static Properties readCache() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(CACHE)) { p.load(r); } catch (Exception ignored) {}
        return p;
    }

    private static void writeCache(Properties p) {
        try {
            Files.createDirectories(CACHE.getParent());
            try (Writer w = Files.newBufferedWriter(CACHE)) { p.store(w, "Alpha Vantage answers for today"); }
        } catch (Exception ignored) {}
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
