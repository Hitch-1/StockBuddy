package stockbuddy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Helpers for options ideas: picking an expiry date and strike, and estimating the price (Black-Scholes). */
public final class Options {
    public static final double RISK_FREE = 0.04;

    private Options() {}

    /** Monthly options expire on the 3rd Friday. We pick the first one at least `minDays` away. */
    public static LocalDate monthlyExpiry(LocalDate today, int minDays) {
        LocalDate d = today.withDayOfMonth(1);
        while (true) {
            LocalDate third = d.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.FRIDAY));
            if (!third.isBefore(today.plusDays(minDays))) return third;
            d = d.plusMonths(1);
        }
    }

    public static double strikeStep(double price) {
        if (price < 25) return 0.5;
        if (price < 100) return 1;
        if (price < 500) return 5;
        return 10;
    }

    public static double roundStrike(double target, double price, boolean up) {
        double step = strikeStep(price);
        return up ? Math.ceil(target / step) * step : Math.floor(target / step) * step;
    }

    /** Estimated price of ONE option share (multiply by 100 for one contract). */
    public static double blackScholes(boolean call, double s, double k, double years, double vol) {
        if (years <= 0) return Math.max(0, call ? s - k : k - s);
        double d1 = (Math.log(s / k) + (RISK_FREE + vol * vol / 2) * years) / (vol * Math.sqrt(years));
        double d2 = d1 - vol * Math.sqrt(years);
        if (call) return s * cdf(d1) - k * Math.exp(-RISK_FREE * years) * cdf(d2);
        return k * Math.exp(-RISK_FREE * years) * cdf(-d2) - s * cdf(-d1);
    }

    /** Standard normal cumulative distribution. */
    static double cdf(double x) {
        double t = 1 / (1 + 0.2316419 * Math.abs(x));
        double d = 0.3989423 * Math.exp(-x * x / 2);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return x > 0 ? 1 - p : p;
    }
}
