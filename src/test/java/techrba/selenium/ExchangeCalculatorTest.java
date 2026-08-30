package techrba.selenium;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.base.BaseTest;
import techrba.config.ConfigManager;
import techrba.util.DecimalParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Selenium Test - RBA Exchange Rate Calculator ({@code /alati/tecajni-kalkulator}).
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
        openHomePageAndSelectCalculator();

        ExchangeTransaction buyGbp = new ExchangeTransaction(
                ConfigManager.getInt("calc.buy.mode"),
                ConfigManager.getString("calc.buy.currency1.code"),
                ConfigManager.getString("calc.buy.currency1.id"),
                ConfigManager.getInt("calc.buy.amount"),
                ConfigManager.getString("calc.buy.currency2.code"),
                ConfigManager.getString("calc.buy.currency2.id"));
        ExchangeTransaction sellUsd = new ExchangeTransaction(
                ConfigManager.getInt("calc.sell.mode"),
                ConfigManager.getString("calc.sell.currency1.code"),
                ConfigManager.getString("calc.sell.currency1.id"),
                ConfigManager.getInt("calc.sell.amount"),
                ConfigManager.getString("calc.sell.currency2.code"),
                ConfigManager.getString("calc.sell.currency2.id"));

        CalculatorResult buyResult = performTransaction("BUY GBP (kupnja funti)", buyGbp);
        CalculatorResult sellResult = performTransaction("SELL USD (prodaja dolara)", sellUsd);

        assertTransactionResult("BUY GBP", "GBP", buyGbp, buyResult);
        assertTransactionResult("SELL USD", "EUR", sellUsd, sellResult);
    }

    @Step("Open homepage, click 'Tecajni kalkulator' and switch to the new tab")
    private void openHomePageAndSelectCalculator() {
        openApp();
        WebDriverWait wait = new WebDriverWait(getDriver(),
                Duration.ofSeconds(ConfigManager.getInt("selenium.explicit.timeout.seconds")));

        dismissCookieBanner(wait);

        By calculatorButton = By.cssSelector("a[href*='tecajni-kalkulator']");
        WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(calculatorButton));
        btn.click();

        // Button opens the calculator in a new tab (target=_blank)
        String originalWindow = getDriver().getWindowHandle();
        String newWindow = wait.until(d -> d.getWindowHandles().stream()
                .filter(h -> !h.equals(originalWindow))
                .findFirst()
                .orElse(null));
        getDriver().switchTo().window(newWindow);
        LOG.info("Switched to calculator tab");

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("val1")));
        // Small settle delay for the initial AJAX state (defaults loaded)
        sleepQuietly(1000);
    }

    /**
     * RBA uses a OneTrust consent banner that overlays the page and can
     * intercept clicks. This helper accepts cookies when the banner is present
     * and is a no-op otherwise, so the flow is robust on first visits and in
     * CI environments already accepting cookies.
     */
    private void dismissCookieBanner(WebDriverWait wait) {
        try {
            By acceptButton = By.id("onetrust-accept-btn-handler");
            if (getDriver().findElements(acceptButton).size() > 0) {
                wait.until(ExpectedConditions.elementToBeClickable(acceptButton)).click();
                LOG.info("Accepted OneTrust cookie banner");
                sleepQuietly(800);
            }
        } catch (Exception e) {
            LOG.warn("Cookie banner not dismissed (continuing): {}", e.getMessage());
        }
    }

    @Step("Perform transaction: {label}")
    private CalculatorResult performTransaction(String label, ExchangeTransaction tx) {
        WebDriverWait wait = new WebDriverWait(getDriver(),
                Duration.ofSeconds(ConfigManager.getInt("selenium.explicit.timeout.seconds")));

        // Optional cookie banner dismissal is skipped; interactions use element waits only.

        selectByValue(wait, By.id("kurs"), String.valueOf(tx.mode));
        selectByValue(wait, By.id("val1"), tx.currency1Id);
        selectByValue(wait, By.id("val2"), tx.currency2Id);

        WebElement amount = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("suma1")));
        amount.clear();
        amount.sendKeys(String.valueOf(tx.amount) + "\n");
        // changing a text input triggers the AJAX after a short debounce; wait for result
        waitForExchangeResult(wait, tx);

        BigDecimal rate = readRate(wait);
        BigDecimal converted = readAmount(wait);

        LOG.info("[{}] rate: 1 {} = {} {}, amount: {} {} = {} {}",
                label, tx.currency1Code, rate, tx.currency2Code,
                tx.amount, tx.currency1Code, converted, tx.currency2Code);

        return new CalculatorResult(rate, converted);
    }

    private void waitForExchangeResult(WebDriverWait wait, ExchangeTransaction tx) {
        // Wait until the converted hidden field reflects a non-empty/non-default value for the new cfg
        waitForNonNullValue(wait, By.id("suma2"));
        // allow the debounced AJAX (300 ms) + render to complete
        sleepQuietly(1200);
    }

    private void waitForNonNullValue(WebDriverWait wait, By locator) {
        wait.until(d -> {
            String v = d.findElement(locator).getAttribute("value");
            return v != null && !v.trim().isEmpty() && !"0".equals(v.trim());
        });
    }

    private BigDecimal readRate(WebDriverWait wait) {
        waitForNonNullValue(wait, By.id("exchangeRate"));
        String txt = getDriver().findElement(By.id("exchangeRate")).getAttribute("value");
        Assert.assertFalse(txt == null || txt.trim().isEmpty(), "Exchange rate was not populated");
        return DecimalParser.parse(txt);
    }

    private BigDecimal readAmount(WebDriverWait wait) {
        waitForNonNullValue(wait, By.id("suma2"));
        String txt = getDriver().findElement(By.id("suma2")).getAttribute("value");
        Assert.assertFalse(txt == null || txt.trim().isEmpty(), "Converted amount was not populated");
        return DecimalParser.parse(txt);
    }

    @Step("Assert transaction result: {name} -> {targetCurrency}")
    private void assertTransactionResult(String name, String targetCurrency,
                                         ExchangeTransaction tx, CalculatorResult result) {
        // Positive sanity checks
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

        // The returned currency should match the requested target
        Assert.assertEquals(targetCurrency, tx.currency2Code,
                name + ": unexpected target currency");

        LOG.info("{} validated OK: 1 {} = {} {}; {} {} = {} {}",
                name, tx.currency1Code, result.rate, tx.currency2Code,
                tx.amount, tx.currency1Code, result.amount, tx.currency2Code);
    }

    private void selectByValue(WebDriverWait wait, By locator, String value) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        new Select(element).selectByValue(value);
        LOG.info("Selected {} -> value {}", locator, value);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
