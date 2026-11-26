package stockbuddy;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The desktop app. It holds the current data and skin, and keeps the main window
 * and the desktop widget up to date.
 */
public final class App {
    public static Skin skin;
    public static Analyzer analyzer = new Analyzer(false);
    public static Advisor.Plan plan;
    public static boolean demo;
    public static volatile boolean loading;
    public static String status = I18n.tr("Starting...");

    private static final List<Runnable> dataListeners = new ArrayList<>();
    private static final List<Runnable> marketListeners = new ArrayList<>();
    private static Timer marketTimer, alertTimer;
    private static volatile boolean marketsLoading;
    private static final List<Runnable> skinListeners = new ArrayList<>();
    private static final List<Consumer<String>> statusListeners = new ArrayList<>();
    private static MainWindow mainWindow;
    private static DesktopWidget widget;
    private static Timer autoRefresh;

    private App() {}

    public static void launch(String[] args) {
        List<String> a = List.of(args);
        Log.DEBUG = a.contains("--debug");
        demo = a.contains("--demo") || Settings.getBool("practiceMode", false);
        SwingUtilities.invokeLater(() -> {
            skin = Skin.byName(Settings.get("skin", "Night Mode"));
            skin.install();
            boolean widgetOnly = a.contains("--widget");
            if (!widgetOnly) showMain(null);
            if (widgetOnly || Settings.getBool("widget.open", false)) showWidget();
            refresh();
            int minutes = Math.max(5, Settings.getInt("refreshMinutes", 15));
            autoRefresh = new Timer(minutes * 60_000, e -> { if (!demo) refresh(); });
            autoRefresh.start();
            marketTimer = new Timer(60_000, e -> refreshMarkets());   // live index prices every minute
            marketTimer.start();
            alertTimer = new Timer(5 * 60_000, e -> checkAlertsSoon());   // price alerts every 5 minutes
            alertTimer.start();
        });
    }

    // ------------------------------------------------------------ windows
    public static void showMain(String lookupTicker) {
        if (mainWindow == null || !mainWindow.isDisplayable()) {
            mainWindow = new MainWindow();
            mainWindow.setVisible(true);
        }
        mainWindow.setState(Frame.NORMAL);
        mainWindow.toFront();
        if (lookupTicker != null) mainWindow.openLookup(lookupTicker);
    }

    public static void showWidget() {
        if (widget == null || !widget.isDisplayable()) widget = new DesktopWidget();
        widget.setVisible(true);
        widget.toFront();
        Settings.set("widget.open", true);
    }

    public static void widgetClosed() {
        Settings.set("widget.open", false);
        exitIfNoWindows();
    }

    public static void exitIfNoWindows() {
        boolean mainOpen = mainWindow != null && mainWindow.isDisplayable();
        boolean widgetOpen = widget != null && widget.isDisplayable();
        if (!mainOpen && !widgetOpen) System.exit(0);
    }

    // ------------------------------------------------------------ data
    public static void refresh() {
        if (loading) return;
        loading = true;
        Analyzer next = new Analyzer(demo);
        setStatus(demo ? I18n.tr("Loading practice data...") : I18n.tr("Collecting today's data from the websites..."));
        new SwingWorker<Advisor.Plan, String>() {
            List<String> firedAlerts;
            @Override protected Advisor.Plan doInBackground() {
                next.run(this::publish);
                Advisor.Plan built = Advisor.build(next);
                firedAlerts = Alerts.check(Alerts.prices(next, false));
                return built;
            }
            @Override protected void process(List<String> chunks) { setStatus(chunks.get(chunks.size() - 1)); }
            @Override protected void done() {
                loading = false;
                try {
                    plan = get();
                    analyzer = next;
                    notifyAlerts(firedAlerts);
                    setStatus(next.stocks.isEmpty()
                            ? I18n.tr("Couldn't download any stock prices. Check your internet connection, or turn on Practice mode.")
                            : I18n.tr("Ready - ") + next.stocks.size() + I18n.tr(" stocks analysed") + (demo ? I18n.tr(" (PRACTICE DATA - not real)") : "") + ".");
                } catch (Exception e) {
                    setStatus(I18n.tr("Something went wrong while loading: ") + e.getMessage());
                }
                dataListeners.forEach(Runnable::run);
            }
        }.execute();
        dataListeners.forEach(Runnable::run); // lets windows show "loading"
    }

    public static void setDemo(boolean on) {
        if (demo == on) return;
        demo = on;
        Settings.set("practiceMode", on);
        refresh();
    }

