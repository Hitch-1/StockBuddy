package stockbuddy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * This week's economic calendar (the "Forex calendar" from ForexFactory): big news like inflation (CPI),
 * jobs reports and central-bank interest-rate decisions. These can move whole markets in seconds,
 * so StockBuddy warns you about them - especially before 0DTE trades.
 */
public final class EconomicCalendar {

    public static final class Event {
        public final String title, country, impact, forecast, previous, actual;
        public final ZonedDateTime time;   // null when the time isn't known ("All Day", "Tentative")
        public final LocalDate day;

        Event(String title, String country, ZonedDateTime time, LocalDate day, String impact, String forecast, String previous, String actual) {
            this.title = title; this.country = country; this.time = time; this.day = day; this.impact = impact;
            this.forecast = forecast; this.previous = previous; this.actual = actual;
        }

        public boolean high() { return "High".equalsIgnoreCase(impact); }
        public boolean medium() { return "Medium".equalsIgnoreCase(impact); }
    }

    public static String lastStatus = "Not loaded yet";

    private static final String[] URLS = {
            "https://nfs.faireconomy.media/ff_calendar_thisweek.json",
            "https://cdn-nfs.faireconomy.media/ff_calendar_thisweek.json"};

    private EconomicCalendar() {}

    public static List<Event> load() {
        for (String url : URLS) {
            String body = Http.get(url);
            if (body == null) continue;
            List<Event> out = parse(body);
            if (!out.isEmpty()) {
                lastStatus = "OK - " + out.size() + " events this week (" + count(out, true) + " high-impact)";
                return out;
            }
        }
        lastStatus = "Could not connect to the economic calendar (ForexFactory)";
        return new ArrayList<>();
    }

    static List<Event> parse(String body) {
        List<Event> out = new ArrayList<>();
        try {
            Object root = Json.parse(body);
            List<Object> rows = root instanceof List ? Json.list(root) : Json.list(Json.path(root, "data"));
            for (Object r : rows) {
                String title = str(r, "title", "event", "name");
                if (title.isEmpty()) continue;
                String date = str(r, "date", "datetime", "time");
                ZonedDateTime when = null;
                LocalDate day = null;
                try {
                    when = OffsetDateTime.parse(date).toZonedDateTime();
                    day = when.toLocalDate();
                } catch (Exception e) {
                    try { day = LocalDate.parse(date.length() >= 10 ? date.substring(0, 10) : date); } catch (Exception ignored) {}
                }
                out.add(new Event(title, str(r, "country", "currency"), when, day, str(r, "impact", "importance"),
                        str(r, "forecast"), str(r, "previous"), str(r, "actual")));
            }
        } catch (Exception e) {
            Log.debug("Economic calendar unreadable: " + e);
        }
        Collections.sort(out, (a, b) -> {
            if (a.time != null && b.time != null) return a.time.compareTo(b.time);
            if (a.day != null && b.day != null) return a.day.compareTo(b.day);
            return 0;
        });
        return out;
    }

    private static String str(Object r, String... keys) {
        for (String k : keys) {
            Object v = Json.path(r, k);
            if (v != null) return v instanceof Double && ((Double) v) == Math.rint((Double) v) ? String.valueOf(((Double) v).longValue()) : String.valueOf(v).trim();
        }
        return "";
    }

    public static int count(List<Event> events, boolean highOnly) {
        int n = 0;
        for (Event e : events) if (!highOnly || e.high()) n++;
        return n;
    }

    /** High-impact US events today (New York date) - the ones that matter most for US stocks and 0DTE. */
    public static List<Event> todaysBigUsEvents(List<Event> events) {
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        List<Event> out = new ArrayList<>();
        for (Event e : events) if (e.high() && "USD".equalsIgnoreCase(e.country) && today.equals(dayInNewYork(e))) out.add(e);
        return out;
    }

    static LocalDate dayInNewYork(Event e) {
        return e.time != null ? e.time.withZoneSameInstant(ZoneId.of("America/New_York")).toLocalDate() : e.day;
    }

    /** Which country / region a currency code stands for. */
    public static String region(String currency) {
        switch (currency == null ? "" : currency.toUpperCase()) {
            case "USD": return I18n.tr("United States");
            case "EUR": return I18n.tr("Eurozone");
            case "GBP": return I18n.tr("United Kingdom");
            case "JPY": return I18n.tr("Japan");
            case "CNY": return I18n.tr("China");
            case "AUD": return I18n.tr("Australia");
            case "CAD": return I18n.tr("Canada");
            case "CHF": return I18n.tr("Switzerland");
            case "NZD": return I18n.tr("New Zealand");
            case "ALL": return I18n.tr("Worldwide");
            default: return currency;
        }
    }

