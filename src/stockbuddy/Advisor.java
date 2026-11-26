package stockbuddy;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Turns the scored stocks into today's ideas: buys, sells, options, shorts and 0DTE. */
public final class Advisor {

    public enum Kind { BUY, SELL, CALL, PUT, ZERO_DTE }

    /** One recommendation, written for beginners. */
    public static final class Idea {
        public final Kind kind;
        public final Stock stock;          // null for "sit out" 0DTE ideas
        public String headline;            // title + action on one line (text version, widget)
        public String title;               // e.g. "Amazon (AMZN)"
        public String action;              // e.g. "BUY at about $182.12 - Strong buy signal - confidence ****."
        public int tone;                   // +1 good news, -1 warning, 0 neutral (for colours)
        public boolean alsoShort;          // a sell idea that is also a short candidate
        public final List<String> why = new ArrayList<>();
        public final List<String> howTo = new ArrayList<>();
        public String risk;

        Idea(Kind kind, Stock stock) { this.kind = kind; this.stock = stock; }
    }

    public static final class Plan {
        public final List<Idea> buys = new ArrayList<>(), sells = new ArrayList<>(), calls = new ArrayList<>(),
                puts = new ArrayList<>(), zeroDte = new ArrayList<>();
        /** The market these ideas are for (index symbol), or null for all markets. */
        public String market;
        /** Set when big economic news (inflation, jobs, interest rates) comes out in the US today. */
        public String newsWarning;
    }

    private static final DateTimeFormatter NICE = DateTimeFormatter.ofPattern("EEEE, MMMM d");

    private Advisor() {}

    public static Plan build(Analyzer a) { return build(a, null); }

