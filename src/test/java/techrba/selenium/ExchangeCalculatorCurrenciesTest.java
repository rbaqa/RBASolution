package techrba.selenium;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.annotation.Requirement;
import techrba.base.BaseCalculatorTest;
import techrba.pages.ExchangeCalculatorPage;

import java.math.BigDecimal;

/**
 * Exercises the exchange calculator with currency pairs other than EUR/GBP,
 * e.g. EUR to CHF and EUR to JPY. Each transaction is verified for a positive
 * exchange rate and a converted amount consistent with {@code rate * input}.
 *
 * <p>Note on the RBA UI: when a pair contains EUR both Kupovni (0), Srednji (1)
 * and Prodajni (2) modes exist. For pairs with neither side EUR the page
 * collapses the buy/sell modes into a single "Kup/prod" entry - so here we keep
 * EUR on one side to cover "other" currencies while all modes remain valid.</p>
 */
public class ExchangeCalculatorCurrenciesTest extends BaseCalculatorTest {

    private static final BigDecimal MIN_RATE = BigDecimal.valueOf(0.000001);
    private static final int AMOUNT = 100;

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("Convert EUR -> CHF via the calculator and verify rate/amount consistency")
    @Requirement({"S8"})
    public void convertEurToChf() {
        ExchangeCalculatorPage calc = openCalculator();
        verifyConversion(calc, "EUR -> CHF", "978", "756", "CHF");
    }

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("Convert EUR -> JPY via the calculator and verify rate/amount consistency")
    @Requirement({"S8"})
    public void convertEurToJpy() {
        ExchangeCalculatorPage calc = openCalculator();
        verifyConversion(calc, "EUR -> JPY", "978", "392", "JPY");
    }

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("Convert USD -> CHF via the calculator and verify rate/amount consistency")
    @Requirement({"S8"})
    public void convertUsdToChf() {
        ExchangeCalculatorPage calc = openCalculator();
        verifyConversion(calc, "USD -> CHF", "840", "756", "CHF");
    }

    @Step("Verify conversion {label}")
    private void verifyConversion(ExchangeCalculatorPage calc, String label,
                                  String fromId, String toId, String toCode) {
        calc.selectRateType(0) // Kupovni
                .selectFromCurrency(fromId)
                .selectToCurrency(toId)
                .enterAmount(AMOUNT);

        BigDecimal rate = calc.readExchangeRate();
        BigDecimal converted = calc.readConvertedAmount();

        LOG.info("[{}] 1 unit = {} {}; {} {} = {} {}",
                label, rate, toCode, AMOUNT, fromId, converted, toCode);

        Assert.assertTrue(rate.compareTo(MIN_RATE) > 0,
                label + ": rate must be positive, was " + rate);
        Assert.assertTrue(converted.compareTo(BigDecimal.ZERO) > 0,
                label + ": converted amount must be positive, was " + converted);

        // The UI renders the rate to 2 dp (toFixed(2)); for high-rate currencies
        // (e.g. JPY ~160) that creates a non-trivial deviation on rate*input, so we
        // assert consistency with a relative tolerance instead of an absolute one.
        BigDecimal expected = rate.multiply(BigDecimal.valueOf(AMOUNT));
        BigDecimal diff = expected.subtract(converted).abs();
        BigDecimal relativeTolerance = expected.multiply(BigDecimal.valueOf(0.02))
                .max(BigDecimal.valueOf(0.01));
        Assert.assertTrue(diff.compareTo(relativeTolerance) <= 0,
                label + ": amount " + converted + " inconsistent with rate " + rate
                        + " * " + AMOUNT + " (expected ~" + expected + ", tolerance " + relativeTolerance + ")");
    }
}
