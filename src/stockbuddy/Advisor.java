package stockbuddy;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Turns the scored stocks into today's ideas: buys, sells, options, shorts and 0DTE. */
public final class Advisor {

    public enum Kind { BUY, SELL, OPTION, SHORT, ZERO_DTE }

    /** One recommendation, written for beginners. */
    public static final class Idea {
        public final Kind kind;
        public final Stock stock;          // null for "sit out" 0DTE ideas
        public String headline;
        public final List<String> why = new ArrayList<>();
        public final List<String> howTo = new ArrayList<>();
        public String risk;

        Idea(Kind kind, Stock stock) { this.kind = kind; this.stock = stock; }
    }

    public static final class Plan {
        public final List<Idea> buys = new ArrayList<>(), sells = new ArrayList<>(), options = new ArrayList<>(),
                shorts = new ArrayList<>(), zeroDte = new ArrayList<>();
    }

    private static final DateTimeFormatter NICE = DateTimeFormatter.ofPattern("EEEE, MMMM d");

    private Advisor() {}

    public static Plan build(Analyzer a) {
        Plan p = new Plan();
        List<Stock> all = new ArrayList<>(a.stocks.values());
        Comparator<Stock> best = Comparator.comparingDouble((Stock s) -> s.score * (0.5 + s.confidence)).reversed();

        // ---------- BUYS ----------
        all.stream().filter(s -> s.score >= 0.2 && !s.tech.downtrend(s.price)).sorted(best).limit(5).forEach(s -> {
            Idea i = new Idea(Kind.BUY, s);
            i.headline = String.format("BUY %s at about %s  -  %s, confidence %s", s.display(), money(s), Scorer.verdict(s.score), Scorer.stars(s.confidence));
            addReasons(i, s, true);
            double stop = s.price * 0.92;
            i.howTo.add("In your broker app, search " + s.ticker + ", choose \"Buy\", and use a LIMIT order near " + money(s) + " so you don't overpay.");
            i.howTo.add(String.format("Consider a \"stop-loss\" around $%.2f (8%% below) - it sells automatically if things go wrong.", stop));
            i.howTo.add("Only put in money you can leave alone for months; don't put everything into one stock.");
            i.risk = riskLine(s);
            p.buys.add(i);
        });

        // ---------- SELLS ----------
        all.stream().filter(s -> s.score <= -0.2).sorted(best.reversed()).limit(5).forEach(s -> {
            Idea i = new Idea(Kind.SELL, s);
            i.headline = String.format("SELL (if you own it) %s at about %s  -  %s, confidence %s", s.display(), money(s), Scorer.verdict(s.score), Scorer.stars(s.confidence));
            addReasons(i, s, false);
            i.howTo.add("If you own " + s.ticker + ", you could sell part (for example half) to lower your risk, or set a stop-loss just below today's price.");
            i.howTo.add("If you don't own it: this just means \"don't buy it right now\".");
            i.risk = "Selling locks in your gain or loss, and in many countries a profit means tax. Stocks with bad signals can still bounce.";
            p.sells.add(i);
        });

        // ---------- SHORTS ----------
        all.stream().filter(s -> s.score <= -0.3 && s.tech.downtrend(s.price) && s.price < s.tech.sma20)
                .filter(s -> !(s.redditMentions >= 8 && s.redditSentiment > 0)) // avoid crowd "short squeeze" names
                .sorted(best.reversed()).limit(3).forEach(s -> {
            Idea i = new Idea(Kind.SHORT, s);
            i.headline = String.format("SHORT idea: %s at about %s  -  confidence %s", s.display(), money(s), Scorer.stars(s.confidence));
            addReasons(i, s, false);
            i.why.add(String.format("The price (%s) is below its 20-, 50- and 200-day averages - a falling staircase.", money(s)));
            i.howTo.add("\"Shorting\" means borrowing shares, selling them now and buying them back later - you profit if the price FALLS.");
            i.howTo.add("You need a margin account. A simpler beginner way: buy a PUT option (see the options section) - you can't lose more than you paid.");
            i.howTo.add(String.format("If you short, set a buy-stop around $%.2f (6%% above) to cap the loss.", s.price * 1.06));
            i.risk = "DANGER: when shorting, losses have NO limit if the price rises. Not recommended for beginners.";
            p.shorts.add(i);
        });

        // ---------- OPTIONS (swing, 1-2 months) ----------
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate exp = Options.monthlyExpiry(today, 30);
        long days = ChronoUnit.DAYS.between(today, exp);
        all.stream().filter(s -> Math.abs(s.score) >= 0.3 && s.confidence >= 0.5)
                .filter(s -> s.score > 0 ? s.tech.uptrend(s.price) : s.tech.downtrend(s.price))
                .sorted(Comparator.comparingDouble((Stock s) -> Math.abs(s.score) * (1 + 0.05 * (s.optionsTalkCalls + s.optionsTalkPuts))).reversed())
                .limit(3).forEach(s -> {
            boolean call = s.score > 0;
            double vol = Math.max(0.15, s.tech.volatility * 1.1);
            double strike = Options.roundStrike(s.price * (call ? 1.02 : 0.98), s.price, call);
            double prem = Options.blackScholes(call, s.price, strike, days / 365.0, vol);
            double breakeven = call ? strike + prem : strike - prem;
            Idea i = new Idea(Kind.OPTION, s);
            i.headline = String.format("%s option on %s: $%s strike, expires %s (%d days)",
                    call ? "CALL" : "PUT", s.display(), fmtStrike(strike), exp.format(NICE), days);
            addReasons(i, s, call);
            if (s.optionsTalkCalls + s.optionsTalkPuts > 0)
                i.why.add(String.format("Options traders on Reddit mentioned calls %d times and puts %d times.", s.optionsTalkCalls, s.optionsTalkPuts));
            i.howTo.add(call ? "A CALL gains value if the stock goes UP. It gives you the right to buy 100 shares at the strike price."
                    : "A PUT gains value if the stock goes DOWN. It gives you the right to sell 100 shares at the strike price.");
            i.howTo.add(String.format("Estimated cost: about $%.2f per share = about $%.0f for one contract (100 shares). Check the real price in your broker.", prem, prem * 100));
            i.howTo.add(String.format("At expiry the stock needs to be %s $%.2f for you to make money (your \"breakeven\").", call ? "above" : "below", breakeven));
            i.howTo.add("Many traders take profit at +50% and cut losses at -50% instead of waiting until expiry.");
            i.risk = "You can lose 100% of what you pay for an option. Options lose value every day (\"time decay\"). Start with 1 contract.";
            p.options.add(i);
        });

        // ---------- 0DTE ----------
        for (String t : new String[]{"SPY", "QQQ"}) p.zeroDte.add(zeroDte(a, t));
        return p;
    }

