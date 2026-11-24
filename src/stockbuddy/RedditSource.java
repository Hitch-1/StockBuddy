package stockbuddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads today's "hot" posts from r/ValueInvesting, r/options and r/wallstreetbets,
 * finds which stock tickers people talk about, and whether the mood is positive or negative.
 *
 * Where the posts come from, in this order:
 *   1. Reddit's API, if you have an approved key (REDDIT_CLIENT_ID / REDDIT_CLIENT_SECRET)
 *   2. Reddit's public JSON pages (no key)
 *   3. Reddit's public RSS news feeds (no key - often works when 2 is blocked)
 *   4. Plus posts you add yourself, saved in data/reddit.csv (see addManualPosts)
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
    /** A plain-English summary of the last load, for the Data Sources page. */
    public static String lastStatus = "Not loaded yet";

    /** How many days posts you added yourself are used for (older ones are ignored, not deleted). */
    public static final int MANUAL_DAYS = 7;

    private RedditSource() {}

    /** Reads the hot posts from all subreddits (any way that works), plus the posts you added yourself. */
    public static List<Post> fetchHot(int limit) {
        List<Post> posts = new ArrayList<>();
        Set<String> methods = new LinkedHashSet<>();
        for (String sub : SUBS.keySet()) {
            List<Post> got = parseJson(sub, fetchSub(sub, limit));
            if (!got.isEmpty()) methods.add("Reddit pages");
            else {
                got = fetchRss(sub, limit);
                if (!got.isEmpty()) methods.add("Reddit news feeds (RSS)");
            }
            posts.addAll(got);
        }
        int online = posts.size();
        List<Post> mine = manualPosts();
        posts.addAll(mine);
        lastLoadWorked = !posts.isEmpty();
        String own = mine.isEmpty() ? "" : mine.size() + " of your own post" + (mine.size() == 1 ? "" : "s");
        if (online > 0) lastStatus = "OK - " + online + " posts read from " + String.join(" + ", methods) + (own.isEmpty() ? "" : ", plus " + own);
        else if (!own.isEmpty()) lastStatus = "OK - using " + own + " (Reddit itself could not be reached)";
        else lastStatus = "Could not connect - you can add posts yourself (Data Sources: \"Add Reddit posts\")";
        return posts;
    }

    private static List<Post> parseJson(String sub, String body) {
        List<Post> out = new ArrayList<>();
        if (body == null || !body.trim().startsWith("{")) return out;
        try {
            for (Object child : Json.list(Json.path(Json.parse(body), "data", "children"))) {
                Object d = Json.path(child, "data");
                if (Boolean.TRUE.equals(Json.path(d, "stickied"))) continue;
                out.add(new Post(sub, String.valueOf(Json.path(d, "title")),
                        Objects.toString(Json.path(d, "selftext"), ""),
                        (int) Json.num(Json.path(d, "score"), 0)));
            }
        } catch (Exception e) {
            Log.debug("Reddit r/" + sub + " unreadable: " + e);
        }
        return out;
    }

    // ------------------------------------------------------------------ RSS (no key needed)
    private static final Pattern ENTRY = Pattern.compile("(?s)<entry>(.*?)</entry>");
    private static final Pattern TITLE = Pattern.compile("(?s)<title>(.*?)</title>");
    private static final Pattern CONTENT = Pattern.compile("(?s)<content[^>]*>(.*?)</content>");

    /** Reddit publishes every subreddit as a public news feed (Atom/RSS). It has no upvote counts. */
    static List<Post> fetchRss(String sub, int limit) {
        String xml = Http.get("https://www.reddit.com/r/" + sub + "/hot/.rss?limit=" + limit);
        if (xml == null || !xml.contains("<entry")) xml = Http.get("https://old.reddit.com/r/" + sub + "/hot/.rss?limit=" + limit);
        return parseRss(sub, xml);
    }

    static List<Post> parseRss(String sub, String xml) {
        List<Post> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher e = ENTRY.matcher(xml);
        while (e.find()) {
            String entry = e.group(1);
            Matcher t = TITLE.matcher(entry);
            if (!t.find()) continue;
            String title = unescape(t.group(1)).trim();
            Matcher c = CONTENT.matcher(entry);
            String body = c.find() ? unescape(Http.htmlToText(unescape(c.group(1)))).trim() : "";
            body = body.replaceAll("submitted by .*$", "").trim();   // remove Reddit's "submitted by /u/... [link] [comments]" footer
            if (!title.isEmpty()) out.add(new Post(sub, title, body, 0));
        }
        return out;
    }

    static String unescape(String s) {
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            int code;
            try { code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16); } catch (NumberFormatException ex) { code = '?'; }
            m.appendReplacement(b, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        m.appendTail(b);
        return b.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&");
    }

    // ------------------------------------------------------------------ posts you add yourself
    public static Path manualFile() { return AppPaths.data("reddit.csv"); }

    /**
     * Your own Reddit posts, from data/reddit.csv. Columns: date, subreddit, title, upvotes
     * (only "title" is required). Posts older than MANUAL_DAYS days are ignored.
     */
    public static List<Post> manualPosts() {
        List<Post> out = new ArrayList<>();
        Path f = manualFile();
        if (!Files.exists(f)) return out;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (lines.isEmpty()) return out;
            List<String> head = Csv.split(lines.get(0).replace("\uFEFF", ""));
            int iDate = -1, iSub = -1, iTitle = -1, iUp = -1;
            for (int i = 0; i < head.size(); i++) {
                String h = head.get(i).trim().toLowerCase();
                if (h.equals("date")) iDate = i;
                else if (h.equals("subreddit") || h.equals("sub")) iSub = i;
                else if (h.equals("title") || h.equals("post") || h.equals("text")) iTitle = i;
                else if (h.equals("upvotes") || h.equals("score")) iUp = i;
            }
            if (iTitle < 0) return out;
            LocalDate oldest = LocalDate.now().minusDays(MANUAL_DAYS);
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().isEmpty()) continue;
                List<String> cells = Csv.split(lines.get(i));
                String title = cell(cells, iTitle);
                if (title.isEmpty()) continue;
                String date = cell(cells, iDate);
                if (!date.isEmpty()) {
                    try { if (LocalDate.parse(date).isBefore(oldest)) continue; } catch (Exception ignored) {}
                }
                String sub = cell(cells, iSub).replaceFirst("^/?r/", "");
                int up = 0;
                try { up = (int) Double.parseDouble(cell(cells, iUp).replace(",", "")); } catch (Exception ignored) {}
                out.add(new Post(sub.isEmpty() ? "other" : sub, title, "", up));
            }
        } catch (Exception e) {
            Log.debug("Could not read " + f + ": " + e);
        }
        return out;
    }

    private static String cell(List<String> cells, int i) { return i >= 0 && i < cells.size() ? cells.get(i).trim() : ""; }

    /**
     * Saves pasted posts (one post title per line) to data/reddit.csv with today's date.
     * Lines may start with "r/name:" to say which subreddit they came from. Returns how many were added.
     */
    public static int addManualPosts(String defaultSub, String pasted) throws java.io.IOException {
        Path f = manualFile();
        Files.createDirectories(f.getParent());
        StringBuilder rows = new StringBuilder();
        if (!Files.exists(f) || Files.size(f) == 0) rows.append("date,subreddit,title,upvotes\n");
        String today = LocalDate.now().toString();
        int n = 0;
        for (String line : pasted.split("\\r?\\n")) {
            String title = line.trim();
            if (title.isEmpty()) continue;
            String sub = defaultSub == null || defaultSub.trim().isEmpty() ? "other" : defaultSub.trim().replaceFirst("^/?r/", "");
            Matcher m = Pattern.compile("^/?r/([A-Za-z0-9_]+)\\s*[:\\-]\\s*(.+)$").matcher(title);
            if (m.find()) { sub = m.group(1); title = m.group(2).trim(); }
            rows.append(today).append(',').append(sub).append(",\"").append(title.replace('"', '\'')).append("\",0\n");
            n++;
        }
        if (n > 0) Files.write(f, rows.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return n;
    }

    /** Deletes all posts you added yourself. */
    public static void clearManualPosts() throws java.io.IOException { Files.deleteIfExists(manualFile()); }

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
                if (s.redditQuotes.size() < 3)
                    s.redditQuotes.add("r/" + p.sub + ": \"" + shorten(p.title, 90) + "\"" + (p.upvotes > 0 ? " (" + p.upvotes + " upvotes)" : ""));
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
