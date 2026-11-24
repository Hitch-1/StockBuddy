package stockbuddy.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

import com.stockbuddy.app.R;

/**
 * Phone notifications for price alerts, and the background job that checks alerts every ~30 minutes.
 * (Notification channels exist from Android 8; they're created by reflection because this app is
 * compiled against an older Android library.)
 */
public final class Notifier {
    private static final String CHANNEL = "price_alerts";
    private static final int FLAG_IMMUTABLE = 0x04000000;

    private Notifier() {}

    public static void alerts(Context ctx, List<String> fired) {
        if (fired == null || fired.isEmpty()) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> ch = Class.forName("android.app.NotificationChannel");
                Object channel = ch.getConstructor(String.class, CharSequence.class, int.class).newInstance(CHANNEL, "Price alerts", 4 /* IMPORTANCE_HIGH */);
                NotificationManager.class.getMethod("createNotificationChannel", ch).invoke(nm, channel);
                b = (Notification.Builder) Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(ctx, CHANNEL);
            } else {
                b = new Notification.Builder(ctx);
            }
            String text = String.join("\n", fired);
            Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("page", "alerts");
            b.setSmallIcon(R.drawable.icon)
                    .setContentTitle(fired.size() == 1 ? "StockBuddy price alert" : fired.size() + " StockBuddy price alerts")
                    .setContentText(fired.get(0))
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE));
            nm.notify((int) (System.currentTimeMillis() % 100000), b.build());
        } catch (Throwable t) {
            stockbuddy.Log.debug("Could not show notification: " + t);
        }
    }

    /** Checks alerts in the background about every 30 minutes while any alert is waiting. */
    public static void scheduleAlertJob(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!stockbuddy.Alerts.anyWaiting()) { js.cancel(4343); return; }
        JobInfo job = new JobInfo.Builder(4343, new ComponentName(ctx, AlertJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30 * 60_000L)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }
}
