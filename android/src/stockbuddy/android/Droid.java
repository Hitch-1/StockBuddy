package stockbuddy.android;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import stockbuddy.Advisor;
import stockbuddy.Analyzer;
import stockbuddy.AppPaths;
import stockbuddy.Http;
import stockbuddy.I18n;
import stockbuddy.Palette;
import stockbuddy.Settings;
import stockbuddy.Stock;
import stockbuddy.WorldMarkets;

/** Shared state for the Android app and its home-screen widget. */
public final class Droid {
    public interface Listener {
        void onStatus(String status);
        void onDone();
    }

    public static volatile Analyzer analyzer;
    public static volatile Advisor.Plan plan;
    public static volatile boolean loading;
    public static volatile String status = I18n.tr("Starting...");
    public static volatile Listener listener;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Runnable> waiting = new ArrayList<>();
    private static final List<String> WIDGET_MARKETS = java.util.Arrays.asList("^GSPC", "^IXIC", "^DJI", "^FTSE", "^GDAXI", "^N225", "^HSI", "^BSESN");

    private Droid() {}

    /** Re-writes every explanation in the newly chosen language (no download needed). */
    public static void relocalize() {
        Analyzer a = analyzer;
        if (a == null) return;
        for (stockbuddy.Stock s : a.stocks.values()) if (s.tech != null) stockbuddy.Scorer.score(s);
        if (plan != null) plan = Advisor.build(a);
        saveSnapshot(a, plan);
    }

    /** Must run before anything reads settings or files: tells the shared code where to keep its files. */
    public static void init(Context c) {
        if (System.getProperty("stockbuddy.home") == null)
            System.setProperty("stockbuddy.home", c.getApplicationContext().getFilesDir().getAbsolutePath());
    }

    public static boolean demo() { return Settings.getBool("practiceMode", false); }

    public static Palette appSkin() { return Palette.byName(Settings.get("skin", "Night Mode")); }

    public static Palette widgetSkin() {
        String s = Settings.get("widget.skin", "Same as app");
        return s.equals("Same as app") ? appSkin() : Palette.byName(s);
    }

    /** Collects fresh data in the background. `after` (optional) runs on the main thread when done. */
    public static void refresh(Context ctx, Runnable after) {
        final Context app = ctx.getApplicationContext();
        synchronized (waiting) {
            if (after != null) waiting.add(after);
            if (loading) return;
            loading = true;
        }
        final boolean demo = demo();
        setStatus(demo ? I18n.tr("Loading practice data...") : I18n.tr("Collecting today's data from the websites..."));
        new Thread(() -> {
            try {
                Http.clearCache();
                Analyzer a = new Analyzer(demo);
                a.run(Droid::setStatus);
                Advisor.Plan p = Advisor.build(a);
                analyzer = a;
                plan = p;
                saveSnapshot(a, p);
                Notifier.alerts(app, stockbuddy.Alerts.check(stockbuddy.Alerts.prices(a, false)));
                status = a.stocks.isEmpty()
                        ? I18n.tr("Couldn't download any stock prices. Check your internet, or turn on Practice mode (More menu).")
                        : I18n.tr("Ready - ") + a.stocks.size() + I18n.tr(" stocks analysed") + (demo ? I18n.tr(" (PRACTICE DATA - not real)") : "") + ".";
            } catch (Throwable t) {
                status = I18n.tr("Something went wrong while loading: ") + t;
            }
            MAIN.post(() -> {
                List<Runnable> todo;
                synchronized (waiting) {
                    loading = false;
                    todo = new ArrayList<>(waiting);
                    waiting.clear();
                }
                Listener l = listener;
                if (l != null) l.onDone();
                StockWidget.updateAll(app);
                for (Runnable r : todo) r.run();
            });
        }, I18n.tr("StockBuddy refresh")).start();
        Listener l = listener;
        if (l != null) l.onDone(); // shows the "loading" state
    }

    private static void setStatus(String s) {
        status = s;
        MAIN.post(() -> { Listener l = listener; if (l != null) l.onStatus(s); });
    }

    /** Asks Android to refresh the widget data in the background, even when the app is closed. */
    public static void scheduleRefresh(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(4242, new ComponentName(ctx, RefreshJob.class))
                .setRequiredNetworkType(demo() ? JobInfo.NETWORK_TYPE_NONE : JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(demo() ? 0 : 5 * 60_000)
                .build();
        js.schedule(job);
    }

    // ------------------------------------------------------------------ widget snapshot
    static Path snapshotFile() { return AppPaths.data("widget.properties"); }

    /** Saves the small summary the home-screen widget shows, so it works even after the app is closed. */
    static void saveSnapshot(Analyzer a, Advisor.Plan p) {
        Properties s = new Properties();
        s.setProperty("time", String.valueOf(System.currentTimeMillis()));
        s.setProperty("updated", new SimpleDateFormat(I18n.tr("HH:mm"), Locale.getDefault()).format(new Date()));
        s.setProperty("demo", String.valueOf(a.demo));
        s.setProperty("mood", WorldMarkets.mood(a.markets).replace("Around the world it's ", "World: "));
        StringBuilder m = new StringBuilder();
        for (WorldMarkets.Market mk : a.markets) {
            if (!WIDGET_MARKETS.contains(mk.symbol)) continue;
            m.append(mk.symbol).append('|').append(Double.isNaN(mk.changePct) ? I18n.tr("NaN") : String.valueOf(mk.changePct))
             .append('|').append(Double.isNaN(mk.price) ? I18n.tr("NaN") : String.valueOf(mk.price)).append('\n');
        }
        s.setProperty("markets", m.toString());
        s.setProperty("buys", tickers(p.buys));
        s.setProperty("sells", tickers(p.sells));
        StringBuilder z = new StringBuilder();
        for (Advisor.Idea i : p.zeroDte) {
            z.append(i.tone > 0 ? "+" : i.tone < 0 ? "-" : "0").append('|')
             .append(i.stock == null ? "" : i.stock.ticker).append(": ").append(i.action == null ? i.headline : i.action).append('\n');
        }
        s.setProperty("zdte", z.toString());
        try {
            Files.createDirectories(snapshotFile().getParent());
            try (Writer w = Files.newBufferedWriter(snapshotFile())) { s.store(w, I18n.tr("StockBuddy widget")); }
        } catch (Exception ignored) {}
    }

    private static String tickers(List<Advisor.Idea> ideas) {
        StringBuilder b = new StringBuilder();
        for (Advisor.Idea i : ideas) {
            Stock s = i.stock;
            b.append(s.ticker).append('|').append(Double.isNaN(s.changePct) ? I18n.tr("NaN") : String.valueOf(s.changePct)).append('\n');
        }
        return b.toString();
    }

    static Properties loadSnapshot() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(snapshotFile())) { p.load(r); } catch (Exception ignored) {}
        return p;
    }
}