    /** Switches language: explanations are re-written (no new download), then every window is rebuilt. */
    public static void setLanguage(String code) {
        I18n.setLang(code);
        for (Stock s : analyzer.stocks.values()) if (s.tech != null) Scorer.score(s);
        if (plan != null) plan = Advisor.build(analyzer);
        for (Window w : Window.getWindows()) if (w.isDisplayable()) w.applyComponentOrientation(I18n.rtl() ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
        new ArrayList<>(skinListeners).forEach(Runnable::run);
        new ArrayList<>(dataListeners).forEach(Runnable::run);
    }

    public static void setSkin(Skin s) {
        skin = s;
        Settings.set("skin", s.name());
        s.install();
        for (Window w : Window.getWindows()) if (w.isDisplayable()) SwingUtilities.updateComponentTreeUI(w);
        new ArrayList<>(skinListeners).forEach(Runnable::run);
    }

    private static void setStatus(String s) {
        status = s;
        statusListeners.forEach(l -> l.accept(s));
    }

    public static void onData(Runnable r) { dataListeners.add(r); }

    /** Checks the price alerts with fresh prices in the background. */
    public static void checkAlertsSoon() {
        if (!Alerts.anyWaiting()) return;
        final Analyzer a = analyzer;
        new Thread(() -> {
            List<String> fired = Alerts.check(Alerts.prices(a, !a.demo));
            SwingUtilities.invokeLater(() -> {
                notifyAlerts(fired);
                for (Window w : Window.getWindows()) if (w instanceof MainWindow && w.isDisplayable()) ((MainWindow) w).refreshAlertsAndPaper();
            });
        }, I18n.tr("StockBuddy alerts")).start();
    }

    /** Desktop notification (system tray / notify-send / macOS) + a small window that doesn't block the app. */
    static void notifyAlerts(List<String> fired) {
        if (fired == null || fired.isEmpty()) return;
        DesktopNotify.show(I18n.tr("StockBuddy price alert"), fired);
        Toolkit.getDefaultToolkit().beep();
        JOptionPane pane = new JOptionPane(I18n.tr("<html><b>Price alert") + (fired.size() > 1 ? "s" : "") + I18n.tr("</b><br>") + String.join("<br>", fired)
                + I18n.tr("<br><br><span style='color:gray'>See the Price Alerts page.</span></html>"), JOptionPane.INFORMATION_MESSAGE);
        JDialog d = pane.createDialog(null, I18n.tr("StockBuddy price alert"));
        d.setModal(false);
        d.setAlwaysOnTop(true);
        d.setIconImages(icons());
        d.setVisible(true);
    }
    public static void onMarkets(Runnable r) { marketListeners.add(r); }

    /** Updates just the world index prices (quick, in the background). */
    public static void refreshMarkets() {
        if (demo || loading || marketsLoading || analyzer.markets.isEmpty()) return;
        marketsLoading = true;
        final List<WorldMarkets.Market> markets = analyzer.markets;
        new Thread(() -> {
            try { WorldMarkets.refreshPrices(markets); }
            finally {
                marketsLoading = false;
                SwingUtilities.invokeLater(() -> new ArrayList<>(marketListeners).forEach(Runnable::run));
            }
        }, I18n.tr("StockBuddy index prices")).start();
    }
    public static void onSkin(Runnable r) { skinListeners.add(r); }
    public static void onStatus(Consumer<String> c) { statusListeners.add(c); }
    public static void removeListeners(Object... rs) {
        for (Object r : rs) { dataListeners.remove(r); skinListeners.remove(r); statusListeners.remove(r); marketListeners.remove(r); }
    }

    // ------------------------------------------------------------ icon
    private static List<Image> icons;

    /** A little green rising-chart icon, drawn in code so no image files are needed. */
    public static List<Image> icons() {
        if (icons != null) return icons;
        icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128}) icons.add(drawIcon(size));
        return icons;
    }

    /** Draws the app icon at any size (also used to make the Android / Windows / Linux icon files). */
    public static BufferedImage drawIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1f8f5f), size, size, new Color(0x0d4f8b)));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.3, size * 0.3));
        Path2D p = new Path2D.Double();
        p.moveTo(size * 0.15, size * 0.72);
        p.lineTo(size * 0.38, size * 0.50);
        p.lineTo(size * 0.55, size * 0.62);
        p.lineTo(size * 0.84, size * 0.26);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1.5f, size / 11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
        g.fill(new java.awt.geom.Ellipse2D.Double(size * 0.84 - size / 12.0, size * 0.26 - size / 12.0, size / 6.0, size / 6.0));
        g.dispose();
        return img;
    }
}
