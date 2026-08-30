package techrba.selenium;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.base.BaseTest;
import techrba.config.ConfigManager;
import techrba.pages.ExchangeCalculatorPage;
import techrba.pages.HomePage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Selenium Test - RBA Exchange Rate Calculator ({@code /alati/tecajni-kalkulator}),
 * written against Page Objects ({@link HomePage}, {@link ExchangeCalculatorPage}).
 *
 * <p>Scenario required by the task:
 * <ul>
 *   <li>Navigate to the "Tecajni kalkulator" from the homepage</li>
 *   <li>Simulate buying GBP (kupnja funti) and selling USD (prodaja dolara)</li>
 *   <li>Read the exchange rate and the final amount for each transaction</li>
 * </ul>
 *
 * <p>Implemented with Selenium WebDriver + TestNG only (no JavaScript Executor).
 * All assertions validate values are positive and internally consistent.</p>
 */
public class ExchangeCalculatorTest extends BaseTest {

    private static final BigDecimal EXPECTED_MIN_RATE = BigDecimal.valueOf(0.001);
    private static final BigDecimal ROUNDING_EPSILON = BigDecimal.valueOf(0.01);

    @Test(groups = {"selenium", "exchange"})
    @Description("Buy GBP and sell USD via the RBA exchange calculator, assert rate and final amount")
    public void exchangeCalculatorRateAndAmount() {
        ExchangeCalculatorPage calculator = new HomePage(getDriver())
                .open()
                .dismissCookieBannerIfPresent()
                .openExchangeCalculator();

        ExchangeTransaction buyGbp = buyGbpTransaction();
        ExchangeTransaction sellUsd = sellUsdTransaction();

        CalculatorResult buyResult = performTransaction("BUY GBP (kupnja funti)", calculator, buyGbp);
        CalculatorResult sellResult = performTransaction("SELL USD (prodaja dolara)", calculator, sellUsd);

        assertTransactionResult("BUY GBP", "GBP", buyGbp, buyResult);
        assertTransactionResult("SELL USD", "EUR", sellUsd, sellResult);
    }

    @Step("Perform {label} on the calculator")
    private CalculatorResult performTransaction(String label, ExchangeCalculatorPage calculator,
                                                ExchangeTransaction tx) {
        calculator.selectRateType(tx.mode)
                .selectFromCurrency(tx.currency1Id)
                .selectToCurrency(tx.currency2Id)
                .enterAmount(tx.amount);

        BigDecimal rate = calculator.readExchangeRate();
        BigDecimal converted = calculator.readConvertedAmount();

        LOG.info("[{}] rate: 1 {} = {} {}, amount: {} {} = {} {}",
                label, tx.currency1Code, rate, tx.currency2Code,
                tx.amount, tx.currency1Code, converted, tx.currency2Code);

        return new CalculatorResult(rate, converted);
    }

    @Step("Assert {name} -> {targetCurrency}")
    private void assertTransactionResult(String name, String targetCurrency,
                                         ExchangeTransaction tx, CalculatorResult result) {
        Assert.assertTrue(result.rate.compareTo(EXPECTED_MIN_RATE) > 0,
                name + ": rate must be positive, was " + result.rate);
        Assert.assertTrue(result.amount.compareTo(BigDecimal.ZERO) > 0,
                name + ": converted amount must be positive, was " + result.amount);

        // Internal consistency: amount ~ rate * input within tolerance.
        // The RBA UI renders the rate to 2 decimal places (toFixed(2)), so the
        // rounding can skew the product by up to input * 0.005; we allow that.
        BigDecimal expected = result.rate.multiply(BigDecimal.valueOf(tx.amount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxRateRounding = BigDecimal.valueOf(tx.amount * 0.005)
                .add(ROUNDING_EPSILON).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = expected.subtract(result.amount).abs().setScale(2, RoundingMode.HALF_UP);
        Assert.assertTrue(diff.compareTo(maxRateRounding) <= 0,
                name + ": amount " + result.amount + " inconsistent with rate " + result.rate
                        + " * " + tx.amount + " (expected " + expected + ", tolerance " + maxRateRounding + ")");

        Assert.assertEquals(targetCurrency, tx.currency2Code,
                name + ": unexpected target currency");

        LOG.info("{} validated OK: 1 {} = {} {}; {} {} = {} {}",
                name, tx.currency1Code, result.rate, tx.currency2Code,
                tx.amount, tx.currency1Code, result.amount, tx.currency2Code);
    }

    private ExchangeTransaction buyGbpTransaction() {
        return new ExchangeTransaction(
                ConfigManager.getInt("calc.buy.mode"),
                ConfigManager.getString("calc.buy.currency1.code"),
                ConfigManager.getString("calc.buy.currency1.id"),
                ConfigManager.getInt("calc.buy.amount"),
                ConfigManager.getString("calc.buy.currency2.code"),
                ConfigManager.getString("calc.buy.currency2.id"));
    }

    private ExchangeTransaction sellUsdTransaction() {
        return new ExchangeTransaction(
                ConfigManager.getInt("calc.sell.mode"),
                ConfigManager.getString("calc.sell.currency1.code"),
                ConfigManager.getString("calc.sell.currency1.id"),
                ConfigManager.getInt("calc.sell.amount"),
                ConfigManager.getString("calc.sell.currency2.code"),
                ConfigManager.getString("calc.sell.currency2.id"));
    }

    /** Immutable description of a currency conversion transaction. */
    private static final class ExchangeTransaction {
        final int mode;
        final String currency1Code;
        final String currency1Id;
        final int amount;
        final String currency2Code;
        final String currency2Id;

        ExchangeTransaction(int mode, String c1Code, String c1Id, int amount,
                            String c2Code, String c2Id) {
            this.mode = mode;
            this.currency1Code = c1Code;
            this.currency1Id = c1Id;
            this.amount = amount;
            this.currency2Code = c2Code;
            this.currency2Id = c2Id;
        }
    }

    /** Result of a conversion: the exchange rate and the converted amount. */
    private static final class CalculatorResult {
        final BigDecimal rate;
        final BigDecimal amount;

        CalculatorResult(BigDecimal rate, BigDecimal amount) {
            this.rate = rate;
            this.amount = amount;
        }
    }
}
