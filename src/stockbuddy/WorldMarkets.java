package stockbuddy;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/** The world's biggest stock markets, whether they're open right now, and how they're doing today. */
public final class WorldMarkets {

    public static final class Market {
        public final String symbol, name, country, zone;
        public final LocalTime open, close;
        public double price = Double.NaN, changePct = Double.NaN, monthPct = Double.NaN;

        Market(String symbol, String name, String country, String zone, String open, String close) {
            this.symbol = symbol; this.name = name; this.country = country; this.zone = zone;
            this.open = LocalTime.parse(open); this.close = LocalTime.parse(close);
        }

        public boolean isOpenNow() {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(zone));
            DayOfWeek d = now.getDayOfWeek();
            if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return false;
            LocalTime t = now.toLocalTime();
            return !t.isBefore(open) && t.isBefore(close);
        }

        public String localTime() {
            return ZonedDateTime.now(ZoneId.of(zone)).toLocalTime().withSecond(0).withNano(0).toString();
        }
    }

    private WorldMarkets() {}

    /** The 30 companies in the Dow Jones Industrial Average (as of 2025). */
    public static final java.util.Set<String> DOW30 = new java.util.HashSet<>(java.util.Arrays.asList(
            "AAPL", "AMGN", "AMZN", "AXP", "BA", "CAT", "CRM", "CSCO", "CVX", "DIS", "GS", "HD", "HON", "IBM", "JNJ",
            "JPM", "KO", "MCD", "MMM", "MRK", "MSFT", "NKE", "NVDA", "PG", "SHW", "TRV", "UNH", "V", "VZ", "WMT"));

    /** Well-known Nasdaq stocks (used when the exchange name isn't known, e.g. in practice mode). */
    private static final java.util.Set<String> NASDAQ_KNOWN = new java.util.HashSet<>(java.util.Arrays.asList(
            "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "GOOG", "META", "TSLA", "AVGO", "AMD", "NFLX", "COST", "INTC", "PEP", "PLTR",
            "ADBE", "CSCO", "QCOM", "TXN", "AMGN", "SBUX", "PYPL", "ASML", "AZN", "SHOP", "JD", "BIDU", "NTES", "SNY", "QQQ"));

    /** True if the stock belongs to the given market (index symbol); null or "all" = every market. */
    public static boolean belongs(Stock s, String market) {
        if (market == null || market.equals("all")) return true;
        boolean usMarket = market.equals("^GSPC") || market.equals("^IXIC") || market.equals("^DJI");
        boolean usStock = s.world != null ? isUs(s.world.market) : ("USD".equals(s.currency) && s.ticker.indexOf('.') < 0 && !s.ticker.startsWith("^"));
        if (!usMarket) return s.world != null && s.world.market.equals(market);
        if (!usStock) return false;
        if (market.equals("^DJI")) return DOW30.contains(s.ticker);
        if (market.equals("^IXIC")) return s.exchange != null ? s.exchange.toLowerCase().contains("nasdaq") || s.exchange.startsWith("NMS") || s.exchange.startsWith("NGM") : NASDAQ_KNOWN.contains(s.ticker);
        return true;   // S&P 500: the big US companies we follow
    }

    public static boolean isUs(String market) { return market.equals("^GSPC") || market.equals("^IXIC") || market.equals("^DJI"); }

    public static List<Market> all() {
        List<Market> m = new ArrayList<>();
        m.add(new Market("^GSPC", "S&P 500 (500 biggest US companies)", "USA", "America/New_York", "09:30", "16:00"));
        m.add(new Market("^IXIC", "Nasdaq (US tech-heavy)", "USA", "America/New_York", "09:30", "16:00"));
        m.add(new Market("^DJI", "Dow Jones (30 big US companies)", "USA", "America/New_York", "09:30", "16:00"));
        m.add(new Market("^GSPTSE", "TSX (Canada)", "Canada", "America/Toronto", "09:30", "16:00"));
        m.add(new Market("^BVSP", "Bovespa (Brazil)", "Brazil", "America/Sao_Paulo", "10:00", "17:00"));
        m.add(new Market("^FTSE", "FTSE 100 (UK)", "UK", "Europe/London", "08:00", "16:30"));
        m.add(new Market("^GDAXI", "DAX (Germany)", "Germany", "Europe/Berlin", "09:00", "17:30"));
        m.add(new Market("^FCHI", "CAC 40 (France)", "France", "Europe/Paris", "09:00", "17:30"));
        m.add(new Market("^STOXX50E", "Euro Stoxx 50 (Eurozone)", "Europe", "Europe/Berlin", "09:00", "17:30"));
        m.add(new Market("^N225", "Nikkei 225 (Japan)", "Japan", "Asia/Tokyo", "09:00", "15:30"));
        m.add(new Market("000001.SS", "Shanghai Composite (China)", "China", "Asia/Shanghai", "09:30", "15:00"));
        m.add(new Market("^HSI", "Hang Seng (Hong Kong)", "Hong Kong", "Asia/Hong_Kong", "09:30", "16:00"));
        m.add(new Market("^BSESN", "Sensex (India)", "India", "Asia/Kolkata", "09:15", "15:30"));
        m.add(new Market("^KS11", "KOSPI (South Korea)", "South Korea", "Asia/Seoul", "09:00", "15:30"));
        m.add(new Market("^TWII", "TAIEX (Taiwan)", "Taiwan", "Asia/Taipei", "09:00", "13:30"));
        m.add(new Market("^AXJO", "ASX 200 (Australia)", "Australia", "Australia/Sydney", "10:00", "16:00"));
        return m;
    }

    /** Downloads today's numbers for every market. Returns how many worked. */
    public static int load(List<Market> markets) {
        int ok = 0;
        for (Market mk : markets) {
            Stock s = new Stock(mk.symbol);
            if (PriceSource.loadDaily(s)) {
                fill(mk, s);
                ok++;
            }
        }
        if (ok > 0) lastPriceUpdate = System.currentTimeMillis();
        return ok;
    }

    /**
     * Quick "live" update of every index's price (never cached). Yahoo's index prices are
     * real-time or a few minutes delayed depending on the exchange. Returns how many updated.
     */
    public static int refreshPrices(List<Market> markets) {
        int ok = 0;
        for (Market mk : markets) {
            Stock s = new Stock(mk.symbol);
            if (PriceSource.loadDailyFresh(s, "3mo")) { fill(mk, s); ok++; }
        }
        if (ok > 0) lastPriceUpdate = System.currentTimeMillis();
        return ok;
    }

    public static volatile long lastPriceUpdate;

    /** An index level like 6,812.45 */
    public static String price(double v) {
        return Double.isNaN(v) ? "n/a" : String.format(java.util.Locale.US, "%,.2f", v);
    }

    static void fill(Market mk, Stock s) {
        mk.price = s.price;
        mk.changePct = s.changePct;
        int n = s.closes.size();
        if (n > 22) mk.monthPct = 100 * (s.price - s.closes.get(n - 22)) / s.closes.get(n - 22);
    }

    /** One plain-English sentence summing up the global mood. */
    public static String mood(List<Market> markets) {
        int up = 0, down = 0; double avg = 0; int n = 0;
        for (Market m : markets) {
            if (Double.isNaN(m.changePct)) continue;
            n++; avg += m.changePct;
            if (m.changePct > 0.15) up++; else if (m.changePct < -0.15) down++;
        }
        if (n == 0) return I18n.tr("No market data available right now.");
        avg /= n;
        String feel = avg > 0.8 ? I18n.tr("a strong, happy day") : avg > 0.2 ? I18n.tr("a mildly positive day") : avg > -0.2 ? I18n.tr("a quiet, mixed day")
                : avg > -0.8 ? I18n.tr("a mildly negative day") : I18n.tr("a rough day");
        return I18n.trf("Around the world it's %s: %d markets are up, %d are down (average move %+.2f%%).", feel, up, down, avg);
    }
}
