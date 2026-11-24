package stockbuddy;

import java.util.List;

/**
 * Chart-based ("technical") indicators, the same ones StockCharts.com is known for:
 * moving averages, RSI, MACD and the SCTR ranking (StockCharts Technical Rank).
 * StockCharts has no public data API, so we calculate them ourselves from the price history
 * using StockCharts' publicly documented formulas.
 */
public class Technicals {
    public double sma20, sma50, sma200;
    public double ema50, ema200;
    public double rsi14;
    public double macd, macdSignal, macdHist, macdHistPrev;
    public double roc20, roc125;
    public double ppoHistSlope;
    public double sctrRaw;       // raw SCTR points
    public double sctr = Double.NaN; // 0..99 rank versus the other stocks we analysed
    public double high52, low52;
    public double volatility;    // yearly volatility, e.g. 0.30 = 30%
    public double volumeRatio;   // today's volume divided by the 20-day average

    public static Technicals compute(List<Double> c, List<Double> v) {
        Technicals t = new Technicals();
        int n = c.size();
        double last = c.get(n - 1);
        t.sma20 = sma(c, 20);
        t.sma50 = sma(c, 50);
        t.sma200 = sma(c, Math.min(200, n));
        t.ema50 = ema(c, 50, n);
        t.ema200 = ema(c, Math.min(200, n), n);
        t.rsi14 = rsi(c, 14);

        double[] macdLine = new double[n];
        for (int i = 0; i < n; i++) macdLine[i] = ema(c, 12, i + 1) - ema(c, 26, i + 1);
        double[] sig = emaArr(macdLine, 9);
        t.macd = macdLine[n - 1];
        t.macdSignal = sig[n - 1];
        t.macdHist = t.macd - t.macdSignal;
        t.macdHistPrev = macdLine[n - 2] - sig[n - 2];

        // PPO = MACD expressed in percent, its histogram slope over 3 days
        double[] ppo = new double[n];
        for (int i = 0; i < n; i++) {
            double e26 = ema(c, 26, i + 1);
            ppo[i] = e26 == 0 ? 0 : 100 * macdLine[i] / e26;
        }
        double[] ppoSig = emaArr(ppo, 9);
        double h0 = ppo[n - 1] - ppoSig[n - 1];
        double h3 = ppo[Math.max(0, n - 4)] - ppoSig[Math.max(0, n - 4)];
        t.ppoHistSlope = (h0 - h3) / 3.0;

        t.roc20 = roc(c, 20);
        t.roc125 = roc(c, Math.min(125, n - 1));

        // StockCharts Technical Rank (SCTR) weights: long term 60%, medium 30%, short 10%
        double pct200 = 100 * (last - t.ema200) / t.ema200;
        double pct50 = 100 * (last - t.ema50) / t.ema50;
        double slopeScore = t.ppoHistSlope < -1 ? 0 : t.ppoHistSlope > 1 ? 5 : (t.ppoHistSlope + 1) * 50 * 0.05;
        t.sctrRaw = 0.30 * pct200 + 0.30 * t.roc125 + 0.15 * pct50 + 0.15 * t.roc20 + slopeScore + 0.05 * t.rsi14;

        int from = Math.max(0, n - 252);
        t.high52 = c.subList(from, n).stream().mapToDouble(d -> d).max().orElse(last);
        t.low52 = c.subList(from, n).stream().mapToDouble(d -> d).min().orElse(last);

        // volatility from the last 60 daily returns
        int m = Math.min(60, n - 1);
        double mean = 0; double[] r = new double[m];
        for (int i = 0; i < m; i++) { r[i] = Math.log(c.get(n - 1 - i) / c.get(n - 2 - i)); mean += r[i]; }
        mean /= m;
        double var = 0; for (double x : r) var += (x - mean) * (x - mean);
        t.volatility = Math.sqrt(var / (m - 1)) * Math.sqrt(252);

        if (v != null && v.size() >= 21) {
            double avg = 0; for (int i = v.size() - 21; i < v.size() - 1; i++) avg += v.get(i);
            avg /= 20;
            t.volumeRatio = avg > 0 ? v.get(v.size() - 1) / avg : 1;
        } else t.volumeRatio = 1;
        return t;
    }

    public boolean uptrend(double price) { return price > sma50 && sma50 > sma200; }
    public boolean downtrend(double price) { return price < sma50 && sma50 < sma200; }

    static double sma(List<Double> c, int p) {
        int n = c.size(); p = Math.min(p, n);
        double s = 0; for (int i = n - p; i < n; i++) s += c.get(i);
        return s / p;
    }

    /** EMA of the first `upto` values. */
    static double ema(List<Double> c, int p, int upto) {
        double k = 2.0 / (p + 1);
        double e = c.get(0);
        for (int i = 1; i < upto; i++) e = c.get(i) * k + e * (1 - k);
        return e;
    }

    static double[] emaArr(double[] x, int p) {
        double k = 2.0 / (p + 1);
        double[] out = new double[x.length];
        out[0] = x[0];
        for (int i = 1; i < x.length; i++) out[i] = x[i] * k + out[i - 1] * (1 - k);
        return out;
    }

    static double rsi(List<Double> c, int p) {
        int n = c.size();
        double gain = 0, loss = 0;
        for (int i = 1; i <= p; i++) {
            double d = c.get(i) - c.get(i - 1);
            if (d > 0) gain += d; else loss -= d;
        }
        gain /= p; loss /= p;
        for (int i = p + 1; i < n; i++) {
            double d = c.get(i) - c.get(i - 1);
            gain = (gain * (p - 1) + Math.max(d, 0)) / p;
            loss = (loss * (p - 1) + Math.max(-d, 0)) / p;
        }
        if (loss == 0) return 100;
        return 100 - 100 / (1 + gain / loss);
    }

    static double roc(List<Double> c, int p) {
        int n = c.size();
        double old = c.get(n - 1 - p);
        return 100 * (c.get(n - 1) - old) / old;
    }
}