    private static Idea zeroDte(Analyzer a, String t) {
        Idea i = new Idea(Kind.ZERO_DTE, a.stocks.get(t));
        String nice = t.equals("SPY") ? "SPY (tracks the S&P 500)" : "QQQ (tracks the Nasdaq 100)";
        ZonedDateTime ny = ZonedDateTime.now(ZoneId.of("America/New_York"));
        boolean weekday = ny.getDayOfWeek().getValue() <= 5;
        LocalTime now = ny.toLocalTime();
        boolean open = a.demo || (weekday && !now.isBefore(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(16, 0)));
        List<List<Double>> d = a.intraday.get(t);
        i.risk = "0DTE options can go to ZERO within minutes. Most beginners lose money with them. Practise with tiny amounts or paper trading first.";
        if (!open || d == null) {
            i.headline = nice + ": no 0DTE idea right now";
            i.why.add(!open ? "The US market is closed (it's open 9:30am-4:00pm New York time, Monday-Friday; it's "
                    + now.withSecond(0).withNano(0) + " there now)." : "Today's minute-by-minute prices couldn't be downloaded.");
            i.howTo.add("Run the program again during market hours. The first 30 minutes are very jumpy, so most people wait until about 10:00am.");
            return i;
        }
        List<Double> c = d.get(0), v = d.get(1);
        double first = c.get(0), last = c.get(c.size() - 1);
        double pv = 0, vs = 0;
        for (int k = 0; k < c.size(); k++) { double vol = v.get(k) > 0 ? v.get(k) : 1; pv += c.get(k) * vol; vs += vol; }
        double vwap = pv / vs;
        double slope = 100 * (last - c.get(Math.max(0, c.size() - 7))) / last;  // last ~30 minutes, in %
        double move = 100 * (last - first) / first;

        int minutesLeft = a.demo ? 180 : (int) ChronoUnit.MINUTES.between(now, LocalTime.of(16, 0));
        boolean bull = last > vwap && slope > 0.05 && move > 0.2;
        boolean bear = last < vwap && slope < -0.05 && move < -0.2;
        i.why.add(String.format("%s is %+.2f%% since the open and %+.2f%% in the last 30 minutes.", t, move, slope));
        i.why.add(String.format("It's trading %s its average price of the day (VWAP $%.2f) - big traders watch this line.", last > vwap ? "ABOVE" : "BELOW", vwap));
        if (!bull && !bear) {
            i.headline = nice + ": SIT OUT - no clear direction today";
            i.why.add("The signals disagree, and with no clear direction 0DTE options usually just lose value. Not trading is a valid choice!");
            i.howTo.add("Check again later in the day if a clear trend shows up.");
            return i;
        }
        boolean call = bull;
        double strike = call ? Math.ceil(last) : Math.floor(last);
        double years = Math.max(5, minutesLeft) / (252.0 * 390.0);
        double prem = Options.blackScholes(call, last, strike, years, 0.16);
        i.headline = String.format("%s: lean %s - $%s strike expiring TODAY", nice, call ? "CALL (bet on up)" : "PUT (bet on down)", fmtStrike(strike));
        i.why.add(call ? "Price is rising steadily and staying above VWAP: buyers are in control right now."
                : "Price is falling steadily and staying below VWAP: sellers are in control right now.");
        i.howTo.add(String.format("Estimated cost: about $%.2f per share (~$%.0f per contract). %d minutes of trading left.", prem, prem * 100, minutesLeft));
        i.howTo.add("Decide your exit BEFORE buying: e.g. sell at +30% profit or -30% loss, and always close before 3:45pm.");
        i.howTo.add(String.format("If %s crosses back %s VWAP ($%.2f), the idea is wrong - get out.", t, call ? "below" : "above", vwap));
        if (minutesLeft < 60) i.howTo.add("Less than 1 hour left: prices move extremely fast now. Even more caution needed.");
        return i;
    }

