package stockbuddy;

import java.util.List;
import java.util.Map;

/** Builds the nicely formatted pages shown inside the app windows, coloured by the current skin. */
public final class HtmlView {
    private HtmlView() {}

    /** The five I18n.tr("Today's ideas") tabs, shared by the desktop and phone apps. */
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
        String empty = tr(IDEA_TEXT[i][2]);
        if (i == 4 && p.market != null && !WorldMarkets.isUs(p.market)) empty = I18n.tr("0DTE ideas are only for US index options (SPY and QQQ). Choose All markets or a US market to see them.");
        else if (p.market != null && !empty.isEmpty()) empty += I18n.tr(" (in this market)");
        String html = ideas(k, tr(IDEA_TEXT[i][0]), tr(IDEA_TEXT[i][1]), ideaLists(p).get(i), empty, demo);
        if (p.market != null) {
            int at = html.indexOf("</h1>");
            String hint = WorldMarkets.isUs(p.market) ? "" : I18n.tr(" <span class=muted style='font-size:10.5pt'>- outside the US many companies only have chart data, and an idea ")
                    + I18n.tr("needs at least two sources, so fewer ideas appear. <a href='sb://page/worldstocks'>See every company's verdict on World Stocks &rarr;</a></span>");
            if (at > 0) html = html.substring(0, at + 5) + I18n.tr("<p><b style='color:") + k.accent() + I18n.tr("'>Showing only: ") + esc(WorldStocks.marketName(p.market))
                    + "</b>" + hint + "</p>" + html.substring(at + 5);
        }
        if (p.newsWarning == null) return html;
        String banner = I18n.tr("<p class=banner><b>&#9888; ") + esc(p.newsWarning) + I18n.tr("</b> <a href='sb://page/calendar'>See the Economic Calendar &rarr;</a></p>");
        int at = html.indexOf("</h1>");
        return at < 0 ? html : html.substring(0, at + 5) + banner + html.substring(at + 5);
    }

    /** True in the desktop app (Swing shows the pages), false on phones (WebView). */
    public static volatile boolean swing;

    static String page(Palette k, String body) {
        String css = I18n.tr("body{font-family:") + k.cssFont() + I18n.tr(";font-size:13pt;color:") + (k.text()) + I18n.tr(";background-color:") + (k.bg())
                + I18n.tr(";margin:14px}")
                + I18n.tr("h1{font-size:19pt;margin:0 0 4px 0;color:") + (k.text()) + "}"
                + I18n.tr("h2{font-size:15pt;margin:14px 0 6px 0;color:") + (k.accent()) + "}"
                + I18n.tr("h3{font-size:13.5pt;margin:0 0 6px 0}")
                + I18n.tr(".card{background-color:") + (k.panel()) + I18n.tr(";border:1px solid ") + (k.line()) + I18n.tr(";padding:10px 12px;margin:0 0 12px 0}")
                + I18n.tr(".muted{color:") + (k.muted()) + "}"
                + I18n.tr(".good{color:") + (k.good()) + I18n.tr("}.bad{color:") + (k.bad()) + I18n.tr("}.warn{color:") + (k.warn()) + "}"
                + I18n.tr(".label{font-weight:bold;margin-top:6px}")
                + I18n.tr("ul{margin-top:2px;margin-bottom:4px;margin-left:22px}li{margin-bottom:3px}")
                + I18n.tr(".banner{background-color:") + (k.mix(k.panel(), k.warn(), 0.18)) + I18n.tr(";padding:8px 10px;border:1px solid ") + (k.warn()) + "}"
                + I18n.tr("td{padding:3px 8px 3px 0}");
        // Phones (WebView) understand dir='rtl' fully; the desktop's simpler HTML viewer only needs right-aligned text
        // (it still writes each Arabic phrase right-to-left), and dir='rtl' there makes pages wider than the window.
        String dir = I18n.rtl() && !swing ? " dir='rtl'" : "";
        return "<html lang='" + I18n.lang() + "'" + dir + "><head><meta charset='utf-8'><meta name=viewport content='width=device-width, initial-scale=1'><style>" + css + extraCss(k)
                + (I18n.rtl() && !swing ? "body{direction:rtl;text-align:right}ul{margin-right:22px;margin-left:0}.num{text-align:left !important}" : "")
                + (I18n.rtl() && swing ? "body,p,div,h1,h2,h3,li,td,th{text-align:right}" : "")
                + "</style></head><body" + dir + ">" + body + "</body></html>";
    }

    public static String message(Palette k, String title, String text) {
        return page(k, "<h1>" + esc(title) + I18n.tr("</h1><p class=muted>") + esc(text) + "</p>");
    }

    public static String ideas(Palette k, String title, String intro, List<Advisor.Idea> ideas, String empty, boolean demo) {
        StringBuilder b = new StringBuilder();
        b.append("<h1>").append(esc(title)).append(demo ? I18n.tr(" <span class=warn>(practice data)</span>") : "").append("</h1>");
        b.append(I18n.tr("<p class=muted>")).append(esc(intro)).append("</p>");
        if (ideas.isEmpty()) b.append(I18n.tr("<div class=card><p>")).append(esc(empty)).append(I18n.tr("</p></div>"));
        int n = 1;
        for (Advisor.Idea i : ideas) {
            String cardTitle = i.title != null ? i.title : i.headline;
            String tone = i.tone > 0 ? k.good() : i.tone < 0 ? k.bad() : k.warn();
            b.append(I18n.tr("<div class=card><h3 style='margin-bottom:2px'>#")).append(n++).append(I18n.tr("&nbsp; ")).append(esc(cardTitle)).append("</h3>");
            if (i.action != null)
                b.append(I18n.tr("<div style='color:")).append(tone).append(I18n.tr(";font-weight:bold;margin:0 0 8px 0'>")).append(esc(starsInText(i.action))).append(I18n.tr("</div>"));
            b.append(I18n.tr("<div class=label>Why</div><ul>"));
            for (String w : i.why) b.append(I18n.tr("<li>")).append(signalHtml(w)).append(I18n.tr("</li>"));
            b.append(I18n.tr("</ul>"));
            if (!i.howTo.isEmpty()) {
                b.append(I18n.tr("<div class=label>How to do it</div><ul>"));
                for (String h : i.howTo) b.append(I18n.tr("<li>")).append(esc(h)).append(I18n.tr("</li>"));
                b.append(I18n.tr("</ul>"));
            }
            if (i.risk != null) b.append(I18n.tr("<p class=warn><b>Risk:</b> ")).append(esc(i.risk)).append("</p>");
            if (i.stock != null && i.kind != Advisor.Kind.ZERO_DTE) {
                String t = esc(i.stock.ticker);
                b.append(I18n.tr("<p><a href='sb://stock/")).append(t).append(I18n.tr("'>Full explanation and chart for ")).append(t).append(I18n.tr(" &rarr;</a>"))
                 .append(I18n.tr(" &nbsp;&middot;&nbsp; <a href='sb://alert/")).append(t).append(I18n.tr("'>&#128276; Set a price alert</a>"));
                if ("USD".equals(i.stock.currency) && i.stock.ticker.indexOf('.') < 0)
                    b.append(I18n.tr(" &nbsp;&middot;&nbsp; <a href='sb://pbuy/")).append(t).append(I18n.tr("'>Paper-trade it (pretend money)</a>"));
                b.append("</p>");
            }
            b.append(I18n.tr("</div>"));
        }
        b.append(disclaimer());
        return page(k, b.toString());
    }

    /** Colours the [+] / [-] / [=] markers. */
    private static String signalHtml(String w) {
        if (w.startsWith("[+] ")) return I18n.tr("<b class=good>[+]</b> ") + esc(w.substring(4));
        if (w.startsWith("[-] ")) return I18n.tr("<b class=bad>[-]</b> ") + esc(w.substring(4));
        if (w.startsWith("[=] ")) return I18n.tr("<b class=muted>[=]</b> ") + esc(w.substring(4));
        return esc(w);
    }

    /** Extra styles only used by phone pages (the desktop's HTML engine ignores what it doesn't know). */
    private static String extraCss(Palette k) {
        return I18n.tr("a{color:") + k.accent() + I18n.tr(";text-decoration:none}")
                + I18n.tr("table.grid{width:100%;border-collapse:collapse}")
                + I18n.tr("table.grid td,table.grid th{padding:7px 6px;border-bottom:1px solid ") + k.line() + I18n.tr(";text-align:left}")
                + I18n.tr("table.grid th{color:") + k.muted() + I18n.tr(";font-size:11pt}")
                + I18n.tr(".num{text-align:right !important;white-space:nowrap}")
                + I18n.tr(".pill{display:inline-block;padding:2px 10px;margin:2px;border:1px solid ") + k.line() + I18n.tr(";border-radius:14px}");
    }

    // ------------------------------------------------------------------ phone pages
    public static String markets(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder(I18n.tr("<h1>World markets</h1>"));
        b.append(I18n.tr("<p><b style='color:")).append(k.accent()).append("'>").append(esc(WorldMarkets.mood(a.markets))).append("</b></p>");
        if (!a.demo && WorldMarkets.lastPriceUpdate > 0)
            b.append(I18n.tr("<p class=muted style='font-size:10pt'>Index prices update every minute while this page is open (some exchanges are a few minutes delayed). Last update: "))
             .append(new java.text.SimpleDateFormat(I18n.tr("HH:mm:ss")).format(new java.util.Date(WorldMarkets.lastPriceUpdate))).append("</p>");
        b.append(I18n.tr("<div class=card><table class=grid><tr><th>Market</th><th class=num>Price</th><th class=num>Today</th><th class=num>Month</th></tr>"));
        for (WorldMarkets.Market m : a.markets) {
            b.append(I18n.tr("<tr><td><a href='sb://market/")).append(esc(m.symbol)).append("'><b>").append(esc(tr(m.name))).append(I18n.tr("</b></a><br><span style='font-size:10pt' class="))
             .append(m.isOpenNow() ? I18n.tr("good>") + tr("OPEN") : I18n.tr("muted>") + tr("closed"))
             .append(I18n.tr("</span><span class=muted style='font-size:10pt'> &middot; ")).append(m.localTime()).append(I18n.tr("</span></td><td class=num><b>"))
             .append(WorldMarkets.price(m.price)).append(I18n.tr("</b></td><td class=num>")).append(pct(m.changePct)).append(I18n.tr("</td><td class=num>")).append(pct(m.monthPct)).append(I18n.tr("</td></tr>"));
        }
        b.append(I18n.tr("</table></div><p class=muted>") + tr("Tap a market to see all its stocks.") + I18n.tr("</p><p><a href='sb://page/worldstocks'>The biggest companies of each market, and where to buy them &rarr;</a></p>")
                + I18n.tr("<p class=muted>An \"index\" is a basket of a country's biggest companies. Asian markets open first, then Europe, then the Americas.</p>"));
        return page(k, b.toString());
    }

    /** Your price alerts. */
    public static String alerts(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder(I18n.tr("<h1>Price alerts</h1><p class=muted>Get told when a stock reaches a price. Type an alert above, for example ")
                + I18n.tr("<b>NVDA above 150</b>, <b>TSLA below 200</b> or <b>AAPL move 5%</b> (a move of 5% up or down in one day). ")
                + I18n.tr("Alerts are checked on every refresh and every few minutes while StockBuddy runs.</p>"));
        java.util.List<Alerts.Alert> list = Alerts.load();
        if (list.isEmpty()) return page(k, b.append(I18n.tr("<div class=card>No alerts yet.</div>")).toString());
        b.append(I18n.tr("<div class=card><table class=grid><tr><th>Alert</th><th class=num>Price now</th><th>Status</th><th></th></tr>"));
        boolean anyTriggered = false;
        for (Alerts.Alert al : list) {
            Stock s = a.stocks.get(al.ticker);
            String now = s == null || Double.isNaN(s.price) ? I18n.tr("<span class=muted>-</span>") : esc(Advisor.money(s)) + I18n.tr("<br>") + pct(s.changePct);
            String status = al.triggeredAt == null ? I18n.tr("<span class=muted>Waiting</span>")
                    : I18n.tr("<b class=good>&#128276; Triggered</b><br><span class=muted style='font-size:10pt'>") + esc(al.triggeredAt) + I18n.tr(" at ") + Alerts.num(al.triggeredPrice) + I18n.tr("</span>");
            anyTriggered |= al.triggeredAt != null;
            b.append(I18n.tr("<tr><td><a href='sb://stock/")).append(esc(al.ticker)).append("'><b>").append(esc(al.ticker)).append("</b></a> ")
             .append(esc(al.describe().substring(al.ticker.length()))).append(I18n.tr("</td><td class=num>")).append(now).append(I18n.tr("</td><td>")).append(status)
             .append(I18n.tr("</td><td><a href='sb://alertdel/")).append(esc(al.id)).append(I18n.tr("'>Remove</a></td></tr>"));
        }
        b.append(I18n.tr("</table></div>"));
        if (anyTriggered) b.append(I18n.tr("<p><a href='sb://alertclear'>Remove all triggered alerts</a></p>"));
        return page(k, b.toString());
    }

    /** The paper-trading portfolio (pretend money). */
    public static String portfolio(Palette k, Analyzer a) {
        PaperPortfolio p = PaperPortfolio.load();
        java.util.Map<String, Double> prices = new java.util.LinkedHashMap<>();
        for (String t : p.positions.keySet()) { Stock s = a.stocks.get(t); if (s != null && !Double.isNaN(s.price)) prices.put(t, s.price); }
        double[] v = p.value(prices);
        double total = p.cash + v[0], gain = total - p.startCash, gainPct = 100 * gain / p.startCash;
        StringBuilder b = new StringBuilder(I18n.tr("<h1>Paper trading</h1><p class=muted>Practise with <b>pretend money</b> - nothing real is bought or sold. ")
                + I18n.tr("Type a trade above, for example <b>buy 10 AAPL</b>, <b>sell 5 AAPL</b> or <b>sell all AAPL</b>. Trades use today's real prices ")
                + I18n.tr("(US-listed stocks).") + (a.demo ? I18n.tr(" <span class=warn>Practice mode is on: prices are made up.</span>") : "") + "</p>");
        b.append(I18n.tr("<div class=card><table class=grid>"))
         .append(I18n.tr("<tr><td class=muted>Total value</td><td class=num><b style='font-size:15pt'>")).append(String.format(java.util.Locale.US, "$%,.2f", total)).append(I18n.tr("</b></td></tr>"))
         .append(I18n.tr("<tr><td class=muted>Since you started (")).append(esc(p.started)).append(I18n.tr(")</td><td class=num><b class=")).append(gain >= 0 ? "good" : "bad").append(">")
         .append(String.format(java.util.Locale.US, "%+,.2f (%+.2f%%)", gain, gainPct)).append(I18n.tr("</b></td></tr>"))
         .append(I18n.tr("<tr><td class=muted>Pretend cash</td><td class=num>")).append(String.format(java.util.Locale.US, "$%,.2f", p.cash)).append(I18n.tr("</td></tr>"))
         .append(I18n.tr("<tr><td class=muted>Invested in stocks</td><td class=num>")).append(String.format(java.util.Locale.US, "$%,.2f", v[0])).append(I18n.tr("</td></tr>"));
        double spxNow = Double.NaN;
        for (WorldMarkets.Market m : a.markets) if (m.symbol.equals("^GSPC")) spxNow = m.price;
        if (!Double.isNaN(p.spxAtStart) && !Double.isNaN(spxNow) && !a.demo) {
            double spx = 100 * (spxNow / p.spxAtStart - 1);
            b.append(I18n.tr("<tr><td class=muted>The S&amp;P 500 did</td><td class=num>")).append(pct(spx)).append(I18n.tr(" &nbsp;")).append(gainPct >= spx ? I18n.tr("<b class=good>you're ahead</b>") : I18n.tr("<b class=warn>you're behind</b>")).append(I18n.tr("</td></tr>"));
        }
        b.append(I18n.tr("</table></div>"));
        if (!p.positions.isEmpty()) {
            b.append(I18n.tr("<h2>What you hold</h2><div class=card><table class=grid><tr><th>Stock</th><th class=num>Shares</th><th class=num>Paid / now</th><th class=num>Profit</th><th></th></tr>"));
            for (PaperPortfolio.Position pos : p.positions.values()) {
                Double now = prices.get(pos.ticker);
                double pl = now == null ? Double.NaN : pos.shares * now - pos.cost;
                b.append(I18n.tr("<tr><td><a href='sb://stock/")).append(esc(pos.ticker)).append("'><b>").append(esc(pos.ticker)).append(I18n.tr("</b></a></td><td class=num>"))
                 .append(PaperPortfolio.fmt(pos.shares)).append(I18n.tr("</td><td class=num>")).append(String.format(java.util.Locale.US, "$%,.2f", pos.avgCost())).append(I18n.tr("<br>"))
                 .append(now == null ? I18n.tr("<span class=muted>refresh</span>") : String.format(java.util.Locale.US, "$%,.2f", now)).append(I18n.tr("</td><td class=num>"))
                 .append(Double.isNaN(pl) ? "-" : I18n.tr("<b class=") + (pl >= 0 ? "good" : "bad") + ">" + String.format(java.util.Locale.US, "%+,.2f", pl) + I18n.tr("</b><br>")
                         + pct(100 * pl / pos.cost))
                 .append(I18n.tr("</td><td><a href='sb://psell/")).append(esc(pos.ticker)).append(I18n.tr("'>Sell all</a></td></tr>"));
            }
            b.append(I18n.tr("</table></div>"));
        }
        if (!p.trades.isEmpty()) {
            b.append(I18n.tr("<h2>Your trades</h2><div class=card><table class=grid>"));
            for (int i = p.trades.size() - 1; i >= 0 && i >= p.trades.size() - 30; i--) {
                PaperPortfolio.Trade t = p.trades.get(i);
                b.append(I18n.tr("<tr><td>")).append(esc(t.date)).append(I18n.tr("</td><td><b class=")).append(t.side.equals("BUY") ? "good" : "bad").append(">").append(t.side)
                 .append("</b> ").append(PaperPortfolio.fmt(t.shares)).append(" ").append(esc(t.ticker)).append(I18n.tr("</td><td class=num>"))
                 .append(String.format(java.util.Locale.US, "$%,.2f", t.price)).append(I18n.tr("</td></tr>"));
            }
            b.append(I18n.tr("</table></div>"));
        }
        b.append(I18n.tr("<p><a href='sb://preset'>Start again with $10,000</a></p>"));
        return page(k, b.toString());
    }

    /** This week's economic calendar, with plain-English explanations. */
    public static String calendar(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder(I18n.tr("<h1>Economic calendar</h1>"));
        b.append(I18n.tr("<p class=muted>Big economic news (inflation, jobs, interest rates) can move whole markets in seconds - especially ")
                + I18n.tr("<b class=bad>high-impact</b> events. Times are shown in your own time zone. Source: ForexFactory")
                + (a.demo ? I18n.tr(" <span class=warn>(practice data)</span>") : "") + ".</p>");
        if (a.events.isEmpty()) return page(k, b.append(I18n.tr("<div class=card>No calendar data right now. Check the Data Sources page.</div>")).toString());
        java.util.List<EconomicCalendar.Event> big = EconomicCalendar.todaysBigUsEvents(a.events);
        if (!big.isEmpty()) b.append(I18n.tr("<p class=banner><b>&#9888; Big US news today.</b> Be extra careful with short-term trades and 0DTE options around these times.</p>"));
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
                if (open) b.append(I18n.tr("</table></div>"));
                b.append("<h2>").append(d.format(dayFmt)).append(d.equals(today) ? I18n.tr(" &middot; TODAY") : "").append(I18n.tr("</h2><div class=card><table class=grid>"));
                shown = d; open = true;
            }
            String ex = EconomicCalendar.explain(e.title);
            b.append(I18n.tr("<tr><td width=60 style='white-space:nowrap;width:60px'>")).append(local == null ? I18n.tr("All day") : local.toLocalTime().toString()).append(I18n.tr("</td><td>"))
             .append(I18n.tr("<b class=")).append(e.high() ? "bad" : "warn").append(">").append(e.high() ? "HIGH" : I18n.tr("Medium")).append(I18n.tr("</b> &middot; "))
             .append(esc(EconomicCalendar.region(e.country))).append(": <b>").append(esc(e.title)).append("</b>");
            if (!ex.isEmpty()) b.append(I18n.tr("<br><span class=muted style='font-size:10.5pt'>")).append(esc(ex)).append(I18n.tr("</span>"));
            StringBuilder nums = new StringBuilder();
            if (!e.forecast.isEmpty()) nums.append(I18n.tr("Expected ")).append(esc(e.forecast)).append(I18n.tr(" &nbsp; "));
            if (!e.previous.isEmpty()) nums.append(I18n.tr("Last time ")).append(esc(e.previous)).append(I18n.tr(" &nbsp; "));
            if (!e.actual.isEmpty()) nums.append(I18n.tr("<b>Actual ")).append(esc(e.actual)).append("</b>");
            if (nums.length() > 0) b.append(I18n.tr("<br><span style='font-size:10.5pt'>")).append(nums).append(I18n.tr("</span>"));
            String[] src = EconomicCalendar.officialSource(e);
            b.append(I18n.tr("<br><span style='font-size:10pt'>")).append(tr("Source:")).append(I18n.tr(" <a href='")).append(esc(src[1])).append("'>").append(esc(src[0])).append("</a>")
             .append(I18n.tr(" &middot; <a href='")).append(esc(LocalNews.searchLink(e.title + " " + EconomicCalendar.region(e.country)))).append("'>").append(tr("News about it"))
             .append(I18n.tr("</a></span>"));
            b.append(I18n.tr("</td></tr>"));
        }
        if (open) b.append(I18n.tr("</table></div>"));
        b.append(I18n.tr("<p class=muted>Tip: when the actual number is very different from what was expected, prices usually move the most.</p>"));
        return page(k, b.toString());
    }

    /** The biggest companies of the other world markets, with where to buy them. */
    public static String worldStocks(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder(I18n.tr("<h1>World stocks</h1><p class=muted>The biggest companies of the world's largest stock markets, ")
                + I18n.tr("what StockBuddy thinks of them today, and <b>where you can buy them</b>. Tap a name for the full explanation and chart.")
                + (a.demo ? I18n.tr(" <span class=warn>(practice data)</span>") : "") + "</p>");
        int top = WorldStocks.top();
        b.append(I18n.tr("<p><b>Companies per market:</b> &nbsp;"));
        for (int n : WorldStocks.TOP_CHOICES)
            b.append(n == top ? I18n.tr("<b style='color:") + k.accent() + I18n.tr("'>[ Top ") + n + " ]</b>" : I18n.tr("<a href='sb://wtop/") + n + I18n.tr("'>Top ") + n + "</a>").append(I18n.tr(" &nbsp;&middot;&nbsp; "));
        b.append(I18n.tr("<a href='sb://wtop/0'>Off</a> &nbsp; <span class=muted>(more companies = slower refresh)</span></p>"));
        if (top == 0) return page(k, b.append(I18n.tr("<div class=card>World stocks are turned off. Choose 3, 5, 10 or 20 above.</div>")).toString());
        boolean changedSinceRefresh = false;
        for (WorldStocks.Entry e : WorldStocks.active()) if (!a.stocks.containsKey(e.analysedTicker())) changedSinceRefresh = true;
        if (changedSinceRefresh) b.append(I18n.tr("<p class=banner>Refresh the data to analyse the companies you just added.</p>"));
        String market = null;
        boolean open = false;
        for (WorldStocks.Entry e : WorldStocks.active()) {
            Stock s = a.stocks.get(e.analysedTicker());
            if (!e.market.equals(market)) {
                if (open) b.append(I18n.tr("</div>"));
                WorldMarkets.Market m = null;
                for (WorldMarkets.Market x : a.markets) if (x.symbol.equals(e.market)) m = x;
                b.append("<h2>").append(esc(WorldStocks.marketName(e.market))).append("</h2>");
                if (m != null) b.append(I18n.tr("<p class=muted style='font-size:10.5pt'>Index ")).append(WorldMarkets.price(m.price)).append(I18n.tr(" &nbsp;"))
                        .append(pct(m.changePct)).append(I18n.tr(" today &middot; ")).append(m.isOpenNow() ? I18n.tr("<b class=good>open now</b>") : I18n.tr("closed now")).append("</p>");
                b.append(I18n.tr("<div>"));
                market = e.market;
                open = true;
            }
            b.append(I18n.tr("<div class=card><b><a href='sb://stock/")).append(esc(e.analysedTicker())).append("'>").append(esc(e.name)).append(I18n.tr("</a></b> <span class=muted>("))
             .append(esc(e.analysedTicker())).append(I18n.tr(")</span>"));
            if (s != null && s.tech != null) {
                String cls = s.score >= 0.2 ? "good" : s.score <= -0.2 ? "bad" : "warn";
                b.append(I18n.tr(" &nbsp; ")).append(esc(Advisor.money(s))).append(I18n.tr(" &nbsp;")).append(pct(s.changePct)).append(I18n.tr(" &nbsp; <b class=")).append(cls).append(">")
                 .append(esc(Scorer.verdict(s.score))).append("</b>");
            } else b.append(I18n.tr(" &nbsp; <span class=muted>no price data right now</span>"));
            b.append(I18n.tr("<ul style='margin-top:6px'>"));
            for (String h : e.howToBuy()) b.append(I18n.tr("<li>")).append(esc(h)).append(I18n.tr("</li>"));
            b.append(I18n.tr("</ul></div>"));
        }
        if (open) b.append(I18n.tr("</div>"));
        b.append(I18n.tr("<p class=muted>Prices of stocks analysed on their home exchange are in local currency. Buying abroad can add currency-exchange ")
                + I18n.tr("costs and different taxes - check with your broker.</p>"));
        return page(k, b.toString());
    }

    /** How StockBuddy's past ideas actually turned out. */
    public static String trackRecord(Palette k, Analyzer a) {
        int days = Settings.getInt("track.days", 30);
        java.time.LocalDate since = java.time.LocalDate.now().minusDays(days);
        java.util.List<TrackRecord.Pick> picks = new java.util.ArrayList<>();
        for (TrackRecord.Pick p : TrackRecord.withPrices(a)) if (!p.date.isBefore(since)) picks.add(p);
        StringBuilder b = new StringBuilder("<h1>" + tr("Track record") + I18n.tr("</h1><p class=muted>") + tr("Every day StockBuddy remembers its ideas and the price at that moment. ")
                + I18n.tr("Here you can see how they actually did - the honest way to learn how much to trust the signals. It gets more useful the longer you use the app.")
                + (a.demo ? I18n.tr(" <span class=warn>") + tr("(practice data)") + I18n.tr("</span>") : "") + "</p>");
        b.append("<p><b>").append(tr("Show:")).append(I18n.tr("</b> &nbsp;"));
        for (int d : new int[]{10, 30, 60})
            b.append(d == days ? I18n.tr("<b style='color:") + k.accent() + "'>[ " + trf("Last %d days", d) + " ]</b>" : I18n.tr("<a href='sb://trackdays/") + d + "'>" + trf("Last %d days", d) + "</a>").append(I18n.tr(" &nbsp;&middot;&nbsp; "));
        b.append("</p>");
        if (picks.isEmpty())
            return page(k, b.append(I18n.tr("<div class=card>")).append(tr("Nothing yet for this period - your ideas are saved every day. Come back in a few days!")).append(I18n.tr("</div>")).toString());
        b.append(I18n.tr("<div class=card><table class=grid><tr><th>")).append(tr("Ideas older than today")).append(I18n.tr("</th><th class=num>")).append(tr("Went the right way"))
         .append(I18n.tr("</th><th class=num>")).append(tr("Average move")).append(I18n.tr("</th></tr>"));
        String[] names = {tr("Buys (want: up)"), tr("Sells (want: down)"), tr("Calls (want: up)"), tr("Puts (want: down)")};
        int i = 0;
        for (java.util.Map.Entry<String, double[]> e : TrackRecord.summary(picks).entrySet()) {
            double[] v = e.getValue();
            String name = names[i++];
            if (v[0] == 0) { b.append(I18n.tr("<tr><td>")).append(name).append(I18n.tr("</td><td class=num>-</td><td class=num>-</td></tr>")); continue; }
            double rate = 100 * v[1] / v[0];
            b.append(I18n.tr("<tr><td>")).append(name).append(I18n.tr(" &middot; ")).append((int) v[0]).append(I18n.tr("</td><td class=num><b class=")).append(rate >= 55 ? "good" : rate < 45 ? "bad" : "warn")
             .append(">").append(String.format("%.0f%%", rate)).append(I18n.tr("</b></td><td class=num>")).append(pct(v[2])).append(I18n.tr("</td></tr>"));
        }
        b.append(I18n.tr("</table><p class=muted style='font-size:10pt'>")).append(tr("About 50% is what you'd expect from a coin flip. A few weeks of data is still very little - ")
                + I18n.tr("markets go through good and bad spells.")).append(I18n.tr("</p></div>"));

        // ---- grid: stocks in rows, days in columns (newest first)
        java.util.TreeSet<java.time.LocalDate> dates = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        java.util.Map<String, java.util.Map<java.time.LocalDate, TrackRecord.Pick>> grid = new java.util.TreeMap<>();
        java.util.Map<String, TrackRecord.Pick> anyPick = new java.util.HashMap<>();
        for (TrackRecord.Pick p : picks) {
            dates.add(p.date);
            if (!grid.containsKey(p.ticker)) grid.put(p.ticker, new java.util.HashMap<>());
            grid.get(p.ticker).put(p.date, p);
            anyPick.put(p.ticker, p);
        }
        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("d MMM", I18n.locale());
        b.append("<h2>").append(trf("Every idea, day by day (last %d days)", days)).append("</h2>")
         .append(I18n.tr("<p class=muted style='font-size:10pt'>")).append(tr("B = buy, S = sell, C = call, P = put. The number is how much the price moved since that day: ")
                 + I18n.tr("green = it went the way the idea said, red = it didn't.")).append("</p>")
         .append(I18n.tr("<div style='overflow-x:auto'><table class=grid style='font-size:10pt'><tr><th>")).append(tr("Stock")).append(I18n.tr("</th><th class=num>")).append(tr("Now")).append(I18n.tr("</th>"));
        for (java.time.LocalDate d : dates) b.append(I18n.tr("<th class=num style='white-space:nowrap'>")).append(esc(d.format(df))).append(I18n.tr("</th>"));
        b.append(I18n.tr("</tr>"));
        for (java.util.Map.Entry<String, java.util.Map<java.time.LocalDate, TrackRecord.Pick>> row : grid.entrySet()) {
            TrackRecord.Pick any = anyPick.get(row.getKey());
            b.append(I18n.tr("<tr><td style='white-space:nowrap'><a href='sb://stock/")).append(esc(row.getKey())).append("'><b>").append(esc(row.getKey())).append(I18n.tr("</b></a></td><td class=num>"))
             .append(Double.isNaN(any.now) ? "-" : String.format(java.util.Locale.US, "%,.2f", any.now)).append(I18n.tr("</td>"));
            for (java.time.LocalDate d : dates) {
                TrackRecord.Pick p = row.getValue().get(d);
                if (p == null) { b.append(I18n.tr("<td class=num><span class=muted>&middot;</span></td>")); continue; }
                Boolean w = p.worked();
                String cls = w == null || TrackRecord.daysAgo(p) == 0 ? "warn" : w ? "good" : "bad";
                b.append(I18n.tr("<td class=num style='white-space:nowrap'><b class=")).append(cls).append(">").append(p.kind.substring(0, 1)).append("</b> ")
                 .append(Double.isNaN(p.change()) ? "" : I18n.tr("<span class=") + cls + ">" + String.format(java.util.Locale.US, "%+.1f%%", p.change()) + I18n.tr("</span>")).append(I18n.tr("</td>"));
            }
            b.append(I18n.tr("</tr>"));
        }
        b.append(I18n.tr("</table></div>"));
        return page(k, b.toString());
    }

    /** All stocks: today's analysis plus every stock checked in the last 30 days (then cleared), optionally for one market. */
    public static String ranking(Palette k, Analyzer a) {
        String market = Settings.get("allstocks.market", "all");
        java.util.List<CheckedHistory.Entry> history = a.demo ? new java.util.ArrayList<>() : CheckedHistory.load();
        StringBuilder b = new StringBuilder("<h1>" + tr("Every stock I checked") + "</h1>");
        if (!market.equals("all"))
            b.append(I18n.tr("<p><b style='color:")).append(k.accent()).append("'>").append(tr("Showing only:")).append(" ").append(esc(tr(WorldStocks.marketName(market))))
             .append(I18n.tr("</b> &nbsp; <a href='sb://allmarket/all'>")).append(tr("Show every market")).append("</a></p>");
        b.append(I18n.tr("<p class=banner>")).append(trf("Every stock you check stays in this list for %d days, then it is cleared automatically.", CheckedHistory.KEEP_DAYS));
        if (CheckedHistory.lastCleared > 0) b.append(" <b>").append(trf("%d old stocks were just cleared.", CheckedHistory.lastCleared)).append("</b>");
        java.time.LocalDate next = CheckedHistory.nextClearing(history);
        if (next != null) b.append(" ").append(trf("Next clean-up: %s.", next.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", I18n.locale()))));
        b.append(I18n.tr("</p><p class=muted>")).append(tr("Tap a ticker for the full explanation.")).append("</p>");
        java.util.List<Stock> list = new java.util.ArrayList<>();
        for (Stock s : a.stocks.values()) if (WorldMarkets.belongs(s, market)) list.add(s);
        list.sort((x, y) -> Double.compare(y.score, x.score));
        b.append("<h2>").append(tr("Checked today")).append(I18n.tr("</h2><div class=card><table class=grid><tr><th>")).append(tr("Stock")).append(I18n.tr("</th><th class=num>"))
         .append(tr("Price")).append(I18n.tr("</th><th>")).append(tr("Verdict")).append(I18n.tr("</th></tr>"));
        for (Stock s : list) {
            String cls = s.score >= 0.2 ? "good" : s.score <= -0.2 ? "bad" : "warn";
            b.append(I18n.tr("<tr><td><a href='sb://stock/")).append(esc(s.ticker)).append("'><b>").append(esc(s.ticker)).append(I18n.tr("</b></a><br><span class=muted style='font-size:10pt'>"))
             .append(esc(s.name == null ? "" : s.name)).append(I18n.tr("</span></td><td class=num>")).append(esc(Advisor.money(s))).append(I18n.tr("<br>"))
             .append(pct(s.changePct)).append(I18n.tr("</td><td class=")).append(cls).append("><b>").append(esc(Scorer.verdict(s.score)))
             .append(I18n.tr("</b><br><span class=muted>")).append(prettyStars(Scorer.stars(s.confidence))).append(I18n.tr("</span></td></tr>"));
        }
        if (list.isEmpty()) b.append(I18n.tr("<tr><td colspan=3 class=muted>")).append(tr("No stock from this market today.")).append(I18n.tr("</td></tr>"));
        b.append(I18n.tr("</table></div>"));
        java.util.List<CheckedHistory.Entry> older = new java.util.ArrayList<>();
        for (CheckedHistory.Entry e : history) if (!a.stocks.containsKey(e.ticker) && (market.equals("all") || WorldStocks.forTicker(e.ticker) == null
                || WorldStocks.forTicker(e.ticker).market.equals(market))) older.add(e);
        if (!older.isEmpty()) {
            b.append("<h2>").append(trf("Checked earlier (last %d days)", CheckedHistory.KEEP_DAYS)).append(I18n.tr("</h2><div class=card><table class=grid><tr><th>")).append(tr("Stock"))
             .append(I18n.tr("</th><th class=num>")).append(tr("Price then")).append(I18n.tr("</th><th>")).append(tr("Verdict then")).append(I18n.tr("</th></tr>"));
            for (CheckedHistory.Entry e : older) {
                String cls = e.score >= 0.2 ? "good" : e.score <= -0.2 ? "bad" : "warn";
                b.append(I18n.tr("<tr><td><a href='sb://stock/")).append(esc(e.ticker)).append("'><b>").append(esc(e.ticker)).append(I18n.tr("</b></a><br><span class=muted style='font-size:10pt'>"))
                 .append(esc(e.name)).append(I18n.tr(" &middot; ")).append(esc(e.day().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", I18n.locale())))).append(I18n.tr("</span></td><td class=num>"))
                 .append(String.format(java.util.Locale.US, "%,.2f", e.price)).append(" ").append(esc("USD".equals(e.currency) ? "" : e.currency)).append(I18n.tr("</td><td class=")).append(cls)
                 .append("><b>").append(esc(Scorer.verdict(e.score))).append(I18n.tr("</b></td></tr>"));
            }
            b.append(I18n.tr("</table></div>"));
        }
        return page(k, b.toString());
    }

    /** My watchlist, each stock coloured like its verdict: green = buy, orange = hold, red = sell. */
    public static String watchlist(Palette k, java.util.List<String> tickers, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>&#9733; " + tr("My watchlist") + I18n.tr("</h1><p class=muted>") + tr("These stocks are always checked, plus today's biggest ")
                + I18n.tr("movers and the stocks Reddit talks about most. Type tickers above to add them (saved straight away). Tap a ticker to look it up, or &times; to remove it.")
                + I18n.tr("</p><p style='font-size:10.5pt'><b class=good>&#9679; ") + tr("Buy") + I18n.tr("</b> &nbsp; <b class=warn>&#9679; ") + tr("Hold / wait") + I18n.tr("</b> &nbsp; <b class=bad>&#9679; ")
                + tr("Sell") + I18n.tr("</b> &nbsp; <span class=muted>&#9679; ") + tr("not analysed yet") + I18n.tr("</span></p><div class=card>"));
        for (String t : tickers) {
            Stock s = a == null ? null : a.stocks.get(t);
            String col = s == null || s.tech == null ? k.muted() : s.score >= 0.2 ? k.good() : s.score <= -0.2 ? k.bad() : k.warn();
            b.append(I18n.tr("<span class=pill style='border:2px solid ")).append(col).append(I18n.tr(";background-color:")).append(k.mix(k.panel(), col, 0.18)).append(I18n.tr("'><a href='sb://stock/"))
             .append(esc(t)).append(I18n.tr("' style='color:")).append(col).append("'><b>").append(esc(t)).append("</b></a>");
            if (s != null && s.tech != null) b.append(I18n.tr(" <span style='font-size:10pt;color:")).append(col).append("'>").append(esc(Scorer.verdict(s.score))).append(I18n.tr("</span>"));
            b.append(I18n.tr(" &nbsp;<a href='sb://remove/")).append(esc(t)).append(I18n.tr("'>&times;</a></span> "));
        }
        b.append(I18n.tr("</div><p><a href='sb://reset'>")).append(tr("Reset to the default list")).append(I18n.tr("</a></p><p class=muted>"))
         .append(tr("Tap the refresh button at the top to analyse your changes.")).append("</p>");
        return page(k, b.toString());
    }

    /** Market news in your language, plus trusted local sources. */
    public static String news(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>" + tr("Market news") + I18n.tr("</h1><p class=muted>") + trf("Headlines in %s from the local financial press. Tap a headline to read the original article.",
                I18n.name(I18n.lang())) + "</p>");
        if (a.news.isEmpty())
            b.append(I18n.tr("<div class=card>")).append(a.demo ? tr("Practice mode has no news - turn it off and refresh to read today's headlines.")
                    : tr("No headlines right now. Check your internet connection and refresh.")).append(I18n.tr("</div>"));
        else {
            b.append(I18n.tr("<div class=card><table class=grid>"));
            for (LocalNews.Item n : a.news)
                b.append(I18n.tr("<tr><td><a href='")).append(esc(n.link)).append("'><b>").append(esc(n.title)).append(I18n.tr("</b></a><br><span class=muted style='font-size:10pt'>"))
                 .append(esc(n.source)).append(n.date.isEmpty() ? "" : I18n.tr(" &middot; ") + esc(n.date)).append(I18n.tr("</span></td></tr>"));
            b.append(I18n.tr("</table></div>"));
        }
        b.append("<h2>").append(tr("Trusted sources")).append(I18n.tr("</h2><div class=card>"));
        for (String[] src : LocalNews.trustedSources(I18n.lang()))
            b.append(I18n.tr("<span class=pill><a href='")).append(esc(src[1])).append("'>").append(esc(src[0])).append(I18n.tr("</a></span> "));
        b.append(I18n.tr("</div>"));
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
        g.append(I18n.tr("<svg viewBox='0 0 ")).append(w + 64).append(' ').append(height).append(I18n.tr("' width='100%' xmlns='http://www.w3.org/2000/svg'>"));
        for (int i = 0; i <= 4; i++) {
            double y = top + mainH * i / 4.0;
            g.append(fmt(I18n.tr("<line x1='0' x2='%d' y1='%.1f' y2='%.1f' stroke='%s' stroke-width='1'/>"), w, y, y, k.line()));
            g.append(fmt(I18n.tr("<text x='%d' y='%.1f' fill='%s' font-size='13'>%,.2f</text>"), w + 6, y + 4, k.muted(), hi - (hi - lo) * i / 4));
        }
        if (bb != null) {
            StringBuilder band = new StringBuilder();
            for (int i = from; i < n; i++) if (!Double.isNaN(bb[0][i])) band.append(band.length() == 0 ? "M" : "L").append(ch.pt(i, bb[0][i])).append(' ');
            for (int i = n - 1; i >= from; i--) if (!Double.isNaN(bb[1][i])) band.append("L").append(ch.pt(i, bb[1][i])).append(' ');
            if (band.length() > 0) g.append(I18n.tr("<path d='")).append(band).append(I18n.tr("Z' fill='")).append(k.muted()).append(I18n.tr("' fill-opacity='0.15' stroke='"))
                    .append(k.muted()).append(I18n.tr("' stroke-opacity='0.5' stroke-width='1'/>"));
        }
        boolean up = c.get(n - 1) >= c.get(from);
        String col = up ? k.good() : k.bad();
        if (candles) {
            double cw = Math.max(1, w / (double) count * 0.65);
            for (int i = from; i < n; i++) {
                double op = s.opens.get(i), cl = c.get(i);
                String cc = cl >= op ? k.good() : k.bad();
                double x = ch.x(i);
                g.append(fmt(I18n.tr("<line x1='%.1f' x2='%.1f' y1='%.1f' y2='%.1f' stroke='%s' stroke-width='1'/>"), x, x, ch.y(s.highs.get(i)), ch.y(s.lows.get(i)), cc));
                double y1 = ch.y(Math.max(op, cl)), y2 = ch.y(Math.min(op, cl));
                g.append(fmt(I18n.tr("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s'/>"), x - cw / 2, y1, cw, Math.max(1, y2 - y1), cc));
            }
        } else {
            StringBuilder pts = new StringBuilder();
            for (int i = from; i < n; i++) pts.append(ch.pt(i, c.get(i))).append(' ');
            if (o.type.equals("area"))
                g.append(I18n.tr("<path d='M")).append(fmt("%.1f,%d ", ch.x(from), top + mainH)).append("L").append(pts.toString().trim().replace(" ", " L"))
                 .append(fmt(" L%.1f,%d Z", ch.x(n - 1), top + mainH)).append(I18n.tr("' fill='")).append(col).append(I18n.tr("' fill-opacity='0.18'/>"));
            g.append(I18n.tr("<polyline fill='none' stroke='")).append(col).append(I18n.tr("' stroke-width='2.4' points='")).append(pts).append("'/>");
        }
        for (Object[] l : lines) g.append(ch.polyline((double[]) l[0], (String) l[1], 1.6));
        if (lower) g.append(lowerSvg(k, s, o, from, count, w, top + mainH + gap, lowH));
        g.append(I18n.tr("<text x='0' y='")).append(height - 4).append(I18n.tr("' fill='")).append(k.muted()).append(I18n.tr("' font-size='13'>"))
         .append(ChartOptions.RANGE_NAMES[rangeIndex(o.range)]).append(I18n.tr(" ago</text>"));
        g.append(I18n.tr("<text x='")).append(w - 40).append("' y='").append(height - 4).append(I18n.tr("' fill='")).append(k.muted()).append(I18n.tr("' font-size='13'>today</text></svg>"));

        StringBuilder legend = new StringBuilder(I18n.tr("<div style='font-size:10.5pt'><span style='color:") + col + "'>&#9644; " + (candles ? I18n.tr("Candles") : I18n.tr("Price")) + I18n.tr("</span>"));
        for (Object[] l : lines) legend.append(I18n.tr(" &nbsp; <span style='color:")).append(l[1]).append("'>&#9644; ").append(l[2]).append(I18n.tr("</span>"));
        if (bb != null) legend.append(I18n.tr(" &nbsp; <span style='color:")).append(k.muted()).append(I18n.tr("'>&#9644; Bollinger</span>"));
        legend.append(I18n.tr("</div>"));
        return I18n.tr("<div class=card>") + g + legend + chartControls(k, o) + I18n.tr("</div>");
    }

    private static String chartControls(Palette k, ChartOptions o) {
        StringBuilder b = new StringBuilder(I18n.tr("<div style='margin-top:8px;font-size:10.5pt;line-height:2.1'>"));
        b.append(I18n.tr("<span class=muted>Chart:</span> "));
        for (int i = 0; i < ChartOptions.TYPES.length; i++) b.append(chip(k, "type/" + ChartOptions.TYPES[i], tr(ChartOptions.TYPE_NAMES[i]), o.type.equals(ChartOptions.TYPES[i])));
        b.append(I18n.tr(" &nbsp;<span class=muted>Period:</span> "));
        for (int i = 0; i < ChartOptions.RANGES.length; i++) b.append(chip(k, "range/" + ChartOptions.RANGES[i], tr(ChartOptions.RANGE_NAMES[i]), o.range == ChartOptions.RANGES[i]));
        b.append(I18n.tr("<br><span class=muted>Lines:</span> "));
        for (int i = 0; i < ChartOptions.OVERLAYS.length; i++) b.append(chip(k, "toggle/" + ChartOptions.OVERLAYS[i], tr(ChartOptions.OVERLAY_NAMES[i]), o.overlay(ChartOptions.OVERLAYS[i])));
        b.append(I18n.tr("<br><span class=muted>Bottom panel:</span> "));
        for (int i = 0; i < ChartOptions.LOWERS.length; i++) b.append(chip(k, "lower/" + ChartOptions.LOWERS[i], tr(ChartOptions.LOWER_NAMES[i]), o.lower.equals(ChartOptions.LOWERS[i])));
        return b.append(I18n.tr("</div>")).toString();
    }

    private static String chip(Palette k, String link, String text, boolean on) {
        return I18n.tr("<a href='sb://chart/") + link + I18n.tr("' style='display:inline-block;padding:1px 9px;margin:1px;border-radius:12px;border:1px solid ")
                + (on ? k.accent() : k.line()) + I18n.tr(";background-color:") + (on ? k.accent() : k.panel()) + I18n.tr(";color:")
                + (on ? (k.dark() ? k.bg() : I18n.tr("#ffffff")) : k.text()) + "'>" + text + "</a>";
    }

    private static String lowerSvg(Palette k, Stock s, ChartOptions o, int from, int count, int w, int top, int h) {
        java.util.List<Double> c = s.closes;
        int n = c.size();
        StringBuilder g = new StringBuilder(fmt(I18n.tr("<line x1='0' x2='%d' y1='%d' y2='%d' stroke='%s'/>"), w, top, top, k.line()));
        double bw = Math.max(1, w / (double) count * 0.7);
        if (o.lower.equals("volume")) {
            double max = 0;
            for (int i = from; i < n && i < s.volumes.size(); i++) max = Math.max(max, s.volumes.get(i));
            if (max > 0) for (int i = from; i < n && i < s.volumes.size(); i++) {
                double bh = h * s.volumes.get(i) / max;
                String cc = i == 0 || c.get(i) >= c.get(i - 1) ? k.good() : k.bad();
                g.append(fmt(I18n.tr("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s' fill-opacity='0.6'/>"), w * (i - from) / (double) Math.max(1, count - 1) - bw / 2, top + h - bh, bw, bh, cc));
            }
            g.append(fmt(I18n.tr("<text x='%d' y='%d' fill='%s' font-size='12'>Volume</text>"), w + 6, top + 13, k.muted()));
        } else if (o.lower.equals("rsi")) {
            Chart ch = new Chart(from, count, w, 0, 100, top, h);
            for (int lvl : new int[]{30, 70})
                g.append(fmt(I18n.tr("<line x1='0' x2='%d' y1='%.1f' y2='%.1f' stroke='%s' stroke-dasharray='4,4'/><text x='%d' y='%.1f' fill='%s' font-size='12'>%d</text>"),
                        w, ch.y(lvl), ch.y(lvl), k.muted(), w + 6, ch.y(lvl) + 4, k.muted(), lvl));
            g.append(ch.polyline(ChartOptions.rsi(c, 14), I18n.tr("#b07cff"), 1.6));
            g.append(fmt(I18n.tr("<text x='%d' y='%d' fill='%s' font-size='12'>RSI</text>"), w + 30, top + 13, k.muted()));
        } else if (o.lower.equals("macd")) {
            double[][] m = ChartOptions.macd(c);
            double max = 1e-9;
            for (int i = from; i < n; i++) for (double[] v : m) if (!Double.isNaN(v[i])) max = Math.max(max, Math.abs(v[i]));
            Chart ch = new Chart(from, count, w, -max, max, top, h);
            for (int i = from; i < n; i++) {
                if (Double.isNaN(m[2][i])) continue;
                double y0 = ch.y(0), y1 = ch.y(m[2][i]);
                g.append(fmt(I18n.tr("<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s' fill-opacity='0.6'/>"), ch.x(i) - bw / 2, Math.min(y0, y1), bw, Math.abs(y1 - y0),
                        m[2][i] >= 0 ? k.good() : k.bad()));
            }
            g.append(ch.polyline(m[0], k.accent(), 1.5)).append(ch.polyline(m[1], k.warn(), 1.3));
            g.append(fmt(I18n.tr("<text x='%d' y='%d' fill='%s' font-size='12'>MACD</text>"), w + 6, top + 13, k.muted()));
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
        b.append(I18n.tr("<p>Price <b>")).append(esc(Advisor.money(s))).append(I18n.tr("</b> &nbsp; Today ")).append(pct(s.changePct))
         .append(s.keyStats.containsKey("52-week range") ? I18n.tr(" &nbsp; 1-year range $") + esc(s.keyStats.get("52-week range")).replace(" - ", " - $")
                 : String.format(I18n.tr(" &nbsp; 1-year range $%.2f - $%.2f"), x.low52, x.high52)).append("</p>");
        b.append(I18n.tr("<div class=card><h3>Overall: <span class=")).append(cls).append(">").append(Scorer.verdict(s.score)).append(I18n.tr("</span></h3>"))
         .append(String.format(I18n.tr("Score %+.0f out of 100 &nbsp;&middot;&nbsp; confidence %s"), s.score * 100, prettyStars(Scorer.stars(s.confidence))))
         .append("<p>").append(esc(Scorer.plainSummary(s))).append(I18n.tr("</p></div>"));
        b.append(I18n.tr("<p><a href='sb://watch/")).append(esc(s.ticker)).append(I18n.tr("'>&#9733; Add ")).append(esc(s.ticker)).append(I18n.tr(" to My Watchlist</a>"))
         .append(I18n.tr(" &nbsp;&middot;&nbsp; <a href='sb://alert/")).append(esc(s.ticker)).append(I18n.tr("'>&#128276; Set a price alert</a>"));
        if ("USD".equals(s.currency) && s.ticker.indexOf('.') < 0)
            b.append(I18n.tr(" &nbsp;&middot;&nbsp; <a href='sb://pbuy/")).append(esc(s.ticker)).append(I18n.tr("'>Paper-trade it</a>"));
        b.append("</p>");
        java.util.List<String[]> stats = KeyStats.ordered(s);
        if (!stats.isEmpty()) {
            b.append(I18n.tr("<h2>Key statistics</h2><div class=card><table class=grid>"));
            for (String[] st : stats) {
                String help = KeyStats.help(st[0]);
                b.append(I18n.tr("<tr><td class=muted")).append(help == null ? "" : " title='" + esc(tr(help)).replace("'", "&#39;") + "'").append(">").append(esc(tr(st[0])))
                 .append(help == null ? "" : "<br><span style='font-size:9.5pt'>" + esc(tr(help)) + "</span>")
                 .append(I18n.tr("</td><td class=num><b>")).append(esc(st[1])).append(I18n.tr("</b></td></tr>"));
            }
            b.append(I18n.tr("</table></div>"));
        }
        if (!s.localNews.isEmpty()) {
            b.append("<h2>").append(tr("News in your language")).append(I18n.tr("</h2><ul>"));
            for (LocalNews.Item n : s.localNews)
                b.append(I18n.tr("<li><a href='")).append(esc(n.link)).append("'>").append(esc(n.title)).append(I18n.tr("</a> <span class=muted style='font-size:10pt'>"))
                 .append(esc(n.source)).append(I18n.tr("</span></li>"));
            b.append(I18n.tr("</ul>"));
        }
        b.append(I18n.tr("<p><a href='")).append(esc(LocalNews.searchLink(s.name == null ? s.ticker : s.name))).append("'>").append(tr("Search the news about this company"))
         .append(I18n.tr(" &rarr;</a></p>"));
        if (!s.newsHeadlines.isEmpty()) {
            b.append(I18n.tr("<h2>In the news</h2><ul>"));
            for (String h : s.newsHeadlines) b.append(I18n.tr("<li>")).append(esc(h)).append(I18n.tr("</li>"));
            b.append(I18n.tr("</ul>"));
        }
        for (java.util.Map.Entry<String, java.util.Map<String, String>> f : s.customData.entrySet()) {
            b.append(I18n.tr("<h2>From your CSV file: ")).append(esc(f.getKey())).append(I18n.tr("</h2><div class=card><table class=grid>"));
            for (java.util.Map.Entry<String, String> c : f.getValue().entrySet())
                b.append(I18n.tr("<tr><td class=muted>")).append(esc(c.getKey())).append(I18n.tr("</td><td class=num><b>")).append(esc(c.getValue())).append(I18n.tr("</b></td></tr>"));
            b.append(I18n.tr("</table></div>"));
        }
        b.append(I18n.tr("<h2>What each source says</h2>"));
        for (Signal g : s.signals) {
            String c = g.score() > 0.1 ? "good" : g.score() < -0.1 ? "bad" : "muted";
            String m = g.score() > 0.1 ? "[+]" : g.score() < -0.1 ? "[-]" : "[=]";
            b.append(I18n.tr("<div class=card><b class=")).append(c).append(">").append(m).append("</b> <b>").append(esc(I18n.tr(g.source())))
             .append(I18n.tr("</b><br>")).append(esc(g.reason())).append(I18n.tr("</div>"));
        }
        if (s.signals.size() < 3) b.append(I18n.tr("<p class=muted>Only a few sources had data for this stock, so treat the result with extra caution.</p>"));
        b.append(I18n.tr("<h2>Chart numbers (StockCharts-style)</h2><table>"))
         .append(row("50-day average", String.format("$%.2f", x.sma50)))
         .append(row("200-day average", String.format("$%.2f", x.sma200)))
         .append(row("RSI (0-100)", String.format("%.0f", x.rsi14) + (x.rsi14 > 70 ? I18n.tr(" &nbsp;<span class=warn>rose fast</span>") : x.rsi14 < 30 ? I18n.tr(" &nbsp;<span class=warn>fell fast</span>") : "")))
         .append(row("MACD momentum", x.macdHist > 0 ? "<span class=good>positive</span>" : "<span class=bad>negative</span>"))
         .append(row("SCTR rank", Double.isNaN(x.sctr) ? "n/a" : String.format("%.0f / 99", x.sctr)))
         .append(row("Typical yearly swing", String.format("%.0f%%", x.volatility * 100)))
         .append(row("Trading activity vs normal", String.format(I18n.tr("%.1fx"), x.volumeRatio)))
         .append(I18n.tr("</table>"));
        if (!s.redditQuotes.isEmpty()) {
            b.append(I18n.tr("<h2>What Reddit is saying</h2><ul>"));
            for (String q : s.redditQuotes) b.append(I18n.tr("<li>")).append(esc(q)).append(I18n.tr("</li>"));
            b.append(I18n.tr("</ul>"));
        }
        b.append(disclaimer());
        return page(k, b.toString());
    }

    public static String glossary(Palette k, String filter) {
        StringBuilder b = new StringBuilder(I18n.tr("<h1>Beginner's dictionary</h1><p class=muted>Investing words, explained simply.</p>"));
        String f = filter == null ? "" : filter.trim().toLowerCase();
        int shown = 0;
        for (Map.Entry<String, String> e : Glossary.WORDS.entrySet()) {
            String word = tr(e.getKey()), meaning = tr(e.getValue());
            if (!f.isEmpty() && !(word + " " + meaning + " " + e.getKey()).toLowerCase().contains(f)) continue;
            b.append("<div class=card><b>").append(esc(word)).append("</b>").append(word.equals(e.getKey()) ? "" : " <span class=muted>(" + esc(e.getKey()) + ")</span>")
             .append("<br>").append(esc(meaning)).append("</div>");
            shown++;
        }
        if (shown == 0) b.append(I18n.tr("<p>No word matches \"")).append(esc(filter)).append("\".</p>");
        return page(k, b.toString());
    }

    public static String sources(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder(I18n.tr("<h1>Where the data comes from</h1>"));
        b.append(I18n.tr("<p class=muted>Green = worked, orange = needs attention. Your files are kept in: ")).append(esc(AppPaths.base().toString())).append("</p>");
        b.append(I18n.tr("<div class=card><table>"));
        a.sourceStatus.forEach((name, v) -> {
            boolean ok = v.startsWith("OK") || v.toLowerCase().startsWith("calc") || v.startsWith("practice");
            b.append(I18n.tr("<tr><td><b>")).append(esc(tr(name))).append(I18n.tr("</b></td><td class=")).append(ok ? "good" : "warn").append(">")
             .append(esc(I18n.trParts(v)));
            String[] fix = ok ? null : fixLink(name);
            if (fix != null) b.append(I18n.tr("<br><a href='")).append(esc(fix[1])).append("'>&#128279; ").append(esc(tr(fix[0]))).append("</a>");
            b.append(I18n.tr("</td></tr>"));
        });
        b.append(I18n.tr("</table></div>"));
        b.append(I18n.tr("<h2>Tips</h2><ul>"))
         .append(I18n.tr("<li><b>Stock Rover</b> needs a paid login, so export a CSV from Stock Rover (with Ticker and Fair Value columns) and click <b>Import Stock Rover CSV</b> below.</li>"))
         .append(I18n.tr("<li><b>WallStreetZen</b> sometimes blocks programs. You can import a CSV with Ticker, Zen Score and Zen Rating columns instead.</li>"))
         .append(I18n.tr("<li><b>StockCharts</b> has no public data feed. Its indicators (moving averages, RSI, MACD, SCTR) are calculated by StockBuddy from price history.</li>"))
         .append(I18n.tr("<li><b>Reddit</b> is read automatically (no account needed). If Reddit blocks it, add posts yourself: copy post titles from ")
                 + I18n.tr("Reddit and use <b>Add Reddit posts</b>, or import a CSV with a <i>title</i> column (optional: date, subreddit, upvotes). ")
                 + I18n.tr("Your own posts are used for ") + RedditSource.MANUAL_DAYS + I18n.tr(" days.</li></ul>"));
        return page(k, b.toString());
    }

    /** Where to get the key / API / file a data source needs: {what to do, link}. */
    static String[] fixLink(String source) {
        String s = source.toLowerCase();
        if (s.contains("finnhub")) return new String[]{I18n.tr("Get a free Finnhub key (finnhub.io/register)"), "https://finnhub.io/register"};
        if (s.contains("alpha vantage")) return new String[]{I18n.tr("Get a free Alpha Vantage key"), "https://www.alphavantage.co/support/#api-key"};
        if (s.contains("wallstreetbets")) return new String[]{I18n.tr("WallStreetBets sentiment API page (Tradestie)"), "https://tradestie.com/apps/reddit/api/"};
        if (s.startsWith("reddit")) return new String[]{I18n.tr("Ask Reddit for API access (Reddit developer page)"), "https://www.reddit.com/prefs/apps"};
        if (s.contains("sec edgar")) return new String[]{I18n.tr("How the SEC wants programs to identify themselves"), "https://www.sec.gov/os/accessing-edgar-data"};
        if (s.contains("stock rover")) return new String[]{I18n.tr("Open Stock Rover (export a screener as CSV)"), "https://www.stockrover.com"};
        if (s.contains("wallstreetzen")) return new String[]{I18n.tr("Open WallStreetZen (Zen Scores)"), "https://www.wallstreetzen.com"};
        if (s.contains("stockanalysis")) return new String[]{I18n.tr("Open stockanalysis.com"), "https://stockanalysis.com"};
        if (s.contains("economic calendar")) return new String[]{I18n.tr("Open the ForexFactory calendar"), "https://www.forexfactory.com/calendar"};
        if (s.contains("world") || s.contains("yahoo")) return new String[]{I18n.tr("Open Yahoo Finance"), "https://finance.yahoo.com"};
        if (s.contains("news")) return new String[]{I18n.tr("Open Google News"), "https://news.google.com"};
        if (s.contains("csv")) return new String[]{I18n.tr("How to add a CSV (README)"), "https://github.com/Hitch-1/StockBuddy#3-where-the-information-comes-from"};
        return null;
    }

    private static String row(String a, String b) { return I18n.tr("<tr><td class=muted>") + esc(a) + I18n.tr("</td><td><b>") + b + I18n.tr("</b></td></tr>"); }

    static String pct(double v) {
        if (Double.isNaN(v)) return I18n.tr("<span class=muted>n/a</span>");
        return I18n.tr("<b class=") + (v > 0.05 ? "good" : v < -0.05 ? "bad" : "warn") + ">" + String.format("%+.2f%%", v) + "</b>";
    }

    private static String disclaimer() {
        return I18n.tr("<p class=banner><b>Important:</b> StockBuddy is a learning and research helper, not a financial advisor. ")
                + I18n.tr("It can be wrong, data can be late, and you can lose money. Never invest money you can't afford to lose.</p>");
    }

    /** Turns "***.." into proper stars for the windowed app. */
    public static String prettyStars(String s) { return s.replace('*', '\u2605').replace('.', '\u2606'); }

    private static String starsInText(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("confidence ([*.]{5})").matcher(s);
        return m.find() ? s.substring(0, m.start(1)) + prettyStars(m.group(1)) + s.substring(m.end(1)) : s;
    }

    private static String tr(String s) { return I18n.tr(s); }

    private static String trf(String f, Object... a) { return I18n.trf(f, a); }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