    /** Today's ideas, only for stocks of one market (index symbol like "^N225"), or all markets when null. */
    public static Plan build(Analyzer a, String market) {
        Plan p = new Plan();
        p.market = market;
        List<Stock> all = new ArrayList<>();
        for (Stock s : a.stocks.values()) if (WorldMarkets.belongs(s, market)) all.add(s);
        Comparator<Stock> best = Comparator.comparingDouble((Stock s) -> s.score * (0.5 + s.confidence)).reversed();

        // ---------- BUYS ----------
        all.stream().filter(s -> s.score >= 0.2 && !s.tech.downtrend(s.price) && s.signals.size() >= 2).sorted(best).limit(5).forEach(s -> {
            Idea i = new Idea(Kind.BUY, s);
            name(i, s, +1, String.format(I18n.tr("BUY at about %s  -  %s  -  confidence %s"), money(s), Scorer.verdict(s.score), Scorer.stars(s.confidence)));
            addReasons(i, s, true);
            if (s.world != null) for (String w : s.world.howToBuy()) i.howTo.add(I18n.tr("Where to buy: ") + w);
            double stop = s.price * 0.92;
            i.howTo.add(I18n.tr("In your broker app, search ") + s.ticker + I18n.tr(", choose \"Buy\", and use a LIMIT order near ") + money(s) + I18n.tr(" so you don't overpay."));
            i.howTo.add(String.format(I18n.tr("Consider a \"stop-loss\" around %s (8%% below) - it sells automatically if things go wrong."), money(s, stop)));
            i.howTo.add(I18n.tr("Only put in money you can leave alone for months; don't put everything into one stock."));
            i.risk = riskLine(s);
            p.buys.add(i);
        });

        // ---------- SELLS & SHORTS (one list: every short idea is also a sell idea) ----------
        all.stream().filter(s -> s.score <= -0.2 && s.signals.size() >= 2).sorted(best.reversed()).limit(6).forEach(s -> {
            boolean shortable = usListed(s) && s.score <= -0.3 && s.tech.downtrend(s.price) && s.price < s.tech.sma20
                    && !(s.redditMentions >= 8 && s.redditSentiment > 0);   // avoid crowd "short squeeze" names
            Idea i = new Idea(Kind.SELL, s);
            i.alsoShort = shortable;
            name(i, s, -1, String.format(I18n.tr("SELL if you own it%s  -  %s  -  confidence %s"),
                    shortable ? I18n.tr("  +  SHORT idea (advanced)") : "", Scorer.verdict(s.score), Scorer.stars(s.confidence)));
            addReasons(i, s, false);
            if (shortable) i.why.add(String.format(I18n.tr("The price (%s) is below its 20-, 50- and 200-day averages - a falling staircase."), money(s)));
            i.howTo.add(I18n.tr("If you own ") + s.ticker + I18n.tr(", you could sell part (for example half) to lower your risk, or set a stop-loss just below today's price."));
            i.howTo.add(I18n.tr("If you don't own it: this just means \"don't buy it right now\"."));
            if (shortable) {
                i.howTo.add(I18n.tr("SHORT (advanced): \"shorting\" means borrowing shares, selling them now and buying them back later - you profit if the price FALLS. You need a margin account."));
                i.howTo.add(String.format(I18n.tr("If you short, set a buy-stop around $%.2f (6%% above) to cap the loss. A simpler, safer way to bet on a fall: buy a PUT (see Best Puts)."), s.price * 1.06));
                i.risk = I18n.tr("Selling locks in your gain or loss (and a profit can mean tax). DANGER: when shorting, losses have NO limit if the price rises - not for beginners.");
            } else {
                i.risk = I18n.tr("Selling locks in your gain or loss, and in many countries a profit means tax. Stocks with bad signals can still bounce.");
            }
            p.sells.add(i);
        });

        // ---------- BEST CALLS and BEST PUTS (1-2 months) ----------
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate exp = Options.monthlyExpiry(today, 30);
        long days = ChronoUnit.DAYS.between(today, exp);
        Comparator<Stock> optionRank = Comparator.comparingDouble((Stock s) -> Math.abs(s.score) * (1 + 0.05 * (s.optionsTalkCalls + s.optionsTalkPuts))).reversed();
        all.stream().filter(s -> s.score >= 0.3 && s.confidence >= 0.5 && s.tech.uptrend(s.price) && usListed(s))
                .sorted(optionRank).limit(3).forEach(s -> p.calls.add(option(s, true, exp, days)));
        all.stream().filter(s -> s.score <= -0.3 && s.confidence >= 0.5 && s.tech.downtrend(s.price) && usListed(s))
                .sorted(optionRank).limit(3).forEach(s -> p.puts.add(option(s, false, exp, days)));

        // ---------- 0DTE ----------
        List<EconomicCalendar.Event> big = EconomicCalendar.todaysBigUsEvents(a.events);
        if (!big.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (EconomicCalendar.Event e : big)
                parts.add((e.time == null ? "" : e.time.withZoneSameInstant(ZoneId.of("America/New_York")).toLocalTime() + I18n.tr(" New York time: ")) + e.title);
            p.newsWarning = I18n.tr("Big news day in the US: ") + String.join("; ", parts)
                    + I18n.tr(". Prices can jump suddenly around these times - consider waiting until after the news, and keep positions small.");
        }
        for (String t : new String[]{"SPY", "QQQ"}) {
            if (market != null && !WorldMarkets.isUs(market)) break;   // 0DTE is about US index options
            Idea z = zeroDte(a, t);
            if (p.newsWarning != null) z.why.add(0, p.newsWarning);
            p.zeroDte.add(z);
        }
        if (!a.demo && !a.stocks.isEmpty() && market == null) TrackRecord.save(p);   // remember today's ideas for the Track Record
        return p;
    }

    /** Options and shorting are only practical for stocks that trade on a big US exchange. */
    static boolean usListed(Stock s) { return s.ticker.indexOf('.') < 0 && "USD".equals(s.currency); }

