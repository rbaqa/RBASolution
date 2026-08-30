package techrba.selenium;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import techrba.annotation.Requirement;
import techrba.base.BaseTest;
import techrba.data.CsvDataProvider;
import techrba.pages.ExchangeCalculatorPage;
import techrba.pages.HomePage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Selenium Test - RBA Exchange Rate Calculator ({@code /alati/tecajni-kalkulator}),
 * written against Page Objects ({@link HomePage}, {@link ExchangeCalculatorPage})
 * and fully data-driven.
 *
 * <p>Scenario required by the task:
 * <ul>
 *   <li>Navigate to the "Tecajni kalkulator" from the homepage</li>
 *   <li>Simulate buying GBP (kupnja funti) and selling USD (prodaja dolara)</li>
 *   <li>Read the exchange rate and the final amount for each transaction</li>
 * </ul>
 *
 * <p>Each transaction comes from {@code testdata/exchange-transactions.csv}
 * via {@link CsvDataProvider}, separating business data from test code.
 * Implemented with Selenium WebDriver + TestNG only (no JavaScript Executor).
 * All assertions validate values are positive and internally consistent.</p>
 */
@Test(groups = {"selenium", "ui", "exchange", "flaky"})
public class ExchangeCalculatorTest extends BaseTest {

    private static final BigDecimal EXPECTED_MIN_RATE = BigDecimal.valueOf(0.001);
    private static final BigDecimal ROUNDING_EPSILON = BigDecimal.valueOf(0.01);

    @DataProvider(name = "exchangeTransactions")
    public Object[][] exchangeTransactions() {
        return CsvDataProvider.transactionData();
    }

    @Test(dataProvider = "exchangeTransactions")
    @Description("Buy GBP and sell USD via the RBA exchange calculator, assert rate and final amount")
    @Requirement({"S7", "S8", "S9", "S10"})
    public void exchangeCalculatorRateAndAmount(
            String transaction, String description, String mode,
            String c1Id, String c1Code, String amount,
            String c2Id, String c2Code) {

        ExchangeTransaction tx = new ExchangeTransaction(
                description,
                Integer.parseInt(mode.trim()),
                c1Id.trim(), c1Code.trim(),
                Integer.parseInt(amount.trim()),
                c2Id.trim(), c2Code.trim());

        LOG.info("--- Transaction {} ({}) ---", transaction, description);

        ExchangeCalculatorPage calculator = new HomePage(getDriver())
                .open()
                .dismissCookieBannerIfPresent()
                .openExchangeCalculator();

        calculator.selectRateType(tx.mode)
                .selectFromCurrency(tx.currency1Id)
                .selectToCurrency(tx.currency2Id)
                .enterAmount(tx.amount);

        BigDecimal rate = calculator.readExchangeRate();
        BigDecimal converted = calculator.readConvertedAmount();

        LOG.info("[{}] rate: 1 {} = {} {}, amount: {} {} = {} {}",
                description, tx.currency1Code, rate, tx.currency2Code,
                tx.amount, tx.currency1Code, converted, tx.currency2Code);

        assertTransactionResult(description, tx, rate, converted);
    }

    @Step("Assert transaction {name}")
    private void assertTransactionResult(String name, ExchangeTransaction tx,
                                         BigDecimal rate, BigDecimal amount) {
        Assert.assertTrue(rate.compareTo(EXPECTED_MIN_RATE) > 0,
                name + ": rate must be positive, was " + rate);
        Assert.assertTrue(amount.compareTo(BigDecimal.ZERO) > 0,
                name + ": converted amount must be positive, was " + amount);

        // Internal consistency: amount ~ rate * input within tolerance.
        // The RBA UI renders the rate to 2 decimal places (toFixed(2)), so the
        // rounding can skew the product by up to input * 0.005; we allow that.
        BigDecimal expected = rate.multiply(BigDecimal.valueOf(tx.amount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxRateRounding = BigDecimal.valueOf(tx.amount * 0.005)
                .add(ROUNDING_EPSILON).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = expected.subtract(amount).abs().setScale(2, RoundingMode.HALF_UP);
        Assert.assertTrue(diff.compareTo(maxRateRounding) <= 0,
                name + ": amount " + amount + " inconsistent with rate " + rate
                        + " * " + tx.amount + " (expected " + expected + ", tolerance " + maxRateRounding + ")");

        LOG.info("{} validated OK: 1 {} = {} {}; {} {} = {} {}",
                name, tx.currency1Code, rate, tx.currency2Code,
                tx.amount, tx.currency1Code, amount, tx.currency2Code);
    }

    /** Immutable description of a currency conversion transaction from the CSV. */
    private static final class ExchangeTransaction {
        final String name;
        final int mode;
        final String currency1Code;
        final String currency1Id;
        final int amount;
        final String currency2Code;
        final String currency2Id;

        ExchangeTransaction(String name, int mode, String c1Id, String c1Code,
                            int amount, String c2Id, String c2Code) {
            this.name = name;
            this.mode = mode;
            this.currency1Code = c1Code;
            this.currency1Id = c1Id;
            this.amount = amount;
            this.currency2Code = c2Code;
            this.currency2Id = c2Id;
        }
    }
}
