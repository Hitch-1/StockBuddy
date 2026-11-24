package stockbuddy.android;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;

import stockbuddy.Alerts;

/** Background check of your price alerts (Android runs it about every 30 minutes, even when the app is closed). */
public class AlertJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Droid.init(this);
        if (Droid.demo() || !Alerts.anyWaiting()) return false;
        new Thread(() -> {
            try {
                List<String> fired = Alerts.check(Alerts.prices(null, true));
                Notifier.alerts(this, fired);
            } catch (Throwable ignored) {}
            jobFinished(params, false);
        }, "StockBuddy alert job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;   // try again later
    }
}