    private static Idea option(Stock s, boolean call, LocalDate exp, long days) {
        double vol = Math.max(0.15, s.tech.volatility * 1.1);
        double strike = Options.roundStrike(s.price * (call ? 1.02 : 0.98), s.price, call);
        double prem = Options.blackScholes(call, s.price, strike, days / 365.0, vol);
        double breakeven = call ? strike + prem : strike - prem;
        Idea i = new Idea(call ? Kind.CALL : Kind.PUT, s);
        name(i, s, call ? +1 : -1, String.format(I18n.tr("BUY a %s  -  $%s strike, expires %s (%d days)  -  about $%.0f per contract"),
                call ? I18n.tr("CALL (bet on up)") : I18n.tr("PUT (bet on down)"), fmtStrike(strike), exp.format(NICE), days, prem * 100));
        addReasons(i, s, call);
        if (s.optionsTalkCalls + s.optionsTalkPuts > 0)
            i.why.add(String.format(I18n.tr("Options traders on Reddit mentioned calls %d times and puts %d times."), s.optionsTalkCalls, s.optionsTalkPuts));
        i.howTo.add(call ? I18n.tr("A CALL gains value if the stock goes UP. It gives you the right to buy 100 shares at the strike price.")
                : I18n.tr("A PUT gains value if the stock goes DOWN. It gives you the right to sell 100 shares at the strike price."));
        i.howTo.add(String.format(I18n.tr("Estimated cost: about $%.2f per share = about $%.0f for one contract (100 shares). Check the real price in your broker."), prem, prem * 100));
        i.howTo.add(String.format(I18n.tr("At expiry the stock needs to be %s $%.2f for you to make money (your \"breakeven\")."), call ? I18n.tr("above") : I18n.tr("below"), breakeven));
        i.howTo.add(I18n.tr("Many traders take profit at +50% and cut losses at -50% instead of waiting until expiry."));
        i.risk = I18n.tr("You can lose 100% of what you pay for an option. Options lose value every day (\"time decay\"). Start with 1 contract.");
        return i;
    }

    /** Title = the company, action = what to do (shown underneath). */
    private static void name(Idea i, Stock s, int tone, String action) {
        i.title = (s.name == null || s.name.trim().isEmpty() ? s.ticker : s.name + " (" + s.ticker + ")") + "   " + money(s);
        i.action = action;
        i.tone = tone;
        i.headline = i.title + "  -  " + action;
    }