    private static void addReasons(Idea i, Stock s, boolean bullish) {
        // Put the signals that support the idea first.
        s.signals.stream()
                .sorted(Comparator.comparingDouble((Signal g) -> bullish ? -g.score() : g.score()))
                .forEach(g -> i.why.add(tag(g) + g.source() + ": " + g.reason()));
        if (!Double.isNaN(s.changePct)) i.why.add(String.format("Today it moved %+.2f%%.", s.changePct));
        for (String q : s.redditQuotes) i.why.add("Seen on " + q);
    }

    private static String tag(Signal g) {
        return g.score() > 0.1 ? "[+] " : g.score() < -0.1 ? "[-] " : "[=] ";
    }

    private static String riskLine(Stock s) {
        double v = s.tech.volatility * 100;
        String lvl = v < 20 ? "LOW (a calm stock)" : v < 40 ? "MEDIUM" : v < 70 ? "HIGH (big daily swings)" : "VERY HIGH (wild swings)";
        return String.format("Risk level: %s - it typically moves about %.0f%% up or down in a year. Past results don't guarantee future ones.", lvl, v);
    }

    static String money(Stock s) {
        String sym = "USD".equals(s.currency) ? "$" : s.currency + " ";
        return String.format("%s%.2f", sym, s.price);
    }

    static String fmtStrike(double k) { return k == Math.floor(k) ? String.format("%.0f", k) : String.format("%.1f", k); }
}
