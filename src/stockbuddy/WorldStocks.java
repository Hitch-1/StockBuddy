package stockbuddy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The biggest companies of the world's largest stock markets (outside the US, which the watchlist covers),
 * and WHERE you can buy them: a US listing (easiest), the home exchange (needs an international broker),
 * or a whole-country ETF (simplest way to own the whole market).
 *
 * The list is fixed on purpose (big, well-known companies). Listings change rarely, but always check
 * the exact ticker in your broker before buying.
 */
public final class WorldStocks {

    public static final class Entry {
        public final String market;      // index symbol in WorldMarkets, e.g. "^FTSE"
        public final String name, homeTicker, homeExchange;
        public final String usTicker;    // null if no US listing
        public final String usVenue;     // "NYSE", "Nasdaq" or "OTC"
        public final String countryEtf;  // a US-listed ETF that holds this country's market

        Entry(String market, String name, String homeTicker, String homeExchange, String usTicker, String usVenue, String countryEtf) {
            this.market = market; this.name = name; this.homeTicker = homeTicker; this.homeExchange = homeExchange;
            this.usTicker = usTicker; this.usVenue = usVenue; this.countryEtf = countryEtf;
        }

        /** Analysed with the US listing when it trades on a big US exchange (dollar prices, more data); otherwise at home. */
        public String analysedTicker() { return hasMainUsListing() ? usTicker : homeTicker; }

        public boolean hasMainUsListing() { return usTicker != null && !"OTC".equals(usVenue); }

        /** Plain-English buying options, best first. */
        public List<String> howToBuy() {
            List<String> out = new ArrayList<>();
            if (hasMainUsListing())
                out.add("Easiest: buy " + usTicker + " on the " + usVenue + " - almost every broker app has it, priced in US dollars.");
            else if (usTicker != null)
                out.add("In the US: " + usTicker + " on the OTC market. Some broker apps allow it (Schwab, Fidelity, Interactive Brokers...); prices can be less smooth.");
            out.add("At home: " + homeTicker.replaceAll("\\.[A-Z]+$", "") + " on the " + homeExchange + ". Needs a broker with international trading "
                    + "(for example Interactive Brokers or Saxo - check what is offered in your country).");
            if (homeTicker.endsWith(".SS"))
                out.add("Mainland China shares are usually bought by foreigners through Hong Kong's \"Stock Connect\" (international brokers), or via a China ETF.");
            if (homeTicker.endsWith(".NS") && usTicker == null)
                out.add("Indian shares are hard for foreigners to buy directly - an India ETF is the practical way.");
            if (countryEtf != null)
                out.add("Simplest way to own the whole market: the ETF " + countryEtf + " (a basket of this country's big companies, sold in US broker apps).");
            return out;
        }
    }

