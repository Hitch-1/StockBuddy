package stockbuddy;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.List;

/** A simple one-year price chart with the 50-day and 200-day average lines. */
public class PriceChart extends JPanel {
    private Stock stock;
    private Skin skin;

    public PriceChart(Skin skin) {
        this.skin = skin;
        setPreferredSize(new Dimension(600, 240));
        setOpaque(true);
    }

    public void show(Stock s, Skin k) { stock = s; skin = k; repaint(); }

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
        double[] s50 = movingAvg(c, 50), s200 = movingAvg(c, 200);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : c) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double pad = (hi - lo) * 0.08 + 1e-9; lo -= pad; hi += pad;

        int left = 16, right = 70, top = 34, bottom = 26;
        int w = getWidth() - left - right, h = getHeight() - top - bottom;

        // grid + price labels
        g.setFont(skin.font(Font.PLAIN, 11));
        for (int i = 0; i <= 4; i++) {
            int y = top + h * i / 4;
            g.setColor(skin.mix(skin.panel(), skin.line(), 0.7));
            g.drawLine(left, y, left + w, y);
            g.setColor(skin.muted());
            g.drawString(String.format("$%.2f", hi - (hi - lo) * i / 4), left + w + 6, y + 4);
        }
        g.drawString("1 year ago", left, getHeight() - 8);
        g.drawString("today", left + w - 30, getHeight() - 8);

        boolean up = c.get(n - 1) >= c.get(0);
        Color lineColor = up ? skin.good() : skin.bad();
        // area under the price line
        Path2D area = new Path2D.Double();
        area.moveTo(x(0, n, left, w), top + h);
        for (int i = 0; i < n; i++) area.lineTo(x(i, n, left, w), y(c.get(i), lo, hi, top, h));
        area.lineTo(x(n - 1, n, left, w), top + h);
        area.closePath();
        g.setColor(new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 40));
        g.fill(area);

        drawLine(g, s200, lo, hi, left, w, top, h, skin.warn(), 1.4f);
        drawLine(g, s50, lo, hi, left, w, top, h, skin.accent(), 1.4f);
        double[] price = c.stream().mapToDouble(d -> d).toArray();
        drawLine(g, price, lo, hi, left, w, top, h, lineColor, 2.2f);

        // legend
        g.setFont(skin.font(Font.BOLD, 13));
        g.setColor(skin.text());
        g.drawString(stock.ticker + "  " + Advisor.money(stock), left, 22);
        int lx = left + 190;
        legend(g, lx, lineColor, "Price");
        legend(g, lx + 80, skin.accent(), "50-day average");
        legend(g, lx + 220, skin.warn(), "200-day average");
        g.dispose();
    }

    private void legend(Graphics2D g, int x, Color c, String label) {
        g.setColor(c);
        g.fillRoundRect(x, 13, 14, 4, 2, 2);
        g.setColor(skin.muted());
        g.setFont(skin.font(Font.PLAIN, 12));
        g.drawString(label, x + 19, 19);
    }

    private static void drawLine(Graphics2D g, double[] v, double lo, double hi, int left, int w, int top, int h, Color col, float width) {
        Path2D p = new Path2D.Double();
        boolean started = false;
        for (int i = 0; i < v.length; i++) {
            if (Double.isNaN(v[i])) continue;
            double px = x(i, v.length, left, w), py = y(v[i], lo, hi, top, h);
            if (!started) { p.moveTo(px, py); started = true; } else p.lineTo(px, py);
        }
        g.setColor(col);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    private static double x(int i, int n, int left, int w) { return left + (double) w * i / (n - 1); }
    private static double y(double v, double lo, double hi, int top, int h) { return top + h * (hi - v) / (hi - lo); }

    private static double[] movingAvg(List<Double> c, int p) {
        double[] out = new double[c.size()];
        double sum = 0;
        for (int i = 0; i < c.size(); i++) {
            sum += c.get(i);
            if (i >= p) sum -= c.get(i - p);
            out[i] = i >= p - 1 ? sum / p : Double.NaN;
        }
        return out;
    }
}
