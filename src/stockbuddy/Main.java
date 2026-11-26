package stockbuddy;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * StockBuddy - a beginner-friendly daily stock helper.
 *
 *   Start:        run.sh  (Mac/Linux)   or   run.bat  (Windows)
 *   Options:      --demo     practice mode with made-up data (works offline)
 *                 --console  use the text (terminal) version instead of windows
 *                 --widget   start with only the small desktop widget
 *                 --report   print the full daily report, save it as a web page, and quit
 *                 --nocolor  plain text (if you see strange symbols like [32m)
 *                 --debug    show which websites could not be reached
 */
public final class Main {
    private static Analyzer analyzer;
    private static Advisor.Plan plan;
    private static final Scanner IN = new Scanner(System.in);

    public static void main(String[] args) {
        List<String> a = Arrays.asList(args);
        // Desktop app by default; the text version with --console (or when there is no screen).
        if (!a.contains("--console") && !a.contains("--report") && !java.awt.GraphicsEnvironment.isHeadless()) {
            App.launch(args);
            return;
        }
        boolean demo = a.contains("--demo");
        if (a.contains("--nocolor") || System.getenv("NO_COLOR") != null) Ui.noColor();
        Log.DEBUG = a.contains("--debug");

        analyzer = new Analyzer(demo);
        Ui.title("STOCKBUDDY - your friendly daily stock helper" + (demo ? "   [PRACTICE MODE]" : ""));
        Ui.para("Hi! I read stockanalysis.com, WallStreetZen, Reddit (r/ValueInvesting, r/options, r/wallstreetbets), "
                + "your Stock Rover export, and StockCharts-style chart signals, then explain in plain English what looks "
                + "interesting today.");
        Ui.disclaimer();

        if (a.contains("--report")) {
            refresh();
            printWorld();
            printPlan();
            saveHtml();
            return;
        }

        refresh();
        while (true) {
            System.out.println();
            System.out.println(Ui.BOLD + "  What would you like to do?" + Ui.RESET);
            System.out.println("    1) See today's recommendations (buys, sells, options, shorts, 0DTE)");
            System.out.println("    2) See how the world's stock markets are doing");
            System.out.println("    3) Look up one stock and get a full explanation");
            System.out.println("    4) Show every stock I checked");
            System.out.println("    5) Beginner's dictionary (what do these words mean?)");
            System.out.println("    6) Save today's report as a web page");
            System.out.println("    7) Edit my watchlist");
            System.out.println("    8) Refresh data / check which websites worked");
            System.out.println("    0) Quit");
            String choice = ask("  Type a number and press Enter: ");
            if (choice == null) return;
            switch (choice.trim()) {
                case "1" -> printPlan();
                case "2" -> printWorld();
                case "3" -> lookup();
                case "4" -> printRanking();
                case "5" -> Glossary.print();
                case "6" -> saveHtml();
                case "7" -> editWatchlist();
                case "8" -> { refresh(); printStatus(); }
                case "0", "q", "Q" -> { System.out.println("  Goodbye, and invest carefully!"); return; }
                default -> System.out.println(Ui.YELLOW + "  Please type a number from the list (0-8)." + Ui.RESET);
            }
        }
    }

    private static String ask(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        return IN.hasNextLine() ? IN.nextLine() : null;
    }

    private static void refresh() {
        System.out.println();
        analyzer.run(msg -> System.out.println(Ui.DIM + "  ... " + msg + Ui.RESET));
        plan = Advisor.build(analyzer);
        List<String> fired = Alerts.check(Alerts.prices(analyzer, false));
        if (!fired.isEmpty()) {
            Ui.section("PRICE ALERTS", Ui.YELLOW);
            for (String f : fired) Ui.bullet(f, "  ");
            DesktopNotify.show("StockBuddy price alert", fired);
        }
        if (analyzer.stocks.isEmpty()) {
            System.out.println(Ui.RED + "  I couldn't download any stock prices. Check your internet connection, or start with --demo to practise." + Ui.RESET);
        } else {
            System.out.println(Ui.GREEN + "  Done! I analysed " + analyzer.stocks.size() + " stocks." + Ui.RESET);
        }
        printStatus();
    }

    private static void printStatus() {
        Ui.section("Where the data came from", Ui.CYAN);
        analyzer.sourceStatus.forEach((k, v) -> {
            String c = v.startsWith("OK") || v.startsWith("Calc") || v.startsWith("practice") || v.startsWith("calc") ? Ui.GREEN : Ui.YELLOW;
            System.out.printf("  %-32s %s%s%s%n", k, c, v, Ui.RESET);
        });
    }

    // ------------------------------------------------------------------ world markets
    private static void printWorld() {
        Ui.title("THE WORLD'S BIGGEST STOCK MARKETS RIGHT NOW");
        Ui.para("An \"index\" is a basket of a country's biggest companies. Green = up today, red = down.");
        System.out.println();
        System.out.printf("  %-38s %-10s %-9s %-9s %s%n", "Market", "Status", "Today", "Last mo.", "Local time");
        for (WorldMarkets.Market m : analyzer.markets) {
            String st = m.isOpenNow() ? Ui.GREEN + "OPEN  " + Ui.RESET : Ui.DIM + "closed" + Ui.RESET;
            System.out.printf("  %-38s %s     %s   %s   %s%n", m.name, st, Ui.colorPct(m.changePct), Ui.colorPct(m.monthPct), m.localTime());
        }
        System.out.println();
        Ui.para(Ui.BOLD + WorldMarkets.mood(analyzer.markets) + Ui.RESET);
        Ui.para(Ui.DIM + "Tip: Asian markets open first, then Europe, then the Americas. A big drop in Asia overnight often "
                + "affects how Europe and the US start the day." + Ui.RESET);
    }

    // ------------------------------------------------------------------ recommendations
    private static void printPlan() {
        String when = DateTimeFormatter.ofPattern("EEEE MMMM d, yyyy 'at' HH:mm").format(Instant.ofEpochMilli(analyzer.finishedAt).atZone(ZoneId.systemDefault()));
        Ui.title("TODAY'S IDEAS  (" + when + ")" + (analyzer.demo ? "  [PRACTICE DATA]" : ""));
        Ui.para("How to read this: [+] means a source sees something good, [-] something bad, [=] neutral. "
                + "Confidence stars ***** mean the sources mostly agree; *.... means they disagree, so be careful.");

        block("1. BEST BUYS  -  stocks that look good to buy today", Ui.GREEN, plan.buys,
                "No stock had enough good signals today. That's OK - waiting is a strategy too.");
        block("2. SELLS & SHORTS  -  if you own these, consider selling or protecting them", Ui.RED, plan.sells,
                "No stock had strong sell signals today.");
        block("3. BEST CALLS  (bet on up, 1-2 months)", Ui.MAGENTA, plan.calls,
                "No stock had signals strong and clear enough for a call today.");
        block("4. BEST PUTS  (bet on down, 1-2 months)", Ui.YELLOW, plan.puts,
                "No stock had signals strong and clear enough for a put today.");
        block("5. 0DTE IDEAS  (options that expire TODAY - extremely risky)", Ui.RED, plan.zeroDte, "");
        Ui.disclaimer();
    }

    private static void block(String title, String color, List<Advisor.Idea> ideas, String empty) {
        Ui.section(title, color);
        if (ideas.isEmpty()) { Ui.para(empty); return; }
        int n = 1;
        for (Advisor.Idea i : ideas) {
            System.out.println();
            System.out.println("  " + color + Ui.BOLD + "#" + n++ + "  " + (i.title != null ? i.title : i.headline) + Ui.RESET);
            if (i.action != null) System.out.println("      " + Ui.BOLD + i.action + Ui.RESET);
            System.out.println("     " + Ui.BOLD + "Why:" + Ui.RESET);
            for (String w : i.why) Ui.bullet(w, "       ");
            if (!i.howTo.isEmpty()) {
                System.out.println("     " + Ui.BOLD + "How to do it:" + Ui.RESET);
                for (String h : i.howTo) Ui.bullet(h, "       ");
            }
            if (i.risk != null) Ui.para(Ui.YELLOW + "Risk: " + i.risk + Ui.RESET, "     ");
        }
    }

    // ------------------------------------------------------------------ single stock
    private static void lookup() {
        String t = ask("  Which stock? Type its ticker (e.g. AAPL): ");
        if (t == null || t.isBlank()) return;
        System.out.println(Ui.DIM + "  ... looking it up" + Ui.RESET);
        Stock s = analyzer.lookup(t);
        if (s == null) {
            System.out.println(Ui.YELLOW + "  Sorry, I couldn't find data for \"" + t.trim().toUpperCase() + "\". Check the spelling"
                    + (analyzer.demo ? " (practice mode only knows: " + String.join(", ", analyzer.stocks.keySet()) + ")" : "") + "." + Ui.RESET);
            return;
        }
        explain(s);
    }

    private static void explain(Stock s) {
        Technicals x = s.tech;
        Ui.title(s.display());
        System.out.printf("  Price: %s   Today: %s   1-year range: $%.2f - $%.2f%n", Advisor.money(s), Ui.colorPct(s.changePct), x.low52, x.high52);
        String color = s.score >= 0.2 ? Ui.GREEN : s.score <= -0.2 ? Ui.RED : Ui.YELLOW;
        System.out.printf("  Overall: %s%s%s  (score %+.0f out of 100, confidence %s)%n", color + Ui.BOLD, Scorer.verdict(s.score), Ui.RESET, s.score * 100, Scorer.stars(s.confidence));

        Ui.section("What each source says", Ui.CYAN);
        for (Signal g : s.signals) {
            String c = g.score() > 0.1 ? Ui.GREEN + "[+]" : g.score() < -0.1 ? Ui.RED + "[-]" : Ui.YELLOW + "[=]";
            System.out.println("  " + c + " " + Ui.BOLD + g.source() + Ui.RESET);
            Ui.para(g.reason(), "      ");
        }
        if (s.signals.size() < 3) Ui.para(Ui.DIM + "(Only a few sources had data for this stock, so treat the result with extra caution.)" + Ui.RESET);

        Ui.section("Chart numbers (StockCharts-style)", Ui.CYAN);
        System.out.printf("  50-day average: $%.2f   200-day average: $%.2f   RSI: %.0f   MACD: %s   SCTR: %s%n",
                x.sma50, x.sma200, x.rsi14, x.macdHist > 0 ? "positive" : "negative", Double.isNaN(x.sctr) ? "n/a" : String.format("%.0f", x.sctr));
        System.out.printf("  Typical yearly swing (volatility): %.0f%%   Trading activity vs normal: %.1fx%n", x.volatility * 100, x.volumeRatio);
        if (!s.redditQuotes.isEmpty()) {
            Ui.section("What Reddit is saying", Ui.CYAN);
            for (String q : s.redditQuotes) Ui.bullet(q, "  ");
        }
        Ui.section("In plain English", Ui.CYAN);
        Ui.para(plainSummary(s));
    }

    static String plainSummary(Stock s) { return Scorer.plainSummary(s); }

    private static void printRanking() {
        Ui.title("EVERY STOCK I CHECKED");
        System.out.printf("  %-4s %-8s %-28s %10s %9s  %-20s %s%n", "#", "Ticker", "Name", "Price", "Today", "Verdict", "Conf.");
        List<Stock> list = new ArrayList<>(analyzer.stocks.values());
        list.sort((p, q) -> Double.compare(q.score, p.score));
        int n = 1;
        for (Stock s : list) {
            String name = s.name == null ? "" : s.name.length() > 27 ? s.name.substring(0, 26) + "." : s.name;
            String c = s.score >= 0.2 ? Ui.GREEN : s.score <= -0.2 ? Ui.RED : Ui.YELLOW;
            System.out.printf("  %-4d %-8s %-28s %10s %s  %s%-20s%s %s%n", n++, s.ticker, name, Advisor.money(s), Ui.colorPct(s.changePct),
                    c, Scorer.verdict(s.score), Ui.RESET, Scorer.stars(s.confidence));
        }
        Ui.para(Ui.DIM + "Tip: choose option 3 and type a ticker to see the full explanation for any of these." + Ui.RESET);
    }

    private static void editWatchlist() {
        List<String> w = new ArrayList<>(Analyzer.readWatchlist());
        Ui.section("Your watchlist", Ui.CYAN);
        Ui.para(String.join(", ", w));
        Ui.para(Ui.DIM + "These stocks are always checked, plus today's big movers and the stocks Reddit talks about most." + Ui.RESET);
        String add = ask("  Tickers to ADD (separated by spaces, or just press Enter): ");
        String rem = ask("  Tickers to REMOVE (separated by spaces, or just press Enter): ");
        if (add != null) for (String t : add.toUpperCase().split("[\\s,]+")) if (!t.isBlank() && !w.contains(t)) w.add(t.replace("$", ""));
        if (rem != null) for (String t : rem.toUpperCase().split("[\\s,]+")) w.remove(t);
        try {
            Analyzer.saveWatchlist(w);
            System.out.println(Ui.GREEN + "  Saved! Choose 8 (Refresh) to analyse the new list." + Ui.RESET);
        } catch (Exception e) {
            System.out.println(Ui.RED + "  Couldn't save the watchlist: " + e.getMessage() + Ui.RESET);
        }
    }

    private static void saveHtml() {
        try {
            Path p = ReportWriter.write(analyzer, plan);
            System.out.println(Ui.GREEN + "  Saved your report: " + p.toAbsolutePath() + Ui.RESET);
            System.out.println("  Double-click that file to open it in your web browser.");
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                    java.awt.Desktop.getDesktop().browse(p.toUri());
            } catch (Throwable ignored) { /* no desktop available - that's fine */ }
        } catch (Exception e) {
            System.out.println(Ui.RED + "  Couldn't save the report: " + e.getMessage() + Ui.RESET);
        }
    }
}
