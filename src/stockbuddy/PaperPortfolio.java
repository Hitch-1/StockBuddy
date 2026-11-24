package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paper trading: practise with pretend money (no real money ever moves). You start with $10,000,
 * buy and sell shares at today's real prices, and see how you'd have done - also compared with
 * simply holding the S&P 500. Saved in data/portfolio.csv.
 */
public final class PaperPortfolio {
    public static final double START_CASH = 10_000;

    public static final class Position {
        public final String ticker;
        public double shares, cost;   // cost = total dollars paid for the shares still held
        Position(String ticker) { this.ticker = ticker; }
        public double avgCost() { return shares <= 0 ? 0 : cost / shares; }
    }

    public static final class Trade {
        public final String date, side, ticker;
        public final double shares, price;
        Trade(String date, String side, String ticker, double shares, double price) {
            this.date = date; this.side = side; this.ticker = ticker; this.shares = shares; this.price = price;
        }
    }

    public double startCash = START_CASH;
    public double cash = START_CASH;
    public String started = LocalDate.now().toString();
    public double spxAtStart = Double.NaN;   // S&P 500 level when you started, for comparison
    public final Map<String, Position> positions = new LinkedHashMap<>();
    public final List<Trade> trades = new ArrayList<>();

    private static final Pattern CMD = Pattern.compile("^(buy|sell)\\s+(all|[0-9]+(?:[.,][0-9]+)?)\\s*(?:shares?\\s+)?(?:of\\s+)?\\$?([A-Za-z0-9][A-Za-z0-9.\\-]{0,11})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CMD2 = Pattern.compile("^(buy|sell)\\s+\\$?([A-Za-z0-9][A-Za-z0-9.\\-]{0,11})\\s+(all|[0-9]+(?:[.,][0-9]+)?)$", Pattern.CASE_INSENSITIVE);

    static Path file() { return AppPaths.data("portfolio.csv"); }

    public static synchronized PaperPortfolio load() {
        PaperPortfolio p = new PaperPortfolio();
        try {
            if (!Files.exists(file())) return p;
            for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                String[] c = l.split(",", -1);
                if (c[0].equals("start") && c.length >= 4) {
                    p.started = c[1];
                    p.startCash = Double.parseDouble(c[2]);
                    p.spxAtStart = c[3].isEmpty() ? Double.NaN : Double.parseDouble(c[3]);
                } else if ((c[0].equals("BUY") || c[0].equals("SELL")) && c.length >= 5) {
                    p.trades.add(new Trade(c[1], c[0], c[2], Double.parseDouble(c[3]), Double.parseDouble(c[4])));
                }
            }
            p.replay();
        } catch (Exception e) {
            Log.debug("Could not read the paper portfolio: " + e);
        }
        return p;
    }

    /** Rebuilds positions and cash from the starting cash + the list of trades. */
    private void replay() {
        double c = startCash;
        positions.clear();
        for (Trade t : trades) {
            Position pos = positions.get(t.ticker);
            if (pos == null) { pos = new Position(t.ticker); positions.put(t.ticker, pos); }
            if (t.side.equals("BUY")) { pos.shares += t.shares; pos.cost += t.shares * t.price; c -= t.shares * t.price; }
            else {
                double avg = pos.avgCost();
                pos.shares -= t.shares;
                pos.cost -= avg * t.shares;
                c += t.shares * t.price;
                if (pos.shares <= 1e-9) positions.remove(t.ticker);
            }
        }
        cash = c;
    }

