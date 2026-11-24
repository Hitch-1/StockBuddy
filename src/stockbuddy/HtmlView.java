package stockbuddy;

import java.util.List;
import java.util.Map;

/** Builds the nicely formatted pages shown inside the app windows, coloured by the current skin. */
public final class HtmlView {
    private HtmlView() {}

    /** The five "Today's ideas" tabs, shared by the desktop and phone apps. */
    public static final String[] IDEA_TABS = {"Best Buys", "Sells & Shorts", "Best Calls", "Best Puts", "0DTE (today)"};
    public static final String[][] IDEA_TEXT = {
            {"Best buys today", "Stocks where most sources see good signs. [+] = a source likes it, [-] = a source sees a problem. More stars = sources agree more.",
                    "No stock had enough good signals today. That's OK - waiting is a strategy too."},
            {"Sells & shorts", "Stocks where most sources see warning signs. If you own one, think about selling or protecting it; if you don't, this means \"don't buy right now\". "
                    + "Ideas marked SHORT are also in a clear downtrend - shorting is for advanced traders only.", "No stock had strong sell signals today."},
            {"Best calls (bet on up, 1-2 months)", "Call options on stocks with strong, clear good signals and a rising price. Costs are estimates - check your broker.",
                    "No stock had signals strong and clear enough for a call today."},
            {"Best puts (bet on down, 1-2 months)", "Put options on stocks with strong warning signs and a falling price. A put is the beginner-safer way to bet on a fall: you can't lose more than you paid.",
                    "No stock had signals strong and clear enough for a put today."},
            {"0DTE ideas (expire today)", "Options on SPY and QQQ that expire at today's close. Extremely risky - often the best idea is to sit out.", ""}};

    public static java.util.List<java.util.List<Advisor.Idea>> ideaLists(Advisor.Plan p) {
        return java.util.Arrays.asList(p.buys, p.sells, p.calls, p.puts, p.zeroDte);
    }

    /** Tab i's page. */
    public static String ideasTab(Palette k, Advisor.Plan p, int i, boolean demo) {
        String empty = IDEA_TEXT[i][2];
        if (i == 4 && p.market != null && !WorldMarkets.isUs(p.market)) empty = "0DTE ideas are only for US index options (SPY and QQQ). Choose All markets or a US market to see them.";
        else if (p.market != null && !empty.isEmpty()) empty += " (in this market)";
        String html = ideas(k, IDEA_TEXT[i][0], IDEA_TEXT[i][1], ideaLists(p).get(i), empty, demo);
        if (p.market != null) {
            int at = html.indexOf("</h1>");
            String hint = WorldMarkets.isUs(p.market) ? "" : " <span class=muted style='font-size:10.5pt'>- outside the US many companies only have chart data, and an idea "
                    + "needs at least two sources, so fewer ideas appear. <a href='sb://page/worldstocks'>See every company's verdict on World Stocks &rarr;</a></span>";
            if (at > 0) html = html.substring(0, at + 5) + "<p><b style='color:" + k.accent() + "'>Showing only: " + esc(WorldStocks.marketName(p.market))
                    + "</b>" + hint + "</p>" + html.substring(at + 5);
        }
        if (p.newsWarning == null) return html;
        String banner = "<p class=banner><b>&#9888; " + esc(p.newsWarning) + "</b> <a href='sb://page/calendar'>See the Economic Calendar &rarr;</a></p>";
        int at = html.indexOf("</h1>");
        return at < 0 ? html : html.substring(0, at + 5) + banner + html.substring(at + 5);
    }

    static String page(Palette k, String body) {
        String css = "body{font-family:" + k.cssFont() + ";font-size:13pt;color:" + (k.text()) + ";background-color:" + (k.bg())
                + ";margin:14px}"
                + "h1{font-size:19pt;margin:0 0 4px 0;color:" + (k.text()) + "}"
                + "h2{font-size:15pt;margin:14px 0 6px 0;color:" + (k.accent()) + "}"
                + "h3{font-size:13.5pt;margin:0 0 6px 0}"
                + ".card{background-color:" + (k.panel()) + ";border:1px solid " + (k.line()) + ";padding:10px 12px;margin:0 0 12px 0}"
                + ".muted{color:" + (k.muted()) + "}"
                + ".good{color:" + (k.good()) + "}.bad{color:" + (k.bad()) + "}.warn{color:" + (k.warn()) + "}"
                + ".label{font-weight:bold;margin-top:6px}"
                + "ul{margin-top:2px;margin-bottom:4px;margin-left:22px}li{margin-bottom:3px}"
                + ".banner{background-color:" + (k.mix(k.panel(), k.warn(), 0.18)) + ";padding:8px 10px;border:1px solid " + (k.warn()) + "}"
                + "td{padding:3px 8px 3px 0}";
        return "<html><head><meta name=viewport content='width=device-width, initial-scale=1'><style>" + css + extraCss(k)
                + "</style></head><body>" + body + "</body></html>";
    }

    public static String message(Palette k, String title, String text) {
        return page(k, "<h1>" + esc(title) + "</h1><p class=muted>" + esc(text) + "</p>");
    }

