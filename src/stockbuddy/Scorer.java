package stockbuddy;

import java.util.Collection;
import java.util.List;

/** Turns the raw numbers from each website into simple "good / bad" signals with plain-English reasons. */
public final class Scorer {
    // How much each source counts in the final score (they're re-balanced if a source is missing).
    public static final double W_TECH = 0.30, W_FUND = 0.25, W_ROVER = 0.15, W_ZEN = 0.15, W_REDDIT = 0.15, W_FINNHUB = 0.15,
            W_WSB = 0.10, W_SEC = 0.10, W_NEWS = 0.10, W_CSV = 0.10;

    private Scorer() {}

    /** StockCharts ranks each stock against the others; we do the same with the stocks we analysed. */
    public static void rankSctr(Collection<Stock> stocks) {
        List<Stock> withTech = stocks.stream().filter(s -> s.tech != null).sorted((a, b) -> Double.compare(a.tech.sctrRaw, b.tech.sctrRaw)).collect(java.util.stream.Collectors.toList());
        int n = withTech.size();
        for (int i = 0; i < n; i++) withTech.get(i).tech.sctr = n == 1 ? 50 : 99.0 * i / (n - 1);
    }

    public static void score(Stock s) {
        s.signals.clear();
        double total = 0, weights = 0;
        int sources = 0;

        // ---------- 1. StockCharts-style technicals (what the price chart says) ----------
        if (s.tech != null) {
            Technicals t = s.tech;
            double sc = 0;
            StringBuilder why = new StringBuilder();
            if (t.uptrend(s.price)) { sc += 0.4; why.append("The price is in an uptrend (above its 50-day and 200-day average prices). "); }
            else if (t.downtrend(s.price)) { sc -= 0.4; why.append("The price is in a downtrend (below its 50-day and 200-day average prices). "); }
            else why.append("The price has no clear direction right now. ");
            if (t.rsi14 > 70) { sc -= 0.2; why.append(String.format("RSI is %.0f, meaning it rose a lot recently and may be due for a pause. ", t.rsi14)); }
            else if (t.rsi14 < 30) { sc += 0.15; why.append(String.format("RSI is %.0f, meaning it fell a lot recently and could bounce back. ", t.rsi14)); }
            if (t.macdHist > 0 && t.macdHistPrev <= 0) { sc += 0.25; why.append("MACD just turned positive - momentum is shifting upward. "); }
            else if (t.macdHist < 0 && t.macdHistPrev >= 0) { sc -= 0.25; why.append("MACD just turned negative - momentum is shifting downward. "); }
            else if (t.macdHist > 0) sc += 0.1; else sc -= 0.1;
            if (!Double.isNaN(t.sctr)) {
                sc += (t.sctr - 50) / 50 * 0.35;
                why.append(String.format("Its StockCharts Technical Rank is %.0f/99 (higher = stronger than the other stocks we checked).", t.sctr));
            }
            add(s, "StockCharts-style chart analysis", sc, why.toString().trim());
            total += W_TECH * clamp(sc); weights += W_TECH; sources++;
        }

        // ---------- 2. stockanalysis.com fundamentals ----------
        boolean hasFund = !Double.isNaN(s.peRatio) || s.analystRating != null || !Double.isNaN(s.priceTarget);
        if (hasFund) {
            double sc = 0; StringBuilder why = new StringBuilder();
            if (s.analystRating != null) {
                String ar = s.analystRating;
                double r = ar.equals("Strong Buy") ? 0.5 : ar.equals("Buy") ? 0.3 : ar.equals("Hold") ? 0 : ar.equals("Sell") ? -0.3 : -0.5;
                sc += r; why.append("Wall Street analysts rate it \"").append(s.analystRating).append("\". ");
            }
            if (!Double.isNaN(s.priceTarget) && s.price > 0) {
                double up = 100 * (s.priceTarget / s.price - 1);
                sc += clamp(up / 40) * 0.4;
                why.append(String.format("Their average price target is $%.2f, which is %+.0f%% from today's price. ", s.priceTarget, up));
            }
            if (!Double.isNaN(s.peRatio)) {
                if (s.peRatio < 15) { sc += 0.15; why.append(String.format("P/E is %.1f - fairly cheap for what the company earns. ", s.peRatio)); }
                else if (s.peRatio > 45) { sc -= 0.15; why.append(String.format("P/E is %.1f - investors are paying a lot for each $1 of profit. ", s.peRatio)); }
                else why.append(String.format("P/E is %.1f - a normal price for its profits. ", s.peRatio));
            }
            add(s, "stockanalysis.com (company numbers & analysts)", sc, why.toString().trim());
            total += W_FUND * clamp(sc); weights += W_FUND; sources++;
        }

        // ---------- 3. Stock Rover ----------
        if (!Double.isNaN(s.fairValue) || !Double.isNaN(s.roverQuality)) {
            double sc = 0; StringBuilder why = new StringBuilder();
            if (!Double.isNaN(s.fairValue) && s.price > 0) {
                double gap = 100 * (s.fairValue / s.price - 1);
                sc += clamp(gap / 30) * 0.6;
                why.append(String.format("Stock Rover's fair value is $%.2f, so the stock looks %s by about %.0f%%. ",
                        s.fairValue, gap >= 0 ? "underpriced (on sale)" : "overpriced", Math.abs(gap)));
            }
            double q = avg(s.roverQuality, s.roverValue, s.roverGrowth);
            if (!Double.isNaN(q)) { sc += (q - 50) / 50 * 0.4; why.append(String.format("Its average quality/value/growth score is %.0f out of 100.", q)); }
            add(s, "Stock Rover (fair value & scores)", sc, why.toString().trim());
            total += W_ROVER * clamp(sc); weights += W_ROVER; sources++;
        }

        // ---------- 4. WallStreetZen ----------
        if (!Double.isNaN(s.zenScore) || s.zenRating != null) {
            double sc = 0; StringBuilder why = new StringBuilder();
            if (!Double.isNaN(s.zenScore)) { sc += (s.zenScore -50) / 50 * 0.7; why.append(String.format("Zen Score is %.0f/100. ", s.zenScore)); }
            if (s.zenRating != null) {
                String z = s.zenRating;
                sc += z.equals("A") ? 0.4 : z.equals("B") ? 0.2 : z.equals("C") ? 0 : z.equals("D") ? -0.2 : -0.4;
                why.append("Zen Rating is ").append(s.zenRating).append(" (A is best, F is worst).");
            }
            add(s, "WallStreetZen", sc, why.toString().trim());
            total += W_ZEN * clamp(sc); weights += W_ZEN; sources++;
        }

        // ---------- 5. Finnhub analyst recommendations ----------
        int fhTotal = s.fhStrongBuy < 0 ? 0 : s.fhStrongBuy + s.fhBuy + s.fhHold + s.fhSell + s.fhStrongSell;
        if (fhTotal > 0) {
            double sc = (2.0 * s.fhStrongBuy + s.fhBuy - s.fhSell - 2.0 * s.fhStrongSell) / (2.0 * fhTotal);
            String why = String.format("Of %d analysts, %d say strong buy, %d buy, %d hold, %d sell and %d strong sell%s.",
                    fhTotal, s.fhStrongBuy, s.fhBuy, s.fhHold, s.fhSell, s.fhStrongSell, s.fhPeriod == null ? "" : " (" + s.fhPeriod + ")");
            add(s, "Finnhub (analyst recommendations)", sc, why);
            total += W_FINNHUB * clamp(sc); weights += W_FINNHUB; sources++;
        }

        // ---------- 6. WallStreetBets sentiment API ----------
        if (s.wsbComments > 0 && !Double.isNaN(s.wsbScore)) {
            double sc = Math.max(-1, Math.min(1, s.wsbScore * 2)) * 0.8;
            String mood = s.wsbLabel != null ? s.wsbLabel.toLowerCase() : s.wsbScore > 0.05 ? "bullish" : s.wsbScore < -0.05 ? "bearish" : "mixed";
            String why = String.format("%d comments on r/wallstreetbets today; the overall mood is %s (score %+.2f).", s.wsbComments, mood, s.wsbScore);
            if (s.wsbComments > 300) why += " It's one of the most-talked-about stocks there - expect big swings.";
            add(s, "WallStreetBets sentiment (WSB API)", sc, why);
            total += W_WSB * clamp(sc); weights += W_WSB; sources++;
        }

        // ---------- 7. SEC EDGAR official filings ----------
        if (!Double.isNaN(s.secRevenueGrowth) || !Double.isNaN(s.secNetIncome)) {
            double sc = 0;
            StringBuilder why = new StringBuilder();
            if (!Double.isNaN(s.secRevenueGrowth)) {
                sc += clamp(s.secRevenueGrowth / 25) * 0.6;
                why.append(String.format("In its official yearly report, sales %s %.0f%%. ", s.secRevenueGrowth >= 0 ? "grew" : "shrank", Math.abs(s.secRevenueGrowth)));
            }
            if (!Double.isNaN(s.secNetIncome)) {
                sc += s.secNetIncome > 0 ? 0.2 : -0.35;
                why.append(s.secNetIncome > 0 ? "The company made a profit. " : "The company lost money last year. ");
            }
            if (s.secForm4Count > 0) why.append(s.secForm4Count).append(" insider-trading reports in 90 days (insiders bought or sold shares). ");
            if (s.sec8KCount > 0) why.append(s.sec8KCount).append(" major-event report").append(s.sec8KCount == 1 ? "" : "s").append(" (8-K) in the last 30 days.");
            add(s, "SEC EDGAR (official company reports)", sc, why.toString().trim());
            total += W_SEC * clamp(sc); weights += W_SEC; sources++;
        }

        // ---------- 8. Alpha Vantage news sentiment ----------
        if (!Double.isNaN(s.newsScore)) {
            double sc = clamp(s.newsScore * 2.5);
            String mood = s.newsScore > 0.15 ? "positive" : s.newsScore > 0.05 ? "slightly positive" : s.newsScore < -0.15 ? "negative" : s.newsScore < -0.05 ? "slightly negative" : "neutral";
            add(s, "News sentiment (Alpha Vantage)", sc, String.format("%d recent news articles; the tone is %s.", s.newsCount, mood));
            total += W_NEWS * clamp(sc); weights += W_NEWS; sources++;
        }

        // ---------- 9. Your own CSV files ----------
        for (java.util.Map.Entry<String, java.util.Map<String, String>> f : s.customData.entrySet()) {
            Object[] r = CustomCsv.score(s, f.getValue());
            if (r == null) continue;
            double sc = (Double) r[0];
            add(s, "Your CSV: " + f.getKey(), sc, (String) r[1]);
            total += W_CSV * clamp(sc); weights += W_CSV; sources++;
        }

        // ---------- 10. Reddit crowd ----------
        if (s.redditMentions > 0) {
            double sc = s.redditSentiment * Math.min(1, 0.4 + s.redditMentions / 10.0);
            String mood = s.redditSentiment > 0.15 ? "mostly positive" : s.redditSentiment < -0.15 ? "mostly negative" : "mixed";
            String why = String.format("Mentioned in %d hot Reddit post%s; the mood is %s.", s.redditMentions, s.redditMentions == 1 ? "" : "s", mood);
            if (s.redditMentions >= 8) why += " Careful: lots of hype can mean the price already moved.";
            add(s, "Reddit (r/ValueInvesting, r/options, r/wallstreetbets)", sc, why);
            total += W_REDDIT * clamp(sc); weights += W_REDDIT; sources++;
        }

        s.score = weights == 0 ? 0 : total / weights;
        // confidence: more sources and agreement between them = more confident
        double agree = 0;
        for (Signal g : s.signals) agree += Math.signum(g.score()) == Math.signum(s.score) ? 1 : 0;
        s.confidence = s.signals.isEmpty() ? 0 : Math.min(1, sources / 5.0) * 0.5 + (agree / s.signals.size()) * 0.5;
    }

