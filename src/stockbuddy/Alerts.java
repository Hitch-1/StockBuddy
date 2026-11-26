package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Price alerts: "tell me when NVDA rises above 150", "...falls below 120", or "...moves 5% in a day".
 * Saved in data/alerts.csv and checked on every refresh and every few minutes while StockBuddy runs
 * (on Android also in the background). An alert fires once, then stays in the list as "triggered".
 */
public final class Alerts {
    public static final class Alert {
        public final String id, ticker, type;   // type: above / below / move
        public final double value;
        public String triggeredAt;               // null while waiting
        public double triggeredPrice = Double.NaN;

        Alert(String id, String ticker, String type, double value) { this.id = id; this.ticker = ticker; this.type = type; this.value = value; }

        public String describe() {
            switch (type) {
                case "above": return ticker + I18n.tr(" rises above ") + num(value);
                case "below": return ticker + I18n.tr(" falls below ") + num(value);
                default: return ticker + I18n.tr(" moves more than ") + num(value) + I18n.tr("% in a day (up or down)");
            }
        }

        boolean hit(double price, double changePct) {
            if (type.equals("above")) return price >= value;
            if (type.equals("below")) return price <= value;
            return !Double.isNaN(changePct) && Math.abs(changePct) >= value;
        }
    }

    private static final Pattern RULE = Pattern.compile(
            "^\\$?([A-Za-z0-9^][A-Za-z0-9.\\-^]{0,11})\\s+(above|over|>|below|under|<|move|moves|moving|change|changes|\\+/-)\\s*\\$?([0-9]+(?:[.,][0-9]+)?)\\s*%?$",
            Pattern.CASE_INSENSITIVE);

    private Alerts() {}

    static Path file() { return AppPaths.data("alerts.csv"); }

    public static synchronized List<Alert> load() {
        List<Alert> out = new ArrayList<>();
        try {
            if (!Files.exists(file())) return out;
            for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                if (l.startsWith("id,") || l.trim().isEmpty()) continue;
                String[] p = l.split(",", -1);
                if (p.length < 4) continue;
                Alert a = new Alert(p[0], p[1], p[2], Double.parseDouble(p[3]));
                if (p.length > 4 && !p[4].isEmpty()) a.triggeredAt = p[4];
                if (p.length > 5 && !p[5].isEmpty()) a.triggeredPrice = Double.parseDouble(p[5]);
                out.add(a);
            }
        } catch (Exception e) {
            Log.debug("Could not read alerts: " + e);
        }
        return out;
    }

    static synchronized void save(List<Alert> list) {
        StringBuilder b = new StringBuilder("id,ticker,type,value,triggeredAt,triggeredPrice\n");
        for (Alert a : list)
            b.append(a.id).append(',').append(a.ticker).append(',').append(a.type).append(',').append(a.value).append(',')
             .append(a.triggeredAt == null ? "" : a.triggeredAt).append(',').append(Double.isNaN(a.triggeredPrice) ? "" : String.valueOf(a.triggeredPrice)).append('\n');
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.debug("Could not save alerts: " + e);
        }
    }

    /**
     * Adds an alert written in plain words, e.g. "NVDA above 150", "TSLA below 200", "AAPL move 5%".
     * Returns null on success, or a friendly explanation of what to type.
     */
    public static String add(String text) {
        Matcher m = RULE.matcher(text == null ? "" : text.trim());
        if (!m.find()) return I18n.tr("Type it like: NVDA above 150   or   TSLA below 200   or   AAPL move 5%");
        String w = m.group(2).toLowerCase(Locale.US);
        String type = w.equals("above") || w.equals("over") || w.equals(">") ? "above" : w.equals("below") || w.equals("under") || w.equals("<") ? "below" : "move";
        double v = Double.parseDouble(m.group(3).replace(',', '.'));
        if (v <= 0) return I18n.tr("The number must be more than 0.");
        if (type.equals("move") && v > 50) return I18n.tr("For a daily move, use a percentage like 5 (meaning 5%).");
        List<Alert> list = load();
        list.add(new Alert(Long.toString(System.currentTimeMillis(), 36), m.group(1).toUpperCase(Locale.US), type, v));
        save(list);
        return null;
    }

    public static void remove(String id) {
        List<Alert> list = load();
        list.removeIf(a -> a.id.equals(id));
        save(list);
    }

    public static void clearTriggered() {
        List<Alert> list = load();
        list.removeIf(a -> a.triggeredAt != null);
        save(list);
    }

    public static boolean anyWaiting() {
        for (Alert a : load()) if (a.triggeredAt == null) return true;
        return false;
    }

    /** Tickers that still have a waiting alert. */
    public static List<String> waitingTickers() {
        List<String> out = new ArrayList<>();
        for (Alert a : load()) if (a.triggeredAt == null && !out.contains(a.ticker)) out.add(a.ticker);
        return out;
    }

    /**
     * Checks waiting alerts against prices {ticker -> {price, change % today}}.
     * Returns plain-English messages for the alerts that just fired (and marks them triggered).
     */
    public static List<String> check(Map<String, double[]> prices) {
        List<Alert> list = load();
        List<String> fired = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        for (Alert a : list) {
            if (a.triggeredAt != null) continue;
            double[] p = prices.get(a.ticker);
            if (p == null || Double.isNaN(p[0])) continue;
            if (a.hit(p[0], p[1])) {
                a.triggeredAt = now;
                a.triggeredPrice = p[0];
                fired.add(a.describe() + I18n.tr(" - now ") + num(p[0]) + (Double.isNaN(p[1]) ? "" : String.format(Locale.US, I18n.tr(" (%+.2f%% today)"), p[1])));
            }
        }
        if (!fired.isEmpty()) save(list);
        return fired;
    }

    /** Prices for alert checks: from today's analysis when available, otherwise a quick fresh download. */
    public static Map<String, double[]> prices(Analyzer a, boolean fresh) {
        Map<String, double[]> out = new java.util.HashMap<>();
        for (String t : waitingTickers()) {
            Stock s = a == null ? null : a.stocks.get(t);
            if (!fresh && s != null && !Double.isNaN(s.price)) { out.put(t, new double[]{s.price, s.changePct}); continue; }
            if (a != null && a.demo) { if (s != null) out.put(t, new double[]{s.price, s.changePct}); continue; }
            Stock f = new Stock(t);
            if (PriceSource.loadDailyFresh(f, "5d")) out.put(t, new double[]{f.price, f.changePct});
            else if (s != null) out.put(t, new double[]{s.price, s.changePct});
        }
        return out;
    }

    static String num(double v) { return String.format(Locale.US, v == Math.rint(v) ? "%,.0f" : "%,.2f", v); }
}
