package stockbuddy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A small panel that stays on your desktop: world markets at a glance, today's top ideas and the 0DTE lean.
 * Drag it anywhere. Right-click for options (skin, size, transparency, always on top).
 * Double-click it, or click a ticker, to open the full StockBuddy window.
 */
public class DesktopWidget extends JFrame {
    private static final Map<String, String> SHORT = Map.of("^GSPC", "S&P 500", "^IXIC", "Nasdaq", "^DJI", "Dow Jones",
            "^FTSE", "FTSE 100 (UK)", "^GDAXI", "DAX (Germany)", "^N225", "Nikkei (Japan)", "^HSI", "Hang Seng (HK)",
            "^BSESN", "Sensex (India)", "000001.SS", "Shanghai", "^STOXX50E", "Euro Stoxx 50");
    private static final List<String> SHOWN = List.of("^GSPC", "^IXIC", "^DJI", "^FTSE", "^GDAXI", "^N225", "^HSI", "^BSESN");
    private static final int W = 320;

    private final Panel panel = new Panel();
    private final Runnable onData = () -> SwingUtilities.invokeLater(this::relayout);
    private final Runnable onSkin = () -> SwingUtilities.invokeLater(this::relayout);
    private final List<Object[]> hotspots = new ArrayList<>(); // {Rectangle, ticker}
    private String updatedAt = "";
    private Point dragFrom;
    private int neededHeight;
    private final boolean rounded;

