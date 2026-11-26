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
        WORDS.put("Candlestick chart", "Each day is drawn as a 'candle': the thick body goes from the opening to the closing price (green = it rose that day, red = it fell), and the thin wicks show the day's high and low.");
        WORDS.put("SMA / EMA", "Simple / Exponential Moving Average: the average price of the last N days drawn as a smooth line. The EMA gives recent days more weight, so it reacts faster.");
        WORDS.put("Bollinger Bands", "A band drawn 2 'standard deviations' above and below the 20-day average. Price near the top band = it rose fast lately; near the bottom = it fell fast lately.");
        WORDS.put("Market cap", "Market capitalisation: share price x number of shares = what the whole company is worth on the stock market.");
        WORDS.put("Beta", "How much a stock swings compared with the whole market. Beta 1 = moves like the market, 2 = twice as much, 0.5 = half as much.");
        WORDS.put("EPS", "Earnings Per Share: the company's profit divided by its number of shares.");
        WORDS.put("Dividend yield", "The cash a company pays its shareholders each year, as a % of the share price.");
        WORDS.put("Debt / equity", "How much the company has borrowed compared with what its owners have put in. Lower usually means safer.");
        WORDS.put("Volume", "How many shares were bought and sold. A big move on high volume is taken more seriously than one on low volume.");
        WORDS.put("VWAP", "Volume-Weighted Average Price: the average price everyone paid today. Day traders use it as a line in the sand.");
        WORDS.put("Volatility", "How much a price jumps around. High volatility = bigger gains AND bigger losses.");
        WORDS.put("Bullish / Bearish", "Bullish = expecting prices to go up. Bearish = expecting prices to go down.");
        WORDS.put("Bull market / Bear market", "A bull market is a long period of rising prices (usually +20% or more). A bear market is a long fall of 20% or more.");
        WORDS.put("Correction", "A drop of about 10% from a recent high. Corrections are normal and happen often.");
        WORDS.put("Recession", "When a country's economy shrinks for a while (often two quarters in a row). Company profits and stock prices usually suffer.");
        WORDS.put("Inflation", "Prices of everyday things rising over time. High inflation usually leads central banks to raise interest rates.");
        WORDS.put("Interest rate", "The price of borrowing money. When central banks raise rates, borrowing costs more and stocks often fall.");
        WORDS.put("Central bank", "The bank that runs a country's money, like the Fed (US), the ECB (Eurozone), the Bank of Japan. It sets the main interest rate.");
        WORDS.put("Bond", "A loan you give to a government or company. They pay you interest and give the money back at the end.");
        WORDS.put("Yield", "How much income an investment pays each year, as a % of its price (for example a bond's interest or a stock's dividend).");
        WORDS.put("Blue chip", "A big, well-known, financially strong company - usually considered safer.");
        WORDS.put("Penny stock", "A very cheap stock (often under $5) of a small company. Very risky and easy to manipulate.");
        WORDS.put("Sector", "A group of companies in the same business, like technology, banks, energy or health care.");
        WORDS.put("Index fund", "A fund that simply copies an index (like the S&P 500). Low cost and a good start for beginners.");
        WORDS.put("Portfolio", "All the investments you own together.");
        WORDS.put("Dollar-cost averaging (DCA)", "Investing the same amount regularly (for example every month), whatever the price. It smooths out ups and downs.");
        WORDS.put("Rebalancing", "Bringing your portfolio back to the mix you planned, by selling a bit of what grew and buying what fell.");
        WORDS.put("Bid / Ask / Spread", "Bid = the highest price buyers offer, Ask = the lowest price sellers want. The spread is the gap between them - a hidden cost.");
        WORDS.put("Market order", "An order to buy or sell right now at whatever the price is. Fast, but the price can surprise you.");
        WORDS.put("Stop-limit order", "Like a stop-loss, but it only sells at your limit price or better - so it might not sell in a fast fall.");
        WORDS.put("Margin", "Money borrowed from your broker to invest. It makes gains and losses bigger - risky for beginners.");
        WORDS.put("Leverage", "Using borrowed money (or options) so a small price move makes a big gain or loss.");
        WORDS.put("Liquidity", "How easily you can buy or sell something quickly at a fair price. Big stocks are very liquid.");
        WORDS.put("Short squeeze", "When a stock that many people bet against rises fast, forcing them to buy it back, which pushes the price up even more.");
        WORDS.put("Earnings", "A company's profit. Companies report earnings every 3 months - prices often jump or fall that day.");
        WORDS.put("Guidance", "What a company says it expects for the next months. Disappointing guidance often hurts the price more than past results.");
        WORDS.put("Stock split", "Cutting each share into several cheaper ones (for example 1 into 10). It doesn't change what your shares are worth.");
        WORDS.put("Buyback", "When a company buys its own shares. Fewer shares left usually supports the price.");
        WORDS.put("Ex-dividend date", "You must own the stock BEFORE this date to receive the next dividend.");
        WORDS.put("Implied volatility (IV)", "How big a move the options market expects. High IV makes options more expensive.");
        WORDS.put("Delta / Theta (the Greeks)", "Delta: how much an option's price moves when the stock moves $1. Theta: how much value it loses each day from time decay.");
        WORDS.put("In / out of the money", "A call is \"in the money\" when the stock is above the strike (a put: below). \"Out of the money\" options are cheaper but riskier.");
        WORDS.put("Covered call", "Owning 100 shares and selling a call on them to earn extra income. You give up big gains above the strike.");
        WORDS.put("Hedge", "An investment that protects you against losses on another one - like insurance.");
        WORDS.put("Risk / reward", "How much you could lose compared with how much you could win. Good trades risk little to win a lot.");
        WORDS.put("ADR", "American Depositary Receipt: a foreign company's shares traded in the US, in dollars (for example TSM for TSMC).");
        WORDS.put("ATH", "All-Time High: the highest price a stock has ever reached.");
        WORDS.put("AUM", "Assets Under Management: how much money a fund manages.");
        WORDS.put("CAGR", "Compound Annual Growth Rate: the average yearly growth over several years, including compounding.");
        WORDS.put("DCF", "Discounted Cash Flow: a way to estimate what a company is worth from the cash it should make in future.");
        WORDS.put("EBITDA", "Earnings Before Interest, Taxes, Depreciation and Amortization - a rough measure of a company's operating profit.");
        WORDS.put("FCF", "Free Cash Flow: the cash a company has left after paying for its operations and investments.");
        WORDS.put("FOMC", "Federal Open Market Committee: the group at the US Fed that decides interest rates, 8 times a year.");
        WORDS.put("CPI", "Consumer Price Index: the main measure of inflation (how fast everyday prices rise).");
        WORDS.put("GDP", "Gross Domestic Product: the total value of everything a country produces - the size of its economy.");
        WORDS.put("IPO", "Initial Public Offering: when a company sells shares to the public for the first time.");
        WORDS.put("M&A", "Mergers and Acquisitions: companies joining or buying each other.");
        WORDS.put("NAV", "Net Asset Value: what one share of a fund is worth, based on everything it owns.");
        WORDS.put("P/B ratio", "Price-to-Book: the share price divided by what the company's assets are worth on paper. Below 1 can mean cheap.");
        WORDS.put("P/S ratio", "Price-to-Sales: the company's value divided by its yearly sales. Useful for companies with no profit yet.");
        WORDS.put("PEG ratio", "P/E divided by the expected profit growth. Around 1 or below can mean a fair price for a growing company.");
        WORDS.put("ROE / ROA", "Return on Equity / on Assets: how much profit the company makes with its owners' money / with everything it owns.");
        WORDS.put("ROI", "Return on Investment: how much you gained or lost compared with what you put in.");
        WORDS.put("YTD", "Year To Date: since January 1st of this year.");
        WORDS.put("YoY / QoQ", "Year over Year / Quarter over Quarter: compared with the same period last year / the previous 3 months.");
        WORDS.put("OTC", "Over The Counter: shares traded outside the big exchanges - often foreign or small companies.");
        WORDS.put("SEC", "Securities and Exchange Commission: the US authority that oversees the stock market and company reports.");
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
