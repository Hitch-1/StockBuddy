package stockbuddy.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.stockbuddy.app.R;
import stockbuddy.Palette;
import stockbuddy.Settings;

/** The StockBuddy home-screen widget: world markets, top buys, stocks to be careful with, and the 0DTE lean. */
public class StockWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.stockbuddy.app.WIDGET_REFRESH";
    private static final int FLAG_IMMUTABLE = 0x04000000; // PendingIntent.FLAG_IMMUTABLE (needed on Android 12+)
    private static final Map<String, String> SHORT = new HashMap<>();
    static {
        SHORT.put("^GSPC", "S&amp;P 500");
        SHORT.put("^IXIC", "Nasdaq");
        SHORT.put("^DJI", "Dow Jones");
        SHORT.put("^FTSE", "FTSE 100 (UK)");
        SHORT.put("^GDAXI", "DAX (Germany)");
        SHORT.put("^N225", "Nikkei (Japan)");
        SHORT.put("^HSI", "Hang Seng (HK)");
        SHORT.put("^BSESN", "Sensex (India)");
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Droid.init(ctx);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            render(ctx, true);
            Droid.scheduleRefresh(ctx);
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        Droid.init(ctx);
        render(ctx, false);
        long age = System.currentTimeMillis() - parseLong(Droid.loadSnapshot().getProperty("time"));
        if (age > 25 * 60_000L) Droid.scheduleRefresh(ctx);
    }

    /** Redraws every StockBuddy widget on the home screen. */
    public static void updateAll(Context ctx) {
        Droid.init(ctx);
        render(ctx, false);
    }

    private static void render(Context ctx, boolean updating) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, StockWidget.class));
        if (ids == null || ids.length == 0) return;
        Palette k = Droid.widgetSkin();
        Properties s = Droid.loadSnapshot();
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        int alpha = Math.round(255 * Settings.getInt("widget.opacity", 92) / 100f);
        v.setInt(R.id.w_root, "setBackgroundColor", (alpha << 24) | (Palette.argb(k.panel()) & 0xFFFFFF));
        v.setTextColor(R.id.w_title, Palette.argb(k.text()));
        v.setTextColor(R.id.w_updated, Palette.argb(k.muted()));
        v.setTextColor(R.id.w_refresh, Palette.argb(k.muted()));
        v.setTextColor(R.id.w_body, Palette.argb(k.text()));
        v.setTextColor(R.id.w_footer, Palette.argb(k.muted()));

        String updated = s.getProperty("updated");
        String sub = updating || Droid.loading ? "updating..." : updated == null ? "Tap ↻ to load" : "updated " + updated;
        if ("true".equals(s.getProperty("demo"))) sub = "PRACTICE DATA - " + sub;
        v.setTextViewText(R.id.w_updated, sub);
        v.setTextViewText(R.id.w_body, Html.fromHtml(body(k, s)));
        v.setTextViewText(R.id.w_footer, "Not financial advice · tap to open");

        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE));
        Intent refresh = new Intent(ctx, StockWidget.class).setAction(ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.w_refresh, PendingIntent.getBroadcast(ctx, 1, refresh, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE));
        mgr.updateAppWidget(ids, v);
    }

    private static String body(Palette k, Properties s) {
        if (s.getProperty("time") == null) return "<font color='" + k.muted() + "'>No data yet. Open StockBuddy once, or tap ↻.</font>";
        StringBuilder b = new StringBuilder();
        b.append("<font color='").append(k.accent()).append("'>").append(esc(s.getProperty("mood", ""))).append("</font><br><br>");
        b.append("<small><b><font color='").append(k.muted()).append("'>WORLD MARKETS</font></b></small><br>");
        for (String line : s.getProperty("markets", "").split("\n")) {
            String[] p = line.split("\\|");
            if (p.length < 2) continue;
            b.append(SHORT.containsKey(p[0]) ? SHORT.get(p[0]) : esc(p[0]));
            if (p.length > 2 && !p[2].equals("NaN")) {
                try { b.append(" &nbsp;<font color='").append(k.muted()).append("'>").append(stockbuddy.WorldMarkets.price(Double.parseDouble(p[2]))).append("</font>"); }
                catch (NumberFormatException ignored) {}
            }
            b.append(" &nbsp; ").append(pct(k, p[1])).append("<br>");
        }
        b.append("<br><small><b><font color='").append(k.muted()).append("'>TOP BUYS TODAY</font></b></small><br>");
        b.append(chips(k, s.getProperty("buys", ""), k.good(), "none today - waiting is fine"));
        b.append("<br><small><b><font color='").append(k.muted()).append("'>CAREFUL WITH</font></b></small><br>");
        b.append(chips(k, s.getProperty("sells", ""), k.bad(), "no strong warnings"));
        b.append("<br><small><b><font color='").append(k.muted()).append("'>0DTE (EXPIRING TODAY)</font></b></small><br>");
        for (String z : s.getProperty("zdte", "").split("\n")) {
            if (z.trim().isEmpty()) continue;
            String c = z.contains("CALL") ? k.good() : z.contains("PUT") ? k.bad() : k.muted();
            b.append("<font color='").append(c).append("'>").append(esc(z)).append("</font><br>");
        }
        return b.toString();
    }

    private static String chips(Palette k, String data, String color, String empty) {
        StringBuilder b = new StringBuilder();
        for (String line : data.split("\n")) {
            String[] p = line.split("\\|");
            if (p.length < 2) continue;
            if (b.length() > 0) b.append(" &nbsp;·&nbsp; ");
            b.append("<b><font color='").append(color).append("'>").append(esc(p[0])).append("</font></b> ").append(pct(k, p[1]));
        }
        return b.length() == 0 ? "<font color='" + k.muted() + "'>" + empty + "</font><br>" : b + "<br>";
    }

    private static String pct(Palette k, String v) {
        double d;
        try { d = Double.parseDouble(v); } catch (Exception e) { d = Double.NaN; }
        if (Double.isNaN(d)) return "<font color='" + k.muted() + "'>n/a</font>";
        String c = d > 0.05 ? k.good() : d < -0.05 ? k.bad() : k.warn();
        return "<font color='" + c + "'>" + String.format(Locale.US, "%+.2f%%", d) + "</font>";
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
