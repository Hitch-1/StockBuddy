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
    public static String status = "Starting...";

    private static final List<Runnable> dataListeners = new ArrayList<>();
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
        setStatus(demo ? "Loading practice data..." : "Collecting today's data from the websites...");
        new SwingWorker<Advisor.Plan, String>() {
            @Override protected Advisor.Plan doInBackground() {
                next.run(this::publish);
                return Advisor.build(next);
            }
            @Override protected void process(List<String> chunks) { setStatus(chunks.get(chunks.size() - 1)); }
            @Override protected void done() {
                loading = false;
                try {
                    plan = get();
                    analyzer = next;
                    setStatus(next.stocks.isEmpty()
                            ? "Couldn't download any stock prices. Check your internet connection, or turn on Practice mode."
                            : "Ready - " + next.stocks.size() + " stocks analysed" + (demo ? " (PRACTICE DATA - not real)" : "") + ".");
                } catch (Exception e) {
                    setStatus("Something went wrong while loading: " + e.getMessage());
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
    public static void onSkin(Runnable r) { skinListeners.add(r); }
    public static void onStatus(Consumer<String> c) { statusListeners.add(c); }
    public static void removeListeners(Object... rs) {
        for (Object r : rs) { dataListeners.remove(r); skinListeners.remove(r); statusListeners.remove(r); }
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
