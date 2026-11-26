package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SEC EDGAR: the official reports US companies must file with the government (free, no key).
 * We read: yearly revenue and profit (to see if the business is growing), the date of the latest
 * annual report, how many insider-trading reports (Form 4) and "major event" reports (8-K) were filed recently.
 *
 * The SEC asks every program to say who it is. StockBuddy sends "StockBuddy (personal research app)";
 * if the SEC refuses, add your email in StockBuddy (Data Sources) - it is only ever sent to sec.gov.
 */
public final class SecEdgar {
    private static final long GAP_MS = 150;   // the SEC allows max 10 requests a second
    private static long nextCall;
    private static Map<String, String> tickerToCik;
    public static volatile int lastOk, lastRefused;

    private SecEdgar() {}

    public static String email() { return Settings.get("sec.email", "").trim(); }

    public static void setEmail(String e) { Settings.set("sec.email", e == null ? "" : e.trim()); }

    private static String userAgent() {
        String e = email();
        return "StockBuddy/1.5 (personal research app" + (e.isEmpty() ? "" : "; " + e) + ")";
    }

    public static void resetCounts() { lastOk = 0; lastRefused = 0; }

    public static String status() {
        if (lastOk > 0) return "OK - official filings for " + lastOk + " US stocks";
        return "Could not get data from sec.gov - check your internet; if it keeps failing, add your email (Data Sources) so the SEC knows who is asking";
    }

    /** Loads SEC data for one US stock. Returns true if anything was found. */
    public static boolean load(Stock s) {
        if (s.ticker.contains(".") || s.ticker.startsWith("^") || s.ticker.equals("SPY") || s.ticker.equals("QQQ")) return false;
        String cik = cik(s.ticker);
        if (cik == null) return false;
        boolean found = false;
        String sub = call("https://data.sec.gov/submissions/CIK" + cik + ".json");
        if (sub != null) {
            try {
                Object recent = Json.path(Json.parse(sub), "filings", "recent");
                List<Object> forms = Json.list(Json.path(recent, "form")), dates = Json.list(Json.path(recent, "filingDate"));
                LocalDate today = LocalDate.now();
                int f4 = 0, k8 = 0;
                for (int i = 0; i < forms.size() && i < dates.size(); i++) {
                    String form = String.valueOf(forms.get(i));
                    LocalDate d;
                    try { d = LocalDate.parse(String.valueOf(dates.get(i))); } catch (Exception e) { continue; }
                    if (form.equals("4") && !d.isBefore(today.minusDays(90))) f4++;
                    if (form.equals("8-K") && !d.isBefore(today.minusDays(30))) k8++;
                    if (form.equals("10-K") && s.secLast10K == null) s.secLast10K = d.toString();
                }
                s.secForm4Count = f4;
                s.sec8KCount = k8;
                found = true;
            } catch (Exception e) { Log.debug("SEC submissions unreadable for " + s.ticker + ": " + e); }
        }
        double[] rev = yearly(cik, "Revenues");
        if (rev == null) rev = yearly(cik, "RevenueFromContractWithCustomerExcludingAssessedTax");
        if (rev != null && rev[0] > 0) { s.secRevenueGrowth = 100 * (rev[1] / rev[0] - 1); found = true; }
        double[] ni = yearly(cik, "NetIncomeLoss");
        if (ni != null) { s.secNetIncome = ni[1]; found = true; }

        if (!Double.isNaN(s.secRevenueGrowth)) s.keyStats.put("Revenue growth (SEC, last year)", String.format(java.util.Locale.US, "%+.1f%%", s.secRevenueGrowth));
        if (!Double.isNaN(s.secNetIncome)) s.keyStats.put("Net income (SEC, last year)", (s.secNetIncome < 0 ? "-" : "") + KeyStats.big(Math.abs(s.secNetIncome)));
        if (s.secLast10K != null) s.keyStats.put("Latest annual report (10-K)", s.secLast10K);
        if (s.secForm4Count >= 0) s.keyStats.put("Insider trade reports (90 days)", String.valueOf(s.secForm4Count));
        if (s.sec8KCount >= 0) s.keyStats.put("Major-event reports (30 days)", String.valueOf(s.sec8KCount));
        synchronized (SecEdgar.class) { if (found) lastOk++; }
        return found;
    }

    /** {previous year, latest year} values of a yearly figure, or null. */
    private static double[] yearly(String cik, String concept) {
        String body = call("https://data.sec.gov/api/xbrl/companyconcept/CIK" + cik + "/us-gaap/" + concept + ".json");
        if (body == null) return null;
        try {
            List<Object> rows = Json.list(Json.path(Json.parse(body), "units", "USD"));
            Map<String, Double> byYear = new HashMap<>();
            for (Object r : rows) {
                Object frame = Json.path(r, "frame");
                if (frame instanceof String && ((String) frame).matches("CY\\d{4}")) byYear.put((String) frame, Json.num(Json.path(r, "val"), Double.NaN));
            }
            List<String> years = new ArrayList<>(byYear.keySet());
            Collections.sort(years);
            if (years.size() < 2) return null;
            String last = years.get(years.size() - 1), prev = years.get(years.size() - 2);
            if (Integer.parseInt(last.substring(2)) < LocalDate.now().getYear() - 2) return null;   // too old
            return new double[]{byYear.get(prev), byYear.get(last)};
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized String cik(String ticker) {
        if (tickerToCik == null) {
            tickerToCik = new HashMap<>();
            Path cache = AppPaths.data("sec-tickers.json");
            String body = null;
            try {
                if (Files.exists(cache) && System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() < 7L * 24 * 3600_000)
                    body = new String(Files.readAllBytes(cache), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            if (body == null) {
                body = call("https://www.sec.gov/files/company_tickers.json");
                if (body != null) {
                    try { Files.createDirectories(cache.getParent()); Files.write(cache, body.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
                }
            }
            if (body != null) {
                try {
                    Object root = Json.parse(body);
                    if (root instanceof Map) for (Object row : ((Map<?, ?>) root).values()) {
                        Object t = Json.path(row, "ticker");
                        double c = Json.num(Json.path(row, "cik_str"), Double.NaN);
                        if (t instanceof String && !Double.isNaN(c)) tickerToCik.put(((String) t).toUpperCase(), String.format("%010d", (long) c));
                    }
                } catch (Exception e) { Log.debug("SEC ticker list unreadable: " + e); }
            }
        }
        String c = tickerToCik.get(ticker.toUpperCase());
        return c != null ? c : tickerToCik.get(ticker.toUpperCase().replace('-', '.'));
    }

    private static String call(String url) {
        synchronized (SecEdgar.class) {
            long wait = nextCall - System.currentTimeMillis();
            if (wait > 0) { try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; } }
            nextCall = System.currentTimeMillis() + GAP_MS;
        }
        String body = Http.request("GET", url, null, null, userAgent());
        if (body == null) synchronized (SecEdgar.class) { lastRefused++; }
        return body;
    }
}
