package stockbuddy;

import java.util.ArrayList;
import java.util.List;

/** Everything we know about one stock, collected from all the websites. */
public class Stock {
    public final String ticker;
    public String name;
    public String currency = "USD";

    // Prices
    public double price = Double.NaN;
    public double changePct = Double.NaN;          // today's move in %
    public List<Double> closes = new ArrayList<>();   // daily closing prices, oldest first
    public List<Double> volumes = new ArrayList<>();
    public List<Double> opens = new ArrayList<>(), highs = new ArrayList<>(), lows = new ArrayList<>(); // for candle charts

    /** "Key statistics" like on Yahoo Finance (label -> value as text), filled by the sources. */
    public final java.util.Map<String, String> keyStats = new java.util.LinkedHashMap<>();

    // Fundamentals (stockanalysis.com)
    public double peRatio = Double.NaN;
    public double forwardPe = Double.NaN;
    public double priceTarget = Double.NaN;
    public String analystRating;                   // e.g. "Buy"
    public double marketCap = Double.NaN;

    // Stock Rover (CSV export)
    public double fairValue = Double.NaN;
    public double roverQuality = Double.NaN, roverValue = Double.NaN, roverGrowth = Double.NaN;

    // Finnhub analyst recommendations (latest month)
    public int fhStrongBuy = -1, fhBuy, fhHold, fhSell, fhStrongSell;
    public String fhPeriod;

    // r/wallstreetbets sentiment API (Tradestie / nbshare)
    public int wsbComments = -1;
    public double wsbScore = Double.NaN;
    public String wsbLabel;

    // SEC EDGAR (official filings)
    public double secRevenueGrowth = Double.NaN;   // % change of yearly revenue
    public double secNetIncome = Double.NaN;       // latest yearly net income (USD)
    public String secLast10K;                      // date of the latest annual report
    public int secForm4Count = -1;                 // insider trading reports, last 90 days
    public int sec8KCount = -1;                    // "major event" reports, last 30 days

    // Alpha Vantage news sentiment
    public double newsScore = Double.NaN;          // about -1 .. +1
    public int newsCount;
    public final List<String> newsHeadlines = new ArrayList<>();

    // Set for the big companies of the other world markets (see WorldStocks)
    public WorldStocks.Entry world;

    // Any CSV files you added (file name -> column -> value)
    public final java.util.Map<String, java.util.Map<String, String>> customData = new java.util.LinkedHashMap<>();

    // WallStreetZen
    public double zenScore = Double.NaN;
    public String zenRating;                       // A..F

    // Reddit buzz
    public int redditMentions;
    public double redditSentiment;                 // -1 (very negative) .. +1 (very positive)
    public int redditUpvotes;
    public int optionsTalkCalls, optionsTalkPuts;
    public List<String> redditQuotes = new ArrayList<>();

    // Calculated (StockCharts-style technical analysis)
    public Technicals tech;

    // Final scoring
    public final List<Signal> signals = new ArrayList<>();
    public double score;          // -1 .. +1
    public double confidence;     // 0 .. 1

    public Stock(String ticker) { this.ticker = ticker.toUpperCase(); }

    public String display() {
        return name == null || name.trim().isEmpty() ? ticker : ticker + " (" + name + ")";
    }

    /** True when we have open/high/low for every day (needed for candles). */
    public boolean hasCandles() {
        return opens.size() == closes.size() && highs.size() == closes.size() && lows.size() == closes.size() && !closes.isEmpty();
    }

    public boolean hasPrices() { return closes.size() >= 60 && !Double.isNaN(price); }
}
