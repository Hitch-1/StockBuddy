package stockbuddy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Languages: English, French, Spanish, Japanese, Italian and Arabic.
 * Translations live in stockbuddy/i18n/<code>.txt (shipped inside the app):
 *
 *     = English text
 *     > Translated text
 *
 * Anything not translated yet simply stays in English. Use tr("...") for text and trf("...%s...", args) for text with numbers.
 */
public final class I18n {
    public static final String[][] LANGS = {
            {"en", "English"}, {"fr", "Français"}, {"es", "Español"}, {"ja", "日本語"}, {"it", "Italiano"}, {"ar", "العربية"}};

    private static volatile String lang;
    private static volatile Map<String, String> map;

    private I18n() {}

    public static String lang() {
        if (lang == null) {
            String l = Settings.get("lang", "en");
            lang = name(l) == null ? "en" : l;
        }
        return lang;
    }

    public static synchronized void setLang(String code) {
        Settings.set("lang", code);
        lang = code;
        map = null;
    }

    /** Display name of a language code, or null if unknown. */
    public static String name(String code) {
        for (String[] l : LANGS) if (l[0].equals(code)) return l[1];
        return null;
    }

    public static boolean rtl() { return "ar".equals(lang()); }

    public static Locale locale() {
        switch (lang()) {
            case "fr": return Locale.FRANCE;
            case "es": return new Locale("es", "ES");
            case "ja": return Locale.JAPAN;
            case "it": return Locale.ITALY;
            case "ar": return new Locale("ar");
            default: return Locale.US;
        }
    }

    /** The translation of an English text (or the English text itself). */
    public static String tr(String en) {
        if (en == null || lang().equals("en")) return en;
        Map<String, String> m = load();
        String v = m.get(en);
        return v == null ? en : v;
    }

    /** Translate a format pattern, then fill in the values (numbers always use a dot, like prices). */
    public static String trf(String enFormat, Object... args) {
        return String.format(Locale.US, tr(enFormat), args);
    }

    /**
     * For messages built from pieces (like "OK - data for 12 stocks"): translates every known piece.
     * Pieces are lines in the translation file whose English starts with "~".
     */
    public static String trParts(String en) {
        if (en == null || lang().equals("en")) return en;
        Map<String, String> m = load();
        String whole = m.get(en);
        if (whole != null) return whole;
        String out = en;
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String key : m.keySet()) if (key.startsWith("~")) keys.add(key);
        keys.sort((x, y) -> y.length() - x.length());   // longest pieces first
        for (String key : keys) if (out.contains(key.substring(1))) out = out.replace(key.substring(1), m.get(key).substring(m.get(key).startsWith("~") ? 1 : 0));
        return out;
    }

    private static synchronized Map<String, String> load() {
        if (map != null) return map;
        Map<String, String> m = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream("/stockbuddy/i18n/" + lang() + ".txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line, en = null;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("= ")) en = unescape(line.substring(2));
                    else if (line.startsWith("> ") && en != null) { m.put(en, unescape(line.substring(2))); en = null; }
                }
            }
        } catch (Exception e) {
            Log.debug("Could not load translations for " + lang() + ": " + e);
        }
        map = m;
        return m;
    }

    private static String unescape(String s) { return s.replace("\\n", "\n"); }
}