    /** The official publisher of an event, {name, link} - so you can read the original release. */
    public static String[] officialSource(Event e) {
        String t = e.title.toLowerCase(), c = e.country == null ? "" : e.country.toUpperCase();
        boolean bank = t.contains("rate") || t.contains("fomc") || t.contains("monetary") || t.contains("speaks") || t.contains("press conference") || t.contains("minutes");
        switch (c) {
            case "USD":
                if (t.contains("fomc") || t.contains("federal funds") || t.contains("fed ") || t.contains("powell") || bank)
                    return new String[]{"Federal Reserve", "https://www.federalreserve.gov/monetarypolicy/fomccalendars.htm"};
                if (t.contains("cpi")) return new String[]{"US Bureau of Labor Statistics", "https://www.bls.gov/cpi/"};
                if (t.contains("ppi")) return new String[]{"US Bureau of Labor Statistics", "https://www.bls.gov/ppi/"};
                if (t.contains("unemployment claims") || t.contains("jobless")) return new String[]{"US Department of Labor", "https://www.dol.gov/ui/data.pdf"};
                if (t.contains("non-farm") || t.contains("employment") || t.contains("unemployment") || t.contains("earnings"))
                    return new String[]{"US Bureau of Labor Statistics", "https://www.bls.gov/ces/"};
                if (t.contains("gdp") || t.contains("pce")) return new String[]{"US Bureau of Economic Analysis", "https://www.bea.gov/news/schedule"};
                if (t.contains("retail sales")) return new String[]{"US Census Bureau", "https://www.census.gov/retail/"};
                if (t.contains("ism")) return new String[]{"Institute for Supply Management", "https://www.ismworld.org/supply-management-news-and-reports/reports/ism-report-on-business/"};
                if (t.contains("crude oil") || t.contains("natural gas")) return new String[]{"US Energy Information Administration", "https://www.eia.gov/petroleum/supply/weekly/"};
                if (t.contains("consumer sentiment")) return new String[]{"University of Michigan", "http://www.sca.isr.umich.edu/"};
                if (t.contains("consumer confidence")) return new String[]{"The Conference Board", "https://www.conference-board.org/topics/consumer-confidence"};
                return new String[]{"US government release calendar", "https://www.bls.gov/schedule/news_release/"};
            case "EUR":
                if (bank || t.contains("ecb") || t.contains("lagarde")) return new String[]{"European Central Bank", "https://www.ecb.europa.eu/press/calendars/mgcgc/html/index.en.html"};
                if (t.contains("zew")) return new String[]{"ZEW", "https://www.zew.de/en"};
                if (t.contains("ifo")) return new String[]{"ifo Institute", "https://www.ifo.de/en"};
                if (t.contains("german")) return new String[]{"Destatis (Germany)", "https://www.destatis.de/EN/"};
                if (t.contains("french")) return new String[]{"INSEE (France)", "https://www.insee.fr/en/"};
                if (t.contains("italian")) return new String[]{"ISTAT (Italy)", "https://www.istat.it/en/"};
                if (t.contains("spanish")) return new String[]{"INE (Spain)", "https://www.ine.es/en/"};
                return new String[]{"Eurostat", "https://ec.europa.eu/eurostat/news/release-calendar"};
            case "GBP":
                if (bank || t.contains("boe") || t.contains("mpc")) return new String[]{"Bank of England", "https://www.bankofengland.co.uk/monetary-policy"};
                return new String[]{"Office for National Statistics", "https://www.ons.gov.uk/releasecalendar"};
            case "JPY":
                if (bank || t.contains("boj") || t.contains("tankan")) return new String[]{"Bank of Japan", "https://www.boj.or.jp/en/"};
                return new String[]{"Statistics Bureau of Japan", "https://www.stat.go.jp/english/"};
            case "CNY":
                if (bank || t.contains("pboc") || t.contains("lpr")) return new String[]{"People's Bank of China", "http://www.pbc.gov.cn/en/"};
                return new String[]{"National Bureau of Statistics of China", "https://www.stats.gov.cn/english/"};
            case "AUD":
                if (bank || t.contains("rba")) return new String[]{"Reserve Bank of Australia", "https://www.rba.gov.au/"};
                return new String[]{"Australian Bureau of Statistics", "https://www.abs.gov.au/"};
            case "CAD":
                if (bank || t.contains("boc")) return new String[]{"Bank of Canada", "https://www.bankofcanada.ca/"};
                return new String[]{"Statistics Canada", "https://www.statcan.gc.ca/en/start"};
            case "CHF":
                return new String[]{"Swiss National Bank", "https://www.snb.ch/en/"};
            case "NZD":
                if (bank || t.contains("rbnz")) return new String[]{"Reserve Bank of New Zealand", "https://www.rbnz.govt.nz/"};
                return new String[]{"Stats NZ", "https://www.stats.govt.nz/"};
            default:
                return new String[]{"ForexFactory calendar", "https://www.forexfactory.com/calendar"};
        }
    }