    private static Idea zeroDte(Analyzer a, String t) {
        Idea i = new Idea(Kind.ZERO_DTE, a.stocks.get(t));
        String nice = t.equals("SPY") ? I18n.tr("SPY (tracks the S&P 500)") : I18n.tr("QQQ (tracks the Nasdaq 100)");
        ZonedDateTime ny = ZonedDateTime.now(ZoneId.of("America/New_York"));
        boolean weekday = ny.getDayOfWeek().getValue() <= 5;
        LocalTime now = ny.toLocalTime();
        boolean open = a.demo || (weekday && !now.isBefore(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(16, 0)));
        List<List<Double>> d = a.intraday.get(t);
        i.risk = I18n.tr("0DTE options can go to ZERO within minutes. Most beginners lose money with them. Practise with tiny amounts or paper trading first.");
        if (!open || d == null) {
            i.headline = nice + I18n.tr(": no 0DTE idea right now");
            i.title = nice; i.action = I18n.tr("No 0DTE idea right now"); i.tone = 0;
            i.why.add(!open ? I18n.tr("The US market is closed (it's open 9:30am-4:00pm New York time, Monday-Friday; it's ")
                    + now.withSecond(0).withNano(0) + I18n.tr(" there now).") : I18n.tr("Today's minute-by-minute prices couldn't be downloaded."));
            i.howTo.add(I18n.tr("Run the program again during market hours. The first 30 minutes are very jumpy, so most people wait until about 10:00am."));
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
        i.why.add(String.format(I18n.tr("%s is %+.2f%% since the open and %+.2f%% in the last 30 minutes."), t, move, slope));
        i.why.add(String.format(I18n.tr("It's trading %s its average price of the day (VWAP $%.2f) - big traders watch this line."), last > vwap ? I18n.tr("ABOVE") : I18n.tr("BELOW"), vwap));
        if (!bull && !bear) {
            i.headline = nice + I18n.tr(": SIT OUT - no clear direction today");
            i.title = nice; i.action = I18n.tr("SIT OUT - no clear direction today"); i.tone = 0;
            i.why.add(I18n.tr("The signals disagree, and with no clear direction 0DTE options usually just lose value. Not trading is a valid choice!"));
            i.howTo.add(I18n.tr("Check again later in the day if a clear trend shows up."));
            return i;
        }
        boolean call = bull;
        double strike = call ? Math.ceil(last) : Math.floor(last);
        double years = Math.max(5, minutesLeft) / (252.0 * 390.0);
        double prem = Options.blackScholes(call, last, strike, years, 0.16);
        i.headline = String.format(I18n.tr("%s: lean %s - $%s strike expiring TODAY"), nice, call ? I18n.tr("CALL (bet on up)") : I18n.tr("PUT (bet on down)"), fmtStrike(strike));
        i.title = nice;
        i.action = String.format(I18n.tr("Lean %s  -  $%s strike expiring TODAY"), call ? I18n.tr("CALL (bet on up)") : I18n.tr("PUT (bet on down)"), fmtStrike(strike));
        i.tone = call ? 1 : -1;
        i.why.add(call ? I18n.tr("Price is rising steadily and staying above VWAP: buyers are in control right now.")
                : I18n.tr("Price is falling steadily and staying below VWAP: sellers are in control right now."));
        i.howTo.add(String.format(I18n.tr("Estimated cost: about $%.2f per share (~$%.0f per contract). %d minutes of trading left."), prem, prem * 100, minutesLeft));
        i.howTo.add(I18n.tr("Decide your exit BEFORE buying: e.g. sell at +30% profit or -30% loss, and always close before 3:45pm."));
        i.howTo.add(String.format(I18n.tr("If %s crosses back %s VWAP ($%.2f), the idea is wrong - get out."), t, call ? I18n.tr("below") : I18n.tr("above"), vwap));
        if (minutesLeft < 60) i.howTo.add(I18n.tr("Less than 1 hour left: prices move extremely fast now. Even more caution needed."));
        return i;
    }

    private static void addReasons(Idea i, Stock s, boolean bullish) {
        // Put the signals that support the idea first.
        s.signals.stream()
                .sorted(Comparator.comparingDouble((Signal g) -> bullish ? -g.score() : g.score()))
                .forEach(g -> i.why.add(tag(g) + I18n.tr(g.source()) + ": " + g.reason()));
        if (!Double.isNaN(s.changePct)) i.why.add(String.format(I18n.tr("Today it moved %+.2f%%."), s.changePct));
        for (String q : s.redditQuotes) i.why.add(I18n.tr("Seen on ") + q);
    }

    private static String tag(Signal g) {
        return g.score() > 0.1 ? "[+] " : g.score() < -0.1 ? "[-] " : "[=] ";
    }

    private static String riskLine(Stock s) {
        double v = s.tech.volatility * 100;
        String lvl = v < 20 ? I18n.tr("LOW (a calm stock)") : v < 40 ? I18n.tr("MEDIUM") : v < 70 ? I18n.tr("HIGH (big daily swings)") : I18n.tr("VERY HIGH (wild swings)");
        return String.format(I18n.tr("Risk level: %s - it typically moves about %.0f%% up or down in a year. Past results don't guarantee future ones."), lvl, v);
    }

    static String money(Stock s, double v) {
        String sym = "USD".equals(s.currency) ? "$" : s.currency + " ";
        return String.format("%s%.2f", sym, v);
    }

    static String money(Stock s) {
        String sym = "USD".equals(s.currency) ? "$" : s.currency + " ";
        return String.format("%s%.2f", sym, s.price);
    }

    static String fmtStrike(double k) { return k == Math.floor(k) ? String.format("%.0f", k) : String.format("%.1f", k); }
}
