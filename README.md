# StockBuddy – your friendly daily stock helper (Java)

StockBuddy collects information from several investing websites, mixes it together, and tells you **in plain English**:

- **Best buys:** stocks that look good to buy today
- **Best sells:** stocks you might want to sell or protect, if you own them
- **Best options ideas:** calls or puts for the next 1–2 months, with an estimated cost and breakeven price
- **Best short ideas:** stocks that look likely to fall (advanced)
- **0DTE ideas:** options on SPY / QQQ that expire *today* (very risky)
- **World markets:** the 16 biggest stock markets (USA, Canada, Brazil, UK, Germany, France, Europe, Japan, China, Hong Kong, India, Korea, Taiwan, Australia). It shows which are open right now and how each is doing.

For every idea it explains **why**, **how to do it**, and **how risky it is**.

> ⚠️ **This is a learning and research tool, not a financial advisor.** It can be wrong, the data can be late, and you can lose money. Never invest money you can't afford to lose.

---

## 1. Install Java (one time only)

Download the free **JDK 17 or newer** from <https://adoptium.net> and install it with the default options.

## 2. Open the app

- **Windows:** double-click `run.bat`
- **Mac / Linux:** open a Terminal in this folder and type `./run.sh`

A window opens with a menu on the left:

| Page | What it shows |
|---|---|
| **Today's Ideas** | Tabs for **Best Buys**, **Sells & Shorts**, **Best Calls**, **Best Puts** and **0DTE**. Each idea shows the company name, with the recommended action underneath. It says *why*, *how to do it*, and *how risky it is*. Sells that are also short candidates are marked **SHORT**. Use **Market: ...** to show ideas for just one of the 16 markets (S&P 500, Nasdaq, Dow, Canada, Brazil, UK, Germany, France, Eurozone, Japan, China, Hong Kong, India, Korea, Taiwan, Australia) |
| **World Markets** | The 16 biggest stock markets with their **index price**, updated every minute (some exchanges are a few minutes delayed). Also whether each is open right now, and how it's doing today and over the last month |
| **World Stocks** | The biggest companies of every big market (choose the **top 3, 5, 10 or 20** of each). This covers the US and the other 13 markets (Canada, Brazil, UK, Germany, France, Eurozone, Japan, China, Hong Kong, India, Korea, Taiwan, Australia), today's verdict, and **where you can buy them**: a US listing (easiest), the home exchange (needs an international broker), or a whole-country ETF. Turn this off with **Data Sources → Include world stocks** to refresh faster |
| **Economic Calendar** | This week's big economic news (inflation, jobs reports, interest-rate decisions...) from the ForexFactory calendar, with what each event means. On days with big US news, the ideas and 0DTE show a warning |
| **Look Up a Stock** | A chart you can customise (see below), **key statistics** like on Yahoo Finance (day's range, market cap, beta, P/E, EPS, dividend, earnings date…), and a plain-English explanation of what every source says. Includes a **★ Add to My Watchlist** button |
| **Price Alerts** | Type an alert like **NVDA above 150**, **TSLA below 200** or **AAPL move 5%**. Alerts are checked on every refresh and every 5 minutes, and you get a desktop notification. On Android they're checked about every 30 minutes even when the app is closed, and arrive as phone notifications |
| **Paper Trading** | Practise with **$10,000 of pretend money**: type **buy 10 AAPL**, **sell 5 AAPL** or **sell all AAPL**, or use "Paper-trade it" on any idea. Shows your positions, profit and trades, and how you compare with the S&P 500. US-listed stocks only |
| **Track Record** | How StockBuddy's past ideas actually turned out: every day's buys, sells, calls and puts are remembered with their price, so you can see what share went the right way |
| **All Stocks** | Every stock checked, with its verdict. Click a column title to sort; double-click a row for details |
| **Dictionary** | Investing words explained simply, with a search box |
| **My Watchlist** | Add or remove the stocks that are always checked. Changes are saved straight away. **Double-click** a stock to look it up |
| **Data Sources** | Which websites worked, plus buttons to import your Stock Rover / WallStreetZen / Reddit CSV files and to add your free Finnhub key |

Along the top:

- **Practice mode:** switch on to learn with made-up data (works without internet).
- **Skin:** pick a colour theme: Classic Light, Night Mode, Wall Street Terminal, Ocean Blue, Sunset, Bull Gold, Mint Fresh or High Contrast. Your choice is remembered.
- **Desktop widget:** opens a small panel that stays on your desktop (see below).
- **Save report:** saves today's report as a **web page (.html)**, a **Word document (.docx)**, a **spreadsheet (.csv)** or **data for programs (.json)**. On Android: **More → Save report**, then choose where to save it.
- **Refresh data:** downloads everything again. The app also refreshes by itself every 15 minutes.

### The chart

Above the chart on **Look Up a Stock** (on Android: under the chart):

- **Chart:** Line, Candles or Area.
- **Period:** 1 month, 3 months, 6 months or 1 year.
- **Lines:** SMA 20 / 50 / 200 (moving averages), EMA 20, and Bollinger Bands.
- **Bottom panel:** nothing, Volume, RSI or MACD.

Your choices are remembered. On desktop, move the mouse over the chart to read each day's open, high, low, close and volume. **Zoom:** scroll the mouse wheel to zoom in or out around the mouse, drag to move through time, and double-click to reset. On a phone, pinch to zoom, or use the 2W period.

### The desktop widget

A small floating panel that shows the world markets, today's top buys, the stocks to be careful with, and the 0DTE lean.

- **Drag** it anywhere on your screen.
- **Click a ticker** (or double-click the widget) to open its full explanation in the main window.
- **Right-click** it (or click ≡) to choose:
  - **Widget skin:** the same as the app, or a different one just for the widget
  - **Size:** Full (markets + ideas) or Compact (markets only)
  - **See-through:** how transparent it is
  - **Always on top** of other windows
  - **Show widget when my computer starts**
- You can close the main window and keep only the widget. Its position and settings are remembered.

To start with only the widget: `run.bat --widget` or `./run.sh --widget`.

### Make a real app with a desktop icon (optional)

Run `make-app.bat` (Windows) or `./make-app.sh` (Mac/Linux). It creates a stand-alone app in `dist/StockBuddy`, with `StockBuddy.exe` on Windows. That app includes its own Java, so it can be copied to another computer. On Windows, right-click `StockBuddy.exe` → *Send to* → *Desktop (create shortcut)*.

When it's started this way, StockBuddy keeps its files (watchlist, CSV imports, reports, settings) in a `StockBuddy` folder in your home folder. **Data Sources → Open my StockBuddy folder** shows you where that is.

### Other start options

| Option      | What it does |
|-------------|--------------|
| `--demo`    | Start in practice mode |
| `--widget`  | Show only the desktop widget |
| `--console` | The old text version in the terminal (add `--report` to print the report and quit) |
| `--debug`   | Text version only: shows which websites could not be reached, and why |

---

## 3. Where the information comes from

| Source | What we use | How |
|---|---|---|
| **stockanalysis.com** | Today's top gainers, losers and most active stocks; P/E ratio; analyst rating; price target | Read automatically from the website |
| **StockCharts** | Moving averages, RSI, MACD and the **SCTR** rank | StockCharts has no public data feed, so StockBuddy **calculates these itself** using StockCharts' published formulas |
| **Stock Rover** | Fair value; quality, value and growth scores | Stock Rover needs a paid login, so **you export a CSV** (see below) |
| **WallStreetZen** | Zen Score (0–100) and Zen Rating (A–F) | Read from the website. If the site blocks the program, you can type the numbers into a CSV |
| **Reddit** (r/ValueInvesting, r/options, r/wallstreetbets) | Which stocks people talk about and whether the mood is positive or negative | Reads the "hot" posts. Each subreddit gets a different trust level: r/wallstreetbets counts least, because it's very hype-driven |
| **Finnhub.io** (optional, free key) | Wall Street analyst recommendations (strong buy … strong sell), plus financial ratios like profit margin, debt/equity and beta | Get a free key at [finnhub.io/register](https://finnhub.io/register), then use **Data Sources → Add Finnhub key** (Android: **More → Finnhub key**). The key is saved only on your device. The free plan allows 60 requests a minute, so the first refresh with Finnhub takes about 2 minutes; answers are reused for 6 hours |
| **WallStreetBets sentiment API** (no key) | The ~50 stocks most discussed on r/wallstreetbets today, with comment counts and a bullish/bearish score | From the public-apis list (nbshare / Tradestie). Several addresses are tried, because the service has moved over time |
| **SEC EDGAR** (no key) | Official company reports: yearly revenue growth and profit, the latest annual report, insider-trading reports (Form 4) and major-event reports (8-K) | US stocks only. If sec.gov refuses, add your email under **Data Sources → SEC email** (it is only sent to sec.gov) |
| **Alpha Vantage** (optional, free key) | News sentiment: is recent news about the company positive or negative? Plus a couple of headlines | Free key at [alphavantage.co](https://www.alphavantage.co/support/#api-key). The free plan allows about 25 requests a **day**, so StockBuddy checks the first 8 stocks of your list and remembers the answers all day |
| **Economic calendar** (no key) | This week's big economic events | ForexFactory's public weekly calendar |
| **Any CSV** | Anything you have in a spreadsheet | **Data Sources → Add any CSV...** (Android: **More → Add any CSV**). The only requirement is a column called Ticker or Symbol. All columns are shown on the stock's page. Columns named like *score / rating / grade*, *recommendation / sentiment* or *target / fair value* also count in the score |
| **Yahoo Finance** (extra) | Daily prices and candles, minute-by-minute prices, world market indexes | Needed because none of the sites above offer free price history |

Each source gives a score from -100 to +100. The final score is a weighted average: **charts 30%, company numbers 25%, Stock Rover 15%, WallStreetZen 15%, Finnhub 15%, Reddit 15%, and 10% each for WallStreetBets sentiment, SEC, news sentiment and each of your CSV files** (these add up to more than 100%, so each is divided by the total). If a source is missing, the others share its weight. A stock needs at least two sources before it can appear as a buy or sell idea. Calls, puts and shorts are only suggested for stocks that trade on a big US exchange. The **confidence stars** show how many sources had data, and whether they agree.

### Adding your Stock Rover data (optional)
1. In Stock Rover, open a screener, watchlist or portfolio.
2. Add the columns **Ticker**, **Fair Value**, and optionally **Quality Score**, **Value Score** and **Growth Score**.
3. Export it as CSV. Then, in StockBuddy, go to **Data Sources → Import Stock Rover CSV...** and pick the file. (Or save it yourself as `data/stockrover.csv`.)

`data/stockrover.sample.csv` shows the format. Every stock in your export is analysed automatically.

### WallStreetZen by hand (optional)
If the Data Sources page says WallStreetZen "could not read", make a CSV in the same format as `data/wallstreetzen.sample.csv`. Then import it with **Data Sources → Import WallStreetZen CSV...**.

### Reddit
StockBuddy reads Reddit by itself, with no account needed. It tries Reddit's public pages first, then Reddit's public **RSS news feeds**, which often still work when the pages are blocked. The **Data Sources** page shows which one worked.

**Adding posts yourself.** This works even when Reddit blocks StockBuddy completely:
- **Data Sources → Add Reddit posts...** (on Android: **More → Add Reddit posts**). Copy the titles of posts you find interesting on Reddit and paste them in, **one post per line**. Tickers like `$NVDA` or `NVDA` are recognised automatically. A line can start with the subreddit, for example `r/options: SPY calls look good`.
- **Import Reddit CSV...** loads a file with a `title` column. The `date` (YYYY-MM-DD), `subreddit` and `upvotes` columns are optional. See `data/reddit.sample.csv`.
- Your posts are saved in `data/reddit.csv` and used for **7 days**. **Clear my Reddit posts** deletes them.

**Reddit API key (optional).** Since November 2025, Reddit no longer lets people create API keys themselves; they must be requested from Reddit and approved. If you do have an approved key, set the environment variables `REDDIT_CLIENT_ID` and `REDDIT_CLIENT_SECRET` before starting StockBuddy, and it will use the key first.

### Your watchlist
Change it on the **My Watchlist** page, or edit `data/watchlist.txt` (one ticker per line). StockBuddy also adds today's biggest movers and the stocks Reddit mentions most, up to 45 stocks in total.

---

## 4. Good habits for beginners
- Start with **paper trading** (fake money). Most brokers offer it.
- Use **limit orders** and **stop-losses**. The program suggests levels for both.
- **Options and 0DTE** can lose 100% of what you pay, very fast. Shorting can lose *more* than you put in.
- Websites change their layout from time to time. If a source stops working, the **Data Sources** page shows which one.

---

## 5. Building the downloadable installers (for developers)

`packaging/make-release.sh` builds `build/release/StockBuddy-<version>.zip`, which contains:

| Folder | What's inside |
|---|---|
| `Windows/` | `Install StockBuddy.bat`. It copies the app to `%LOCALAPPDATA%\StockBuddy`, offers to install Java (via winget) if it's missing, and creates Desktop and Start-menu shortcuts plus an uninstaller |
| `Linux/` | `install.sh` with a small built-in Java (x86_64). It adds menu entries, Desktop icons and a `stockbuddy` command |
| `Android/` | `StockBuddy.apk`, if a signed one has been built, plus install instructions |
| `Source code/` | This project |

### Android app

The phone app (`android/`) reuses the shared code in `src/stockbuddy` and shows the same pages in a WebView. It also has a home-screen widget, with its own skin and see-through settings. It needs Android 8.0 or newer.

The Android app is built with Ubuntu/Debian's packaged Android tools:

```bash
sudo apt install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23 openjdk-8-jdk-headless
```

Then build it and sign it:

```bash
android/build-android.sh
```

```bash
android/sign-android.sh
```

The first signing run creates `android/stockbuddy-release.jks`. **Keep that file:** updates to the app must be signed with the same key.

The shared files used by Android must stay **Java 8 compatible**: no records, `switch` expressions or `instanceof` patterns, and no `List.of` or `java.net.http`. The build script checks this, because it compiles them with `--release 8`.