    /** A plain-English explanation of what an event means. */
    public static String explain(String title) {
        String t = title.toLowerCase();
        if (t.contains("cpi") || t.contains("inflation")) return I18n.tr("Inflation report: how fast prices are rising. Higher than expected often pushes stocks down (interest rates may stay high).");
        if (t.contains("ppi")) return I18n.tr("Producer prices: inflation at the factory level - an early hint of future consumer inflation.");
        if (t.contains("non-farm") || t.contains("nfp") || t.contains("employment change")) return I18n.tr("Jobs report: how many jobs were created. One of the biggest market movers of the month.");
        if (t.contains("unemployment")) return I18n.tr("How many people are out of work. A sudden rise can mean the economy is slowing.");
        if (t.contains("fomc") || t.contains("federal funds") || t.contains("powell")) return I18n.tr("The US central bank (the Fed) on interest rates. Can move every market within seconds.");
        if (t.contains("rate") && (t.contains("decision") || t.contains("statement") || t.contains("cash rate") || t.contains("main refinancing")))
            return I18n.tr("A central bank decides its interest rate. Surprises move currencies and stock markets.");
        if (t.contains("gdp")) return I18n.tr("GDP: how fast the whole economy is growing (or shrinking).");
        if (t.contains("retail sales")) return I18n.tr("How much people spent in shops - shoppers drive a big part of the economy.");
        if (t.contains("pmi") || t.contains("ism")) return I18n.tr("Survey of business managers. Above 50 = business is growing, below 50 = shrinking.");
        if (t.contains("consumer confidence") || t.contains("sentiment")) return I18n.tr("How optimistic people feel about the economy.");
        if (t.contains("crude oil")) return I18n.tr("Oil stockpiles - moves oil prices and energy stocks.");
        if (t.contains("speaks") || t.contains("testifies") || t.contains("press conference")) return I18n.tr("An official speaks - hints about future interest rates can move markets.");
        if (t.contains("holiday")) return I18n.tr("Bank holiday - markets in this country may be closed or quiet.");
        return "";
    }

    /** Made-up events for practice mode. */
    static List<Event> practice() {
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate monday = LocalDate.now(ny).with(java.time.DayOfWeek.MONDAY);
        Random r = new Random(monday.toEpochDay());
        String[][] ev = {
                {"0", "08:30", "USD", "High", "Core CPI m/m", "0.3%", "0.2%"},
                {"0", "10:00", "USD", "Medium", "ISM Services PMI", "52.1", "51.4"},
                {"1", "04:00", "EUR", "Medium", "German ZEW Economic Sentiment", "12.5", "10.3"},
                {"1", "19:30", "AUD", "High", "Cash Rate", "4.10%", "4.10%"},
                {"2", "08:30", "USD", "High", "Retail Sales m/m", "0.4%", "0.6%"},
                {"2", "14:00", "USD", "High", "FOMC Statement", "", ""},
                {"3", "02:00", "GBP", "High", "GDP m/m", "0.1%", "-0.1%"},
                {"3", "08:30", "USD", "Medium", "Unemployment Claims", "221K", "218K"},
                {"4", "08:30", "USD", "High", "Non-Farm Employment Change", "180K", "175K"},
                {"4", "21:30", "CNY", "Medium", "CPI y/y", "0.5%", "0.4%"}};
        List<Event> out = new ArrayList<>();
        for (String[] e : ev) {
            LocalDate d = monday.plusDays(Integer.parseInt(e[0]));
            ZonedDateTime t = d.atTime(java.time.LocalTime.parse(e[1])).atZone(ny);
            out.add(new Event(e[4], e[2], t, d, e[3], e[5], e[6], t.isBefore(ZonedDateTime.now(ny)) ? (r.nextBoolean() ? e[5] : e[6]) : ""));
        }
        lastStatus = "practice data";
        return out;
    }
}
