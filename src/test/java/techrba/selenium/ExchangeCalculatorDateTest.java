package techrba.selenium;

import io.qameta.allure.Description;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.annotation.Requirement;
import techrba.base.BaseCalculatorTest;
import techrba.pages.ExchangeCalculatorPage;

/**
 * Verifies the date picker of the RBA exchange calculator: after a date is
 * selected in the Mobiscroll calendar the "{@code Tecaj na dan:}" label
 * ({@code #dateVal}) must show the same date as the one picked.
 *
 * <p>The {@code #d} field is readonly once Mobiscroll is attached, so the date
 * can only be changed by opening the calendar bubble and tapping a day. The
 * page JS then sets {@code #dateVal} to {@code &nbsp; + $('#d').val()} on the
 * next recalculation, i.e. the label mirrors the chosen date.</p>
 */
public class ExchangeCalculatorDateTest extends BaseCalculatorTest {

    @Test(groups = {"selenium", "ui", "exchange", "regression"})
    @Description("The #dateVal label shows the date selected in the date picker")
    @Requirement({"S8"})
    public void dateLabelMirrorsSelectedDate() {
        ExchangeCalculatorPage calc = openCalculator();

        // A business day in the same month, a different month and a day in the past.
        String[] dates = {"07.08.2026", "05.03.2026", "14.08.2026"};
        for (String selected : dates) {
            calc.setDate(selected);

            String label = calc.readSelectedDateLabel();
            String picker = calc.readAppliedDate();

            LOG.info("Picked {} -> #dateVal '{}', picker '#d' '{}'", selected, label, picker);

            Assert.assertEquals(
                    normalize(label),
                    normalize(selected),
                    "#dateVal label must show the date selected in the picker (picked '" + selected
                            + "', label '" + label + "')");
            Assert.assertEquals(
                    normalize(picker),
                    normalize(selected),
                    "Picker field #d must hold the selected date (picked '" + selected
                            + "', field '" + picker + "')");
        }
    }

    /** Lowercase/trim a dotted date so the comparison ignores case/whitespace. */
    private static String normalize(String d) {
        return d == null ? "" : d.trim().toLowerCase();
    }
}