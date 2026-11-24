package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Track record: every day StockBuddy remembers its ideas (and the price at that moment), so you can see
 * later how they actually did. It's the honest way to learn whether to trust the signals - and it gets
 * more useful the longer you use the app. Saved in data/track-record.csv.
 */
public final class TrackRecord {
    public static final class Pick {
        public final LocalDate date;
        public final String kind, ticker, name, currency;
        public final double price;
        public double now = Double.NaN;

        Pick(LocalDate date, String kind, String ticker, String name, double price, String currency) {
            this.date = date; this.kind = kind; this.ticker = ticker; this.name = name; this.price = price; this.currency = currency;
        }

        /** % change since the idea (NaN if today's price is unknown). */
        public double change() { return Double.isNaN(now) || price <= 0 ? Double.NaN : 100 * (now / price - 1); }

        /** Did the idea go the right way? (buy/call = price up, sell/put = price down) */
        public Boolean worked() {
            double c = change();
            if (Double.isNaN(c)) return null;
            return kind.equals("BUY") || kind.equals("CALL") ? c > 0 : c < 0;
        }
    }

    public static final int SHOW_DAYS = 60;
    private static final Map<String, Double> extraPrices = new HashMap<>();

    private TrackRecord() {}

    static Path file() { return AppPaths.data("track-record.csv"); }

    /** Saves today's ideas (replacing anything saved earlier today). Not used in practice mode. */
    public static void save(Advisor.Plan p) {
        String today = LocalDate.now().toString();
        List<String> lines = new ArrayList<>();
        lines.add("date,kind,ticker,name,price,currency");
        try {
            if (Files.exists(file()))
                for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) if (!l.startsWith("date,") && !l.startsWith(today)) lines.add(l);
        } catch (Exception ignored) {}
        add(lines, today, "BUY", p.buys);
        add(lines, today, "SELL", p.sells);
        add(lines, today, "CALL", p.calls);
        add(lines, today, "PUT", p.puts);
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.debug("Could not save the track record: " + e);
        }
    }

    private static void add(List<String> lines, String today, String kind, List<Advisor.Idea> ideas) {
        for (Advisor.Idea i : ideas) {
            Stock s = i.stock;
            if (s == null || Double.isNaN(s.price)) continue;
            lines.add(today + "," + kind + "," + s.ticker + ",\"" + (s.name == null ? "" : s.name.replace('"', '\'')) + "\"," + s.price + "," + s.currency);
        }
    }

    public static List<Pick> load() {
        List<Pick> out = new ArrayList<>();
        try {
            if (!Files.exists(file())) return out;
            LocalDate oldest = LocalDate.now().minusDays(SHOW_DAYS);
            for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                if (l.startsWith("date,") || l.trim().isEmpty()) continue;
                List<String> c = Csv.split(l);
                if (c.size() < 6) continue;
                LocalDate d = LocalDate.parse(c.get(0));
                if (d.isBefore(oldest)) continue;
                out.add(new Pick(d, c.get(1), c.get(2), c.get(3), Double.parseDouble(c.get(4)), c.get(5)));
            }
        } catch (Exception e) {
            Log.debug("Could not read the track record: " + e);
        }
        return out;
    }

    /** Past tickers that today's analysis doesn't cover: fetch just their price (live mode). */
    public static void fetchMissingPrices(Analyzer a) {
        int n = 0;
        for (Pick p : load()) {
            if (a.stocks.containsKey(p.ticker) || extraPrices.containsKey(p.ticker) || n++ >= 30) continue;
            Stock s = new Stock(p.ticker);
            if (PriceSource.loadDaily(s)) extraPrices.put(p.ticker, s.price);
        }
    }

    /** The saved picks with today's prices filled in. */
    public static List<Pick> withPrices(Analyzer a) {
        List<Pick> picks = a.demo ? practice(a) : load();
        for (Pick p : picks) {
            Stock s = a.stocks.get(p.ticker);
            if (s != null && !Double.isNaN(s.price)) p.now = s.price;
            else if (extraPrices.containsKey(p.ticker)) p.now = extraPrices.get(p.ticker);
        }
        return picks;
    }

    /** kind -> {ideas with a result, how many went the right way, average % change} */
    public static Map<String, double[]> summary(List<Pick> picks) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (String k : new String[]{"BUY", "SELL", "CALL", "PUT"}) m.put(k, new double[3]);
        LocalDate today = LocalDate.now();
        for (Pick p : picks) {
            Boolean w = p.worked();
            if (w == null || !p.date.isBefore(today) || !m.containsKey(p.kind)) continue;
            double[] v = m.get(p.kind);
            v[0]++;
            if (w) v[1]++;
            v[2] += p.change();
        }
        for (double[] v : m.values()) if (v[0] > 0) v[2] /= v[0];
        return m;
    }

    public static long daysAgo(Pick p) { return ChronoUnit.DAYS.between(p.date, LocalDate.now()); }

    /** Practice mode: pretend StockBuddy made ideas over the last weeks, using the practice price history. */
    static List<Pick> practice(Analyzer a) {
        List<Pick> out = new ArrayList<>();
        Random r = new Random(LocalDate.now().toEpochDay());
        List<Stock> list = new ArrayList<>(a.stocks.values());
        for (int week = 1; week <= 6; week++) {
            for (int k = 0; k < 4; k++) {
                Stock s = list.get(r.nextInt(list.size()));
                int back = week * 5;
                if (s.closes.size() <= back) continue;
                double then = s.closes.get(s.closes.size() - 1 - back);
                double trend = then / s.closes.get(Math.max(0, s.closes.size() - 1 - back - 20)) - 1;   // what a chart signal would have said then
                out.add(new Pick(LocalDate.now().minusDays(week * 7L), trend >= 0 ? "BUY" : "SELL", s.ticker, s.name, then, s.currency));
            }
        }
        return out;
    }
}
