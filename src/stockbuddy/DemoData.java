package stockbuddy;

import java.time.LocalDate;
import java.util.*;

/**
 * PRACTICE MODE: made-up but realistic data so you can learn how the program works
 * without an internet connection. Nothing here is real market data!
 */
public final class DemoData {
    private DemoData() {}

    // ticker, name, yearly trend, volatility, P/E, analyst rating, target vs price, zen score, zen rating, fair value vs price
    private static final Object[][] STOCKS = {
            {"AAPL", "Apple", 0.18, 0.25, 31.0, "Buy", 1.08, 66, "B", 0.97},
            {"MSFT", "Microsoft", 0.25, 0.22, 34.0, "Strong Buy", 1.15, 74, "A", 1.05},
            {"NVDA", "NVIDIA", 0.55, 0.50, 48.0, "Strong Buy", 1.20, 71, "B", 0.92},
            {"AMZN", "Amazon", 0.22, 0.30, 38.0, "Strong Buy", 1.18, 69, "B", 1.10},
            {"GOOGL", "Alphabet", 0.20, 0.27, 23.0, "Buy", 1.14, 77, "A", 1.18},
            {"META", "Meta Platforms", 0.30, 0.35, 26.0, "Buy", 1.12, 72, "B", 1.08},
            {"TSLA", "Tesla", -0.30, 0.60, 95.0, "Hold", 0.85, 38, "D", 0.60},
            {"AMD", "Advanced Micro Devices", 0.05, 0.48, 42.0, "Buy", 1.20, 58, "C", 0.95},
            {"INTC", "Intel", -0.45, 0.45, 80.0, "Sell", 0.88, 29, "F", 0.75},
            {"KO", "Coca-Cola", 0.06, 0.14, 22.0, "Buy", 1.07, 63, "B", 1.02},
            {"JPM", "JPMorgan Chase", 0.20, 0.22, 12.5, "Buy", 1.09, 75, "A", 1.15},
            {"XOM", "Exxon Mobil", -0.05, 0.24, 13.0, "Hold", 1.03, 55, "C", 1.05},
            {"DIS", "Walt Disney", -0.25, 0.30, 40.0, "Hold", 0.96, 44, "D", 0.85},
            {"NFLX", "Netflix", 0.35, 0.33, 44.0, "Buy", 1.10, 68, "B", 0.90},
            {"PLTR", "Palantir", 0.60, 0.70, 210.0, "Hold", 0.80, 47, "C", 0.40},
            {"WMT", "Walmart", 0.15, 0.17, 36.0, "Strong Buy", 1.08, 70, "B", 0.95},
            {"PFE", "Pfizer", -0.35, 0.25, 9.5, "Hold", 1.05, 41, "D", 1.20},
            {"NKE", "Nike", -0.40, 0.32, 30.0, "Hold", 0.95, 35, "D", 0.80},
            {"SPY", "SPDR S&P 500 ETF", 0.14, 0.15, Double.NaN, null, Double.NaN, Double.NaN, null, Double.NaN},
            {"QQQ", "Invesco QQQ (Nasdaq 100)", 0.18, 0.20, Double.NaN, null, Double.NaN, Double.NaN, null, Double.NaN},
    };
    private static final double[] START = {190, 410, 120, 180, 165, 500, 250, 150, 30, 62, 200, 115, 110, 650, 60, 80, 28, 90, 590, 510};

