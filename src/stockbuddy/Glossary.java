package stockbuddy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Simple explanations of investing words used by the program. */
public final class Glossary {
    public static final Map<String, String> WORDS = new LinkedHashMap<>();
    static {
        WORDS.put("Stock / share", "A tiny piece of ownership in a company. If the company does well, the price usually goes up.");
        WORDS.put("Ticker", "The short code for a stock, like AAPL for Apple or MSFT for Microsoft.");
        WORDS.put("Index", "A basket of many stocks that shows how a whole market is doing (e.g. the S&P 500 = 500 big US companies).");
        WORDS.put("ETF", "A fund you can buy like a single stock that holds many stocks at once (SPY holds the whole S&P 500). Great for beginners.");
        WORDS.put("Buy", "Buying shares because you expect the price to go up over time.");
        WORDS.put("Sell", "Selling shares you own - to take a profit or to stop a loss from growing.");
        WORDS.put("Short (short selling)", "Borrowing shares, selling them, and buying them back later. You win if the price falls, but losses are unlimited if it rises.");
        WORDS.put("Option", "A contract that lets you bet on a stock's direction for a small price. One contract = 100 shares. It expires on a set date.");
        WORDS.put("Call option", "Makes money if the stock goes UP past the strike price before expiry.");
        WORDS.put("Put option", "Makes money if the stock goes DOWN below the strike price before expiry.");
        WORDS.put("Strike price", "The price written in the option contract that the stock has to beat.");
        WORDS.put("Expiry / expiration", "The last day the option is valid. After that it's worth nothing if it didn't pay off.");
        WORDS.put("Premium", "The price you pay for an option. It's the most you can lose when you BUY an option.");
        WORDS.put("Breakeven", "The stock price where your option trade neither makes nor loses money at expiry.");
        WORDS.put("0DTE", "\"Zero Days To Expiration\": options that expire TODAY. Very cheap, very fast, very risky - like a coin flip with a ticking clock.");
        WORDS.put("Time decay", "Options lose a little value every day just from time passing. 0DTE options lose it by the minute.");
        WORDS.put("Limit order", "An order that says \"buy only at this price or better\". Protects you from overpaying.");
        WORDS.put("Stop-loss", "An automatic sell order if the price falls to a level you choose. It limits how much you can lose.");
        WORDS.put("P/E ratio", "Price divided by yearly profit per share. P/E 20 = you pay $20 for every $1 the company earns. Lower can mean cheaper.");
        WORDS.put("Price target", "Where Wall Street analysts think the price will be in about 12 months.");
        WORDS.put("Fair value", "Stock Rover's estimate of what the stock is really worth. Price below fair value = possibly \"on sale\".");
        WORDS.put("Zen Score", "WallStreetZen's 0-100 grade for a company's overall quality (higher = better).");
        WORDS.put("Moving average (50-day, 200-day)", "The average price of the last 50 or 200 days. Price above them = generally an uptrend.");
        WORDS.put("RSI", "A 0-100 meter of how fast a price moved recently. Above 70 = maybe \"overheated\", below 30 = maybe \"oversold\".");
        WORDS.put("MACD", "A momentum gauge. When it turns positive, the price is speeding up; negative = slowing down.");
        WORDS.put("SCTR", "StockCharts Technical Rank: 0-99 score of how strong a stock's chart is compared to others. 90+ = among the strongest.");
        WORDS.put("VWAP", "Volume-Weighted Average Price: the average price everyone paid today. Day traders use it as a line in the sand.");
        WORDS.put("Volatility", "How much a price jumps around. High volatility = bigger gains AND bigger losses.");
        WORDS.put("Bullish / Bearish", "Bullish = expecting prices to go up. Bearish = expecting prices to go down.");
        WORDS.put("Paper trading", "Practising with fake money in a broker's simulator. The best way to learn options safely.");
        WORDS.put("Diversification", "Spreading money across many stocks so one bad one doesn't sink you.");
    }

    private Glossary() {}

    public static void print() {
        Ui.title("BEGINNER'S DICTIONARY - investing words made simple");
        for (Map.Entry<String, String> e : WORDS.entrySet()) {
            System.out.println();
            System.out.println("  " + Ui.BOLD + Ui.CYAN + e.getKey() + Ui.RESET);
            Ui.para(e.getValue(), "    ");
        }
    }
}
