package stockbuddy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Price history for stocks and world market indexes.
 * The sites you chose (stockanalysis.com, StockCharts, etc.) don't offer free price-history
 * downloads, so we use Yahoo Finance's public chart data, with Stooq as a backup.
 */
public final class PriceSource {
    private PriceSource() {}

    /** Fills price, change and 1 year of daily history. Returns true on success. */
    public static boolean loadDaily(Stock s) {
        boolean ok = loadYahoo(s, "1y", "1d", false) || loadStooq(s);
        if (ok) KeyStats.fromPrices(s);
        return ok;
    }

    /** Fresh (never cached) daily data - used for the live index prices. */
    public static boolean loadDailyFresh(Stock s, String range) {
        return loadYahoo(s, range, "1d", true);
    }

    /** Intraday (5-minute) prices for today, used for the 0DTE idea. Returns [closes, volumes] or null. */
    public static List<List<Double>> loadIntraday(String ticker) {
        Stock tmp = new Stock(ticker);
        if (!loadYahoo(tmp, "1d", "5m", true) || tmp.closes.size() < 6) return null;
        return java.util.Arrays.asList(tmp.closes, tmp.volumes);
    }

    private static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static boolean loadYahoo(Stock s, String range, String interval, boolean fresh) {
        String sym = encode(s.ticker);
        for (String host : new String[]{"query1", "query2"}) {
            String url = "https://" + host + ".finance.yahoo.com/v8/finance/chart/" + sym
                    + "?range=" + range + "&interval=" + interval + "&includePrePost=false";
            String body = fresh ? Http.getFresh(url) : Http.get(url);
            if (body == null) continue;
            try {
                Object root = Json.parse(body);
                Object res = Json.path(root, "chart", "result", 0);
                if (res == null) continue;
                Object meta = Json.path(res, "meta");
                List<Object> closes = Json.list(Json.path(res, "indicators", "quote", 0, "close"));
                List<Object> vols = Json.list(Json.path(res, "indicators", "quote", 0, "volume"));
                List<Object> op = Json.list(Json.path(res, "indicators", "quote", 0, "open"));
                List<Object> hi = Json.list(Json.path(res, "indicators", "quote", 0, "high"));
                List<Object> lo = Json.list(Json.path(res, "indicators", "quote", 0, "low"));
                s.closes.clear(); s.volumes.clear(); s.opens.clear(); s.highs.clear(); s.lows.clear();
                for (int i = 0; i < closes.size(); i++) {
                    Object c = closes.get(i);
                    if (c instanceof Number) {
                        double cv = ((Number) c).doubleValue();
                        s.closes.add(cv);
                        s.volumes.add(i < vols.size() ? Json.num(vols.get(i), 0) : 0);
                        s.opens.add(i < op.size() ? Json.num(op.get(i), cv) : cv);
                        s.highs.add(i < hi.size() ? Json.num(hi.get(i), cv) : cv);
                        s.lows.add(i < lo.size() ? Json.num(lo.get(i), cv) : cv);
                    }
                }
                if (s.closes.isEmpty()) continue;
                s.price = Json.num(Json.path(meta, "regularMarketPrice"), s.closes.get(s.closes.size() - 1));
                if (meta instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) meta;
                    Object nm = m.get("longName") != null ? m.get("longName") : m.get("shortName");
                    if (nm instanceof String && s.name == null) s.name = (String) nm;
                    if (m.get("currency") instanceof String) s.currency = (String) m.get("currency");
                    Object ex = m.get("fullExchangeName") != null ? m.get("fullExchangeName") : m.get("exchangeName");
                    if (ex instanceof String) s.exchange = (String) ex;
                }
                double prev = "1d".equals(range)
                        ? Json.num(Json.path(meta, "chartPreviousClose"), Double.NaN)
                        : (s.closes.size() > 1 ? s.closes.get(s.closes.size() - 2) : Double.NaN);
                s.changePct = 100 * (s.price - prev) / prev;
                return true;
            } catch (Exception e) {
                Log.debug("Yahoo data for " + s.ticker + " unreadable: " + e);
            }
        }
        return false;
    }

    private static boolean loadStooq(Stock s) {
        if (s.ticker.startsWith("^")) return false;
        String body = Http.get("https://stooq.com/q/d/l/?s=" + s.ticker.toLowerCase().replace('.', '-') + ".us&i=d");
        if (body == null || !body.startsWith("Date")) return false;
        String[] lines = body.split("\n");
        List<Double> c = new ArrayList<>(), v = new ArrayList<>(), o = new ArrayList<>(), h = new ArrayList<>(), l = new ArrayList<>();
        for (int i = Math.max(1, lines.length - 260); i < lines.length; i++) {
            String[] p = lines[i].trim().split(",");
            if (p.length < 6) continue;
            try {
                double oo = Double.parseDouble(p[1]), hh = Double.parseDouble(p[2]), ll = Double.parseDouble(p[3]);
                double cc = Double.parseDouble(p[4]), vv = Double.parseDouble(p[5]);
                o.add(oo); h.add(hh); l.add(ll); c.add(cc); v.add(vv);
            } catch (NumberFormatException ignored) {}
        }
        if (c.size() < 2) return false;
        s.closes = c; s.volumes = v; s.opens = o; s.highs = h; s.lows = l;
        s.price = c.get(c.size() - 1);
        s.changePct = 100 * (s.price - c.get(c.size() - 2)) / c.get(c.size() - 2);
        return true;
    }
}