    private static void add(Stock s, String src, double sc, String why) { s.signals.add(new Signal(src, sc, why)); }

    static double clamp(double v) { return Math.max(-1, Math.min(1, v)); }

    private static double avg(double... v) {
        double sum = 0; int n = 0;
        for (double d : v) if (!Double.isNaN(d)) { sum += d; n++; }
        return n == 0 ? Double.NaN : sum / n;
    }

    /** A short plain-English summary of what to do. */
    public static String plainSummary(Stock s) {
        StringBuilder b = new StringBuilder();
        if (s.score >= 0.2) b.append("Most signs are positive. If you believe in the company long term, this could be a reasonable time to buy a small amount. ");
        else if (s.score <= -0.2) b.append("Most signs are negative right now. If you're thinking of buying, it may be wiser to wait. If you own it, think about protecting yourself with a stop-loss. ");
        else b.append("The signs are mixed, so there's no strong reason to buy or sell today. Waiting and watching is fine. ");
        if (s.tech != null && s.tech.volatility > 0.5) b.append("Note: this stock swings a lot, so only use money you're comfortable seeing go up and down quickly.");
        return b.toString();
    }

    public static String verdict(double score) {
        if (score >= 0.45) return "STRONG BUY signal";
        if (score >= 0.2) return "BUY signal";
        if (score > -0.2) return "HOLD / WAIT";
        if (score > -0.45) return "SELL signal";
        return "STRONG SELL signal";
    }

    public static String stars(double confidence) {
        int n = (int) Math.round(confidence * 5);
        n = Math.max(1, Math.min(5, n));
        return Ui.repeat("*", n) + Ui.repeat(".", 5 - n);
    }
}
