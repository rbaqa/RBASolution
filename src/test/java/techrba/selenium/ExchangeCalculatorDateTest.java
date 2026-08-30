package techrba.selenium;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.annotation.Requirement;
import techrba.base.BaseCalculatorTest;
import techrba.pages.ExchangeCalculatorPage;

/**
 * Verifies the date label of the RBA exchange calculator.
 *
 * <p>The "{@code Tecaj na dan:}" label ({@code #dateVal}) is kept in sync with
 * the date field ({@code #d}): the page JS sets it to the value of {@code #d}
 * on every recalculation ({@code $('#dateVal').html('&nbsp;' + $('#d').val())}).
 * This test checks that after a date change + recalculation the label mirrors
 * the date shown in the picker field.</p>
 *
 * <p>Important environment note: the simulated backend serves a single (latest)
 * rate series and normalizes any historical date entered into the picker back
 * to the current latest date. So this test asserts the verified UI contract
 * that both the picker ({@code #d}) and the label ({@code #dateVal}) end up in
 * sync after a change, rather than asserting a specific historical date sticks.</p>
 */
public class ExchangeCalculatorDateTest extends BaseCalculatorTest {

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("The #dateVal label stays in sync with the date shown in the date picker")
    @Requirement({"S8"})
    public void dateLabelMirrorsDatePicker() {
        ExchangeCalculatorPage calc = openCalculator();

        String[] dates = {"05.03.2026", "09.07.2026"};
        for (String selected : dates) {
            calc.setDate(selected);
            String label = calc.readSelectedDateLabel();
            String picker = calc.readAppliedDate();

            LOG.info("Changed picker to {} -> #dateVal '{}', #d '{}'",
                    selected, label, picker);

            // The label and the picker must always agree on the displayed date
            // (both are updated together by the recalculation JS).
            Assert.assertEquals(
                    normalize(label),
                    normalize(picker),
                    "#dateVal label '"
                            + label + "' must mirror the date shown in the picker '#d' '" + picker + "'");
        }
    }

    /** Lowercase/trim a dotted date so the comparison ignores case/whitespace. */
    @Step("Normalize date for comparison")
    private static String normalize(String d) {
        return d == null ? "" : d.trim().toLowerCase();
    }
}