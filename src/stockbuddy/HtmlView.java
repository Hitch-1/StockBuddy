package stockbuddy;

import java.util.List;
import java.util.Map;

/** Builds the nicely formatted pages shown inside the app windows, coloured by the current skin. */
public final class HtmlView {
    private HtmlView() {}

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
            b.append("<div class=card><h3>#").append(n++).append("&nbsp; ").append(esc(starsInText(i.headline))).append("</h3>");
            b.append("<div class=label>Why</div><ul>");
            for (String w : i.why) b.append("<li>").append(signalHtml(w)).append("</li>");
            b.append("</ul>");
            if (!i.howTo.isEmpty()) {
                b.append("<div class=label>How to do it</div><ul>");
                for (String h : i.howTo) b.append("<li>").append(esc(h)).append("</li>");
                b.append("</ul>");
            }
            if (i.risk != null) b.append("<p class=warn><b>Risk:</b> ").append(esc(i.risk)).append("</p>");
            if (i.stock != null && i.kind != Advisor.Kind.ZERO_DTE)
                b.append("<p><a href='sb://stock/").append(esc(i.stock.ticker)).append("'>Full explanation and chart for ")
                 .append(esc(i.stock.ticker)).append(" &rarr;</a></p>");
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
        b.append("<div class=card><table class=grid><tr><th>Market</th><th>Status</th><th class=num>Today</th><th class=num>Month</th></tr>");
        for (WorldMarkets.Market m : a.markets) {
            b.append("<tr><td>").append(esc(m.name)).append("<br><span class=muted style='font-size:10pt'>local time ")
             .append(m.localTime()).append("</span></td><td class=").append(m.isOpenNow() ? "good>OPEN" : "muted>closed")
             .append("</td><td class=num>").append(pct(m.changePct)).append("</td><td class=num>").append(pct(m.monthPct)).append("</td></tr>");
        }
        b.append("</table></div><p class=muted>An \"index\" is a basket of a country's biggest companies. Asian markets open first, "
                + "then Europe, then the Americas.</p>");
        return page(k, b.toString());
    }

    public static String ranking(Palette k, Analyzer a) {
        StringBuilder b = new StringBuilder("<h1>Every stock, best to worst</h1><p class=muted>Tap a ticker for the full explanation.</p>");
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
                + "movers and the stocks Reddit talks about most. Type tickers above to add them. Tap &times; to remove one.</p><div class=card>");
        for (String t : tickers)
            b.append("<span class=pill><b>").append(esc(t)).append("</b> &nbsp;<a href='sb://remove/").append(esc(t)).append("'>&times;</a></span> ");
        b.append("</div><p><a href='sb://reset'>Reset to the default list</a></p><p class=muted>Tap the refresh button at the top to analyse your changes.</p>");
        return page(k, b.toString());
    }

    /** One-year price chart as SVG, with the 50- and 200-day averages. */
    public static String chartSvg(Palette k, Stock s) {
        java.util.List<Double> c = s.closes;
        int n = c.size();
        if (n < 2) return "";
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : c) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double pad = (hi - lo) * 0.08 + 1e-9; lo -= pad; hi += pad;
        int w = 600, h = 220;
        boolean up = c.get(n - 1) >= c.get(0);
        String col = up ? k.good() : k.bad();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox='0 0 ").append(w + 60).append(' ').append(h + 30).append("' width='100%' xmlns='http://www.w3.org/2000/svg'>");
        for (int i = 0; i <= 4; i++) {
            double y = 10 + h * i / 4.0;
            svg.append(String.format(java.util.Locale.US, "<line x1='0' x2='%d' y1='%.1f' y2='%.1f' stroke='%s' stroke-width='1'/>", w, y, y, k.line()));
            svg.append(String.format(java.util.Locale.US, "<text x='%d' y='%.1f' fill='%s' font-size='13'>%.2f</text>", w + 6, y + 4, k.muted(), hi - (hi - lo) * i / 4));
        }
        StringBuilder area = new StringBuilder(String.format(java.util.Locale.US, "M0,%d ", h + 10));
        svg.append("<polyline fill='none' stroke='").append(k.warn()).append("' stroke-width='1.6' points='").append(avgPoints(c, 200, lo, hi, w, h)).append("'/>");
        svg.append("<polyline fill='none' stroke='").append(k.accent()).append("' stroke-width='1.6' points='").append(avgPoints(c, 50, lo, hi, w, h)).append("'/>");
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String p = String.format(java.util.Locale.US, "%.1f,%.1f", (double) w * i / (n - 1), 10 + h * (hi - c.get(i)) / (hi - lo));
            pts.append(p).append(' ');
            area.append("L").append(p).append(' ');
        }
        area.append(String.format(java.util.Locale.US, "L%d,%d Z", w, h + 10));
        svg.append("<path d='").append(area).append("' fill='").append(col).append("' fill-opacity='0.15'/>");
        svg.append("<polyline fill='none' stroke='").append(col).append("' stroke-width='2.4' points='").append(pts).append("'/>");
        svg.append("<text x='0' y='").append(h + 28).append("' fill='").append(k.muted()).append("' font-size='13'>1 year ago</text>");
        svg.append("<text x='").append(w - 40).append("' y='").append(h + 28).append("' fill='").append(k.muted()).append("' font-size='13'>today</text></svg>");
        return "<div class=card>" + svg + "<div style='font-size:10.5pt'><span style='color:" + col + "'>&#9644; Price</span> &nbsp; <span style='color:"
                + k.accent() + "'>&#9644; 50-day average</span> &nbsp; <span style='color:" + k.warn() + "'>&#9644; 200-day average</span></div></div>";
    }

    private static String avgPoints(java.util.List<Double> c, int p, double lo, double hi, int w, int h) {
        StringBuilder b = new StringBuilder();
        double sum = 0;
        for (int i = 0; i < c.size(); i++) {
            sum += c.get(i);
            if (i >= p) sum -= c.get(i - p);
            if (i >= p - 1) b.append(String.format(java.util.Locale.US, "%.1f,%.1f ", (double) w * i / (c.size() - 1), 10 + h * (hi - sum / p) / (hi - lo)));
        }
        return b.toString();
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
         .append(String.format(" &nbsp; 1-year range $%.2f - $%.2f", x.low52, x.high52)).append("</p>");
        b.append("<div class=card><h3>Overall: <span class=").append(cls).append(">").append(Scorer.verdict(s.score)).append("</span></h3>")
         .append(String.format("Score %+.0f out of 100 &nbsp;&middot;&nbsp; confidence %s", s.score * 100, prettyStars(Scorer.stars(s.confidence))))
         .append("<p>").append(esc(Scorer.plainSummary(s))).append("</p></div>");
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
         .append("<li><b>Reddit</b> may block anonymous readers. A free key from reddit.com/prefs/apps fixes it (see README).</li></ul>");
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
