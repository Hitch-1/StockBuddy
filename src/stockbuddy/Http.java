package stockbuddy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Downloads web pages politely (one place for timeouts, caching and a browser-like user agent).
 * Uses HttpURLConnection so the same code works on Windows, Linux, Mac and Android.
 */
public final class Http {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36 StockBuddy/1.0";

    private Http() {}

    /** Always downloads again (no cache) - for prices that change every minute. */
    public static String getFresh(String url) { return request("GET", url, null, null, null); }

    /** Forget downloaded pages (used before a fresh refresh). */
    public static void clearCache() { CACHE.clear(); }

    /** Returns the page text, or null if the site could not be reached. Never throws. */
    public static String get(String url) {
        String cached = CACHE.get(url);
        if (cached != null) return cached;
        String body = request("GET", url, null, null, null);
        if (body != null) CACHE.put(url, body);
        return body;
    }

    /**
     * Low-level request. Returns the body for 2xx answers, otherwise null.
     * authHeader / formBody are optional.
     */
    public static String request(String method, String url, String authHeader, String formBody, String userAgent) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", userAgent != null ? userAgent : UA);
            c.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            c.setRequestProperty("Accept-Encoding", "gzip");
            if (authHeader != null) c.setRequestProperty("Authorization", authHeader);
            if (formBody != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream o = c.getOutputStream()) { o.write(formBody.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.debug("HTTP " + code + " for " + url.replaceAll("token=[^&]*", "token=***"));
                return null;
            }
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            try (InputStream is = in) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = is.read(b)) > 0) buf.write(b, 0, n);
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Log.debug("Could not reach " + url.replaceAll("token=[^&]*", "token=***") + " (" + e.getClass().getSimpleName() + ")");
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Turns HTML into plain text so we can look for "label value" pairs. */
    public static String htmlToText(String html) {
        if (html == null) return "";
        String t = html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"");
        return t.replaceAll("\\s+", " ");
    }
}
