package stockbuddy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Collects data from every source and scores every stock. */
public class Analyzer {
    public static final Path WATCHLIST = AppPaths.data("watchlist.txt");
    public static final List<String> DEFAULT_WATCHLIST = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "AMD", "AVGO", "JPM", "V", "KO",
            "PEP", "JNJ", "WMT", "COST", "XOM", "DIS", "NFLX", "INTC", "PLTR", "BRK-B", "UNH", "BAC"));
    public static final int MAX_STOCKS = 45;

    public final boolean demo;
    public final Map<String, Stock> stocks = new LinkedHashMap<>();
    public List<WorldMarkets.Market> markets = WorldMarkets.all();
    public List<RedditSource.Post> posts = new ArrayList<>();
    public final Map<String, List<List<Double>>> intraday = new HashMap<>();
    public final Map<String, String> sourceStatus = new LinkedHashMap<>();
    public long finishedAt;

    public Analyzer(boolean demo) { this.demo = demo; }

    public void run(Consumer<String> progress) {
        stocks.clear(); posts.clear(); intraday.clear(); sourceStatus.clear();
        if (demo) {
            DemoData.fill(this);
        } else {
            runLive(progress);
        }
        for (Stock s : stocks.values()) if (s.hasPrices()) s.tech = Technicals.compute(s.closes, s.volumes);
        stocks.values().removeIf(s -> s.tech == null);
        Scorer.rankSctr(stocks.values());
        for (Stock s : stocks.values()) Scorer.score(s);
        finishedAt = System.currentTimeMillis();
    }

    private void runLive(Consumer<String> progress) {
        progress.accept("Checking the world's biggest stock markets...");
        int mk = WorldMarkets.load(markets);
        sourceStatus.put("World markets (Yahoo Finance)", mk > 0 ? "OK - " + mk + " markets" : "Could not connect");

        Set<String> universe = new LinkedHashSet<>(readWatchlist());
        progress.accept("Looking at today's biggest movers on stockanalysis.com...");
        Set<String> movers = StockAnalysisSource.todaysMovers(6);
        universe.addAll(movers);

        if (StockRoverSource.available()) universe.addAll(StockRoverSource.tickers());

        progress.accept("Reading hot posts on r/ValueInvesting, r/options and r/wallstreetbets...");
        posts = RedditSource.fetchHot(60);
        sourceStatus.put("Reddit", RedditSource.lastStatus);
        int extra = 0;
        for (String t : RedditSource.findTickers(posts, universe)) {
            if (!universe.contains(t) && extra++ < 10) universe.add(t);
        }

        List<String> list = new ArrayList<>(universe).subList(0, Math.min(MAX_STOCKS, universe.size()));
        progress.accept("Downloading prices and ratings for " + list.size() + " stocks (this can take a minute)...");
        int[] fund = {0}, zen = {0}, rover = {0};
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> jobs = new ArrayList<>();
        for (String t : list) {
            Stock s = new Stock(t);
            stocks.put(s.ticker, s);
            jobs.add(pool.submit(() -> {
                if (!PriceSource.loadDaily(s)) return;
                if (StockAnalysisSource.loadFundamentals(s)) synchronized (fund) { fund[0]++; }
                if (StockRoverSource.load(s)) synchronized (rover) { rover[0]++; }
                if (WallStreetZenSource.load(s)) synchronized (zen) { zen[0]++; }
            }));
        }
        for (Future<?> f : jobs) { try { f.get(90, TimeUnit.SECONDS); } catch (Exception ignored) {} }
        pool.shutdownNow();

        sourceStatus.put("stockanalysis.com", fund[0] > 0 || !movers.isEmpty() ? "OK - data for " + fund[0] + " stocks" : "Could not connect");
        sourceStatus.put("StockCharts-style technicals", "Calculated from price history (StockCharts has no public data feed)");
        sourceStatus.put("Stock Rover", StockRoverSource.available() ? "OK - " + rover[0] + " stocks from data/stockrover.csv"
                : "Not set up - export a CSV from Stock Rover to data/stockrover.csv");
        sourceStatus.put("WallStreetZen", zen[0] > 0 ? "OK - data for " + zen[0] + " stocks" : "Could not read (site may block programs; you can use data/wallstreetzen.csv)");

        RedditSource.score(posts, stocks);

        progress.accept("Checking today's minute-by-minute moves for 0DTE ideas...");
        for (String t : new String[]{"SPY", "QQQ"}) {
            List<List<Double>> d = PriceSource.loadIntraday(t);
            if (d != null) intraday.put(t, d);
        }
    }

    public static List<String> readWatchlist() {
        try {
            if (!Files.exists(WATCHLIST)) {
                Files.createDirectories(WATCHLIST.getParent());
                Files.write(WATCHLIST, ("# Your watchlist: one ticker per line. Lines starting with # are ignored.\n"
                        + String.join("\n", DEFAULT_WATCHLIST) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            List<String> out = new ArrayList<>();
            for (String line : Files.readAllLines(WATCHLIST)) {
                String t = line.trim().toUpperCase();
                if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
            }
            return out;
        } catch (Exception e) {
            return DEFAULT_WATCHLIST;
        }
    }

    public static void saveWatchlist(List<String> tickers) throws java.io.IOException {
        Files.createDirectories(WATCHLIST.getParent());
        Files.write(WATCHLIST, ("# Your watchlist: one ticker per line. Lines starting with # are ignored.\n"
                + String.join("\n", tickers) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Look up a single stock (even one not in the list). */
    public Stock lookup(String ticker) {
        String t = ticker.trim().toUpperCase().replace("$", "");
        Stock s = stocks.get(t);
        if (s != null || demo) return s;
        s = new Stock(t);
        if (!PriceSource.loadDaily(s) || !s.hasPrices()) return null;
        StockAnalysisSource.loadFundamentals(s);
        StockRoverSource.load(s);
        WallStreetZenSource.load(s);
        Map<String, Stock> one = java.util.Collections.singletonMap(t, s);
        RedditSource.score(posts, one);
        s.tech = Technicals.compute(s.closes, s.volumes);
        // place it in the SCTR ranking of the others
        final Stock found = s;
        long below = stocks.values().stream().filter(o -> o.tech.sctrRaw < found.tech.sctrRaw).count();
        found.tech.sctr = stocks.isEmpty() ? Double.NaN : 99.0 * below / stocks.size();
        Scorer.score(found);
        return found;
    }
}
