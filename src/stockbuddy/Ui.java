package stockbuddy;

import java.util.ArrayList;
import java.util.List;

/** Pretty console output: colours, headings and word-wrapping. */
public final class Ui {
    public static String RESET = "\u001B[0m", BOLD = "\u001B[1m", DIM = "\u001B[2m", GREEN = "\u001B[32m",
            RED = "\u001B[31m", YELLOW = "\u001B[33m", CYAN = "\u001B[36m", MAGENTA = "\u001B[35m";
    public static final int WIDTH = 88;

    private Ui() {}

    public static void noColor() { RESET = BOLD = DIM = GREEN = RED = YELLOW = CYAN = MAGENTA = ""; }

    public static void title(String t) {
        String line = repeat("=", WIDTH);
        System.out.println();
        System.out.println(CYAN + line + RESET);
        System.out.println(CYAN + BOLD + "  " + t + RESET);
        System.out.println(CYAN + line + RESET);
    }

    public static void section(String t, String color) {
        System.out.println();
        System.out.println(color + BOLD + "--- " + t + " " + repeat("-", Math.max(3, WIDTH - t.length() - 5)) + RESET);
    }

    public static void para(String text) { para(text, "  "); }

    public static void para(String text, String indent) {
        for (String l : wrap(text, WIDTH - indent.length())) System.out.println(indent + l);
    }

    public static void bullet(String text, String indent) {
        List<String> lines = wrap(text, WIDTH - indent.length() - 2);
        for (int i = 0; i < lines.size(); i++) System.out.println(indent + (i == 0 ? "- " : "  ") + lines.get(i));
    }

    public static String colorPct(double pct) {
        if (Double.isNaN(pct)) return DIM + "   n/a " + RESET;
        String c = pct > 0.05 ? GREEN : pct < -0.05 ? RED : YELLOW;
        return c + String.format("%+6.2f%%", pct) + RESET;
    }

    public static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() + 1 > width && line.length() > 0) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    public static void disclaimer() {
        System.out.println();
        System.out.println(YELLOW + "  IMPORTANT: This program is a learning and research helper, NOT a financial advisor." + RESET);
        para(YELLOW + "It combines public opinions and simple math - it can be wrong, data can be late, and you can lose money. "
                + "Always do your own research and never invest money you can't afford to lose." + RESET);
    }
}
