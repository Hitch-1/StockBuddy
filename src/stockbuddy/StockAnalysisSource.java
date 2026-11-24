package stockbuddy;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * stockanalysis.com: today's biggest movers (to find interesting stocks) and each stock's
 * key numbers: P/E ratio, analyst rating and analyst price target.
 */
public final class StockAnalysisSource {
    private static final String BASE = "https://stockanalysis.com";
    private static final Pattern LINK = Pattern.compile("href=\"/stocks/([a-z0-9.\\-]{1,6})/\"");

    private StockAnalysisSource() {}

    /** Tickers from the gainers / losers / most-active lists. */
    public static Set<String> todaysMovers(int maxPerList) {
        Set<String> out = new LinkedHashSet<>();
        for (String page : new String[]{"/markets/gainers/", "/markets/losers/", "/markets/active/"}) {
            String html = Http.get(BASE + page);
            if (html == null) continue;
            Matcher m = LINK.matcher(html);
            int count = 0;
            while (m.find() && count < maxPerList) {
                if (out.add(m.group(1).toUpperCase())) count++;
            }
        }
        return out;
    }

    /** Fills P/E, forward P/E, analyst rating and price target. Returns true if anything was found. */
    public static boolean loadFundamentals(Stock s) {
        if (s.ticker.startsWith("^")) return false;
        String path = s.ticker.equals("SPY") || s.ticker.equals("QQQ") ? "/etf/" : "/stocks/";
        String html = Http.get(BASE + path + s.ticker.toLowerCase() + "/");
        if (html == null) return false;
        String text = Http.htmlToText(html);
        KeyStats.fromStockAnalysis(s, text);
        boolean found = false;

        double pe = number(text, "PE Ratio\\s+([0-9,.]+)");
        if (!Double.isNaN(pe)) { s.peRatio = pe; found = true; }
        double fpe = number(text, "Forward PE\\s+([0-9,.]+)");
        if (!Double.isNaN(fpe)) s.forwardPe = fpe;

        Matcher r = Pattern.compile("Analysts?(?: Rating| Consensus)?\\s+(Strong Buy|Buy|Hold|Sell|Strong Sell)",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (r.find()) { s.analystRating = capitalise(r.group(1)); found = true; }

        double pt = number(text, "Price Target\\s+\\$?([0-9,.]+)");
        if (!Double.isNaN(pt)) { s.priceTarget = pt; found = true; }

        Matcher mc = Pattern.compile("Market Cap\\s+([0-9.,]+)\\s*([TBMK])").matcher(text);
        if (mc.find()) {
            double v = Double.parseDouble(mc.group(1).replace(",", ""));
            String u = mc.group(2);
            s.marketCap = v * (u.equals("T") ? 1e12 : u.equals("B") ? 1e9 : u.equals("M") ? 1e6 : 1e3);
        }
        if (s.name == null) {
            Matcher t = Pattern.compile("<title>([^<(]+)\\(").matcher(html);
            if (t.find()) s.name = t.group(1).trim();
        }
        return found;
    }

    static double number(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return Double.NaN;
        try { return Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static String capitalise(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase().split(" ")) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }
}
