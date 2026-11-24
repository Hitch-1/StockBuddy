package stockbuddy.android;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Refreshes the widget's data in the background (Android decides the exact moment). */
public class RefreshJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Droid.init(this);
        Droid.refresh(this, () -> jobFinished(params, false));
        return true; // still working (on a background thread)
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
