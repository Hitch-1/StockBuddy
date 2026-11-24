package stockbuddy;

import java.util.List;

/**
 * How the price chart looks: line, candles or area; which period; which indicator lines;
 * and which extra panel underneath (volume, RSI or MACD). Remembered between runs.
 * Shared by the desktop chart and the phone chart.
 */
public final class ChartOptions {
    public static final String[] TYPES = {"line", "candles", "area"};
    public static final String[] TYPE_NAMES = {"Line", "Candles", "Area"};
    public static final int[] RANGES = {10, 21, 63, 126, 252};
    public static final String[] RANGE_NAMES = {"2W", "1M", "3M", "6M", "1Y"};
    public static final String[] OVERLAYS = {"sma20", "sma50", "sma200", "ema20", "bollinger"};
    public static final String[] OVERLAY_NAMES = {"SMA 20", "SMA 50", "SMA 200", "EMA 20", "Bollinger"};
    public static final String[] LOWERS = {"none", "volume", "rsi", "macd"};
    public static final String[] LOWER_NAMES = {"Nothing", "Volume", "RSI", "MACD"};

    public String type = "line";
    public int range = 252;
    public boolean sma20, sma50 = true, sma200 = true, ema20, bollinger;
    public String lower = "volume";

    public static ChartOptions load() {
        ChartOptions o = new ChartOptions();
        o.type = Settings.get("chart.type", o.type);
        o.range = Settings.getInt("chart.range", o.range);
        o.sma20 = Settings.getBool("chart.sma20", o.sma20);
        o.sma50 = Settings.getBool("chart.sma50", o.sma50);
        o.sma200 = Settings.getBool("chart.sma200", o.sma200);
        o.ema20 = Settings.getBool("chart.ema20", o.ema20);
        o.bollinger = Settings.getBool("chart.bollinger", o.bollinger);
        o.lower = Settings.get("chart.lower", o.lower);
        return o;
    }

    public void save() {
        Settings.set("chart.type", type);
        Settings.set("chart.range", range);
        Settings.set("chart.sma20", sma20);
        Settings.set("chart.sma50", sma50);
        Settings.set("chart.sma200", sma200);
        Settings.set("chart.ema20", ema20);
        Settings.set("chart.bollinger", bollinger);
        Settings.set("chart.lower", lower);
    }

    public boolean overlay(String name) {
        switch (name) {
            case "sma20": return sma20;
            case "sma50": return sma50;
            case "sma200": return sma200;
            case "ema20": return ema20;
            default: return bollinger;
        }
    }

    public void toggle(String name) {
        switch (name) {
            case "sma20": sma20 = !sma20; break;
            case "sma50": sma50 = !sma50; break;
            case "sma200": sma200 = !sma200; break;
            case "ema20": ema20 = !ema20; break;
            default: bollinger = !bollinger;
        }
    }

    /** Applies a phone link like "sb://chart/type/candles", ".../range/63", ".../toggle/sma50", ".../lower/rsi". */
    public static void applyLink(String url) {
        String[] p = url.replace("sb://chart/", "").split("/");
        if (p.length < 2) return;
        ChartOptions o = load();
        if (p[0].equals("type")) o.type = p[1];
        else if (p[0].equals("range")) { try { o.range = Integer.parseInt(p[1]); } catch (NumberFormatException ignored) {} }
        else if (p[0].equals("toggle")) o.toggle(p[1]);
        else if (p[0].equals("lower")) o.lower = p[1];
        o.save();
    }

    // ------------------------------------------------------------------ indicator lines (full length, NaN where not ready)
    public static double[] sma(List<Double> c, int p) {
        double[] out = new double[c.size()];
        double sum = 0;
        for (int i = 0; i < c.size(); i++) {
            sum += c.get(i);
            if (i >= p) sum -= c.get(i - p);
            out[i] = i >= p - 1 ? sum / p : Double.NaN;
        }
        return out;
    }

    public static double[] ema(List<Double> c, int p) {
        double[] out = new double[c.size()];
        double k = 2.0 / (p + 1), e = c.isEmpty() ? 0 : c.get(0);
        for (int i = 0; i < c.size(); i++) {
            e = i == 0 ? c.get(0) : c.get(i) * k + e * (1 - k);
            out[i] = i >= p - 1 ? e : Double.NaN;
        }
        return out;
    }

    /** {upper, lower} Bollinger Bands (20 days, 2 standard deviations). */
    public static double[][] bollinger(List<Double> c) {
        int p = 20;
        double[] mid = sma(c, p), up = new double[c.size()], lo = new double[c.size()];
        for (int i = 0; i < c.size(); i++) {
            if (Double.isNaN(mid[i])) { up[i] = lo[i] = Double.NaN; continue; }
            double v = 0;
            for (int j = i - p + 1; j <= i; j++) v += (c.get(j) - mid[i]) * (c.get(j) - mid[i]);
            double sd = Math.sqrt(v / p);
            up[i] = mid[i] + 2 * sd;
            lo[i] = mid[i] - 2 * sd;
        }
        return new double[][]{up, lo};
    }

    public static double[] rsi(List<Double> c, int p) {
        double[] out = new double[c.size()];
        java.util.Arrays.fill(out, Double.NaN);
        if (c.size() <= p) return out;
        double gain = 0, loss = 0;
        for (int i = 1; i <= p; i++) {
            double d = c.get(i) - c.get(i - 1);
            if (d > 0) gain += d; else loss -= d;
        }
        gain /= p; loss /= p;
        out[p] = loss == 0 ? 100 : 100 - 100 / (1 + gain / loss);
        for (int i = p + 1; i < c.size(); i++) {
            double d = c.get(i) - c.get(i - 1);
            gain = (gain * (p - 1) + Math.max(d, 0)) / p;
            loss = (loss * (p - 1) + Math.max(-d, 0)) / p;
            out[i] = loss == 0 ? 100 : 100 - 100 / (1 + gain / loss);
        }
        return out;
    }

    /** {macd line, signal line, histogram} with the usual 12/26/9 settings. */
    public static double[][] macd(List<Double> c) {
        int n = c.size();
        double[] line = new double[n], sig = new double[n], hist = new double[n];
        double k12 = 2.0 / 13, k26 = 2.0 / 27, k9 = 2.0 / 10, e12 = 0, e26 = 0, s = 0;
        for (int i = 0; i < n; i++) {
            double v = c.get(i);
            e12 = i == 0 ? v : v * k12 + e12 * (1 - k12);
            e26 = i == 0 ? v : v * k26 + e26 * (1 - k26);
            line[i] = e12 - e26;
            s = i == 0 ? line[i] : line[i] * k9 + s * (1 - k9);
            sig[i] = s;
            hist[i] = line[i] - s;
        }
        for (int i = 0; i < Math.min(n, 33); i++) line[i] = sig[i] = hist[i] = Double.NaN;  // not ready yet
        return new double[][]{line, sig, hist};
    }
}
