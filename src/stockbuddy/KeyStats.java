package stockbuddy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Key statistics" like on Yahoo Finance (previous close, day's range, market cap, beta, EPS...).
 * Filled from the price history, stockanalysis.com and Finnhub. Shown on every stock's page.
 */
public final class KeyStats {
    /** The order stats are shown in (anything else is shown after these). */
    public static final List<String> ORDER = Arrays.asList(
            "Previous close", "Open", "Day's range", "52-week range", "Volume", "Avg. volume (3 months)",
            "Market cap", "Beta (5Y monthly)", "P/E ratio (TTM)", "Forward P/E", "EPS (TTM)", "Earnings date",
            "Dividend & yield", "Ex-dividend date", "1y target estimate", "Analyst rating",
            "Revenue (TTM)", "Net income (TTM)", "Shares outstanding",
            "Profit margin", "Return on equity", "Revenue growth (YoY)", "Debt / equity", "Current ratio");

    /** One-line explanations for beginners (shown as a tooltip / small text). */
    public static final String[][] HELP = {
            {"Previous close", "The price when the market closed last time."},
            {"Day's range", "Today's lowest and highest price."},
            {"52-week range", "The lowest and highest price in the past year."},
            {"Volume", "How many shares changed hands today."},
            {"Avg. volume (3 months)", "How many shares usually change hands in a day."},
            {"Market cap", "What the whole company is worth on the stock market."},
            {"Beta (5Y monthly)", "How much the stock swings compared with the whole market. 1 = same, 2 = twice as much."},
            {"P/E ratio (TTM)", "Price divided by the last 12 months' profit per share. Lower can mean cheaper."},
            {"Forward P/E", "Like P/E, but using next year's expected profit."},
            {"EPS (TTM)", "Profit per share over the last 12 months."},
            {"Earnings date", "When the company next reports its results - prices often jump that day."},
            {"Dividend & yield", "Cash paid to shareholders each year, and as a % of the price."},
            {"1y target estimate", "Where analysts think the price will be in a year."},
            {"Profit margin", "How many cents of every $1 of sales the company keeps as profit."},
            {"Return on equity", "How much profit the company makes with its owners' money."},
            {"Debt / equity", "How much the company borrowed compared with what it owns. Lower is safer."},
            {"Current ratio", "Can it pay its bills this year? Above 1 is usually fine."}};

    private KeyStats() {}

    public static String help(String label) {
        for (String[] h : HELP) if (h[0].equals(label)) return h[1];
        return null;
    }

    /** Stats we can work out from the daily prices ourselves. */
    public static void fromPrices(Stock s) {
        int n = s.closes.size();
        if (n < 2) return;
        Map<String, String> k = s.keyStats;
        k.put("Previous close", money(s.closes.get(n - 2)));
        if (s.hasCandles()) {
            k.put("Open", money(s.opens.get(n - 1)));
            k.put("Day's range", money(s.lows.get(n - 1)) + " - " + money(s.highs.get(n - 1)));
        }
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        List<Double> loSrc = s.hasCandles() ? s.lows : s.closes, hiSrc = s.hasCandles() ? s.highs : s.closes;
        for (int i = Math.max(0, n - 252); i < n; i++) { lo = Math.min(lo, loSrc.get(i)); hi = Math.max(hi, hiSrc.get(i)); }
        k.put("52-week range", money(lo) + " - " + money(hi));
        if (!s.volumes.isEmpty() && s.volumes.get(n - 1) > 0) {
            k.put("Volume", whole(s.volumes.get(n - 1)));
            double sum = 0; int c = 0;
            for (int i = Math.max(0, n - 63); i < n; i++) { sum += s.volumes.get(i); c++; }
            k.put("Avg. volume (3 months)", whole(sum / c));
        }
    }

    // stockanalysis.com overview labels -> our labels
    private static final String[][] SA = {
            {"Market Cap", "Market cap"}, {"Revenue (ttm)", "Revenue (TTM)"}, {"Net Income (ttm)", "Net income (TTM)"},
            {"Shares Out", "Shares outstanding"}, {"EPS (ttm)", "EPS (TTM)"}, {"PE Ratio", "P/E ratio (TTM)"},
            {"Forward PE", "Forward P/E"}, {"Ex-Dividend Date", "Ex-dividend date"}, {"Dividend", "Dividend & yield"},
            {"Volume", "Volume"}, {"Open", "Open"}, {"Previous Close", "Previous close"}, {"Day's Range", "Day's range"},
            {"52-Week Range", "52-week range"}, {"Beta", "Beta (5Y monthly)"}, {"Analysts", "Analyst rating"},
            {"Price Target", "1y target estimate"}, {"Earnings Date", "Earnings date"}};