    private synchronized void save() {
        StringBuilder b = new StringBuilder("start," + started + "," + startCash + "," + (Double.isNaN(spxAtStart) ? "" : spxAtStart) + "\n");
        for (Trade t : trades) b.append(t.side).append(',').append(t.date).append(',').append(t.ticker).append(',').append(t.shares).append(',').append(t.price).append('\n');
        try {
            Files.createDirectories(file().getParent());
            Files.write(file(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.debug("Could not save the paper portfolio: " + e);
        }
    }

    /**
     * Runs a command like "buy 10 AAPL", "sell 5 AAPL", "sell all AAPL" or "buy AAPL 3" at the given price lookup.
     * Returns a plain-English result (starting with "OK" when it worked).
     */
    public synchronized String run(String command, Analyzer a) {
        String cmd = command == null ? "" : command.trim();
        Matcher m = CMD.matcher(cmd);
        String side, qty, ticker;
        if (m.find()) { side = m.group(1); qty = m.group(2); ticker = m.group(3); }
        else {
            Matcher m2 = CMD2.matcher(cmd);
            if (!m2.find()) return "Type it like: buy 10 AAPL   or   sell 5 AAPL   or   sell all AAPL";
            side = m2.group(1); ticker = m2.group(2); qty = m2.group(3);
        }
        ticker = ticker.toUpperCase(Locale.US);
        double[] p = price(ticker, a);
        if (p == null) return "Couldn't find a price for " + ticker + ". Check the ticker (US-listed stocks only, e.g. TSM instead of 2330.TW).";
        double price = p[0];
        boolean buy = side.equalsIgnoreCase("buy");
        Position pos = positions.get(ticker);
        double shares;
        if (qty.equalsIgnoreCase("all")) {
            if (buy) shares = Math.floor(cash / price);
            else shares = pos == null ? 0 : pos.shares;
        } else shares = Double.parseDouble(qty.replace(',', '.'));
        if (shares <= 0) return buy ? "Not enough pretend cash to buy even 1 share." : "You don't own any " + ticker + ".";
        if (buy && shares * price > cash + 1e-6)
            return String.format(Locale.US, "Not enough pretend cash: %s shares cost $%,.2f but you have $%,.2f.", fmt(shares), shares * price, cash);
        if (!buy && (pos == null || shares > pos.shares + 1e-9))
            return "You only own " + (pos == null ? "0" : fmt(pos.shares)) + " shares of " + ticker + ".";
        if (Double.isNaN(spxAtStart) && a != null) {
            for (WorldMarkets.Market mk : a.markets) if (mk.symbol.equals("^GSPC") && !Double.isNaN(mk.price) && !a.demo) spxAtStart = mk.price;
        }
        trades.add(new Trade(LocalDate.now().toString(), buy ? "BUY" : "SELL", ticker, shares, price));
        replay();
        save();
        return String.format(Locale.US, "OK - %s %s shares of %s at $%,.2f (pretend money). Cash left: $%,.2f.",
                buy ? "bought" : "sold", fmt(shares), ticker, price, cash);
    }

    /** Starts again with $10,000 and no trades. */
    public static void reset() {
        new PaperPortfolio().save();
    }

    /** {price, change % today} in US dollars, or null. Paper trading uses US-listed (dollar) prices only. */
    public static double[] price(String ticker, Analyzer a) {
        Stock s = a == null ? null : a.stocks.get(ticker);
        if (s != null && !Double.isNaN(s.price) && "USD".equals(s.currency)) return new double[]{s.price, s.changePct};
        if (a != null && a.demo) return null;
        if (ticker.contains(".")) return null;
        Stock f = new Stock(ticker);
        if (PriceSource.loadDailyFresh(f, "5d") && "USD".equals(f.currency)) return new double[]{f.price, f.changePct};
        return null;
    }

    /** {invested value now, profit/loss on positions} using the given prices (ticker -> price). */
    public double[] value(Map<String, Double> prices) {
        double v = 0, pl = 0;
        for (Position pos : positions.values()) {
            Double px = prices.get(pos.ticker);
            double now = px == null ? pos.avgCost() : px;
            v += pos.shares * now;
            pl += pos.shares * now - pos.cost;
        }
        return new double[]{v, pl};
    }

    /** Current prices for everything you hold. */
    public Map<String, Double> currentPrices(Analyzer a) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String t : positions.keySet()) {
            double[] p = price(t, a);
            if (p != null) m.put(t, p[0]);
        }
        return m;
    }

    static String fmt(double shares) { return shares == Math.rint(shares) ? String.format(Locale.US, "%.0f", shares) : String.format(Locale.US, "%.4f", shares); }
}
