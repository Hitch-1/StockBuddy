package stockbuddy;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Market news in your language, from that country's financial press (Les Echos, Expansion, Nikkei, Il Sole 24 Ore,
 * Argaam...). Headlines come through Google News' public RSS feeds, which group articles from many local
 * publications and link to the original article. No key needed.
 */
public final class LocalNews {
    public static final class Item {
        public final String title, link, source, sourceUrl, date;
        Item(String title, String link, String source, String sourceUrl, String date) {
            this.title = title; this.link = link; this.source = source; this.sourceUrl = sourceUrl; this.date = date;
        }
    }

    public static volatile String lastStatus = "Not loaded yet";

    /** Per language: {Google News language, country, search words for "stock market"}. */
    private static String[] feed(String lang) {
        switch (lang) {
            case "fr": return new String[]{"fr", "FR", "bourse actions marchés"};
            case "es": return new String[]{"es", "ES", "bolsa acciones mercados"};
            case "ja": return new String[]{"ja", "JP", "株式 市場 日経平均"};
            case "it": return new String[]{"it", "IT", "borsa azioni mercati"};
            case "ar": return new String[]{"ar", "SA", "البورصة الأسهم"};
            default: return new String[]{"en-US", "US", "stock market"};
        }
    }

    /** Trusted financial sources for each language: {name, website}. Shown on the News page. */
    public static String[][] trustedSources(String lang) {
        switch (lang) {
            case "fr": return new String[][]{{"Les Echos", "https://www.lesechos.fr"}, {"Boursorama", "https://www.boursorama.com"},
                    {"Zonebourse", "https://www.zonebourse.com"}, {"Investir", "https://investir.lesechos.fr"}, {"BFM Bourse", "https://www.tradingsat.com"},
                    {"AMF (régulateur)", "https://www.amf-france.org"}, {"Banque de France", "https://www.banque-france.fr"}, {"INSEE", "https://www.insee.fr"}};
            case "es": return new String[][]{{"Expansión", "https://www.expansion.com"}, {"Cinco Días", "https://cincodias.elpais.com"},
                    {"elEconomista", "https://www.eleconomista.es"}, {"Investing.com España", "https://es.investing.com"},
                    {"CNMV (regulador)", "https://www.cnmv.es"}, {"Banco de España", "https://www.bde.es"}, {"INE", "https://www.ine.es"}};
            case "ja": return new String[][]{{"日本経済新聞", "https://www.nikkei.com"}, {"株探 (Kabutan)", "https://kabutan.jp"},
                    {"Yahoo!ファイナンス", "https://finance.yahoo.co.jp"}, {"みんかぶ", "https://minkabu.jp"},
                    {"日本取引所グループ (JPX)", "https://www.jpx.co.jp"}, {"日本銀行", "https://www.boj.or.jp"},
                    {"金融庁", "https://www.fsa.go.jp"}};
            case "it": return new String[][]{{"Il Sole 24 Ore", "https://www.ilsole24ore.com"}, {"Milano Finanza", "https://www.milanofinanza.it"},
                    {"Borsa Italiana", "https://www.borsaitaliana.it"}, {"Teleborsa", "https://www.teleborsa.it"},
                    {"CONSOB (autorità)", "https://www.consob.it"}, {"Banca d'Italia", "https://www.bancaditalia.it"}, {"ISTAT", "https://www.istat.it"}};
            case "ar": return new String[][]{{"أرقام (Argaam)", "https://www.argaam.com"}, {"مباشر (Mubasher)", "https://www.mubasher.info"},
                    {"CNBC عربية", "https://www.cnbcarabia.com"}, {"العربية أسواق", "https://www.alarabiya.net/aswaq"},
                    {"تداول السعودية", "https://www.saudiexchange.sa"},
                    {"هيئة السوق المالية", "https://cma.org.sa"}};
            default: return new String[][]{{"Reuters Markets", "https://www.reuters.com/markets/"}, {"CNBC", "https://www.cnbc.com/markets/"},
                    {"Yahoo Finance", "https://finance.yahoo.com"}, {"MarketWatch", "https://www.marketwatch.com"}, {"SEC", "https://www.sec.gov"},
                    {"Federal Reserve", "https://www.federalreserve.gov"}};
        }
    }

    /** Google News search link (in your language) for anything - used for "news about this event / stock". */
    public static String searchLink(String query) {
        String[] f = feed(I18n.lang());
        return "https://news.google.com/search?q=" + enc(query) + "&hl=" + f[0] + "&gl=" + f[1] + "&ceid=" + f[1] + ":" + f[0].replaceAll("-.*", "");
    }

    /** Today's market headlines in your language. */
    public static List<Item> market(int max) {
        String[] f = feed(I18n.lang());
        List<Item> out = fetch(f[2], max);
        lastStatus = out.isEmpty() ? "Could not connect to the news feed" : "OK - " + out.size() + " headlines in " + I18n.name(I18n.lang());
        return out;
    }

    /** Recent headlines about one company, in your language. */
    public static List<Item> about(Stock s, int max) {
        String q = (s.name == null || s.name.isEmpty() ? s.ticker : s.name.replaceAll("\\s*\\(.*\\)", "")) + (s.ticker.indexOf('.') < 0 ? " " + s.ticker : "");
        return fetch(q, max);
    }

    static List<Item> fetch(String query, int max) {
        String[] f = feed(I18n.lang());
        String url = "https://news.google.com/rss/search?q=" + enc(query + " when:7d") + "&hl=" + f[0] + "&gl=" + f[1] + "&ceid=" + f[1] + ":" + f[0].replaceAll("-.*", "");
        return parse(Http.get(url), max);
    }

    static List<Item> parse(String xml, int max) {
        List<Item> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = Pattern.compile("(?s)<item>(.*?)</item>").matcher(xml);
        while (m.find() && out.size() < max) {
            String it = m.group(1);
            String title = RedditSource.unescape(tag(it, "title"));
            String link = RedditSource.unescape(tag(it, "link"));
            String date = tag(it, "pubDate");
            Matcher src = Pattern.compile("(?s)<source[^>]*url=\"([^\"]*)\"[^>]*>(.*?)</source>").matcher(it);
            String source = "", sourceUrl = "";
            if (src.find()) { sourceUrl = RedditSource.unescape(src.group(1)); source = RedditSource.unescape(src.group(2)).trim(); }
            if (!source.isEmpty() && title.endsWith(" - " + source)) title = title.substring(0, title.length() - source.length() - 3);
            if (!title.isEmpty() && link.startsWith("http")) out.add(new Item(title.trim(), link.trim(), source, sourceUrl, date.length() > 16 ? date.substring(0, 16) : date));
        }
        return out;
    }

    private static String tag(String s, String t) {
        Matcher m = Pattern.compile("(?s)<" + t + "[^>]*>(.*?)</" + t + ">").matcher(s);
        if (!m.find()) return "";
        return m.group(1).replace("<![CDATA[", "").replace("]]>", "").trim();
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
