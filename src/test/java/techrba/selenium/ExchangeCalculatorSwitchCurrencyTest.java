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
 * Verifies the currency-swap feature of the calculator: clicking the
 * {@code #switchCurrency} element swaps the "from" and "to" currencies and
 * recalculates the result for the mirrored pair.
 */
public class ExchangeCalculatorSwitchCurrencyTest extends BaseCalculatorTest {

    private static final String EUR_ID = "978";
    private static final String USD_ID = "840";
    private static final int AMOUNT = 100;

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("Click #switchCurrency to swap EUR/USD pair and recalculate")
    @Requirement({"S8"})
    public void switchCurrencySwapsPairAndRecalculates() {
        ExchangeCalculatorPage calc = openCalculator();
        calc.selectRateType(0)
                .selectFromCurrency(EUR_ID)
                .selectToCurrency(USD_ID)
                .enterAmount(AMOUNT);

        BigDecimal beforeRate = calc.readExchangeRate();
        BigDecimal beforeAmount = calc.readConvertedAmount();
        LOG.info("Before swap: 1 EUR = {} USD; {} EUR = {} USD", beforeRate, AMOUNT, beforeAmount);

        calc.switchCurrencies();

        String fromNow = calc.readSelectedFromCurrency();
        String toNow = calc.readSelectedToCurrency();
        LOG.info("After swap: from=val1={}, to=val2={}", fromNow, toNow);

        // The currencies must literally swap.
        Assert.assertEquals(fromNow, USD_ID, "val1 (from) should now be USD after swap");
        Assert.assertEquals(toNow, EUR_ID, "val2 (to) should now be EUR after swap");

        BigDecimal afterRate = calc.readExchangeRate();
        BigDecimal afterAmount = calc.readConvertedAmount();
        LOG.info("After swap: 1 USD = {} EUR; {} USD = {} EUR", afterRate, AMOUNT, afterAmount);

        // Recalculation must produce a fresh, positive result for the mirrored pair.
        Assert.assertTrue(afterRate.compareTo(BigDecimal.ZERO) > 0,
                "Rate after swap must be positive, was " + afterRate);
        Assert.assertTrue(afterAmount.compareTo(BigDecimal.ZERO) > 0,
                "Amount after swap must be positive, was " + afterAmount);

        // The swapped pair is the mirror image: EUR->USD and USD->EUR rates are
        // reciprocals (within a small tolerance). This confirms the calculation
        // really is for the swapped direction rather than a stale result.
        BigDecimal expectedReciprocal = reciprocal(beforeRate);
        BigDecimal swapTolerance = expectedReciprocal.multiply(BigDecimal.valueOf(0.05));
        BigDecimal diff = expectedReciprocal.subtract(afterRate).abs();
        Assert.assertTrue(diff.compareTo(swapTolerance) <= 0,
                "Swapped rate " + afterRate + " should be ~reciprocal of " + beforeRate
                        + " (expected ~" + expectedReciprocal + ", tolerance " + swapTolerance + ")");
    }

    private static BigDecimal reciprocal(BigDecimal rate) {
        return BigDecimal.ONE.divide(rate, 8, java.math.RoundingMode.HALF_UP);
    }
}