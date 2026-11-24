package stockbuddy;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Any CSV as a data source": drop CSV files into the data/csv folder (or use "Add any CSV").
 * The only requirement is a column called Ticker or Symbol. Every column is shown on the stock's page,
 * and these columns are understood and used in the score when present:
 *   score / rating / grade / signal  (0-100, 0-10, 1-5, -1..+1, A-F, or words like "Buy")
 *   recommendation / action / sentiment  (Strong Buy, Buy, Hold, Sell, Bullish, Bearish...)
 *   target / price target / fair value / intrinsic value  (compared with today's price)
 */
public final class CustomCsv {
    private static Map<String, Map<String, Map<String, String>>> files;   // file name -> ticker -> row

    private CustomCsv() {}

    public static Path folder() { return AppPaths.data("csv"); }

    public static synchronized void reload() { files = null; }

    public static synchronized Map<String, Map<String, Map<String, String>>> all() {
        if (files != null) return files;
        files = new LinkedHashMap<>();
        Path dir = folder();
        if (!Files.isDirectory(dir)) return files;
        List<Path> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.csv")) { for (Path p : ds) list.add(p); }
        catch (IOException ignored) {}
        java.util.Collections.sort(list);
        for (Path p : list) {
            Map<String, Map<String, String>> rows = Csv.readByTicker(p);
            if (!rows.isEmpty()) files.put(p.getFileName().toString().replaceAll("(?i)\\.csv$", ""), rows);
        }
        return files;
    }

    /** All tickers mentioned in your CSV files. */
    public static Set<String> tickers() {
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, Map<String, String>> f : all().values()) out.addAll(f.keySet());
        return out;
    }

    public static String status() {
        Map<String, Map<String, Map<String, String>>> f = all();
        if (f.isEmpty()) return "None yet - you can add any CSV with a Ticker or Symbol column (optional)";
        int rows = 0;
        for (Map<String, Map<String, String>> x : f.values()) rows += x.size();
        return "OK - " + f.size() + " file" + (f.size() == 1 ? "" : "s") + " (" + String.join(", ", f.keySet()) + "), " + rows + " rows";
    }

    public static void apply(Stock s) {
        for (Map.Entry<String, Map<String, Map<String, String>>> f : all().entrySet()) {
            Map<String, String> row = f.getValue().get(s.ticker);
            if (row != null) s.customData.put(f.getKey(), row);
        }
    }

    /** Copies a CSV into the folder. Returns how many tickers it has (0 = not usable, not copied). */
    public static int add(Path source, String fileName) throws IOException {
        Map<String, Map<String, String>> rows = Csv.readByTicker(source);
        if (rows.isEmpty()) return 0;
        Files.createDirectories(folder());
        String name = fileName.replaceAll("[^A-Za-z0-9 ._()-]", "_");
        if (!name.toLowerCase().endsWith(".csv")) name += ".csv";
        Files.copy(source, folder().resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        reload();
        return rows.size();
    }

    public static void removeAll() throws IOException {
        Path dir = folder();
        if (Files.isDirectory(dir)) try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.csv")) { for (Path p : ds) Files.delete(p); }
        reload();
    }

    /** Turns the understood columns of one file's row into a score (-1..+1) and reason, or null. */
    public static Object[] score(Stock s, Map<String, String> row) {
        double total = 0;
        int n = 0;
        List<String> why = new ArrayList<>();
        for (Map.Entry<String, String> e : row.entrySet()) {
            String col = e.getKey(), val = e.getValue() == null ? "" : e.getValue().trim();
            if (val.isEmpty()) continue;
            Double sc = null;
            if (col.matches(".*(score|rating|grade|signal|rank).*")) sc = scaled(val);
            else if (col.matches(".*(recommend|action|sentiment|consensus|view).*")) sc = words(val);
            else if (col.matches(".*(target|fair value|intrinsic).*") && s.price > 0) {
                double v = number(val);
                if (!Double.isNaN(v) && v > 0) sc = Math.max(-1, Math.min(1, (v / s.price - 1) / 0.3));
            }
            if (sc != null) { total += sc; n++; why.add(col + " " + val); }
        }
        if (n == 0) return null;
        return new Object[]{total / n, "Your file says: " + String.join(", ", why) + "."};
    }

    private static Double scaled(String v) {
        Double w = words(v);
        if (w != null) return w;
        if (v.matches("[A-Fa-f][+-]?")) return new double[]{0.8, 0.4, 0, -0.4, -0.6, -0.8}[Character.toUpperCase(v.charAt(0)) - 'A'];
        double d = number(v);
        if (Double.isNaN(d)) return null;
        if (d >= -1 && d <= 1) return d;
        if (d >= 1 && d <= 5 && v.matches("[1-5](\\.\\d+)?")) return (d - 3) / 2;
        if (d >= 0 && d <= 10) return (d - 5) / 5;
        if (d >= 0 && d <= 100) return (d - 50) / 50;
        return null;
    }

    private static Double words(String v) {
        String t = v.toLowerCase();
        if (t.contains("strong buy") || t.contains("very bullish")) return 0.9;
        if (t.contains("strong sell") || t.contains("very bearish")) return -0.9;
        if (t.contains("buy") || t.contains("bull") || t.contains("outperform") || t.contains("overweight") || t.equals("positive")) return 0.5;
        if (t.contains("sell") || t.contains("bear") || t.contains("underperform") || t.contains("underweight") || t.equals("negative")) return -0.5;
        if (t.contains("hold") || t.contains("neutral")) return 0.0;
        return null;
    }

    private static double number(String v) {
        try { return Double.parseDouble(v.replaceAll("[$,%\\s]", "")); } catch (Exception e) { return Double.NaN; }
    }
}
