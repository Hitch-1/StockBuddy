package stockbuddy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Saves the daily report: as a web page (HTML), a Word document (.docx), a spreadsheet (.csv) or data (.json).
 * Shared by the desktop app (saved in the "reports" folder) and the phone app (saved where you choose).
 */
public final class ReportWriter {
    public enum Format {
        HTML("Web page (.html)", "html", "text/html"),
        WORD("Word document (.docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        CSV("Spreadsheet (.csv)", "csv", "text/csv"),
        JSON("Data for programs (.json)", "json", "application/json");

        public final String label, extension, mime;
        Format(String label, String extension, String mime) { this.label = label; this.extension = extension; this.mime = mime; }
    }

    private static final String[] SECTIONS = {"Best buys", "Sells & shorts", "Best calls", "Best puts", "0DTE"};

    private ReportWriter() {}

    /** Saves a web page report in the reports folder (kept for the text version). */
    public static Path write(Analyzer a, Advisor.Plan p) throws Exception { return write(Format.HTML, a, p); }

    public static Path write(Format f, Analyzer a, Advisor.Plan p) throws Exception {
        Path dir = AppPaths.reports();
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName(f, p));
        Files.write(file, build(f, a, p));
        return file;
    }

    public static String fileName(Format f, Advisor.Plan p) {
        String market = p == null || p.market == null ? "" : "-" + p.market.replaceAll("[^A-Za-z0-9]", "");
        return "stockbuddy-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + market + "." + f.extension;
    }

    public static byte[] build(Format f, Analyzer a, Advisor.Plan p) throws Exception {
        switch (f) {
            case WORD: return docx(a, p);
            case CSV: return csv(a, p).getBytes(StandardCharsets.UTF_8);
            case JSON: return json(a, p).getBytes(StandardCharsets.UTF_8);
            default: return html(a, p).getBytes(StandardCharsets.UTF_8);
        }
    }

    static String html(Analyzer a, Advisor.Plan p) {
        LocalDateTime now = LocalDateTime.now();
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
        return h.toString();
    }

    // ------------------------------------------------------------------ CSV (one table: markets, ideas, stocks)
    static String csv(Analyzer a, Advisor.Plan p) {
        StringBuilder b = new StringBuilder("\uFEFF");   // lets Excel read accents correctly
        b.append("type,section,rank,ticker,name,price,currency,change_pct_today,verdict,score,confidence,action,why\n");
        for (WorldMarkets.Market m : a.markets)
            row(b, "market", m.country, "", m.symbol, m.name, m.price, "", m.changePct, m.isOpenNow() ? "open" : "closed", "", "", "", "");
        List<List<Advisor.Idea>> lists = HtmlView.ideaLists(p);
        for (int s = 0; s < lists.size(); s++) {
            int n = 1;
            for (Advisor.Idea i : lists.get(s)) {
                Stock st = i.stock;
                row(b, "idea", SECTIONS[s], String.valueOf(n++), st == null ? "" : st.ticker, st != null && st.name != null ? st.name : (i.title == null ? i.headline : i.title),
                        st == null ? Double.NaN : st.price, st == null ? "" : st.currency, st == null ? Double.NaN : st.changePct,
                        st == null ? "" : Scorer.verdict(st.score), st == null ? "" : fmt(st.score * 100, "%.0f"), st == null ? "" : Scorer.stars(st.confidence),
                        i.action == null ? i.headline : i.action, String.join(" | ", i.why));
            }
        }
        int n = 1;
        for (Stock s : ranked(a))
            row(b, "stock", s.world == null ? "" : WorldStocks.marketName(s.world.market), String.valueOf(n++), s.ticker, s.name == null ? "" : s.name,
                    s.price, s.currency, s.changePct, Scorer.verdict(s.score), fmt(s.score * 100, "%.0f"), Scorer.stars(s.confidence), "", "");
        return b.toString();
    }

