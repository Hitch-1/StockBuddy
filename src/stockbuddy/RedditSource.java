package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads today's "hot" posts from r/ValueInvesting, r/options and r/wallstreetbets,
 * finds which stock tickers people talk about, and whether the mood is positive or negative.
 *
 * Optional: Reddit sometimes blocks anonymous readers. If that happens, create a free
 * "script" app at reddit.com/prefs/apps and set REDDIT_CLIENT_ID and REDDIT_CLIENT_SECRET.
 */
public final class RedditSource {
    /** Subreddit -> how much we trust it (wallstreetbets is fun but very hype-driven). */
    public static final Map<String, Double> SUBS = new LinkedHashMap<>();
    static {
        SUBS.put("ValueInvesting", 1.0);
        SUBS.put("options", 0.8);
        SUBS.put("wallstreetbets", 0.5);
    }

    private static final Pattern DOLLAR = Pattern.compile("\\$([A-Za-z]{1,5})\\b");
    private static final Pattern CAPS = Pattern.compile("\\b([A-Z]{2,5})\\b");
    private static final Set<String> NOT_TICKERS = new HashSet<>(Arrays.asList(
            "YOLO", "DD", "CEO", "CFO", "USA", "US", "IPO", "ETF", "EPS", "GDP", "FED", "SEC", "IMO", "LOL", "ATH",
            "OTM", "ITM", "ATM", "IV", "PE", "DTE", "FOMO", "WSB", "THE", "AND", "FOR", "BUY", "SELL", "HOLD", "CALL",
            "PUT", "PUTS", "CALLS", "TLDR", "EDIT", "AI", "API", "CPI", "PPI", "FOMC", "RIP", "GG", "OP", "EOD", "EOW",
            "NYSE", "UK", "EU", "IRA", "ROTH", "TA", "FA", "MOASS", "HODL", "LMAO", "WTF", "OMG", "FYI", "PSA", "ER",
            "IT", "ARE", "NOT", "ALL", "NEW", "ONE", "NOW", "BIG", "CAN", "HAS", "WAS", "ANY", "OUT", "GO", "AM", "PM"));

    private static final String[] BULL = {"buy", "bought", "long", "calls", "bullish", "undervalued", "moon", "rocket",
            "breakout", "cheap", "strong", "beat", "growth", "upside", "rally", "squeeze", "accumulate", "loading", "green", "rip"};
    private static final String[] BEAR = {"sell", "sold", "short", "puts", "bearish", "overvalued", "crash", "dump",
            "bubble", "expensive", "weak", "miss", "downside", "drop", "bagholder", "red", "fraud", "lawsuit", "downgrade", "tank"};

    /** One Reddit post. */
    public static final class Post {
        public final String sub, title, body;
        public final int upvotes;
        public Post(String sub, String title, String body, int upvotes) {
            this.sub = sub; this.title = title; this.body = body; this.upvotes = upvotes;
        }
    }

    private static String token;
    public static boolean lastLoadWorked;

    private RedditSource() {}

    public static List<Post> fetchHot(int limit) {
        List<Post> posts = new ArrayList<>();
        for (String sub : SUBS.keySet()) {
            String body = fetchSub(sub, limit);
            if (body == null) continue;
            try {
                for (Object child : Json.list(Json.path(Json.parse(body), "data", "children"))) {
                    Object d = Json.path(child, "data");
                    if (Boolean.TRUE.equals(Json.path(d, "stickied"))) continue;
                    posts.add(new Post(sub, String.valueOf(Json.path(d, "title")),
                            Objects.toString(Json.path(d, "selftext"), ""),
                            (int) Json.num(Json.path(d, "score"), 0)));
                }
            } catch (Exception e) {
                Log.debug("Reddit r/" + sub + " unreadable: " + e);
            }
        }
        lastLoadWorked = !posts.isEmpty();
        return posts;
    }