    public DesktopWidget() {
        super("StockBuddy widget");
        setIconImages(App.icons());
        setUndecorated(true);
        setType(Type.UTILITY); // keeps it out of the taskbar
        setAlwaysOnTop(Settings.getBool("widget.onTop", true));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        rounded = gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        if (rounded) setBackground(new Color(0, 0, 0, 0));
        setContentPane(panel);
        applyOpacity();

        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menu(e); else dragFrom = e.getPoint(); }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) { menu(e); return; }
                dragFrom = null;
                Settings.set("widget.x", getX()); Settings.set("widget.y", getY());
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragFrom == null) return;
                Point p = e.getLocationOnScreen();
                setLocation(p.x - dragFrom.x, p.y - dragFrom.y);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                if (closeBox().contains(e.getPoint())) { close(); return; }
                if (menuBox().contains(e.getPoint())) { menu(e); return; }
                for (Object[] h : hotspots) if (((Rectangle) h[0]).contains(e.getPoint())) { App.showMain((String) h[1]); return; }
                if (e.getClickCount() == 2) App.showMain(null);
            }
            @Override public void mouseMoved(MouseEvent e) {
                boolean hand = closeBox().contains(e.getPoint()) || menuBox().contains(e.getPoint())
                        || hotspots.stream().anyMatch(h -> ((Rectangle) h[0]).contains(e.getPoint()));
                setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.MOVE_CURSOR));
            }
        };
        panel.addMouseListener(m);
        panel.addMouseMotionListener(m);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { App.removeListeners(onData, onSkin); }
        });
        App.onData(onData);
        App.onSkin(onSkin);

        relayout();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = Settings.getInt("widget.x", screen.x + screen.width - getWidth() - 24);
        int y = Settings.getInt("widget.y", screen.y + 24);
        // keep it on screen if the screen size changed
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - 80));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - 80));
        setLocation(x, y);
    }

    private Skin skin() {
        String s = Settings.get("widget.skin", "Same as app");
        return s.equals("Same as app") ? App.skin : Skin.byName(s);
    }

    private boolean full() { return !"compact".equals(Settings.get("widget.size", "full")); }

    private void relayout() {
        if (App.plan != null && !App.loading) updatedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int h = neededHeight > 0 ? neededHeight : (full() ? 520 : 300);
        setSize(W, h);
        if (rounded) setShape(null);
        else {
            try { setShape(new RoundRectangle2D.Double(0, 0, W, h, 22, 22)); } catch (Exception ignored) {}
        }
        panel.repaint();
    }

    private void applyOpacity() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            try { setOpacity(Settings.getInt("widget.opacity", 95) / 100f); } catch (Exception ignored) {}
        }
    }

    private Rectangle closeBox() { return new Rectangle(W - 34, 10, 24, 24); }
    private Rectangle menuBox() { return new Rectangle(W - 62, 10, 24, 24); }

    private void close() { dispose(); App.widgetClosed(); }

    // ------------------------------------------------------------------ right-click menu
    private void menu(MouseEvent e) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open StockBuddy window");
        open.addActionListener(a -> App.showMain(null));
        JMenuItem refresh = new JMenuItem(App.loading ? "Refreshing..." : "Refresh now");
        refresh.setEnabled(!App.loading);
        refresh.addActionListener(a -> App.refresh());
        m.add(open); m.add(refresh); m.addSeparator();

        JMenu skins = new JMenu("Widget skin");
        ButtonGroup g = new ButtonGroup();
        List<String> names = new ArrayList<>(List.of("Same as app"));
        Skin.ALL.forEach(s -> names.add(s.name()));
        String cur = Settings.get("widget.skin", "Same as app");
        for (String n : names) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(n, n.equals(cur));
            it.addActionListener(a -> { Settings.set("widget.skin", n); relayout(); });
            g.add(it); skins.add(it);
        }
        m.add(skins);

        JMenu size = new JMenu("Size");
        ButtonGroup sg = new ButtonGroup();
        for (String[] s : new String[][]{{"full", "Full (markets + ideas)"}, {"compact", "Compact (markets only)"}}) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(s[1], Settings.get("widget.size", "full").equals(s[0]));
            it.addActionListener(a -> { Settings.set("widget.size", s[0]); relayout(); });
            sg.add(it); size.add(it);
        }
        m.add(size);

        JMenu op = new JMenu("See-through");
        ButtonGroup og = new ButtonGroup();
        int curOp = Settings.getInt("widget.opacity", 95);
        for (int v : new int[]{100, 95, 85, 75, 60}) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(v == 100 ? "Solid" : (100 - v) + "% see-through", v == curOp);
            it.addActionListener(a -> { Settings.set("widget.opacity", v); applyOpacity(); });
            og.add(it); op.add(it);
        }
        m.add(op);

        JCheckBoxMenuItem top = new JCheckBoxMenuItem("Always on top of other windows", isAlwaysOnTop());
        top.addActionListener(a -> { setAlwaysOnTop(top.isSelected()); Settings.set("widget.onTop", top.isSelected()); });
        m.add(top);
        JCheckBoxMenuItem auto = new JCheckBoxMenuItem("Show widget when my computer starts", Autostart.isEnabled());
        auto.addActionListener(a -> {
            String err = Autostart.set(auto.isSelected());
            if (err != null) JOptionPane.showMessageDialog(this, err);
        });
        m.add(auto);
        m.addSeparator();
        JMenuItem close = new JMenuItem("Close widget");
        close.addActionListener(a -> close());
        m.add(close);
        m.show(panel, e.getX(), e.getY());
    }

    // ------------------------------------------------------------------ drawing
    private class Panel extends JPanel {
        Panel() { setOpaque(false); }

        @Override protected void paintComponent(Graphics g0) {
            Skin k = skin();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            hotspots.clear();

            // background card with a soft accent glow at the top
            RoundRectangle2D card = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 22, 22);
            g.setPaint(new GradientPaint(0, 0, k.mix(k.panel(), k.accent(), k.dark() ? 0.18 : 0.10), 0, 120, k.panel()));
            g.fill(card);
            g.setColor(k.mix(k.line(), k.accent(), 0.35));
            g.draw(card);

            // title bar
            g.drawImage(App.icons().get(1), 14, 12, 22, 22, null);
            g.setColor(k.text());
            g.setFont(k.font(Font.BOLD, 16));
            g.drawString("StockBuddy", 44, 29);
            g.setFont(k.font(Font.PLAIN, 11));
            g.setColor(k.muted());
            String sub = App.loading ? "updating..." : updatedAt.isEmpty() ? "" : "updated " + updatedAt;
            if (App.demo) sub = "PRACTICE DATA" + (sub.isEmpty() ? "" : " - " + sub);
            g.drawString(sub, 44, 43);
            Rectangle mb = menuBox(), cb = closeBox();
            g.setColor(k.muted());
            g.setFont(k.font(Font.BOLD, 15));
            g.drawString("≡", mb.x + 7, mb.y + 17);   // menu icon
            g.drawString("×", cb.x + 8, cb.y + 17);   // close icon

            int y = 58;
            Analyzer a = App.analyzer;
            if (App.plan == null) {
                g.setFont(k.font(Font.PLAIN, 13));
                g.setColor(k.muted());
                g.drawString("Getting today's data...", 16, y + 20);
                g.dispose();
                return;
            }

            // mood
            g.setFont(k.font(Font.PLAIN, 12));
            g.setColor(k.accent());
            for (String line : Ui.wrap(WorldMarkets.mood(a.markets).replace("Around the world it's ", "World: "), 46)) {
                g.drawString(line, 16, y + 4); y += 16;
            }
            y += 6;

            // markets
            header(g, k, "WORLD MARKETS", y); y += 18;
            int rows = full() ? SHOWN.size() : 8;
            for (String sym : SHOWN.subList(0, rows)) {
                WorldMarkets.Market m = a.markets.stream().filter(x -> x.symbol.equals(sym)).findFirst().orElse(null);
                if (m == null) continue;
                g.setColor(m.isOpenNow() ? k.good() : k.mix(k.panel(), k.muted(), 0.6));
                g.fillOval(18, y - 8, 7, 7);
                g.setColor(k.text());
                g.setFont(k.font(Font.PLAIN, 13));
                g.drawString(SHORT.getOrDefault(sym, m.name), 32, y);
                pct(g, k, m.changePct, w - 18, y);
                y += 20;
            }
            if (!full()) { fit(y + 22); footer(g, k, h); g.dispose(); return; }

            y += 6;
            header(g, k, "TOP BUYS TODAY", y); y += 8;
            y = chips(g, k, App.plan.buys, k.good(), y, "none today - waiting is fine");
            y += 8;
            header(g, k, "CAREFUL WITH (sell signals)", y); y += 8;
            y = chips(g, k, App.plan.sells, k.bad(), y, "no strong warnings");
            y += 8;
            header(g, k, "0DTE (OPTIONS EXPIRING TODAY)", y); y += 18;
            g.setFont(k.font(Font.PLAIN, 12));
            for (Advisor.Idea i : App.plan.zeroDte) {
                String t = i.headline.replace(" (tracks the S&P 500)", "").replace(" (tracks the Nasdaq 100)", "")
                        .replace(" expiring TODAY", "").replace(" - no clear direction today", "");
                g.setColor(t.contains("CALL") ? k.good() : t.contains("PUT") ? k.bad() : k.muted());
                g.drawString(t.length() > 46 ? t.substring(0, 45) + "..." : t, 16, y);
                y += 17;
            }
            fit(y + 26);
            footer(g, k, h);
            g.dispose();
        }

        /** Grows or shrinks the widget so everything fits. */
        private void fit(int needed) {
            if (needed != neededHeight) {
                neededHeight = needed;
                if (Math.abs(getHeight() - needed) > 2) SwingUtilities.invokeLater(DesktopWidget.this::relayout);
            }
        }

        private void header(Graphics2D g, Skin k, String t, int y) {
            g.setFont(k.font(Font.BOLD, 11));
            g.setColor(k.muted());
            g.drawString(t, 16, y);
            g.setColor(k.line());
            int tw = g.getFontMetrics().stringWidth(t);
            g.drawLine(16 + tw + 8, y - 4, W - 18, y - 4);
        }

        private void pct(Graphics2D g, Skin k, double v, int right, int y) {
            String s = Double.isNaN(v) ? "n/a" : String.format("%+.2f%%", v);
            g.setFont(k.font(Font.BOLD, 13));
            g.setColor(Double.isNaN(v) ? k.muted() : v > 0.05 ? k.good() : v < -0.05 ? k.bad() : k.warn());
            g.drawString(s, right - g.getFontMetrics().stringWidth(s), y);
        }

        /** Ticker "pills" you can click to open the full explanation. */
        private int chips(Graphics2D g, Skin k, List<Advisor.Idea> ideas, Color c, int y, String empty) {
            g.setFont(k.font(Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            int x = 16, rowY = y + 6;
            if (ideas.isEmpty()) {
                g.setFont(k.font(Font.PLAIN, 12));
                g.setColor(k.muted());
                g.drawString(empty, 16, rowY + 15);
                return rowY + 26;
            }
            for (Advisor.Idea i : ideas) {
                String t = i.stock.ticker;
                String label = t + "  " + String.format("%+.1f", i.stock.changePct) + "%";
                int cw = fm.stringWidth(label) + 20;
                if (x + cw > W - 16) { x = 16; rowY += 30; }
                g.setColor(k.mix(k.panel(), c, k.dark() ? 0.22 : 0.14));
                g.fillRoundRect(x, rowY, cw, 24, 24, 24);
                g.setColor(c);
                g.drawRoundRect(x, rowY, cw, 24, 24, 24);
                g.setColor(k.text());
                g.drawString(label, x + 10, rowY + 17);
                hotspots.add(new Object[]{new Rectangle(x, rowY, cw, 24), t});
                x += cw + 6;
            }
            return rowY + 34;
        }

        private void footer(Graphics2D g, Skin k, int h) {
            g.setFont(k.font(Font.PLAIN, 10));
            g.setColor(k.muted());
            g.drawString("Not financial advice  ·  right-click for options", 16, h - 12);
        }
    }
}
