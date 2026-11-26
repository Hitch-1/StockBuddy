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
            if (WorldMarkets.isUs(market)) {
                out.add(I18n.tr("Buy ") + usTicker + I18n.tr(" in almost any broker app - it trades on the ") + usVenue + I18n.tr(" in US dollars."));
                out.add(I18n.tr("Simplest way to own the 500 biggest US companies at once: an S&P 500 ETF such as SPY or VOO."));
                return out;
            }
            if (hasMainUsListing())
                out.add(I18n.tr("Easiest: buy ") + usTicker + I18n.tr(" on the ") + usVenue + I18n.tr(" - almost every broker app has it, priced in US dollars."));
            else if (usTicker != null)
                out.add(I18n.tr("In the US: ") + usTicker + I18n.tr(" on the OTC market. Some broker apps allow it (Schwab, Fidelity, Interactive Brokers...); prices can be less smooth."));
            out.add(I18n.tr("At home: ") + homeTicker.replaceAll("\\.[A-Z]+$", "") + I18n.tr(" on the ") + homeExchange + I18n.tr(". Needs a broker with international trading ")
                    + I18n.tr("(for example Interactive Brokers or Saxo - check what is offered in your country)."));
            if (homeTicker.endsWith(".SS"))
                out.add(I18n.tr("Mainland China shares are usually bought by foreigners through Hong Kong's \"Stock Connect\" (international brokers), or via a China ETF."));
            if (homeTicker.endsWith(".NS") && usTicker == null)
                out.add(I18n.tr("Indian shares are hard for foreigners to buy directly - an India ETF is the practical way."));
            if (countryEtf != null)
                out.add(I18n.tr("Simplest way to own the whole market: the ETF ") + countryEtf + I18n.tr(" (a basket of this country's big companies, sold in US broker apps)."));
            return out;
        }
    }


    /**
     * The biggest companies of each market, largest first (roughly by market value in 2025).
     * Format per company: home ticker (as Yahoo writes it) ; name ; US ticker ; US venue (NYSE / Nasdaq / OTC).
     * A US listing is only given where it's well known - otherwise use the home exchange or the country ETF.
     */
    private static final Object[][] MARKETS = {
            {"^GSPC", "NYSE / Nasdaq", "SPY", new String[]{
                    "AAPL;Apple;AAPL;Nasdaq", "MSFT;Microsoft;MSFT;Nasdaq", "NVDA;NVIDIA;NVDA;Nasdaq", "AMZN;Amazon;AMZN;Nasdaq",
                    "GOOGL;Alphabet (Google);GOOGL;Nasdaq", "META;Meta Platforms;META;Nasdaq", "AVGO;Broadcom;AVGO;Nasdaq", "TSLA;Tesla;TSLA;Nasdaq",
                    "BRK-B;Berkshire Hathaway;BRK-B;NYSE", "JPM;JPMorgan Chase;JPM;NYSE", "LLY;Eli Lilly;LLY;NYSE", "V;Visa;V;NYSE",
                    "WMT;Walmart;WMT;NYSE", "ORCL;Oracle;ORCL;NYSE", "MA;Mastercard;MA;NYSE", "XOM;Exxon Mobil;XOM;NYSE",
                    "NFLX;Netflix;NFLX;Nasdaq", "COST;Costco;COST;Nasdaq", "JNJ;Johnson & Johnson;JNJ;NYSE", "PG;Procter & Gamble;PG;NYSE"}},
            {"^GSPTSE", "Toronto Stock Exchange", "EWC", new String[]{
                    "RY.TO;Royal Bank of Canada;RY;NYSE", "SHOP.TO;Shopify;SHOP;Nasdaq", "TD.TO;Toronto-Dominion Bank;TD;NYSE", "ENB.TO;Enbridge;ENB;NYSE",
                    "BN.TO;Brookfield Corp;BN;NYSE", "CNQ.TO;Canadian Natural Resources;CNQ;NYSE", "CP.TO;Canadian Pacific Kansas City;CP;NYSE",
                    "CNR.TO;Canadian National Railway;CNI;NYSE", "BMO.TO;Bank of Montreal;BMO;NYSE", "BNS.TO;Scotiabank;BNS;NYSE", "CM.TO;CIBC;CM;NYSE",
                    "TRI.TO;Thomson Reuters;TRI;NYSE", "SU.TO;Suncor Energy;SU;NYSE", "ATD.TO;Alimentation Couche-Tard;;", "MFC.TO;Manulife;MFC;NYSE",
                    "CSU.TO;Constellation Software;;", "WCN.TO;Waste Connections;WCN;NYSE", "AEM.TO;Agnico Eagle Mines;AEM;NYSE", "TRP.TO;TC Energy;TRP;NYSE",
                    "NA.TO;National Bank of Canada;;"}},
            {"^BVSP", "B3 (Sao Paulo)", "EWZ", new String[]{
                    "PETR4.SA;Petrobras;PBR;NYSE", "VALE3.SA;Vale;VALE;NYSE", "ITUB4.SA;Itau Unibanco;ITUB;NYSE", "BBDC4.SA;Bradesco;BBD;NYSE",
                    "ABEV3.SA;Ambev;ABEV;NYSE", "BBAS3.SA;Banco do Brasil;;", "WEGE3.SA;WEG;;", "B3SA3.SA;B3;;", "BPAC11.SA;BTG Pactual;;",
                    "ITSA4.SA;Itausa;;", "SUZB3.SA;Suzano;SUZ;NYSE", "ELET3.SA;Eletrobras;EBR;NYSE", "RENT3.SA;Localiza;;", "GGBR4.SA;Gerdau;GGB;NYSE",
                    "VIVT3.SA;Telefonica Brasil;VIV;NYSE", "SBSP3.SA;Sabesp;SBS;NYSE", "PRIO3.SA;PRIO;;", "RADL3.SA;Raia Drogasil;;", "EQTL3.SA;Equatorial;;",
                    "CMIG4.SA;Cemig;CIG;NYSE"}},
            {"^FTSE", "London Stock Exchange", "EWU", new String[]{
                    "AZN.L;AstraZeneca;AZN;Nasdaq", "SHEL.L;Shell;SHEL;NYSE", "HSBA.L;HSBC;HSBC;NYSE", "ULVR.L;Unilever;UL;NYSE", "RIO.L;Rio Tinto;RIO;NYSE",
                    "BP.L;BP;BP;NYSE", "REL.L;RELX;RELX;NYSE", "GSK.L;GSK;GSK;NYSE", "BATS.L;British American Tobacco;BTI;NYSE", "LSEG.L;London Stock Exchange Group;;",
                    "DGE.L;Diageo;DEO;NYSE", "BARC.L;Barclays;BCS;NYSE", "LLOY.L;Lloyds Banking Group;LYG;NYSE", "RR.L;Rolls-Royce;;", "NG.L;National Grid;NGG;NYSE",
                    "GLEN.L;Glencore;;", "BA.L;BAE Systems;;", "CPG.L;Compass Group;;", "HLN.L;Haleon;HLN;NYSE", "NWG.L;NatWest;NWG;NYSE"}},
            {"^GDAXI", "Frankfurt (Xetra)", "EWG", new String[]{
                    "SAP.DE;SAP;SAP;NYSE", "SIE.DE;Siemens;;", "ALV.DE;Allianz;;", "DTE.DE;Deutsche Telekom;;", "MUV2.DE;Munich Re;;", "AIR.DE;Airbus;;",
                    "MBG.DE;Mercedes-Benz;;", "BMW.DE;BMW;;", "IFX.DE;Infineon;;", "DB1.DE;Deutsche Boerse;;", "RHM.DE;Rheinmetall;;", "DBK.DE;Deutsche Bank;DB;NYSE",
                    "BAS.DE;BASF;;", "ADS.DE;Adidas;;", "SHL.DE;Siemens Healthineers;;", "VOW3.DE;Volkswagen;;", "MRK.DE;Merck KGaA;;", "BAYN.DE;Bayer;;",
                    "DHL.DE;DHL Group;;", "EOAN.DE;E.ON;;"}},
            {"^FCHI", "Euronext Paris", "EWQ", new String[]{
                    "MC.PA;LVMH;;", "OR.PA;L'Oreal;;", "RMS.PA;Hermes;;", "TTE.PA;TotalEnergies;TTE;NYSE", "SAN.PA;Sanofi;SNY;Nasdaq", "SU.PA;Schneider Electric;;",
                    "AI.PA;Air Liquide;;", "EL.PA;EssilorLuxottica;;", "BNP.PA;BNP Paribas;;", "SAF.PA;Safran;;", "CS.PA;AXA;;", "DG.PA;Vinci;;",
                    "RI.PA;Pernod Ricard;;", "DSY.PA;Dassault Systemes;;", "BN.PA;Danone;;", "ACA.PA;Credit Agricole;;", "CAP.PA;Capgemini;;", "KER.PA;Kering;;",
                    "ORA.PA;Orange;;", "STLAP.PA;Stellantis;STLA;NYSE"}},
            {"^STOXX50E", "Euronext / Madrid / Milan", "EZU", new String[]{
                    "ASML.AS;ASML;ASML;Nasdaq", "SAN.MC;Banco Santander;SAN;NYSE", "IBE.MC;Iberdrola;;", "ITX.MC;Inditex;;", "BBVA.MC;BBVA;BBVA;NYSE",
                    "UCG.MI;UniCredit;;", "ISP.MI;Intesa Sanpaolo;;", "ENEL.MI;Enel;;", "RACE.MI;Ferrari;RACE;NYSE", "INGA.AS;ING Group;ING;NYSE",
                    "PRX.AS;Prosus;;", "AD.AS;Ahold Delhaize;;", "NOKIA.HE;Nokia;NOK;NYSE", "ABI.BR;AB InBev;BUD;NYSE", "ENI.MI;Eni;E;NYSE",
                    "ADYEN.AS;Adyen;;", "UMG.AS;Universal Music Group;;", "KBC.BR;KBC Group;;", "NDA-FI.HE;Nordea;;", "PHIA.AS;Philips;PHG;NYSE"}},
            {"^N225", "Tokyo Stock Exchange", "EWJ", new String[]{
                    "7203.T;Toyota;TM;NYSE", "8306.T;Mitsubishi UFJ Financial;MUFG;NYSE", "6758.T;Sony;SONY;NYSE", "9983.T;Fast Retailing (Uniqlo);;",
                    "6861.T;Keyence;;", "9984.T;SoftBank Group;;", "6501.T;Hitachi;;", "8316.T;Sumitomo Mitsui Financial;SMFG;NYSE", "8035.T;Tokyo Electron;;",
                    "9432.T;NTT;;", "4063.T;Shin-Etsu Chemical;;", "7974.T;Nintendo;;", "6098.T;Recruit;;", "8058.T;Mitsubishi Corp;;", "8001.T;Itochu;;",
                    "8411.T;Mizuho Financial;MFG;NYSE", "4502.T;Takeda;TAK;NYSE", "7267.T;Honda;HMC;NYSE", "6367.T;Daikin;;", "6857.T;Advantest;;"}},
            {"000001.SS", "Shanghai Stock Exchange", "ASHR", new String[]{
                    "600519.SS;Kweichow Moutai;;", "601398.SS;ICBC (bank);;", "601288.SS;Agricultural Bank of China;;", "601939.SS;China Construction Bank;;",
                    "601857.SS;PetroChina;;", "600036.SS;China Merchants Bank;;", "601988.SS;Bank of China;;", "601318.SS;Ping An Insurance;;",
                    "600900.SS;China Yangtze Power;;", "600028.SS;Sinopec;;", "601088.SS;China Shenhua Energy;;", "600941.SS;China Mobile;;",
                    "601628.SS;China Life Insurance;;", "601899.SS;Zijin Mining;;", "600030.SS;CITIC Securities;;", "601166.SS;Industrial Bank;;",
                    "600309.SS;Wanhua Chemical;;", "601012.SS;LONGi Green Energy;;", "600276.SS;Jiangsu Hengrui Medicine;;", "601728.SS;China Telecom;;"}},
            {"^HSI", "Hong Kong Stock Exchange", "EWH", new String[]{
                    "0700.HK;Tencent;;", "9988.HK;Alibaba;BABA;NYSE", "1299.HK;AIA Group;;", "0939.HK;China Construction Bank;;", "0005.HK;HSBC (Hong Kong listing);;",
                    "3690.HK;Meituan;;", "0941.HK;China Mobile;;", "1398.HK;ICBC;;", "1810.HK;Xiaomi;;", "9618.HK;JD.com;JD;Nasdaq", "2318.HK;Ping An Insurance;;",
                    "0388.HK;Hong Kong Exchanges;;", "3988.HK;Bank of China;;", "1211.HK;BYD;;", "9999.HK;NetEase;NTES;Nasdaq", "9888.HK;Baidu;BIDU;Nasdaq",
                    "0883.HK;CNOOC;;", "2020.HK;ANTA Sports;;", "0016.HK;Sun Hung Kai Properties;;", "0001.HK;CK Hutchison;;"}},
            {"^BSESN", "National Stock Exchange of India", "INDA", new String[]{
                    "RELIANCE.NS;Reliance Industries;;", "HDFCBANK.NS;HDFC Bank;HDB;NYSE", "TCS.NS;Tata Consultancy Services;;", "BHARTIARTL.NS;Bharti Airtel;;",
                    "ICICIBANK.NS;ICICI Bank;IBN;NYSE", "INFY.NS;Infosys;INFY;NYSE", "SBIN.NS;State Bank of India;;", "HINDUNILVR.NS;Hindustan Unilever;;",
                    "ITC.NS;ITC;;", "LICI.NS;Life Insurance Corp of India;;", "LT.NS;Larsen & Toubro;;", "BAJFINANCE.NS;Bajaj Finance;;", "HCLTECH.NS;HCL Technologies;;",
                    "KOTAKBANK.NS;Kotak Mahindra Bank;;", "SUNPHARMA.NS;Sun Pharma;;", "MARUTI.NS;Maruti Suzuki;;", "AXISBANK.NS;Axis Bank;;",
                    "ULTRACEMCO.NS;UltraTech Cement;;", "NTPC.NS;NTPC;;", "WIPRO.NS;Wipro;WIT;NYSE"}},
            {"^KS11", "Korea Exchange", "EWY", new String[]{
                    "005930.KS;Samsung Electronics;;", "000660.KS;SK Hynix;;", "373220.KS;LG Energy Solution;;", "207940.KS;Samsung Biologics;;",
                    "005380.KS;Hyundai Motor;;", "000270.KS;Kia;;", "068270.KS;Celltrion;;", "105560.KS;KB Financial;KB;NYSE", "055550.KS;Shinhan Financial;SHG;NYSE",
                    "012450.KS;Hanwha Aerospace;;", "035420.KS;NAVER;;", "005490.KS;POSCO Holdings;PKX;NYSE", "006400.KS;Samsung SDI;;", "028260.KS;Samsung C&T;;",
                    "035720.KS;Kakao;;", "012330.KS;Hyundai Mobis;;", "032830.KS;Samsung Life Insurance;;", "086790.KS;Hana Financial;;", "051910.KS;LG Chem;;",
                    "015760.KS;KEPCO;KEP;NYSE"}},
            {"^TWII", "Taiwan Stock Exchange", "EWT", new String[]{
                    "2330.TW;TSMC;TSM;NYSE", "2317.TW;Hon Hai (Foxconn);;", "2454.TW;MediaTek;;", "2308.TW;Delta Electronics;;", "2382.TW;Quanta Computer;;",
                    "2881.TW;Fubon Financial;;", "2412.TW;Chunghwa Telecom;CHT;NYSE", "2882.TW;Cathay Financial;;", "2891.TW;CTBC Financial;;",
                    "3711.TW;ASE Technology;ASX;NYSE", "2303.TW;United Microelectronics;UMC;NYSE", "2886.TW;Mega Financial;;", "1216.TW;Uni-President;;",
                    "2345.TW;Accton;;", "3231.TW;Wistron;;", "2357.TW;Asustek;;", "2603.TW;Evergreen Marine;;", "1301.TW;Formosa Plastics;;",
                    "2002.TW;China Steel;;", "2884.TW;E.Sun Financial;;"}},
            {"^AXJO", "Australian Securities Exchange", "EWA", new String[]{
                    "BHP.AX;BHP;BHP;NYSE", "CBA.AX;Commonwealth Bank;;", "CSL.AX;CSL;;", "NAB.AX;National Australia Bank;;", "WBC.AX;Westpac;;",
                    "ANZ.AX;ANZ Group;;", "WES.AX;Wesfarmers;;", "MQG.AX;Macquarie Group;;", "GMG.AX;Goodman Group;;", "RIO.AX;Rio Tinto (Australian listing);;",
                    "FMG.AX;Fortescue;;", "WDS.AX;Woodside Energy;WDS;NYSE", "TLS.AX;Telstra;;", "WOW.AX;Woolworths;;", "TCL.AX;Transurban;;",
                    "ALL.AX;Aristocrat Leisure;;", "REA.AX;REA Group;;", "QBE.AX;QBE Insurance;;", "XRO.AX;Xero;;", "JHX.AX;James Hardie;JHX;NYSE"}}};

    /** Every company we know, all markets, largest first within each market. */
    public static final List<Entry> ALL;
    static {
        List<Entry> all = new ArrayList<>();
        for (Object[] m : MARKETS) {
            for (String row : (String[]) m[3]) {
                String[] c = row.split(";", -1);
                all.add(new Entry((String) m[0], c[1], c[0], (String) m[1], c[2].isEmpty() ? null : c[2], c[3].isEmpty() ? null : c[3], (String) m[2]));
            }
        }
        ALL = Collections.unmodifiableList(all);
    }

    public static final int[] TOP_CHOICES = {3, 5, 10, 20};

    /** How many companies per market to follow: 0 (off), 3, 5, 10 or 20. */
    public static int top() { return Settings.getInt("worldStocks.top", Settings.getBool("worldStocks", true) ? 3 : 0); }

    public static void setTop(int n) { Settings.set("worldStocks.top", n); Settings.set("worldStocks", n > 0); }

    /** The companies followed right now: the top N of every market. */
    public static List<Entry> active() {
        int n = top();
        List<Entry> out = new ArrayList<>();
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        for (Entry e : ALL) {
            int c = count.containsKey(e.market) ? count.get(e.market) : 0;
            if (c < n) { out.add(e); count.put(e.market, c + 1); }
        }
        return out;
    }

    private WorldStocks() {}

    public static boolean enabled() { return top() > 0; }

    public static Entry forTicker(String t) {
        for (Entry e : ALL) if (e.analysedTicker().equalsIgnoreCase(t)) return e;
        return null;
    }

    public static String marketName(String symbol) {
        for (WorldMarkets.Market m : WorldMarkets.all()) if (m.symbol.equals(symbol)) return m.name;
        return symbol;
    }
}