    private static void row(StringBuilder b, String type, String section, String rank, String ticker, String name, double price, String cur,
                            double change, String verdict, String score, String conf, String action, String why) {
        String[] cells = {type, section, rank, ticker, name, Double.isNaN(price) ? "" : fmt(price, "%.4f"), cur,
                Double.isNaN(change) ? "" : fmt(change, "%.2f"), verdict, score, conf, action, why};
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) b.append(',');
            String c = cells[i] == null ? "" : cells[i];
            if (c.contains(",") || c.contains("\"") || c.contains("\n")) c = "\"" + c.replace("\"", "\"\"") + "\"";
            b.append(c);
        }
        b.append('\n');
    }

    // ------------------------------------------------------------------ JSON
    static String json(Analyzer a, Advisor.Plan p) {
        StringBuilder b = new StringBuilder("{\n");
        b.append("  \"generated\": ").append(q(LocalDateTime.now().toString())).append(",\n");
        b.append("  \"practiceData\": ").append(a.demo).append(",\n");
        b.append("  \"disclaimer\": ").append(q("A learning and research helper, not financial advice.")).append(",\n");
        b.append("  \"market\": ").append(p.market == null ? "\"all\"" : q(p.market)).append(",\n");
        b.append("  \"newsWarning\": ").append(p.newsWarning == null ? "null" : q(p.newsWarning)).append(",\n");
        b.append("  \"worldMarkets\": [\n");
        for (int i = 0; i < a.markets.size(); i++) {
            WorldMarkets.Market m = a.markets.get(i);
            b.append("    {\"symbol\": ").append(q(m.symbol)).append(", \"name\": ").append(q(m.name)).append(", \"country\": ").append(q(m.country))
             .append(", \"price\": ").append(n(m.price)).append(", \"changePct\": ").append(n(m.changePct)).append(", \"monthPct\": ").append(n(m.monthPct))
             .append(", \"open\": ").append(m.isOpenNow()).append("}").append(i + 1 < a.markets.size() ? ",\n" : "\n");
        }
        b.append("  ],\n  \"ideas\": {\n");
        String[] keys = {"buys", "sellsAndShorts", "calls", "puts", "zeroDte"};
        List<List<Advisor.Idea>> lists = HtmlView.ideaLists(p);
        for (int s = 0; s < lists.size(); s++) {
            b.append("    ").append(q(keys[s])).append(": [");
            List<Advisor.Idea> l = lists.get(s);
            for (int i = 0; i < l.size(); i++) {
                Advisor.Idea idea = l.get(i);
                b.append(i == 0 ? "\n" : ",\n").append("      {\"ticker\": ").append(idea.stock == null ? "null" : q(idea.stock.ticker))
                 .append(", \"title\": ").append(q(idea.title == null ? idea.headline : idea.title)).append(", \"action\": ").append(q(idea.action == null ? idea.headline : idea.action))
                 .append(", \"price\": ").append(idea.stock == null ? "null" : n(idea.stock.price)).append(", \"currency\": ").append(idea.stock == null ? "null" : q(idea.stock.currency))
                 .append(", \"why\": ").append(arr(idea.why)).append(", \"howTo\": ").append(arr(idea.howTo)).append(", \"risk\": ").append(idea.risk == null ? "null" : q(idea.risk)).append("}");
            }
            b.append(l.isEmpty() ? "]" : "\n    ]").append(s + 1 < lists.size() ? ",\n" : "\n");
        }
        b.append("  },\n  \"stocks\": [\n");
        List<Stock> ranked = ranked(a);
        for (int i = 0; i < ranked.size(); i++) {
            Stock s = ranked.get(i);
            b.append("    {\"ticker\": ").append(q(s.ticker)).append(", \"name\": ").append(s.name == null ? "null" : q(s.name))
             .append(", \"price\": ").append(n(s.price)).append(", \"currency\": ").append(q(s.currency)).append(", \"changePct\": ").append(n(s.changePct))
             .append(", \"verdict\": ").append(q(Scorer.verdict(s.score))).append(", \"score\": ").append(n(Math.round(s.score * 100)))
             .append(", \"confidence\": ").append(n(Math.round(s.confidence * 100) / 100.0))
             .append(", \"market\": ").append(s.world == null ? "null" : q(s.world.market)).append(", \"signals\": [");
            for (int g = 0; g < s.signals.size(); g++) {
                Signal sg = s.signals.get(g);
                b.append(g == 0 ? "" : ", ").append("{\"source\": ").append(q(sg.source())).append(", \"score\": ").append(n(Math.round(sg.score() * 100) / 100.0))
                 .append(", \"reason\": ").append(q(sg.reason())).append("}");
            }
            b.append("]}").append(i + 1 < ranked.size() ? ",\n" : "\n");
        }
        b.append("  ],\n  \"sources\": {");
        int i = 0;
        for (Map.Entry<String, String> e : a.sourceStatus.entrySet())
            b.append(i++ == 0 ? "\n" : ",\n").append("    ").append(q(e.getKey())).append(": ").append(q(e.getValue()));
        b.append("\n  }\n}\n");
        return b.toString();
    }

    private static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private static String n(double v) { return Double.isNaN(v) || Double.isInfinite(v) ? "null" : (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v)); }

    private static String arr(List<String> l) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) b.append(i == 0 ? "" : ", ").append(q(l.get(i)));
        return b.append("]").toString();
    }

    // ------------------------------------------------------------------ Word (.docx, written by hand - no extra libraries)
    static byte[] docx(Analyzer a, Advisor.Plan p) throws Exception {
        StringBuilder d = new StringBuilder();
        para(d, "StockBuddy daily report" + (a.demo ? " (PRACTICE DATA)" : ""), 36, true, "1F3B73");
        para(d, "Made " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE MMMM d, yyyy 'at' HH:mm", Locale.US)) + " - " + a.stocks.size() + " stocks analysed"
                + (p.market == null ? "" : " - only " + WorldStocks.marketName(p.market)), 20, false, "5D6676");
        para(d, "Important: this is a learning and research helper, not financial advice. It can be wrong and you can lose money.", 20, true, "9A6700");
        if (p.newsWarning != null) para(d, p.newsWarning, 20, true, "B42318");

        heading(d, "World markets");
        para(d, WorldMarkets.mood(a.markets), 22, false, null);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Market", "Index price", "Today", "Last month", "Status"});
        for (WorldMarkets.Market m : a.markets)
            rows.add(new String[]{m.name, WorldMarkets.price(m.price), pctText(m.changePct), pctText(m.monthPct), m.isOpenNow() ? "Open" : "Closed"});
        table(d, rows);

        List<List<Advisor.Idea>> lists = HtmlView.ideaLists(p);
        for (int s = 0; s < lists.size(); s++) {
            heading(d, HtmlView.IDEA_TEXT[s][0]);
            if (lists.get(s).isEmpty()) { para(d, HtmlView.IDEA_TEXT[s][2].isEmpty() ? "No ideas right now." : HtmlView.IDEA_TEXT[s][2], 22, false, "5D6676"); continue; }
            int n = 1;
            for (Advisor.Idea i : lists.get(s)) {
                para(d, "#" + n++ + "  " + (i.title == null ? i.headline : i.title), 26, true, null);
                if (i.action != null) para(d, i.action, 22, true, i.tone > 0 ? "127A3E" : i.tone < 0 ? "B42318" : "9A6700");
                para(d, "Why", 22, true, null);
                for (String w : i.why) para(d, "\u2022 " + w, 21, false, null);
                if (!i.howTo.isEmpty()) {
                    para(d, "How to do it", 22, true, null);
                    for (String h : i.howTo) para(d, "\u2022 " + h, 21, false, null);
                }
                if (i.risk != null) para(d, "Risk: " + i.risk, 21, false, "9A6700");
            }
        }

        heading(d, "Every stock");
        rows = new ArrayList<>();
        rows.add(new String[]{"#", "Ticker", "Name", "Price", "Today", "Verdict", "Confidence"});
        int n = 1;
        for (Stock s : ranked(a))
            rows.add(new String[]{String.valueOf(n++), s.ticker, s.name == null ? "" : s.name, Advisor.money(s), pctText(s.changePct), Scorer.verdict(s.score), Scorer.stars(s.confidence)});
        table(d, rows);

        heading(d, "Where the data came from");
        for (Map.Entry<String, String> e : a.sourceStatus.entrySet()) para(d, e.getKey() + ": " + e.getValue(), 20, false, null);

        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + d
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr>"
                + "</w:body></w:document>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            put(z, "word/document.xml", document);
        }
        return out.toByteArray();
    }

    private static void put(ZipOutputStream z, String name, String content) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static void heading(StringBuilder d, String text) { para(d, text, 30, true, "2457C5"); }

    /** One paragraph. size is in half-points (22 = 11pt). color is hex without # (or null). */
    private static void para(StringBuilder d, String text, int size, boolean bold, String color) {
        d.append("<w:p><w:pPr><w:spacing w:after=\"80\"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>")
         .append(bold ? "<w:b/>" : "").append(color == null ? "" : "<w:color w:val=\"" + color + "\"/>")
         .append("<w:sz w:val=\"").append(size).append("\"/></w:rPr><w:t xml:space=\"preserve\">").append(xml(text)).append("</w:t></w:r></w:p>");
    }

    private static void table(StringBuilder d, List<String[]> rows) {
        d.append("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders>");
        for (String side : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"})
            d.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D9DEE6\"/>");
        d.append("</w:tblBorders></w:tblPr>");
        for (int r = 0; r < rows.size(); r++) {
            d.append("<w:tr>");
            for (String c : rows.get(r)) {
                d.append("<w:tc><w:tcPr>").append(r == 0 ? "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EEF1F5\"/>" : "").append("</w:tcPr><w:p><w:r><w:rPr>")
                 .append(r == 0 ? "<w:b/>" : "").append("<w:sz w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">").append(xml(c)).append("</w:t></w:r></w:p></w:tc>");
            }
            d.append("</w:tr>");
        }
        d.append("</w:tbl><w:p/>");
    }

    private static String xml(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '&') b.append("&amp;");
            else if (c == '<') b.append("&lt;");
            else if (c == '>') b.append("&gt;");
            else if (c == '"') b.append("&quot;");
            else if (c < 0x20 && c != '\t' && c != '\n') continue;   // not allowed in XML
            else b.append(c);
        }
        return b.toString();
    }

    private static List<Stock> ranked(Analyzer a) {
        List<Stock> list = new ArrayList<>(a.stocks.values());
        list.sort((x, y) -> Double.compare(y.score, x.score));
        return list;
    }

    private static String pctText(double v) { return Double.isNaN(v) ? "n/a" : String.format(Locale.US, "%+.2f%%", v); }

    private static String fmt(double v, String f) { return String.format(Locale.US, f, v); }

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
