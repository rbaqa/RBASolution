package techrba.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Sanity checks around the exchange rate date used by the calculator.
 *
 * <p>Currency mid-rates published by HNB/RBA apply to banking days. Running
 * the calculator tests on a non-banking day (weekend/holiday) can return a
 * previous date's rate; this doesn't invalidate the {@code amount == rate *
 * input} consistency check, but we surface a warning so analysts aren't
 * surprised by a "stale" date in the reports.</p>
 */
public final class DateSanity {

    private static final Logger LOG = LogManager.getLogger(DateSanity.class);

    private DateSanity() {
        // static utility
    }

    /**
     * Logs a WARN when today is not a banking day so report consumers know the
     * exchange date may be the previous working day. A no-op otherwise.
     */
    public static void warnIfNonBankingDay() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        if (weekend) {
            LOG.warn("Today ({}) is not a banking day; the latest applicable exchange date "
                    + "may be the previous working day. Assertions still verify internal "
                    + "consistency (amount = rate * input).", today);
        } else {
            LOG.info("Today ({}) is a banking day - exchange rate is expected to be current.", today);
        }
    }

    /**
     * Returns true only when the given date is a weekday (crude banking-day
     * proxy ignoring bank holidays). Used by the freshness pre-check.
     */
    public static boolean isBankingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
