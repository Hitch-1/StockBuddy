package stockbuddy;

import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finnhub.io (free account + API key): Wall Street analyst recommendations and
 * basic financial ratios for each stock.
 *
 * Get a free key at https://finnhub.io/register, then paste it in StockBuddy
 * (desktop: Data Sources page, phone: More menu). It is saved only on your device.
 * The free plan allows 60 requests a minute, so calls are spaced out and remembered for 6 hours.
 */
public final class FinnhubSource {
    private static final String BASE = "https://finnhub.io/api/v1";
    private static final long GAP_MS = 1100;                 // ~55 calls a minute: under the free limit
    private static final long KEEP_MS = 6 * 3600_000L;       // reuse answers for 6 hours
    private static final Map<String, Object[]> CACHE = new ConcurrentHashMap<>(); // url -> {time, body}
    private static long nextCall;

    public static volatile int lastOk, lastFailed;

    private FinnhubSource() {}

    /** Your key: from the app's settings, or the FINNHUB_API_KEY environment variable. */
    public static String key() {
        String k = Settings.get("finnhub.key", "").trim();
        if (k.isEmpty()) {
            String env = System.getenv("FINNHUB_API_KEY");
            k = env == null ? "" : env.trim();
        }
        return k;
    }

    public static boolean enabled() { return !key().isEmpty(); }

    public static void setKey(String k) { Settings.set("finnhub.key", k == null ? "" : k.trim()); CACHE.clear(); }

    public static void resetCounts() { lastOk = 0; lastFailed = 0; }

    /** Loads recommendations + ratios for one stock. Returns true if anything was found. */
    public static boolean load(Stock s) {
        if (!enabled() || s.ticker.startsWith("^")) return false;
        String sym = enc(s.ticker.replace('-', '.'));
        boolean found = false;
        String rec = call("/stock/recommendation?symbol=" + sym);
        if (rec != null) {
            try {
                List<Object> months = Json.list(Json.parse(rec));
                if (!months.isEmpty()) {
                    Object m = months.get(0);   // newest month first
                    s.fhStrongBuy = (int) Json.num(Json.path(m, "strongBuy"), 0);
                    s.fhBuy = (int) Json.num(Json.path(m, "buy"), 0);
                    s.fhHold = (int) Json.num(Json.path(m, "hold"), 0);
                    s.fhSell = (int) Json.num(Json.path(m, "sell"), 0);
                    s.fhStrongSell = (int) Json.num(Json.path(m, "strongSell"), 0);
                    Object p = Json.path(m, "period");
                    s.fhPeriod = p instanceof String ? (String) p : null;
                    found = true;
                }
            } catch (Exception e) { Log.debug("Finnhub recommendation unreadable for " + s.ticker + ": " + e); }
        }
        String met = call("/stock/metric?metric=all&symbol=" + sym);
        if (met != null) {
            try {
                Object metric = Json.path(Json.parse(met), "metric");
                if (metric instanceof Map && !((Map<?, ?>) metric).isEmpty()) {
                    KeyStats.fromFinnhub(s, metric);
                    found = true;
                }
            } catch (Exception e) { Log.debug("Finnhub metrics unreadable for " + s.ticker + ": " + e); }
        }
        synchronized (FinnhubSource.class) { if (found) lastOk++; else lastFailed++; }
        return found;
    }

    /** Checks a key by asking for one quote. Returns null if fine, or a friendly error. */
    public static String test(String k) {
        String body = Http.request("GET", BASE + "/quote?symbol=AAPL&token=" + enc(k.trim()), null, null, null);
        if (body == null) return "Finnhub didn't accept the key, or couldn't be reached. Check the key and your internet connection.";
        return body.contains("\"c\"") ? null : "Finnhub answered, but not as expected: " + (body.length() > 120 ? body.substring(0, 120) : body);
    }

    private static String call(String path) {
        String url = BASE + path + "&token=" + enc(key());
        Object[] hit = CACHE.get(url);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < KEEP_MS) return (String) hit[1];
        synchronized (FinnhubSource.class) {
            long wait = nextCall - System.currentTimeMillis();
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
            }
            nextCall = System.currentTimeMillis() + GAP_MS;
        }
        String body = Http.request("GET", url, null, null, null);
        if (body != null) CACHE.put(url, new Object[]{System.currentTimeMillis(), body});
        return body;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
