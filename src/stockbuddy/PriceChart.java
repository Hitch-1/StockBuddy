package stockbuddy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The price chart on the "Look Up a Stock" page: line, candles or area, with optional indicator
 * lines (moving averages, Bollinger Bands) and a lower panel (volume, RSI or MACD).
 * Move the mouse over it to read each day's prices.
 */
public class PriceChart extends JPanel {
    private Stock stock;
    private Skin skin;
    private ChartOptions opt = ChartOptions.load();
    private int hover = -1;                 // index of the day under the mouse
    private int left, plotW, from, count;   // layout from the last paint (for the mouse)

    public PriceChart(Skin skin) {
        this.skin = skin;
        setPreferredSize(new Dimension(600, 330));
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (stock == null || count < 2 || plotW <= 0) return;
                int i = (int) Math.round((e.getX() - left) * (count - 1) / (double) plotW);
                int h = (i < 0 || i >= count) ? -1 : from + i;
                if (h != hover) { hover = h; repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void show(Stock s, Skin k) { stock = s; skin = k; hover = -1; repaint(); }

    public void setOptions(ChartOptions o) { opt = o; repaint(); }

    public ChartOptions options() { return opt; }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(skin.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(skin.line());
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        if (stock == null || stock.closes.size() < 2) {
            g.setColor(skin.muted());
            g.setFont(skin.font(Font.PLAIN, 14));
            g.drawString("Look up a stock to see its price chart here.", 16, getHeight() / 2);
            g.dispose();
            return;
        }
        List<Double> c = stock.closes;
        int n = c.size();
        boolean candles = opt.type.equals("candles") && stock.hasCandles();
        from = Math.max(0, n - opt.range);
        count = n - from;

        // indicator lines over the whole history (so they're ready at the left edge)
        List<Object[]> lines = new ArrayList<>();   // {values, colour, label}
        if (opt.sma20) lines.add(new Object[]{ChartOptions.sma(c, 20), skin.mix(skin.accent(), skin.text(), 0.45), "SMA 20"});
        if (opt.sma50) lines.add(new Object[]{ChartOptions.sma(c, 50), skin.accent(), "SMA 50"});
        if (opt.sma200) lines.add(new Object[]{ChartOptions.sma(c, 200), skin.warn(), "SMA 200"});
        if (opt.ema20) lines.add(new Object[]{ChartOptions.ema(c, 20), new Color(0xb07cff), "EMA 20"});
        double[][] bb = opt.bollinger ? ChartOptions.bollinger(c) : null;

        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = from; i < n; i++) {
            lo = Math.min(lo, candles ? stock.lows.get(i) : c.get(i));
            hi = Math.max(hi, candles ? stock.highs.get(i) : c.get(i));
            for (Object[] l : lines) { double v = ((double[]) l[0])[i]; if (!Double.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
            if (bb != null && !Double.isNaN(bb[0][i])) { lo = Math.min(lo, bb[1][i]); hi = Math.max(hi, bb[0][i]); }
        }
        double pad = (hi - lo) * 0.06 + 1e-9; lo -= pad; hi += pad;

        boolean lowerPane = !opt.lower.equals("none");
        left = 14;
        int right = 74, top = 40, bottom = 24;
        plotW = getWidth() - left - right;
        int total = getHeight() - top - bottom;
        int lowerH = lowerPane ? (int) (total * 0.26) : 0;
        int mainH = total - lowerH - (lowerPane ? 12 : 0);

        // grid + price labels
        g.setFont(skin.font(Font.PLAIN, 11));
        for (int i = 0; i <= 4; i++) {
            int y = top + mainH * i / 4;
            g.setColor(skin.mix(skin.panel(), skin.line(), 0.7));
            g.drawLine(left, y, left + plotW, y);
            g.setColor(skin.muted());
            g.drawString(String.format("%,.2f", hi - (hi - lo) * i / 4), left + plotW + 6, y + 4);
        }
        g.drawString(ChartOptions.RANGE_NAMES[rangeIndex()] + " ago", left, getHeight() - 7);
        g.drawString("today", left + plotW - 30, getHeight() - 7);

        // Bollinger band (shaded)
        if (bb != null) {
            Path2D band = new Path2D.Double();
            boolean started = false;
            for (int i = from; i < n; i++) if (!Double.isNaN(bb[0][i])) {
                double x = x(i), y = y(bb[0][i], lo, hi, top, mainH);
                if (!started) { band.moveTo(x, y); started = true; } else band.lineTo(x, y);
            }
            for (int i = n - 1; i >= from; i--) if (!Double.isNaN(bb[1][i])) band.lineTo(x(i), y(bb[1][i], lo, hi, top, mainH));
            if (started) {
                band.closePath();
                g.setColor(alpha(skin.muted(), 40));
                g.fill(band);
                g.setColor(alpha(skin.muted(), 140));
                g.setStroke(new BasicStroke(1f));
                g.draw(band);
            }
        }

        boolean up = c.get(n - 1) >= c.get(from);
        Color priceCol = up ? skin.good() : skin.bad();
        if (candles) {
            double cw = Math.max(1, plotW / (double) count * 0.65);
            for (int i = from; i < n; i++) {
                double o = stock.opens.get(i), cl = c.get(i);
                Color col = cl >= o ? skin.good() : skin.bad();
                g.setColor(col);
                double x = x(i);
                g.setStroke(new BasicStroke(1f));
                g.draw(new java.awt.geom.Line2D.Double(x, y(stock.highs.get(i), lo, hi, top, mainH), x, y(stock.lows.get(i), lo, hi, top, mainH)));
                double y1 = y(Math.max(o, cl), lo, hi, top, mainH), y2 = y(Math.min(o, cl), lo, hi, top, mainH);
                g.fill(new java.awt.geom.Rectangle2D.Double(x - cw / 2, y1, cw, Math.max(1, y2 - y1)));
            }
        } else {
            if (opt.type.equals("area")) {
                Path2D area = new Path2D.Double();
                area.moveTo(x(from), top + mainH);
                for (int i = from; i < n; i++) area.lineTo(x(i), y(c.get(i), lo, hi, top, mainH));
                area.lineTo(x(n - 1), top + mainH);
                area.closePath();
                g.setPaint(new GradientPaint(0, top, alpha(priceCol, 90), 0, top + mainH, alpha(priceCol, 5)));
                g.fill(area);
            }
            double[] p = new double[n];
            for (int i = 0; i < n; i++) p[i] = c.get(i);
            line(g, p, lo, hi, top, mainH, priceCol, 2.2f);
        }
        for (Object[] l : lines) line(g, (double[]) l[0], lo, hi, top, mainH, (Color) l[1], 1.5f);

        if (lowerPane) drawLower(g, top + mainH + 12, lowerH);

        // header + legend
        g.setFont(skin.font(Font.BOLD, 14));
        g.setColor(skin.text());
        String head = stock.ticker + "  " + Advisor.money(stock);
        g.drawString(head, left + 2, 24);
        int lx = left + 16 + g.getFontMetrics().stringWidth(head);
        g.setFont(skin.font(Font.PLAIN, 12));
        lx = legend(g, lx, priceCol, candles ? "Candles" : "Price");
        for (Object[] l : lines) lx = legend(g, lx, (Color) l[1], (String) l[2]);
        if (bb != null) legend(g, lx, skin.muted(), "Bollinger Bands");

        // hover readout
        if (hover >= from && hover < n) {
            int hx = (int) x(hover);
            g.setColor(alpha(skin.text(), 90));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
            g.drawLine(hx, top, hx, getHeight() - bottom);
            int ago = n - 1 - hover;
            String txt = (ago == 0 ? "Today" : ago + (ago == 1 ? " day ago" : " days ago"))
                    + (stock.hasCandles() ? String.format("   Open %,.2f   High %,.2f   Low %,.2f   Close %,.2f",
                    stock.opens.get(hover), stock.highs.get(hover), stock.lows.get(hover), c.get(hover))
                    : String.format("   Close %,.2f", c.get(hover)))
                    + (hover < stock.volumes.size() && stock.volumes.get(hover) > 0 ? String.format("   Volume %,.0f", stock.volumes.get(hover)) : "");
            g.setFont(skin.font(Font.PLAIN, 12));
            int tw = g.getFontMetrics().stringWidth(txt) + 16;
            int bx = Math.max(left, Math.min(hx - tw / 2, left + plotW - tw));
            g.setColor(skin.mix(skin.panel(), skin.bg(), 0.5));
            g.fillRoundRect(bx, top + 2, tw, 22, 10, 10);
            g.setColor(skin.line());
            g.drawRoundRect(bx, top + 2, tw, 22, 10, 10);
            g.setColor(skin.text());
            g.drawString(txt, bx + 8, top + 17);
        }
        g.dispose();
    }

    private void drawLower(Graphics2D g, int top, int h) {
        List<Double> c = stock.closes;
        int n = c.size();
        g.setColor(skin.mix(skin.panel(), skin.line(), 0.7));
        g.drawLine(left, top, left + plotW, top);
        g.setFont(skin.font(Font.PLAIN, 11));
        g.setColor(skin.muted());
        if (opt.lower.equals("volume")) {
            double max = 0;
            for (int i = from; i < n && i < stock.volumes.size(); i++) max = Math.max(max, stock.volumes.get(i));
            if (max <= 0) { g.drawString("No volume data", left + 4, top + 14); return; }
            double bw = Math.max(1, plotW / (double) count * 0.7);
            for (int i = from; i < n && i < stock.volumes.size(); i++) {
                boolean upDay = i == 0 || c.get(i) >= c.get(i - 1);
                g.setColor(alpha(upDay ? skin.good() : skin.bad(), 150));
                double bh = h * stock.volumes.get(i) / max;
                g.fill(new java.awt.geom.Rectangle2D.Double(x(i) - bw / 2, top + h - bh, bw, bh));
            }
            g.setColor(skin.muted());
            g.drawString("Volume", left + plotW + 6, top + 12);
        } else if (opt.lower.equals("rsi")) {
            double[] r = ChartOptions.rsi(c, 14);
            for (int lvl : new int[]{30, 70}) {
                int y = (int) (top + h * (100 - lvl) / 100.0);
                g.setColor(alpha(skin.muted(), 120));
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
                g.drawLine(left, y, left + plotW, y);
                g.setColor(skin.muted());
                g.drawString(String.valueOf(lvl), left + plotW + 6, y + 4);
            }
            line(g, r, 0, 100, top, h, new Color(0xb07cff), 1.6f);
            g.setColor(skin.muted());
            g.drawString("RSI " + (Double.isNaN(r[n - 1]) ? "" : String.format("%.0f", r[n - 1])), left + plotW + 6, top + 12);
        } else if (opt.lower.equals("macd")) {
            double[][] m = ChartOptions.macd(c);
            double max = 1e-9;
            for (int i = from; i < n; i++) for (double[] s : m) if (!Double.isNaN(s[i])) max = Math.max(max, Math.abs(s[i]));
            double mid = top + h / 2.0, bw = Math.max(1, plotW / (double) count * 0.7);
            for (int i = from; i < n; i++) {
                if (Double.isNaN(m[2][i])) continue;
                double bh = (h / 2.0) * m[2][i] / max;
                g.setColor(alpha(m[2][i] >= 0 ? skin.good() : skin.bad(), 140));
                g.fill(new java.awt.geom.Rectangle2D.Double(x(i) - bw / 2, bh >= 0 ? mid - bh : mid, bw, Math.abs(bh)));
            }
            line(g, m[0], -max, max, top, h, skin.accent(), 1.5f);
            line(g, m[1], -max, max, top, h, skin.warn(), 1.3f);
            g.setColor(skin.muted());
            g.drawString("MACD", left + plotW + 6, top + 12);
        }
    }

    private int rangeIndex() {
        for (int i = 0; i < ChartOptions.RANGES.length; i++) if (ChartOptions.RANGES[i] == opt.range) return i;
        return ChartOptions.RANGES.length - 1;
    }

    private double x(int i) { return left + (count <= 1 ? 0 : (double) plotW * (i - from) / (count - 1)); }

    private static double y(double v, double lo, double hi, int top, int h) { return top + h * (hi - v) / (hi - lo); }

    private void line(Graphics2D g, double[] v, double lo, double hi, int top, int h, Color col, float width) {
        Path2D p = new Path2D.Double();
        boolean started = false;
        for (int i = from; i < v.length; i++) {
            if (Double.isNaN(v[i])) continue;
            double px = x(i), py = y(v[i], lo, hi, top, h);
            if (!started) { p.moveTo(px, py); started = true; } else p.lineTo(px, py);
        }
        g.setColor(col);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    private int legend(Graphics2D g, int x, Color c, String label) {
        g.setColor(c);
        g.fillRoundRect(x, 17, 14, 4, 2, 2);
        g.setColor(skin.muted());
        g.drawString(label, x + 19, 23);
        return x + 32 + g.getFontMetrics().stringWidth(label);
    }

    private static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
}