    private static String fetchSub(String sub, int limit) {
        String id = System.getenv("REDDIT_CLIENT_ID"), secret = System.getenv("REDDIT_CLIENT_SECRET");
        if (id != null && secret != null) {
            if (token == null) token = login(id, secret);
            if (token != null) {
                String r = Http.request("GET", "https://oauth.reddit.com/r/" + sub + "/hot?limit=" + limit, "Bearer " + token, null,
                        "java:stockbuddy:1.0 (personal use)");
                if (r != null) return r;
            }
        }
        String b = Http.get("https://www.reddit.com/r/" + sub + "/hot.json?limit=" + limit + "&raw_json=1");
        if (b == null) b = Http.get("https://old.reddit.com/r/" + sub + "/hot.json?limit=" + limit + "&raw_json=1");
        return b;
    }

    private static String login(String id, String secret) {
        try {
            String auth = Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
            String body = Http.request("POST", "https://www.reddit.com/api/v1/access_token", "Basic " + auth,
                    "grant_type=client_credentials", "java:stockbuddy:1.0 (personal use)");
            Object t = body == null ? null : Json.path(Json.parse(body), "access_token");
            return t instanceof String ? (String) t : null;
        } catch (Exception e) {
            Log.debug("Reddit login failed: " + e);
            return null;
        }
    }

    /** Finds tickers in the posts. `known` = tickers we already follow (so plain "AAPL" counts too). */
    public static Set<String> findTickers(List<Post> posts, Set<String> known) {
        Map<String, Integer> counts = new HashMap<>();
        for (Post p : posts) for (String t : tickersIn(p.title + " " + p.body, known)) counts.merge(t, 1, Integer::sum);
        Set<String> out = new LinkedHashSet<>();
        counts.entrySet().stream().filter(e -> e.getValue() >= 2 || known.contains(e.getKey()))
                .sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> out.add(e.getKey()));
        return out;
    }

    static Set<String> tickersIn(String text, Set<String> known) {
        Set<String> found = new HashSet<>();
        Matcher d = DOLLAR.matcher(text);
        while (d.find()) { String t = d.group(1).toUpperCase(); if (!NOT_TICKERS.contains(t)) found.add(t); }
        Matcher c = CAPS.matcher(text);
        while (c.find()) { String t = c.group(1); if (known.contains(t) && !NOT_TICKERS.contains(t)) found.add(t); }
        return found;
    }

    /** Adds mentions, mood and a couple of example post titles to each stock. */
    public static void score(List<Post> posts, Map<String, Stock> stocks) {
        Map<String, Double> weightSum = new HashMap<>();
        for (Post p : posts) {
            String text = p.title + " " + p.body;
            String lower = text.toLowerCase();
            double mood = 0;
            for (String w : BULL) mood += count(lower, w);
            for (String w : BEAR) mood -= count(lower, w);
            mood = Math.tanh(mood / 3.0) * SUBS.getOrDefault(p.sub, 0.5);
            int calls = count(lower, "call") , puts = count(lower, "put");
            for (String t : tickersIn(text, stocks.keySet())) {
                Stock s = stocks.get(t);
                if (s == null) continue;
                double w = 1 + Math.log10(1 + Math.max(0, p.upvotes));
                double before = weightSum.getOrDefault(t, 0.0);
                s.redditSentiment = (s.redditSentiment * before + mood * w) / (before + w);
                weightSum.put(t, before + w);
                s.redditMentions++;
                s.redditUpvotes += Math.max(0, p.upvotes);
                if (p.sub.equals("options") || p.sub.equals("wallstreetbets")) { s.optionsTalkCalls += calls; s.optionsTalkPuts += puts; }
                if (s.redditQuotes.size() < 3) s.redditQuotes.add("r/" + p.sub + ": \"" + shorten(p.title, 90) + "\" (" + p.upvotes + " upvotes)");
            }
        }
    }

    private static int count(String text, String word) {
        Matcher m = Pattern.compile("\\b" + word + "s?\\b").matcher(text);
        int n = 0; while (m.find()) n++;
        return n;
    }

    private static String shorten(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 3) + "..."; }
}