    public static String ideas(Palette k, String title, String intro, List<Advisor.Idea> ideas, String empty, boolean demo) {
        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(esc(title)).append(demo ? " <span class=warn>(practice data)</span>" : "").append("</h1>");
        b.append("<p class=muted>").append(esc(intro)).append("</p>");
        if (ideas.isEmpty()) b.append("<div class=card><p>").append(esc(empty)).append("</p></div>");
        int n = 1;
        for (Advisor.Idea i : ideas) {
            String cardTitle = i.title != null ? i.title : i.headline;
            String tone = i.tone > 0 ? k.good() : i.tone < 0 ? k.bad() : k.warn();
            b.append("<div class=card><h3 style='margin-bottom:2px'>#").append(n++).append("&nbsp; ").append(esc(cardTitle)).append("</h3>");
            if (i.action != null)
                b.append("<div style='color:").append(tone).append(";font-weight:bold;margin:0 0 8px 0'>").append(esc(starsInText(i.action))).append("</div>");
            b.append("<div class=label>Why</div><ul>");
            for (String w : i.why) b.append("<li>").append(signalHtml(w)).append("</li>");
            b.append("</ul>");
            if (!i.howTo.isEmpty()) {
                b.append("<div class=label>How to do it</div><ul>");
                for (String h : i.howTo) b.append("<li>").append(esc(h)).append("</li>");
                b.append("</ul>");
            }
            if (i.risk != null) b.append("<p class=warn><b>Risk:</b> ").append(esc(i.risk)).append("</p>");
            if (i.stock != null && i.kind != Advisor.Kind.ZERO_DTE) {
                String t = esc(i.stock.ticker);
                b.append("<p><a href='sb://stock/").append(t).append("'>Full explanation and chart for ").append(t).append(" &rarr;</a>")
                 .append(" &nbsp;&middot;&nbsp; <a href='sb://alert/").append(t).append("'>&#128276; Set a price alert</a>");
                if ("USD".equals(i.stock.currency) && i.stock.ticker.indexOf('.') < 0)
                    b.append(" &nbsp;&middot;&nbsp; <a href='sb://pbuy/").append(t).append("'>Paper-trade it (pretend money)</a>");
                b.append("</p>");
            }
            b.append("</div>");
        }
        b.append(disclaimer());
        return page(k, b.toString());
    }

    /** Colours the [+] / [-] / [=] markers. */
    private static String signalHtml(String w) {
        if (w.startsWith("[+] ")) return "<b class=good>[+]</b> " + esc(w.substring(4));
        if (w.startsWith("[-] ")) return "<b class=bad>[-]</b> " + esc(w.substring(4));
        if (w.startsWith("[=] ")) return "<b class=muted>[=]</b> " + esc(w.substring(4));
        return esc(w);
    }

    /** Extra styles only used by phone pages (the desktop's HTML engine ignores what it doesn't know). */
    private static String extraCss(Palette k) {
        return "a{color:" + k.accent() + ";text-decoration:none}"
                + "table.grid{width:100%;border-collapse:collapse}"
                + "table.grid td,table.grid th{padding:7px 6px;border-bottom:1px solid " + k.line() + ";text-align:left}"
                + "table.grid th{color:" + k.muted() + ";font-size:11pt}"
                + ".num{text-align:right !important;white-space:nowrap}"
                + ".pill{display:inline-block;padding:2px 10px;margin:2px;border:1px solid " + k.line() + ";border-radius:14px}";
    }