    /** Reads the "label value" pairs from stockanalysis.com's overview text. */
    public static void fromStockAnalysis(Stock s, String text) {
        StringBuilder alt = new StringBuilder();
        for (String[] p : SA) alt.append(alt.length() == 0 ? "" : "|").append(Pattern.quote(p[0]));
        Matcher m = Pattern.compile("(?<![A-Za-z-])(" + alt + ")(?![A-Za-z])").matcher(text);
        List<int[]> hits = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int start = text.indexOf("Market Cap");
        if (start < 0) return;
        m.region(start, text.length());
        while (m.find() && hits.size() < 60) { hits.add(new int[]{m.start(), m.end()}); labels.add(m.group(1)); }
        for (int i = 0; i < hits.size(); i++) {
            int end = i + 1 < hits.size() ? hits.get(i + 1)[0] : Math.min(text.length(), hits.get(i)[1] + 40);
            String value = text.substring(hits.get(i)[1], end).trim();
            if (i + 1 == hits.size()) {   // the last one has no next label to stop at: keep just a date or a number
                Matcher v = Pattern.compile("^([A-Z][a-z]{2} \\d{1,2}, \\d{4}|[$]?[-+]?[\\d.,]+[%TBMK]?( \\([^)]*\\))?|[A-Z][a-z]+( [A-Z][a-z]+)?)").matcher(value);
                value = v.find() ? v.group(1) : "";
            }
            if (value.isEmpty() || value.length() > 40 || value.equalsIgnoreCase("n/a")) continue;
            String ours = null;
            for (String[] p : SA) if (p[0].equals(labels.get(i))) ours = p[1];
            if (ours != null && !s.keyStats.containsKey(ours + "#sa")) {
                s.keyStats.put(ours, value);
                s.keyStats.put(ours + "#sa", "");   // remember: first (overview) value wins
            }
        }
        s.keyStats.keySet().removeIf(x -> x.endsWith("#sa"));
    }

    /** Fills extra ratios from Finnhub's "basic financials" (only where we don't have a value yet). */
    public static void fromFinnhub(Stock s, Object metric) {
        putIfMissing(s, "Beta (5Y monthly)", Json.num(Json.path(metric, "beta"), Double.NaN), "%.2f");
        putIfMissing(s, "P/E ratio (TTM)", Json.num(Json.path(metric, "peTTM"), Double.NaN), "%.2f");
        putIfMissing(s, "EPS (TTM)", Json.num(Json.path(metric, "epsTTM"), Double.NaN), "%.2f");
        double cap = Json.num(Json.path(metric, "marketCapitalization"), Double.NaN);
        if (!Double.isNaN(cap) && !s.keyStats.containsKey("Market cap")) s.keyStats.put("Market cap", big(cap * 1e6));
        putIfMissing(s, "Profit margin", Json.num(Json.path(metric, "netProfitMarginTTM"), Double.NaN), "%.1f%%");
        putIfMissing(s, "Return on equity", Json.num(Json.path(metric, "roeTTM"), Double.NaN), "%.1f%%");
        putIfMissing(s, "Revenue growth (YoY)", Json.num(Json.path(metric, "revenueGrowthTTMYoy"), Double.NaN), "%+.1f%%");
        putIfMissing(s, "Debt / equity", Json.num(Json.path(metric, "totalDebt/totalEquityQuarterly"), Double.NaN), "%.2f");
        putIfMissing(s, "Current ratio", Json.num(Json.path(metric, "currentRatioQuarterly"), Double.NaN), "%.2f");
        double dy = Json.num(Json.path(metric, "dividendYieldIndicatedAnnual"), Double.NaN);
        if (!Double.isNaN(dy) && dy > 0 && !s.keyStats.containsKey("Dividend & yield"))
            s.keyStats.put("Dividend & yield", String.format(Locale.US, "%.2f%% a year", dy));
    }

    private static void putIfMissing(Stock s, String label, double v, String fmt) {
        if (!Double.isNaN(v) && !s.keyStats.containsKey(label)) s.keyStats.put(label, String.format(Locale.US, fmt, v));
    }

    /** Stats in display order. */
    public static List<String[]> ordered(Stock s) {
        List<String[]> out = new ArrayList<>();
        for (String l : ORDER) if (s.keyStats.containsKey(l)) out.add(new String[]{l, s.keyStats.get(l)});
        for (Map.Entry<String, String> e : s.keyStats.entrySet())
            if (!ORDER.contains(e.getKey())) out.add(new String[]{e.getKey(), e.getValue()});
        return out;
    }

    static String money(double v) { return String.format(Locale.US, "%,.2f", v); }

    static String whole(double v) { return String.format(Locale.US, "%,.0f", v); }

    public static String big(double v) {
        double a = Math.abs(v);
        if (a >= 1e12) return String.format(Locale.US, "%.2fT", v / 1e12);
        if (a >= 1e9) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.US, "%.2fM", v / 1e6);
        return whole(v);
    }
}