    public static final List<Entry> ALL = Collections.unmodifiableList(Arrays.asList(
            // Canada (TSX)
            new Entry("^GSPTSE", "Royal Bank of Canada", "RY.TO", "Toronto Stock Exchange", "RY", "NYSE", "EWC"),
            new Entry("^GSPTSE", "Shopify", "SHOP.TO", "Toronto Stock Exchange", "SHOP", "Nasdaq", "EWC"),
            new Entry("^GSPTSE", "Enbridge", "ENB.TO", "Toronto Stock Exchange", "ENB", "NYSE", "EWC"),
            // Brazil (Bovespa)
            new Entry("^BVSP", "Petrobras", "PETR4.SA", "B3 (Sao Paulo)", "PBR", "NYSE", "EWZ"),
            new Entry("^BVSP", "Vale", "VALE3.SA", "B3 (Sao Paulo)", "VALE", "NYSE", "EWZ"),
            new Entry("^BVSP", "Itau Unibanco", "ITUB4.SA", "B3 (Sao Paulo)", "ITUB", "NYSE", "EWZ"),
            // UK (FTSE 100)
            new Entry("^FTSE", "AstraZeneca", "AZN.L", "London Stock Exchange", "AZN", "Nasdaq", "EWU"),
            new Entry("^FTSE", "Shell", "SHEL.L", "London Stock Exchange", "SHEL", "NYSE", "EWU"),
            new Entry("^FTSE", "HSBC", "HSBA.L", "London Stock Exchange", "HSBC", "NYSE", "EWU"),
            // Germany (DAX)
            new Entry("^GDAXI", "SAP", "SAP.DE", "Frankfurt (Xetra)", "SAP", "NYSE", "EWG"),
            new Entry("^GDAXI", "Siemens", "SIE.DE", "Frankfurt (Xetra)", "SIEGY", "OTC", "EWG"),
            new Entry("^GDAXI", "Allianz", "ALV.DE", "Frankfurt (Xetra)", "ALIZY", "OTC", "EWG"),
            // France (CAC 40)
            new Entry("^FCHI", "LVMH", "MC.PA", "Euronext Paris", "LVMUY", "OTC", "EWQ"),
            new Entry("^FCHI", "TotalEnergies", "TTE.PA", "Euronext Paris", "TTE", "NYSE", "EWQ"),
            new Entry("^FCHI", "L'Oreal", "OR.PA", "Euronext Paris", "LRLCY", "OTC", "EWQ"),
            // Eurozone (Euro Stoxx 50)
            new Entry("^STOXX50E", "ASML", "ASML.AS", "Euronext Amsterdam", "ASML", "Nasdaq", "EZU"),
            new Entry("^STOXX50E", "Banco Santander", "SAN.MC", "Madrid Stock Exchange", "SAN", "NYSE", "EZU"),
            // Japan (Nikkei 225)
            new Entry("^N225", "Toyota", "7203.T", "Tokyo Stock Exchange", "TM", "NYSE", "EWJ"),
            new Entry("^N225", "Sony", "6758.T", "Tokyo Stock Exchange", "SONY", "NYSE", "EWJ"),
            new Entry("^N225", "Mitsubishi UFJ Financial", "8306.T", "Tokyo Stock Exchange", "MUFG", "NYSE", "EWJ"),
            // China (Shanghai)
            new Entry("000001.SS", "Kweichow Moutai", "600519.SS", "Shanghai Stock Exchange", null, null, "ASHR"),
            new Entry("000001.SS", "ICBC (bank)", "601398.SS", "Shanghai Stock Exchange", "IDCBY", "OTC", "ASHR"),
            // Hong Kong (Hang Seng)
            new Entry("^HSI", "Tencent", "0700.HK", "Hong Kong Stock Exchange", "TCEHY", "OTC", "EWH"),
            new Entry("^HSI", "Alibaba", "9988.HK", "Hong Kong Stock Exchange", "BABA", "NYSE", "EWH"),
            new Entry("^HSI", "AIA Group", "1299.HK", "Hong Kong Stock Exchange", "AAGIY", "OTC", "EWH"),
            // India (Sensex)
            new Entry("^BSESN", "Reliance Industries", "RELIANCE.NS", "National Stock Exchange of India", null, null, "INDA"),
            new Entry("^BSESN", "Infosys", "INFY.NS", "National Stock Exchange of India", "INFY", "NYSE", "INDA"),
            new Entry("^BSESN", "HDFC Bank", "HDFCBANK.NS", "National Stock Exchange of India", "HDB", "NYSE", "INDA"),
            // South Korea (KOSPI)
            new Entry("^KS11", "Samsung Electronics", "005930.KS", "Korea Exchange", null, null, "EWY"),
            new Entry("^KS11", "SK Hynix", "000660.KS", "Korea Exchange", null, null, "EWY"),
            new Entry("^KS11", "KB Financial", "105560.KS", "Korea Exchange", "KB", "NYSE", "EWY"),
            // Taiwan (TAIEX)
            new Entry("^TWII", "TSMC", "2330.TW", "Taiwan Stock Exchange", "TSM", "NYSE", "EWT"),
            new Entry("^TWII", "Hon Hai (Foxconn)", "2317.TW", "Taiwan Stock Exchange", "HNHPF", "OTC", "EWT"),
            new Entry("^TWII", "United Microelectronics", "2303.TW", "Taiwan Stock Exchange", "UMC", "NYSE", "EWT"),
            // Australia (ASX 200)
            new Entry("^AXJO", "BHP", "BHP.AX", "Australian Securities Exchange", "BHP", "NYSE", "EWA"),
            new Entry("^AXJO", "Commonwealth Bank", "CBA.AX", "Australian Securities Exchange", "CMWAY", "OTC", "EWA"),
            new Entry("^AXJO", "CSL", "CSL.AX", "Australian Securities Exchange", "CSLLY", "OTC", "EWA")));

    private WorldStocks() {}

    public static boolean enabled() { return Settings.getBool("worldStocks", true); }

    public static Entry forTicker(String t) {
        for (Entry e : ALL) if (e.analysedTicker().equalsIgnoreCase(t)) return e;
        return null;
    }

    public static String marketName(String symbol) {
        for (WorldMarkets.Market m : WorldMarkets.all()) if (m.symbol.equals(symbol)) return m.name;
        return symbol;
    }
}