    // ------------------------------------------------------------------ phone pages
    public static String markets(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>World markets</h1>");
        b.append("<p><b style='color:").append(k.accent()).append("'>").append(esc(WorldMarkets.mood(a.markets))).append("</b></p>");
        if (!a.demo && WorldMarkets.lastPriceUpdate > 0)
            b.append("<p class=muted style='font-size:10pt'>Index prices update every minute while this page is open (some exchanges are a few minutes delayed). Last update: ")
             .append(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(WorldMarkets.lastPriceUpdate))).append("</p>");
        b.append("<div class=card><table class=grid><tr><th>Market</th><th class=num>Price</th><th class=num>Today</th><th class=num>Month</th></tr>");
        for (WorldMarkets.Market m : a.markets) {
            b.append("<tr><td>").append(esc(m.name)).append("<br><span style='font-size:10pt' class=").append(m.isOpenNow() ? "good>OPEN" : "muted>closed")
             .append("</span><span class=muted style='font-size:10pt'> &middot; ").append(m.localTime()).append("</span></td><td class=num><b>")
             .append(WorldMarkets.price(m.price)).append("</b></td><td class=num>").append(pct(m.changePct)).append("</td><td class=num>").append(pct(m.monthPct)).append("</td></tr>");
        }
        b.append("</table></div><p><a href='sb://page/worldstocks'>The biggest companies of each market, and where to buy them &rarr;</a></p>"
                + "<p class=muted>An \"index\" is a basket of a country's biggest companies. Asian markets open first, then Europe, then the Americas.</p>");
        return page(k, b.toString());
    }

    /** Your price alerts. */
    public static String alerts(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>Price alerts</h1><p class=muted>Get told when a stock reaches a price. Type an alert above, for example "
                + "<b>NVDA above 150</b>, <b>TSLA below 200</b> or <b>AAPL move 5%</b> (a move of 5% up or down in one day). "
                + "Alerts are checked on every refresh and every few minutes while StockBuddy runs.</p>");
        java.util.List<Alerts.Alert> list = Alerts.load();
        if (list.isEmpty()) return page(k, b.append("<div class=card>No alerts yet.</div>").toString());
        b.append("<div class=card><table class=grid><tr><th>Alert</th><th class=num>Price now</th><th>Status</th><th></th></tr>");
        boolean anyTriggered = false;
        for (Alerts.Alert al : list) {
            Stock s = a.stocks.get(al.ticker);
            String now = s == null || Double.isNaN(s.price) ? "<span class=muted>-</span>" : esc(Advisor.money(s)) + "<br>" + pct(s.changePct);
            String status = al.triggeredAt == null ? "<span class=muted>Waiting</span>"
                    : "<b class=good>&#128276; Triggered</b><br><span class=muted style='font-size:10pt'>" + esc(al.triggeredAt) + " at " + Alerts.num(al.triggeredPrice) + "</span>";
            anyTriggered |= al.triggeredAt != null;
            b.append("<tr><td><a href='sb://stock/").append(esc(al.ticker)).append("'><b>").append(esc(al.ticker)).append("</b></a> ")
             .append(esc(al.describe().substring(al.ticker.length()))).append("</td><td class=num>").append(now).append("</td><td>").append(status)
             .append("</td><td><a href='sb://alertdel/").append(esc(al.id)).append("'>Remove</a></td></tr>");
        }
        b.append("</table></div>");
        if (anyTriggered) b.append("<p><a href='sb://alertclear'>Remove all triggered alerts</a></p>");
        return page(k, b.toString());
    }

    /** The paper-trading portfolio (pretend money). */
    public static String portfolio(Palette k, Analyzer a) {
        PaperPortfolio p = PaperPortfolio.load();
        java.util.Map<String, Double> prices = new java.util.LinkedHashMap<>();
        for (String t : p.positions.keySet()) { Stock s = a.stocks.get(t); if (s != null && !Double.isNaN(s.price)) prices.put(t, s.price); }
        double[] v = p.value(prices);
        double total = p.cash + v[0], gain = total - p.startCash, gainPct = 100 * gain / p.startCash;
        StringBuilder b = new StringBuilder("<h1>Paper trading</h1><p class=muted>Practise with <b>pretend money</b> - nothing real is bought or sold. "
                + "Type a trade above, for example <b>buy 10 AAPL</b>, <b>sell 5 AAPL</b> or <b>sell all AAPL</b>. Trades use today's real prices "
                + "(US-listed stocks)." + (a.demo ? " <span class=warn>Practice mode is on: prices are made up.</span>" : "") + "</p>");
        b.append("<div class=card><table class=grid>")
         .append("<tr><td class=muted>Total value</td><td class=num><b style='font-size:15pt'>").append(String.format(java.util.Locale.US, "$%,.2f", total)).append("</b></td></tr>")
         .append("<tr><td class=muted>Since you started (").append(esc(p.started)).append(")</td><td class=num><b class=").append(gain >= 0 ? "good" : "bad").append(">")
         .append(String.format(java.util.Locale.US, "%+,.2f (%+.2f%%)", gain, gainPct)).append("</b></td></tr>")
         .append("<tr><td class=muted>Pretend cash</td><td class=num>").append(String.format(java.util.Locale.US, "$%,.2f", p.cash)).append("</td></tr>")
         .append("<tr><td class=muted>Invested in stocks</td><td class=num>").append(String.format(java.util.Locale.US, "$%,.2f", v[0])).append("</td></tr>");
        double spxNow = Double.NaN;
        for (WorldMarkets.Market m : a.markets) if (m.symbol.equals("^GSPC")) spxNow = m.price;
        if (!Double.isNaN(p.spxAtStart) && !Double.isNaN(spxNow) && !a.demo) {
            double spx = 100 * (spxNow / p.spxAtStart - 1);
            b.append("<tr><td class=muted>The S&amp;P 500 did</td><td class=num>").append(pct(spx)).append(" &nbsp;").append(gainPct >= spx ? "<b class=good>you're ahead</b>" : "<b class=warn>you're behind</b>").append("</td></tr>");
        }
        b.append("</table></div>");
        if (!p.positions.isEmpty()) {
            b.append("<h2>What you hold</h2><div class=card><table class=grid><tr><th>Stock</th><th class=num>Shares</th><th class=num>Paid / now</th><th class=num>Profit</th><th></th></tr>");
            for (PaperPortfolio.Position pos : p.positions.values()) {
                Double now = prices.get(pos.ticker);
                double pl = now == null ? Double.NaN : pos.shares * now - pos.cost;
                b.append("<tr><td><a href='sb://stock/").append(esc(pos.ticker)).append("'><b>").append(esc(pos.ticker)).append("</b></a></td><td class=num>")
                 .append(PaperPortfolio.fmt(pos.shares)).append("</td><td class=num>").append(String.format(java.util.Locale.US, "$%,.2f", pos.avgCost())).append("<br>")
                 .append(now == null ? "<span class=muted>refresh</span>" : String.format(java.util.Locale.US, "$%,.2f", now)).append("</td><td class=num>")
                 .append(Double.isNaN(pl) ? "-" : "<b class=" + (pl >= 0 ? "good" : "bad") + ">" + String.format(java.util.Locale.US, "%+,.2f", pl) + "</b><br>"
                         + pct(100 * pl / pos.cost))
                 .append("</td><td><a href='sb://psell/").append(esc(pos.ticker)).append("'>Sell all</a></td></tr>");
            }
            b.append("</table></div>");
        }
        if (!p.trades.isEmpty()) {
            b.append("<h2>Your trades</h2><div class=card><table class=grid>");
            for (int i = p.trades.size() - 1; i >= 0 && i >= p.trades.size() - 30; i--) {
                PaperPortfolio.Trade t = p.trades.get(i);
                b.append("<tr><td>").append(esc(t.date)).append("</td><td><b class=").append(t.side.equals("BUY") ? "good" : "bad").append(">").append(t.side)
                 .append("</b> ").append(PaperPortfolio.fmt(t.shares)).append(" ").append(esc(t.ticker)).append("</td><td class=num>")
                 .append(String.format(java.util.Locale.US, "$%,.2f", t.price)).append("</td></tr>");
            }
            b.append("</table></div>");
        }
        b.append("<p><a href='sb://preset'>Start again with $10,000</a></p>");
        return page(k, b.toString());
    }

    /** This week's economic calendar, with plain-English explanations. */
    public static String calendar(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>Economic calendar</h1>");
        b.append("<p class=muted>Big economic news (inflation, jobs, interest rates) can move whole markets in seconds - especially "
                + "<b class=bad>high-impact</b> events. Times are shown in your own time zone. Source: ForexFactory"
                + (a.demo ? " <span class=warn>(practice data)</span>" : "") + ".</p>");
        if (a.events.isEmpty()) return page(k, b.append("<div class=card>No calendar data right now. Check the Data Sources page.</div>").toString());
        java.util.List<EconomicCalendar.Event> big = EconomicCalendar.todaysBigUsEvents(a.events);
        if (!big.isEmpty()) b.append("<p class=banner><b>&#9888; Big US news today.</b> Be extra careful with short-term trades and 0DTE options around these times.</p>");
        java.time.ZoneId me = java.time.ZoneId.systemDefault();
        java.time.LocalDate today = java.time.LocalDate.now(me), shown = null;
        java.time.format.DateTimeFormatter dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d", java.util.Locale.US);
        boolean open = false;
        for (EconomicCalendar.Event e : a.events) {
            if (!e.high() && !e.medium()) continue;   // low-impact news rarely matters for beginners
            java.time.ZonedDateTime local = e.time == null ? null : e.time.withZoneSameInstant(me);
            java.time.LocalDate d = local != null ? local.toLocalDate() : e.day;
            if (d == null) continue;
            if (!d.equals(shown)) {
                if (open) b.append("</table></div>");
                b.append("<h2>").append(d.format(dayFmt)).append(d.equals(today) ? " &middot; TODAY" : "").append("</h2><div class=card><table class=grid>");
                shown = d; open = true;
            }
            String ex = EconomicCalendar.explain(e.title);
            b.append("<tr><td width=60 style='white-space:nowrap;width:60px'>").append(local == null ? "All day" : local.toLocalTime().toString()).append("</td><td>")
             .append("<b class=").append(e.high() ? "bad" : "warn").append(">").append(e.high() ? "HIGH" : "Medium").append("</b> &middot; ")
             .append(esc(EconomicCalendar.region(e.country))).append(": <b>").append(esc(e.title)).append("</b>");
            if (!ex.isEmpty()) b.append("<br><span class=muted style='font-size:10.5pt'>").append(esc(ex)).append("</span>");
            StringBuilder nums = new StringBuilder();
            if (!e.forecast.isEmpty()) nums.append("Expected ").append(esc(e.forecast)).append(" &nbsp; ");
            if (!e.previous.isEmpty()) nums.append("Last time ").append(esc(e.previous)).append(" &nbsp; ");
            if (!e.actual.isEmpty()) nums.append("<b>Actual ").append(esc(e.actual)).append("</b>");
            if (nums.length() > 0) b.append("<br><span style='font-size:10.5pt'>").append(nums).append("</span>");
            b.append("</td></tr>");
        }
        if (open) b.append("</table></div>");
        b.append("<p class=muted>Tip: when the actual number is very different from what was expected, prices usually move the most.</p>");
        return page(k, b.toString());
    }

    /** The biggest companies of the other world markets, with where to buy them. */
    public static String worldStocks(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>World stocks</h1><p class=muted>The biggest companies of the world's largest stock markets, "
                + "what StockBuddy thinks of them today, and <b>where you can buy them</b>. Tap a name for the full explanation and chart."
                + (a.demo ? " <span class=warn>(practice data)</span>" : "") + "</p>");
        int top = WorldStocks.top();
        b.append("<p><b>Companies per market:</b> &nbsp;");
        for (int n : WorldStocks.TOP_CHOICES)
            b.append(n == top ? "<b style='color:" + k.accent() + "'>[ Top " + n + " ]</b>" : "<a href='sb://wtop/" + n + "'>Top " + n + "</a>").append(" &nbsp;&middot;&nbsp; ");
        b.append("<a href='sb://wtop/0'>Off</a> &nbsp; <span class=muted>(more companies = slower refresh)</span></p>");
        if (top == 0) return page(k, b.append("<div class=card>World stocks are turned off. Choose 3, 5, 10 or 20 above.</div>").toString());
        boolean changedSinceRefresh = false;
        for (WorldStocks.Entry e : WorldStocks.active()) if (!a.stocks.containsKey(e.analysedTicker())) changedSinceRefresh = true;
        if (changedSinceRefresh) b.append("<p class=banner>Refresh the data to analyse the companies you just added.</p>");
        String market = null;
        boolean open = false;
        for (WorldStocks.Entry e : WorldStocks.active()) {
            Stock s = a.stocks.get(e.analysedTicker());
            if (!e.market.equals(market)) {
                if (open) b.append("</div>");
                WorldMarkets.Market m = null;
                for (WorldMarkets.Market x : a.markets) if (x.symbol.equals(e.market)) m = x;
                b.append("<h2>").append(esc(WorldStocks.marketName(e.market))).append("</h2>");
                if (m != null) b.append("<p class=muted style='font-size:10.5pt'>Index ").append(WorldMarkets.price(m.price)).append(" &nbsp;")
                        .append(pct(m.changePct)).append(" today &middot; ").append(m.isOpenNow() ? "<b class=good>open now</b>" : "closed now").append("</p>");
                b.append("<div>");
                market = e.market;
                open = true;
            }
            b.append("<div class=card><b><a href='sb://stock/").append(esc(e.analysedTicker())).append("'>").append(esc(e.name)).append("</a></b> <span class=muted>(")
             .append(esc(e.analysedTicker())).append(")</span>");
            if (s != null && s.tech != null) {
                String cls = s.score >= 0.2 ? "good" : s.score <= -0.2 ? "bad" : "warn";
                b.append(" &nbsp; ").append(esc(Advisor.money(s))).append(" &nbsp;").append(pct(s.changePct)).append(" &nbsp; <b class=").append(cls).append(">")
                 .append(esc(Scorer.verdict(s.score))).append("</b>");
            } else b.append(" &nbsp; <span class=muted>no price data right now</span>");
            b.append("<ul style='margin-top:6px'>");
            for (String h : e.howToBuy()) b.append("<li>").append(esc(h)).append("</li>");
            b.append("</ul></div>");
        }
        if (open) b.append("</div>");
        b.append("<p class=muted>Prices of stocks analysed on their home exchange are in local currency. Buying abroad can add currency-exchange "
                + "costs and different taxes - check with your broker.</p>");
        return page(k, b.toString());
    }

    /** How StockBuddy's past ideas actually turned out. */
    public static String trackRecord(Palette k, Analyzer a) {
        java.util.List<TrackRecord.Pick> picks = TrackRecord.withPrices(a);
        StringBuilder b = new StringBuilder("<h1>Track record</h1><p class=muted>Every day StockBuddy remembers its ideas and the price at that moment. "
                + "Here you can see how they actually did - the honest way to learn how much to trust the signals. It gets more useful the longer you use the app."
                + (a.demo ? " <span class=warn>(practice data)</span>" : "") + "</p>");
        if (picks.isEmpty())
            return page(k, b.append("<div class=card>Nothing yet - your first ideas are saved today. Come back in a few days!</div>").toString());
        b.append("<div class=card><table class=grid><tr><th>Ideas older than today</th><th class=num>Went the right way</th><th class=num>Average move</th></tr>");
        String[] names = {"Buys (want: up)", "Sells (want: down)", "Calls (want: up)", "Puts (want: down)"};
        int i = 0;
        for (java.util.Map.Entry<String, double[]> e : TrackRecord.summary(picks).entrySet()) {
            double[] v = e.getValue();
            String name = names[i++];
            if (v[0] == 0) { b.append("<tr><td>").append(name).append("</td><td class=num class=muted>-</td><td class=num>-</td></tr>"); continue; }
            double rate = 100 * v[1] / v[0];
            b.append("<tr><td>").append(name).append(" &middot; ").append((int) v[0]).append("</td><td class=num><b class=").append(rate >= 55 ? "good" : rate < 45 ? "bad" : "warn")
             .append(">").append(String.format("%.0f%%", rate)).append("</b></td><td class=num>").append(pct(v[2])).append("</td></tr>");
        }
        b.append("</table><p class=muted style='font-size:10pt'>About 50% is what you'd expect from a coin flip. A few weeks of data is still very little - "
                + "markets go through good and bad spells.</p></div>");
        b.append("<h2>Every idea (last ").append(TrackRecord.SHOW_DAYS).append(" days)</h2><div class=card><table class=grid><tr><th>Idea</th><th class=num>Then</th><th class=num>Since</th></tr>");
        java.util.List<TrackRecord.Pick> sorted = new java.util.ArrayList<>(picks);
        sorted.sort((x, y) -> y.date.compareTo(x.date));
        for (TrackRecord.Pick p : sorted) {
            Boolean w = p.worked();
            long ago = TrackRecord.daysAgo(p);
            b.append("<tr><td><b>").append(esc(p.kind)).append("</b> <a href='sb://stock/").append(esc(p.ticker)).append("'>").append(esc(p.ticker)).append("</a> <span class=muted>")
             .append(esc(p.name)).append("<br>").append(ago == 0 ? "today" : ago + (ago == 1 ? " day ago" : " days ago")).append("</span></td><td class=num>")
             .append(String.format(java.util.Locale.US, "%,.2f", p.price)).append("</td><td class=num>").append(pct(p.change()))
             .append(w == null || ago == 0 ? "" : w ? " <b class=good>&#10003;</b>" : " <b class=bad>&#10007;</b>").append("</td></tr>");
        }
        b.append("</table></div>");
        return page(k, b.toString());
    }

    public static String ranking(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>Every stock I checked</h1><p class=muted>Tap a ticker for the full explanation.</p>");
        java.util.List<Stock> list = new java.util.ArrayList<>(a.stocks.values());
        list.sort((x, y) -> Double.compare(y.score, x.score));
        b.append("<div class=card><table class=grid><tr><th>Stock</th><th class=num>Price</th><th>Verdict</th></tr>");
        for (Stock s : list) {
            String cls = s.score >= 0.2 ? "good" : s.score <= -0.2 ? "bad" : "warn";
            b.append("<tr><td><a href='sb://stock/").append(esc(s.ticker)).append("'><b>").append(esc(s.ticker)).append("</b></a><br><span class=muted style='font-size:10pt'>")
             .append(esc(s.name == null ? "" : s.name)).append("</span></td><td class=num>").append(esc(Advisor.money(s))).append("<br>")
             .append(pct(s.changePct)).append("</td><td class=").append(cls).append("><b>").append(esc(Scorer.verdict(s.score)))
             .append("</b><br><span class=muted>").append(prettyStars(Scorer.stars(s.confidence))).append("</span></td></tr>");
        }
        b.append("</table></div>");
        return page(k, b.toString());
    }

    public static String watchlist(Palette k, java.util.List<String> tickers) {
        StringBuilder b = new StringBuilder("<h1>My watchlist</h1><p class=muted>These stocks are always checked, plus today's biggest "
                + "movers and the stocks Reddit talks about most. Type tickers above to add them (saved straight away). Tap a ticker to look it up, or &times; to remove it.</p><div class=card>");
        for (String t : tickers)
            b.append("<span class=pill><a href='sb://stock/").append(esc(t)).append("'><b>").append(esc(t)).append("</b></a> &nbsp;<a href='sb://remove/")
             .append(esc(t)).append("'>&times;</a></span> ");
        b.append("</div><p><a href='sb://reset'>Reset to the default list</a></p><p class=muted>Tap the refresh button at the top to analyse your changes.</p>");
        return page(k, b.toString());
    }

    /** The price chart as SVG for the phone app, drawn with the user's chart options, plus tappable controls. */
    public static String chartSvg(Palette k, Stock s) {
        ChartOptions o = ChartOptions.load();
        java.util.List<Double> c = s.closes;
        int n = c.size();
        if (n < 2) return "";
        boolean candles = o.type.equals("candles") && s.hasCandles();
        int from = Math.max(0, n - o.range), count = n - from;
        java.util.List<Object[]> lines = new java.util.ArrayList<>();
        if (o.sma20) lines.add(new Object[]{ChartOptions.sma(c, 20), k.mix(k.accent(), k.text(), 0.45), "SMA 20"});
        if (o.sma50) lines.add(new Object[]{ChartOptions.sma(c, 50), k.accent(), "SMA 50"});
        if (o.sma200) lines.add(new Object[]{ChartOptions.sma(c, 200), k.warn(), "SMA 200"});
        if (o.ema20) lines.add(new Object[]{ChartOptions.ema(c, 20), "#b07cff", "EMA 20"});
        double[][] bb = o.bollinger ? ChartOptions.bollinger(c) : null;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = from; i < n; i++) {
            lo = Math.min(lo, candles ? s.lows.get(i) : c.get(i));
            hi = Math.max(hi, candles ? s.highs.get(i) : c.get(i));
            for (Object[] l : lines) { double v = ((double[]) l[0])[i]; if (!Double.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
            if (bb != null && !Double.isNaN(bb[0][i])) { lo = Math.min(lo, bb[1][i]); hi = Math.max(hi, bb[0][i]); }
        }
        double pad = (hi - lo) * 0.06 + 1e-9; lo -= pad; hi += pad;
        boolean lower = !o.lower.equals("none");
        int w = 600, mainH = 220, lowH = lower ? 70 : 0, gap = lower ? 14 : 0, top = 8;
        int height = top + mainH + gap + lowH + 24;
        Chart ch = new Chart(from, count, w, lo, hi, top, mainH);
        StringBuilder g = new StringBuilder();
        g.append("<svg viewBox='0 0 ").append(w + 64).append(' ').append(height).append("' width='100%' xmlns='http://www.w3.org/2000/svg'>");
        for (int i = 0; i <= 4; i++) {
            double y = top + mainH * i / 4.0;
            g.append(fmt("<line x1='0' x2='%d' y1='%.1f' y2='%.1f' stroke='%s' stroke-width='1'/>", w, y, y, k.line()));
            g.append(fmt("<text x='%d' y='%.1f' fill='%s' font-size='13'>%,.2f</text>", w + 6, y + 4, k.muted(), hi - (hi - lo) * i / 4));
        }
        if (bb != null) {
            StringBuilder band = new StringBuilder();
            for (int i = from; i < n; i++) if (!Double.isNaN(bb[0][i])) band.append(band.length() == 0 ? "M" : "L").append(ch.pt(i, bb[0][i])).append(' ');
            for (int i = n - 1; i >= from; i--) if (!Double.isNaN(bb[1][i])) band.append("L").append(ch.pt(i, bb[1][i])).append(' ');
            if (band.length() > 0) g.append("<path d='").append(band).append("Z' fill='").append(k.muted()).append("' fill-opacity='0.15' stroke='")
                    .append(k.muted()).append("' stroke-opacity='0.5' stroke-width='1'/>");
        }
        boolean up = c.get(n - 1) >= c.get(from);
        String col = up ? k.good() : k.bad();
        if (candles) {
            double cw = Math.max(1, w / (double) count * 0.65);
            for (int i = from; i < n; i++) {
                double op = s.opens.get(i), cl = c.get(i);
                String cc = cl >= op ? k.good() : k.bad();
                double x = ch.x(i);
                g.append(fmt("<line x1='%.1f' x2='%.1f' y1='%.1f' y2='%.1f' stroke='%s' stroke-width='1'/>", x, x, ch.y(s.highs.get(i)), ch.y(s.lows.get(i)), cc));
                double y1 = ch.y(Math.max(op, cl)), y2 = ch.y(Math.min(op, cl));
                g.append(fmt("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s'/>", x - cw / 2, y1, cw, Math.max(1, y2 - y1), cc));
            }
        } else {
            StringBuilder pts = new StringBuilder();
            for (int i = from; i < n; i++) pts.append(ch.pt(i, c.get(i))).append(' ');
            if (o.type.equals("area"))
                g.append("<path d='M").append(fmt("%.1f,%d ", ch.x(from), top + mainH)).append("L").append(pts.toString().trim().replace(" ", " L"))
                 .append(fmt(" L%.1f,%d Z", ch.x(n - 1), top + mainH)).append("' fill='").append(col).append("' fill-opacity='0.18'/>");
            g.append("<polyline fill='none' stroke='").append(col).append("' stroke-width='2.4' points='").append(pts).append("'/>");
        }
        for (Object[] l : lines) g.append(ch.polyline((double[]) l[0], (String) l[1], 1.6));
        if (lower) g.append(lowerSvg(k, s, o, from, count, w, top + mainH + gap, lowH));
        g.append("<text x='0' y='").append(height - 4).append("' fill='").append(k.muted()).append("' font-size='13'>")
         .append(ChartOptions.RANGE_NAMES[rangeIndex(o.range)]).append(" ago</text>");
        g.append("<text x='").append(w - 40).append("' y='").append(height - 4).append("' fill='").append(k.muted()).append("' font-size='13'>today</text></svg>");

        StringBuilder legend = new StringBuilder("<div style='font-size:10.5pt'><span style='color:" + col + "'>&#9644; " + (candles ? "Candles" : "Price") + "</span>");
        for (Object[] l : lines) legend.append(" &nbsp; <span style='color:").append(l[1]).append("'>&#9644; ").append(l[2]).append("</span>");
        if (bb != null) legend.append(" &nbsp; <span style='color:").append(k.muted()).append("'>&#9644; Bollinger</span>");
        legend.append("</div>");
        return "<div class=card>" + g + legend + chartControls(k, o) + "</div>";
    }

    private static String chartControls(Palette k, ChartOptions o) {
        StringBuilder b = new StringBuilder("<div style='margin-top:8px;font-size:10.5pt;line-height:2.1'>");
        b.append("<span class=muted>Chart:</span> ");
        for (int i = 0; i < ChartOptions.TYPES.length; i++) b.append(chip(k, "type/" + ChartOptions.TYPES[i], ChartOptions.TYPE_NAMES[i], o.type.equals(ChartOptions.TYPES[i])));
        b.append(" &nbsp;<span class=muted>Period:</span> ");
        for (int i = 0; i < ChartOptions.RANGES.length; i++) b.append(chip(k, "range/" + ChartOptions.RANGES[i], ChartOptions.RANGE_NAMES[i], o.range == ChartOptions.RANGES[i]));
        b.append("<br><span class=muted>Lines:</span> ");
        for (int i = 0; i < ChartOptions.OVERLAYS.length; i++) b.append(chip(k, "toggle/" + ChartOptions.OVERLAYS[i], ChartOptions.OVERLAY_NAMES[i], o.overlay(ChartOptions.OVERLAYS[i])));
        b.append("<br><span class=muted>Bottom panel:</span> ");
        for (int i = 0; i < ChartOptions.LOWERS.length; i++) b.append(chip(k, "lower/" + ChartOptions.LOWERS[i], ChartOptions.LOWER_NAMES[i], o.lower.equals(ChartOptions.LOWERS[i])));
        return b.append("</div>").toString();
    }

    private static String chip(Palette k, String link, String text, boolean on) {
        return "<a href='sb://chart/" + link + "' style='display:inline-block;padding:1px 9px;margin:1px;border-radius:12px;border:1px solid "
                + (on ? k.accent() : k.line()) + ";background-color:" + (on ? k.accent() : k.panel()) + ";color:"
                + (on ? (k.dark() ? k.bg() : "#ffffff") : k.text()) + "'>" + text + "</a>";
    }

    private static String lowerSvg(Palette k, Stock s, ChartOptions o, int from, int count, int w, int top, int h) {
        java.util.List<Double> c = s.closes;
        int n = c.size();
        StringBuilder g = new StringBuilder(fmt("<line x1='0' x2='%d' y1='%d' y2='%d' stroke='%s'/>", w, top, top, k.line()));
        double bw = Math.max(1, w / (double) count * 0.7);
        if (o.lower.equals("volume")) {
            double max = 0;
            for (int i = from; i < n && i < s.volumes.size(); i++) max = Math.max(max, s.volumes.get(i));
            if (max > 0) for (int i = from; i < n && i < s.volumes.size(); i++) {
                double bh = h * s.volumes.get(i) / max;
                String cc = i == 0 || c.get(i) >= c.get(i - 1) ? k.good() : k.bad();
                g.append(fmt("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s' fill-opacity='0.6'/>", w * (i - from) / (double) Math.max(1, count - 1) - bw / 2, top + h - bh, bw, bh, cc));
            }
            g.append(fmt("<text x='%d' y='%d' fill='%s' font-size='12'>Volume</text>", w + 6, top + 13, k.muted()));
        } else if (o.lower.equals("rsi")) {
            Chart ch = new Chart(from, count, w, 0, 100, top, h);
            for (int lvl : new int[]{30, 70})
                g.append(fmt("<line x1='0' x2='%d' y1='%.1f' y2='%.1f' stroke='%s' stroke-dasharray='4,4'/><text x='%d' y='%.1f' fill='%s' font-size='12'>%d</text>",
                        w, ch.y(lvl), ch.y(lvl), k.muted(), w + 6, ch.y(lvl) + 4, k.muted(), lvl));
            g.append(ch.polyline(ChartOptions.rsi(c, 14), "#b07cff", 1.6));
            g.append(fmt("<text x='%d' y='%d' fill='%s' font-size='12'>RSI</text>", w + 30, top + 13, k.muted()));
        } else if (o.lower.equals("macd")) {
            double[][] m = ChartOptions.macd(c);
            double max = 1e-9;
            for (int i = from; i < n; i++) for (double[] v : m) if (!Double.isNaN(v[i])) max = Math.max(max, Math.abs(v[i]));
            Chart ch = new Chart(from, count, w, -max, max, top, h);
            for (int i = from; i < n; i++) {
                if (Double.isNaN(m[2][i])) continue;
                double y0 = ch.y(0), y1 = ch.y(m[2][i]);
                g.append(fmt("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s' fill-opacity='0.6'/>", ch.x(i) - bw / 2, Math.min(y0, y1), bw, Math.abs(y1 - y0),
                        m[2][i] >= 0 ? k.good() : k.bad()));
            }
            g.append(ch.polyline(m[0], k.accent(), 1.5)).append(ch.polyline(m[1], k.warn(), 1.3));
            g.append(fmt("<text x='%d' y='%d' fill='%s' font-size='12'>MACD</text>", w + 6, top + 13, k.muted()));
        }
        return g.toString();
    }

    private static int rangeIndex(int r) {
        for (int i = 0; i < ChartOptions.RANGES.length; i++) if (ChartOptions.RANGES[i] == r) return i;
        return ChartOptions.RANGES.length - 1;
    }

    private static String fmt(String f, Object... a) { return String.format(java.util.Locale.US, f, a); }

    /** Maps day index / price to SVG coordinates. */
    private static final class Chart {
        final int from, count, w, top, h;
        final double lo, hi;
        Chart(int from, int count, int w, double lo, double hi, int top, int h) {
            this.from = from; this.count = count; this.w = w; this.lo = lo; this.hi = hi; this.top = top; this.h = h;
        }
        double x(int i) { return count <= 1 ? 0 : (double) w * (i - from) / (count - 1); }
        double y(double v) { return top + h * (hi - v) / (hi - lo); }
        String pt(int i, double v) { return fmt("%.1f,%.1f", x(i), y(v)); }
        String polyline(double[] v, String color, double width) {
            StringBuilder p = new StringBuilder();
            for (int i = from; i < v.length; i++) if (!Double.isNaN(v[i])) p.append(pt(i, v[i])).append(' ');
            return p.length() == 0 ? "" : "<polyline fill='none' stroke='" + color + "' stroke-width='" + width + "' points='" + p + "'/>";
        }
    }

    /** Stock page for phones: same as the desktop one, plus the chart. */
    public static String stockWithChart(Palette k, Stock s) {
        String html = stock(k, s);
        int at = html.indexOf("<div class=card><h3>Overall:");
        return at < 0 ? html : html.substring(0, at) + chartSvg(k, s) + html.substring(at);
    }

    public static String stock(Palette k, Stock s) {
        Technicals x = s.tech;
        String cls = s.score >= 0.2 ? "good" : s.score <= -0.2 ? "bad" : "warn";
        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(esc(s.display())).append("</h1>");
        b.append("<p>Price <b>").append(esc(Advisor.money(s))).append("</b> &nbsp; Today ").append(pct(s.changePct))
         .append(s.keyStats.containsKey("52-week range") ? " &nbsp; 1-year range $" + esc(s.keyStats.get("52-week range")).replace(" - ", " - $")
                 : String.format(" &nbsp; 1-year range $%.2f - $%.2f", x.low52, x.high52)).append("</p>");
        b.append("<div class=card><h3>Overall: <span class=").append(cls).append(">").append(Scorer.verdict(s.score)).append("</span></h3>")
         .append(String.format("Score %+.0f out of 100 &nbsp;&middot;&nbsp; confidence %s", s.score * 100, prettyStars(Scorer.stars(s.confidence))))
         .append("<p>").append(esc(Scorer.plainSummary(s))).append("</p></div>");
        b.append("<p><a href='sb://watch/").append(esc(s.ticker)).append("'>&#9733; Add ").append(esc(s.ticker)).append(" to My Watchlist</a>")
         .append(" &nbsp;&middot;&nbsp; <a href='sb://alert/").append(esc(s.ticker)).append("'>&#128276; Set a price alert</a>");
        if ("USD".equals(s.currency) && s.ticker.indexOf('.') < 0)
            b.append(" &nbsp;&middot;&nbsp; <a href='sb://pbuy/").append(esc(s.ticker)).append("'>Paper-trade it</a>");
        b.append("</p>");
        java.util.List<String[]> stats = KeyStats.ordered(s);
        if (!stats.isEmpty()) {
            b.append("<h2>Key statistics</h2><div class=card><table class=grid>");
            for (String[] st : stats) {
                String help = KeyStats.help(st[0]);
                b.append("<tr><td class=muted").append(help == null ? "" : " title='" + esc(help).replace("'", "&#39;") + "'").append(">").append(esc(st[0]))
                 .append(help == null ? "" : "<br><span style='font-size:9.5pt'>" + esc(help) + "</span>")
                 .append("</td><td class=num><b>").append(esc(st[1])).append("</b></td></tr>");
            }
            b.append("</table></div>");
        }
        if (!s.newsHeadlines.isEmpty()) {
            b.append("<h2>In the news</h2><ul>");
            for (String h : s.newsHeadlines) b.append("<li>").append(esc(h)).append("</li>");
            b.append("</ul>");
        }
        for (java.util.Map.Entry<String, java.util.Map<String, String>> f : s.customData.entrySet()) {
            b.append("<h2>From your CSV file: ").append(esc(f.getKey())).append("</h2><div class=card><table class=grid>");
            for (java.util.Map.Entry<String, String> c : f.getValue().entrySet())
                b.append("<tr><td class=muted>").append(esc(c.getKey())).append("</td><td class=num><b>").append(esc(c.getValue())).append("</b></td></tr>");
            b.append("</table></div>");
        }
        b.append("<h2>What each source says</h2>");
        for (Signal g : s.signals) {
            String c = g.score() > 0.1 ? "good" : g.score() < -0.1 ? "bad" : "muted";
            String m = g.score() > 0.1 ? "[+]" : g.score() < -0.1 ? "[-]" : "[=]";
            b.append("<div class=card><b class=").append(c).append(">").append(m).append("</b> <b>").append(esc(g.source()))
             .append("</b><br>").append(esc(g.reason())).append("</div>");
        }
        if (s.signals.size() < 3) b.append("<p class=muted>Only a few sources had data for this stock, so treat the result with extra caution.</p>");
        b.append("<h2>Chart numbers (StockCharts-style)</h2><table>")
         .append(row("50-day average", String.format("$%.2f", x.sma50)))
         .append(row("200-day average", String.format("$%.2f", x.sma200)))
         .append(row("RSI (0-100)", String.format("%.0f", x.rsi14) + (x.rsi14 > 70 ? " &nbsp;<span class=warn>rose fast</span>" : x.rsi14 < 30 ? " &nbsp;<span class=warn>fell fast</span>" : "")))
         .append(row("MACD momentum", x.macdHist > 0 ? "<span class=good>positive</span>" : "<span class=bad>negative</span>"))
         .append(row("SCTR rank", Double.isNaN(x.sctr) ? "n/a" : String.format("%.0f / 99", x.sctr)))
         .append(row("Typical yearly swing", String.format("%.0f%%", x.volatility * 100)))
         .append(row("Trading activity vs normal", String.format("%.1fx", x.volumeRatio)))
         .append("</table>");
        if (!s.redditQuotes.isEmpty()) {
            b.append("<h2>What Reddit is saying</h2><ul>");
            for (String q : s.redditQuotes) b.append("<li>").append(esc(q)).append("</li>");
            b.append("</ul>");
        }
        b.append(disclaimer());
        return page(k, b.toString());
    }

    public static String glossary(Palette k, String filter) {
        StringBuilder b = new StringBuilder("<h1>Beginner's dictionary</h1><p class=muted>Investing words, explained simply.</p>");
        String f = filter == null ? "" : filter.trim().toLowerCase();
        int shown = 0;
        for (Map.Entry<String, String> e : Glossary.WORDS.entrySet()) {
            if (!f.isEmpty() && !(e.getKey() + " " + e.getValue()).toLowerCase().contains(f)) continue;
            b.append("<div class=card><b>").append(esc(e.getKey())).append("</b><br>").append(esc(e.getValue())).append("</div>");
            shown++;
        }
        if (shown == 0) b.append("<p>No word matches \"").append(esc(filter)).append("\".</p>");
        return page(k, b.toString());
    }

    public static String sources(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>Where the data comes from</h1>");
        b.append("<p class=muted>Green = worked, orange = needs attention. Your files are kept in: ").append(esc(AppPaths.base().toString())).append("</p>");
        b.append("<div class=card><table>");
        a.sourceStatus.forEach((name, v) -> {
            boolean ok = v.startsWith("OK") || v.toLowerCase().startsWith("calc") || v.startsWith("practice");
            b.append("<tr><td><b>").append(esc(name)).append("</b></td><td class=").append(ok ? "good" : "warn").append(">")
             .append(esc(v)).append("</td></tr>");
        });
        b.append("</table></div>");
        b.append("<h2>Tips</h2><ul>")
         .append("<li><b>Stock Rover</b> needs a paid login, so export a CSV from Stock Rover (with Ticker and Fair Value columns) and click <b>Import Stock Rover CSV</b> below.</li>")
         .append("<li><b>WallStreetZen</b> sometimes blocks programs. You can import a CSV with Ticker, Zen Score and Zen Rating columns instead.</li>")
         .append("<li><b>StockCharts</b> has no public data feed. Its indicators (moving averages, RSI, MACD, SCTR) are calculated by StockBuddy from price history.</li>")
         .append("<li><b>Reddit</b> is read automatically (no account needed). If Reddit blocks it, add posts yourself: copy post titles from "
                 + "Reddit and use <b>Add Reddit posts</b>, or import a CSV with a <i>title</i> column (optional: date, subreddit, upvotes). "
                 + "Your own posts are used for " + RedditSource.MANUAL_DAYS + " days.</li></ul>");
        return page(k, b.toString());
    }

    private static String row(String a, String b) { return "<tr><td class=muted>" + esc(a) + "</td><td><b>" + b + "</b></td></tr>"; }

    static String pct(double v) {
        if (Double.isNaN(v)) return "<span class=muted>n/a</span>";
        return "<b class=" + (v > 0.05 ? "good" : v < -0.05 ? "bad" : "warn") + ">" + String.format("%+.2f%%", v) + "</b>";
    }

    private static String disclaimer() {
        return "<p class=banner><b>Important:</b> StockBuddy is a learning and research helper, not a financial advisor. "
                + "It can be wrong, data can be late, and you can lose money. Never invest money you can't afford to lose.</p>";
    }

    /** Turns "***.." into proper stars for the windowed app. */
    public static String prettyStars(String s) { return s.replace('*', '\u2605').replace('.', '\u2606'); }

    private static String starsInText(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("confidence ([*.]{5})").matcher(s);
        return m.find() ? s.substring(0, m.start(1)) + prettyStars(m.group(1)) + s.substring(m.end(1)) : s;
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