    public static void fill(Analyzer a) {
        long seed = LocalDate.now().toEpochDay();
        for (int k = 0; k < STOCKS.length; k++) {
            Object[] r = STOCKS[k];
            Stock s = new Stock((String) r[0]);
            s.name = (String) r[1];
            Random rnd = new Random(seed * 31 + s.ticker.hashCode());
            double drift = (double) r[2], vol = (double) r[3];
            double p = START[k];
            for (int d = 0; d < 252; d++) {
                // the last 40 days follow the stock's trend more strongly so the chart has a clear story
                double mu = drift / 252 * (d > 210 ? 2.0 : 1.0);
                p *= Math.exp(mu + vol / Math.sqrt(252) * rnd.nextGaussian());
                s.closes.add(p);
                s.volumes.add(1e6 * (5 + rnd.nextDouble() * 5));
            }
            s.price = p;
            s.changePct = 100 * (p / s.closes.get(250) - 1);
            s.peRatio = (double) r[4];
            s.analystRating = (String) r[5];
            if (!Double.isNaN((double) r[6])) s.priceTarget = p * (double) r[6];
            s.zenScore = ((Number) r[7]).doubleValue();
            s.zenRating = (String) r[8];
            if (!Double.isNaN((double) r[9])) {
                s.fairValue = p * (double) r[9];
                s.roverQuality = 40 + rnd.nextInt(50);
                s.roverValue = (Double) r[9] > 1 ? 60 + rnd.nextInt(35) : 20 + rnd.nextInt(40);
                s.roverGrowth = drift > 0.1 ? 60 + rnd.nextInt(35) : 25 + rnd.nextInt(40);
            }
            a.stocks.put(s.ticker, s);
        }

        a.posts = new ArrayList<>(Arrays.asList(
                new RedditSource.Post("ValueInvesting", "GOOGL looks undervalued at this P/E - strong cash flow, buying more", "", 840),
                new RedditSource.Post("ValueInvesting", "JPM: cheap bank with strong growth, accumulating", "", 410),
                new RedditSource.Post("ValueInvesting", "Is PFE a value trap? Cheap but weak pipeline, sales drop", "", 300),
                new RedditSource.Post("options", "MSFT calls for next month - breakout above resistance", "bought calls", 520),
                new RedditSource.Post("options", "INTC puts printing, weak guidance, downgrade after downgrade", "", 380),
                new RedditSource.Post("wallstreetbets", "$TSLA puts, this bubble is going to crash", "overvalued", 2100),
                new RedditSource.Post("wallstreetbets", "NVDA to the moon, loading calls", "", 3500),
                new RedditSource.Post("wallstreetbets", "PLTR rocket keeps going, calls calls calls", "", 2900),
                new RedditSource.Post("wallstreetbets", "NKE bagholder support group, it keeps dropping", "", 1200),
                new RedditSource.Post("wallstreetbets", "SPY 0DTE calls, bullish into close", "", 1800)));
        RedditSource.score(a.posts, a.stocks);

        Random rnd = new Random(seed);
        for (WorldMarkets.Market m : a.markets) {
            m.price = 1000 + rnd.nextInt(40000);
            m.changePct = rnd.nextGaussian() * 0.8 + 0.1;
            m.monthPct = rnd.nextGaussian() * 3 + 0.8;
        }

        // intraday: SPY trending up, QQQ choppy
        for (String t : new String[]{"SPY", "QQQ"}) {
            Stock s = a.stocks.get(t);
            Random r = new Random(seed + t.hashCode());
            List<Double> c = new ArrayList<>(), v = new ArrayList<>();
            double p = s.price / (t.equals("SPY") ? 1.006 : 1.0);
            for (int i = 0; i < 48; i++) {
                p *= 1 + (t.equals("SPY") ? 0.00015 : 0) + 0.0007 * r.nextGaussian();
                c.add(p); v.add(1e5 * (1 + r.nextDouble()));
            }
            a.intraday.put(t, Arrays.asList(c, v));
        }

        a.sourceStatus.put("PRACTICE MODE", "All data below is MADE UP for learning - not real prices!");
        a.sourceStatus.put("World markets", "practice data");
        a.sourceStatus.put("stockanalysis.com", "practice data");
        a.sourceStatus.put("StockCharts-style technicals", "calculated from practice prices");
        a.sourceStatus.put("Stock Rover", "practice data");
        a.sourceStatus.put("WallStreetZen", "practice data");
        a.sourceStatus.put("Reddit", "practice posts");
    }
}
