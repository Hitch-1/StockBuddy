package stockbuddy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A colour theme ("skin") as plain hex colours. Shared by the desktop app (see Skin)
 * and the Android app, so every skin looks the same everywhere.
 */
public final class Palette {
    public static final List<Palette> ALL = Collections.unmodifiableList(Arrays.asList(
            new Palette("Classic Light", false, "#eef1f5", "#ffffff", "#1d2330", "#5d6676", "#2457c5", "#127a3e", "#b42318", "#9a6700", "#d9dee6", "SansSerif"),
            new Palette("Night Mode", true, "#171b22", "#20262f", "#e7eaf0", "#9aa3b2", "#7aa2ff", "#4cc27e", "#ff7a6b", "#e6b450", "#2f3743", "SansSerif"),
            new Palette("Wall Street Terminal", true, "#070b07", "#0e160e", "#c8f7c5", "#6f9a6c", "#39ff88", "#39ff88", "#ff5f56", "#ffd75f", "#1f3a1f", "Monospaced"),
            new Palette("Ocean Blue", true, "#0c1d2e", "#13304a", "#e6f1fb", "#8fb3d1", "#4fc3f7", "#5ee0a0", "#ff8a80", "#ffd166", "#21486b", "SansSerif"),
            new Palette("Sunset", true, "#26172a", "#35213b", "#fbe9e7", "#c9a6b8", "#ff8a65", "#7ee2a8", "#ff6b81", "#ffd166", "#4d3054", "SansSerif"),
            new Palette("Bull Gold", false, "#f7f1e3", "#fffdf7", "#2b2414", "#6e6248", "#a87400", "#2e7d32", "#c62828", "#a15c00", "#e6dcc3", "Serif"),
            new Palette("Mint Fresh", false, "#e9f5f0", "#ffffff", "#15302a", "#547167", "#0f8b6c", "#0f8b6c", "#c2410c", "#a16207", "#cfe6dc", "SansSerif"),
            new Palette("High Contrast", true, "#000000", "#000000", "#ffffff", "#d0d0d0", "#ffff00", "#00ff66", "#ff4040", "#ffff00", "#ffffff", "SansSerif")));

    private final String name, bg, panel, text, muted, accent, good, bad, warn, line, font;
    private final boolean dark;

    public Palette(String name, boolean dark, String bg, String panel, String text, String muted, String accent,
                   String good, String bad, String warn, String line, String font) {
        this.name = name; this.dark = dark; this.bg = bg; this.panel = panel; this.text = text; this.muted = muted;
        this.accent = accent; this.good = good; this.bad = bad; this.warn = warn; this.line = line; this.font = font;
    }

    public static Palette byName(String n) {
        for (Palette p : ALL) if (p.name.equalsIgnoreCase(n)) return p;
        return ALL.get(1);
    }

    public String name() { return name; }
    public boolean dark() { return dark; }
    public String bg() { return bg; }
    public String panel() { return panel; }
    public String text() { return text; }
    public String muted() { return muted; }
    public String accent() { return accent; }
    public String good() { return good; }
    public String bad() { return bad; }
    public String warn() { return warn; }
    public String line() { return line; }
    public String font() { return font; }

    /** Font list for web pages (desktop font names don't exist in browsers / Android). */
    public String cssFont() {
        // Java's own name first (used by the desktop app), then fonts that phones and browsers have.
        if (font.equals("Monospaced")) return "Monospaced, 'Roboto Mono', 'Courier New', monospace";
        if (font.equals("Serif")) return "Serif, 'Noto Serif', Georgia, serif";
        return "SansSerif, Roboto, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif";
    }

    /** Blend two hex colours: t=0 gives a, t=1 gives b. */
    public String mix(String a, String b, double t) {
        int x = Integer.parseInt(a.substring(1), 16), y = Integer.parseInt(b.substring(1), 16);
        int r = (int) (((x >> 16) & 255) * (1 - t) + ((y >> 16) & 255) * t);
        int g = (int) (((x >> 8) & 255) * (1 - t) + ((y >> 8) & 255) * t);
        int bl = (int) ((x & 255) * (1 - t) + (y & 255) * t);
        return String.format("#%02x%02x%02x", r, g, bl);
    }

    /** As an Android-style ARGB int. */
    public static int argb(String hex) { return 0xFF000000 | Integer.parseInt(hex.substring(1), 16); }

    @Override public String toString() { return name; }
}
