package stockbuddy;

import javax.swing.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.util.List;

/** A colour theme ("skin") for the main window and the desktop widget. */
public record Skin(Palette palette, String name, boolean dark, Color bg, Color panel, Color text, Color muted, Color accent,
                   Color good, Color bad, Color warn, Color line, String font) {

    /** All skins, built from the shared Palette list (the Android app uses the same list). */
    public static final List<Skin> ALL = Palette.ALL.stream().map(Skin::of).toList();

    static Skin of(Palette p) {
        return new Skin(p, p.name(), p.dark(), c(p.bg()), c(p.panel()), c(p.text()), c(p.muted()), c(p.accent()),
                c(p.good()), c(p.bad()), c(p.warn()), c(p.line()), p.font());
    }

    public static Skin byName(String n) {
        for (Skin s : ALL) if (s.name.equalsIgnoreCase(n)) return s;
        return ALL.get(1);
    }

    static Color c(String hex) { return Color.decode(hex); }

    public static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    public Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Selection / hover colour. */
    public Color soft() { return mix(panel, accent, dark ? 0.28 : 0.16); }

    public Font font(int style, float size) { return new Font(font, style, Math.round(size)); }

    @Override public String toString() { return name; }

    /** Applies this skin to every standard Swing control (buttons, tables, scroll bars...). */
    public void install() {
        UIManager.put("control", bg);
        UIManager.put("info", panel);
        UIManager.put("nimbusBase", mix(accent, dark ? Color.BLACK : Color.DARK_GRAY, 0.45));
        UIManager.put("nimbusBlueGrey", mix(panel, muted, 0.35));
        UIManager.put("nimbusFocus", accent);
        UIManager.put("nimbusLightBackground", panel);
        UIManager.put("nimbusSelectionBackground", accent);
        UIManager.put("nimbusSelectedText", dark ? Color.BLACK : Color.WHITE);
        UIManager.put("nimbusSelection", accent);
        UIManager.put("nimbusDisabledText", muted);
        UIManager.put("nimbusInfoBlue", accent);
        UIManager.put("nimbusAlertYellow", warn);
        UIManager.put("nimbusGreen", good);
        UIManager.put("nimbusRed", bad);
        UIManager.put("nimbusOrange", warn);
        UIManager.put("text", text);
        UIManager.put("menuText", text);
        UIManager.put("textForeground", text);
        UIManager.put("textBackground", panel);
        UIManager.put("Table.alternateRowColor", mix(panel, bg, 0.6));
        UIManager.put("Table.gridColor", line);
        UIManager.put("defaultFont", font(Font.PLAIN, 14));
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Exception ignored) { /* falls back to the default look */ }
    }
}
