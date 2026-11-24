package stockbuddy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The free "WallStreetBets sentiment" API (from public-apis: nbshare / Tradestie): the ~50 stocks
 * most discussed on r/wallstreetbets today, with comment counts and a Bullish/Bearish score.
 * No key needed. Several addresses are tried because the service has moved over time.
 */
public final class WsbSentiment {
    public static final class Row {
        public final String ticker, label;
        public final int comments;
        public final double score;
        Row(String ticker, int comments, String label, double score) {
            this.ticker = ticker; this.comments = comments; this.label = label; this.score = score;
        }
    }

    private static final String[] URLS = {
            "https://tradestie.com/api/v1/apps/reddit",
            "https://api.tradestie.com/v1/apps/reddit",
            "https://dashboard.nbshare.io/api/v1/apps/reddit",
            "https://api.nbshare.io/api/v1/apps/reddit"};

    public static String lastStatus = "Not loaded yet";

    private WsbSentiment() {}

    /** Ticker -> today's WallStreetBets sentiment, most discussed first. Empty if unreachable. */
    public static Map<String, Row> load() {
        for (String url : URLS) {
            String body = Http.get(url);
            if (body == null) continue;
            Map<String, Row> m = parse(body);
            if (!m.isEmpty()) {
                lastStatus = "OK - " + m.size() + " stocks discussed on r/wallstreetbets today";
                return m;
            }
        }
        lastStatus = "Could not connect to the WallStreetBets sentiment service";
        return new LinkedHashMap<>();
    }

    static Map<String, Row> parse(String body) {
        Map<String, Row> out = new LinkedHashMap<>();
        try {
            Object root = Json.parse(body);
            List<Object> rows = root instanceof List ? Json.list(root)
                    : !Json.list(Json.path(root, "data")).isEmpty() ? Json.list(Json.path(root, "data")) : Json.list(Json.path(root, "results"));
            for (Object r : rows) {
                Object t = Json.path(r, "ticker");
                if (t == null) t = Json.path(r, "symbol");
                if (!(t instanceof String) || ((String) t).trim().isEmpty()) continue;
                int comments = (int) Json.num(first(r, "no_of_comments", "comments", "mentions", "count"), 0);
                double score = Json.num(first(r, "sentiment_score", "score", "polarity"), Double.NaN);
                Object lab = first(r, "sentiment", "label");
                String label = lab instanceof String ? (String) lab : null;
                if (Double.isNaN(score) && label != null) score = label.toLowerCase().startsWith("bull") ? 0.3 : label.toLowerCase().startsWith("bear") ? -0.3 : 0;
                String tk = ((String) t).trim().toUpperCase().replace("$", "");
                out.put(tk, new Row(tk, comments, label, score));
            }
        } catch (Exception e) {
            Log.debug("WSB sentiment unreadable: " + e);
        }
        return out;
    }

    private static Object first(Object r, String... keys) {
        for (String k : keys) { Object v = Json.path(r, k); if (v != null) return v; }
        return null;
    }

    public static void apply(Map<String, Row> rows, Stock s) {
        Row r = rows.get(s.ticker);
        if (r == null) return;
        s.wsbComments = r.comments;
        s.wsbScore = r.score;
        s.wsbLabel = r.label;
    }
}
