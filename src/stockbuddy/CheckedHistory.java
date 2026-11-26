package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Every stock I checked": remembers each stock StockBuddy analysed or you looked up, with its last price and verdict,
 * for 30 days. After 30 days it's cleared automatically (and the All Stocks page says so).
 * Saved in data/checked.csv.
 */
public final class CheckedHistory {
    public static final int KEEP_DAYS = 30;
    public static volatile int lastCleared;   // how many old stocks the last clean-up removed

    public static final class Entry {
        public final String ticker, name, currency, date;
        public final double price, score, changePct;
        Entry(String ticker, String date, String name, double price, String currency, double score, double changePct) {
            this.ticker = ticker; this.date = date; this.name = name; this.price = price; this.currency = currency; this.score = score; this.changePct = changePct;
        }
        public LocalDate day() { try { return LocalDate.parse(date); } catch (Exception e) { return LocalDate.now(); } }
    }

    private CheckedHistory() {}

    static Path file() { return AppPaths.data("checked.csv"); }

    /** All remembered stocks (newest first), after removing anything older than 30 days. */
    public static synchronized List<Entry> load() {
        Map<String, Entry> m = read();
        LocalDate oldest = LocalDate.now().minusDays(KEEP_DAYS);
        int before = m.size();
        m.values().removeIf(e -> e.day().isBefore(oldest));
        if (m.size() < before) {
            lastCleared = before - m.size();
            write(m);
        }
        List<Entry> out = new ArrayList<>(m.values());
        out.sort((a, b) -> b.date.compareTo(a.date));
        return out;
    }

    /** Remembers (or refreshes) these stocks with today's date. */
    public static synchronized void record(Collection<Stock> stocks) {
        Map<String, Entry> m = read();
        String today = LocalDate.now().toString();
        for (Stock s : stocks) {
            if (Double.isNaN(s.price) || s.tech == null) continue;
            m.put(s.ticker, new Entry(s.ticker, today, s.name == null ? "" : s.name, s.price, s.currency, s.score, s.changePct));
        }
        write(m);
        load();   // also clears anything older than 30 days
    }

    /** The first day something will be cleared (oldest entry + 30 days), or null. */
    public static LocalDate nextClearing(List<Entry> list) {
        LocalDate oldest = null;
        for (Entry e : list) if (oldest == null || e.day().isBefore(oldest)) oldest = e.day();
        return oldest == null ? null : oldest.plusDays(KEEP_DAYS + 1L);
    }

    private static Map<String, Entry> read() {
        Map<String, Entry> m = new LinkedHashMap<>();
        try {
            if (!Files.exists(file())) return m;
            for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                if (l.startsWith("ticker,") || l.trim().isEmpty()) continue;
                List<String> c = Csv.split(l);
                if (c.size() < 7) continue;
                m.put(c.get(0), new Entry(c.get(0), c.get(1), c.get(2), num(c.get(3)), c.get(4), num(c.get(5)), num(c.get(6))));
            }
        } catch (Exception e) {
            Log.debug("Could not read checked.csv: " + e);
        }
        return m;
    }

    private static void write(Map<String, Entry> m) {
        StringBuilder b = new StringBuilder("ticker,date,name,price,currency,score,changePct\n");
        for (Entry e : m.values())
            b.append(e.ticker).append(',').append(e.date).append(",\"").append(e.name.replace('"', '\'')).append("\",").append(e.price).append(',')
             .append(e.currency).append(',').append(e.score).append(',').append(e.changePct).append('\n');
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.debug("Could not save checked.csv: " + e);
        }
    }

    private static double num(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return Double.NaN; } }
}
