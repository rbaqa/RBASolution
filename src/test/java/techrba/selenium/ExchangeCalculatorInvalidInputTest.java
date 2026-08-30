package techrba.selenium;

import io.qameta.allure.Description;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.annotation.Requirement;
import techrba.base.BaseCalculatorTest;
import techrba.pages.ExchangeCalculatorPage;

/**
 * Verifies the calculator's handling of non-numeric input in the amount field.
 *
 * <p>The RBA page JS formats the amount on every {code keyup}: it keeps digits
 * and one decimal separator, then re-writes the field as
 * {code parseFloat(value).toFixed(2)}. For purely textual input that yields
 * {code NaN}, which is displayed inline in the amount field itself.</p>
 */
public class ExchangeCalculatorInvalidInputTest extends BaseCalculatorTest {

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("Typing text into the amount field displays NaN")
    @Requirement({"S8"})
    public void textAmountDisplaysNaN() {
        ExchangeCalculatorPage calc = openCalculator();

        calc.typeAmountText("abc");
        String displayed = calc.readAmountInputValue();

        LOG.info("Amount field after typing 'abc' = '{}'", displayed);

        Assert.assertEquals(displayed, "NaN",
                "Amount field should display NaN for non-numeric input, but was '" + displayed + "'");
    }
}