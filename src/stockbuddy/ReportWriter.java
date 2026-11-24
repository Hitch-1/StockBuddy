package stockbuddy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Saves the daily report as an easy-to-read web page in the "reports" folder. */
public final class ReportWriter {
    private ReportWriter() {}

    public static Path write(Analyzer a, Advisor.Plan p) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Path dir = AppPaths.reports();
        Files.createDirectories(dir);
        Path file = dir.resolve("stockbuddy-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".html");

        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=en><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>")
         .append("<title>StockBuddy daily report</title><style>")
         .append(":root{--bg:#f6f7f9;--card:#fff;--ink:#1d2330;--muted:#5d6676;--line:#e3e6eb;--good:#127a3e;--bad:#b42318;--warn:#9a6700;--accent:#2457c5}")
         .append("@media (prefers-color-scheme:dark){:root{--bg:#12151b;--card:#1b2029;--ink:#e7eaf0;--muted:#9aa3b2;--line:#2c3340;--good:#4cc27e;--bad:#ff7a6b;--warn:#e6b450;--accent:#7aa2ff}}")
         .append("body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}")
         .append("main{max-width:900px;margin:0 auto;padding:24px 16px 60px}h1{font-size:28px;margin:0 0 4px}h2{margin:36px 0 10px;font-size:21px}")
         .append(".muted{color:var(--muted)}.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px 18px;margin:12px 0}")
         .append(".card h3{margin:0 0 8px;font-size:17px}ul{margin:4px 0 8px;padding-left:20px}.risk{color:var(--warn);font-size:14px}")
         .append(".warn{border-left:4px solid var(--warn);padding:10px 14px;background:var(--card);border-radius:8px}")
         .append("table{width:100%;border-collapse:collapse;font-size:14px}td,th{padding:6px 8px;border-bottom:1px solid var(--line);text-align:left}")
         .append(".up{color:var(--good)}.down{color:var(--bad)}.wrap{overflow-x:auto}.tag{font-weight:600}</style></head><body><main>");
        h.append("<h1>StockBuddy daily report").append(a.demo ? " (PRACTICE DATA)" : "").append("</h1>");
        h.append("<p class=muted>Made ").append(now.format(DateTimeFormatter.ofPattern("EEEE MMMM d, yyyy 'at' HH:mm"))).append(" &middot; ")
         .append(a.stocks.size()).append(" stocks analysed</p>");
        h.append("<p class=warn><b>Important:</b> this is a learning and research helper, not financial advice. It can be wrong and you can lose money.</p>");

        h.append("<h2>World markets</h2><p>").append(esc(WorldMarkets.mood(a.markets))).append("</p><div class=wrap><table><tr><th>Market</th><th>Status</th><th>Today</th><th>Last month</th></tr>");
        for (WorldMarkets.Market m : a.markets)
            h.append("<tr><td>").append(esc(m.name)).append("</td><td>").append(m.isOpenNow() ? "Open" : "Closed").append("</td><td>")
             .append(pct(m.changePct)).append("</td><td>").append(pct(m.monthPct)).append("</td></tr>");
        h.append("</table></div>");

        section(h, "Best buys", p.buys, "No stock had enough good signals today.");
        section(h, "Sells & shorts (if you own them)", p.sells, "No strong sell signals today.");
        section(h, "Best calls (bet on up, 1-2 months)", p.calls, "No clear call ideas today.");
        section(h, "Best puts (bet on down, 1-2 months)", p.puts, "No clear put ideas today.");
        section(h, "0DTE ideas (expire today - extremely risky)", p.zeroDte, "");

        h.append("<h2>Every stock, ranked</h2><div class=wrap><table><tr><th>#</th><th>Ticker</th><th>Name</th><th>Price</th><th>Today</th><th>Verdict</th><th>Confidence</th></tr>");
        List<Stock> list = new ArrayList<>(a.stocks.values());
        list.sort((x, y) -> Double.compare(y.score, x.score));
        int n = 1;
        for (Stock s : list)
            h.append("<tr><td>").append(n++).append("</td><td><b>").append(esc(s.ticker)).append("</b></td><td>").append(esc(s.name == null ? "" : s.name))
             .append("</td><td>").append(esc(Advisor.money(s))).append("</td><td>").append(pct(s.changePct)).append("</td><td>")
             .append(esc(Scorer.verdict(s.score))).append("</td><td>").append(Scorer.stars(s.confidence)).append("</td></tr>");
        h.append("</table></div>");

        h.append("<h2>Where the data came from</h2><ul>");
        a.sourceStatus.forEach((k, v) -> h.append("<li><b>").append(esc(k)).append(":</b> ").append(esc(v)).append("</li>"));
        h.append("</ul><h2>Dictionary</h2>");
        Glossary.WORDS.forEach((k, v) -> h.append("<p><b>").append(esc(k)).append("</b> &mdash; ").append(esc(v)).append("</p>"));
        h.append("</main></body></html>");
        Files.writeString(file, h.toString());
        return file;
    }

    private static void section(StringBuilder h, String title, List<Advisor.Idea> ideas, String empty) {
        h.append("<h2>").append(esc(title)).append("</h2>");
        if (ideas.isEmpty()) { h.append("<p class=muted>").append(esc(empty)).append("</p>"); return; }
        for (Advisor.Idea i : ideas) {
            h.append("<div class=card><h3>").append(esc(i.title != null ? i.title : i.headline)).append("</h3>");
            if (i.action != null) h.append("<p class=").append(i.tone > 0 ? "up" : i.tone < 0 ? "down" : "risk").append("><b>").append(esc(i.action)).append("</b></p>");
            h.append("<b>Why</b><ul>");
            for (String w : i.why) {
                String cls = w.startsWith("[+]") ? "up" : w.startsWith("[-]") ? "down" : "";
                h.append("<li class='").append(cls).append("'>").append(esc(w)).append("</li>");
            }
            h.append("</ul>");
            if (!i.howTo.isEmpty()) {
                h.append("<b>How to do it</b><ul>");
                for (String w : i.howTo) h.append("<li>").append(esc(w)).append("</li>");
                h.append("</ul>");
            }
            if (i.risk != null) h.append("<p class=risk>Risk: ").append(esc(i.risk)).append("</p>");
            h.append("</div>");
        }
    }

    private static String pct(double v) {
        if (Double.isNaN(v)) return "n/a";
        return "<span class=" + (v >= 0 ? "up" : "down") + ">" + String.format("%+.2f%%", v) + "</span>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }
}
