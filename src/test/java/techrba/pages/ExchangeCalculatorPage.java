package techrba.pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import techrba.base.DriverWait;
import techrba.util.DecimalParser;

import java.math.BigDecimal;

/**
 * Page Object for the RBA exchange-rate calculator.
 *
 * <p>Encapsulates the form controls ({@code kurs}/{@code val1}/{@code val2}/
 * {@code suma1}) and the AJAX-populated result fields ({@code exchangeRate}/
 * {@code suma2}). Methods are purpose-built so tests only talk about a
 * "buy"/"sell" transaction, never raw WebElements.</p>
 */
public class ExchangeCalculatorPage {

    private static final Logger LOG = LogManager.getLogger(ExchangeCalculatorPage.class);

    static final By FROM_CURRENCY_DROPDOWN = By.id("val1");

    private static final By RATE_TYPE_DROPDOWN = By.id("kurs");     // Kupovni=0, Srednji=1, Prodajni=2
    private static final By FROM_CURRENCY = By.id("val1");          // currency being exchanged
    private static final By TO_CURRENCY = By.id("val2");            // result currency
    private static final By AMOUNT_INPUT = By.id("suma1");
    private static final By RESULT_AMOUNT = By.id("suma2");         // hidden, AJAX-populated
    private static final By EXCHANGE_RATE = By.id("exchangeRate"); // hidden, AJAX-populated

    private final WebDriver driver;
    private final DriverWait wait;

    public ExchangeCalculatorPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new DriverWait(driver);
    }

    /** Sets the rate mode: 0=Kupovni, 1=Srednji, 2=Prodajni. */
    public ExchangeCalculatorPage selectRateType(int mode) {
        selectByValue(RATE_TYPE_DROPDOWN, String.valueOf(mode));
        return this;
    }

    public ExchangeCalculatorPage selectFromCurrency(String currencyId) {
        selectByValue(FROM_CURRENCY, currencyId);
        return this;
    }

    public ExchangeCalculatorPage selectToCurrency(String currencyId) {
        selectByValue(TO_CURRENCY, currencyId);
        return this;
    }

    /** Sets the amount to convert. Changing it triggers the backend AJAX call. */
    public ExchangeCalculatorPage enterAmount(int amount) {
        WebElement input = wait.get().until(ExpectedConditions.visibilityOfElementLocated(AMOUNT_INPUT));
        input.clear();
        input.sendKeys(String.valueOf(amount) + "\n");
        waitForExchangeResult();
        return this;
    }

    /** Reads the normalised exchange rate (e.g. 0.830960) after the AJAX reply. */
    public BigDecimal readExchangeRate() {
        waitForNonNullValue(EXCHANGE_RATE);
        String txt = driver.findElement(EXCHANGE_RATE).getAttribute("value");
        return DecimalParser.parse(txt);
    }

    /** Reads the converted amount (e.g. 33.2384) after the AJAX reply. */
    public BigDecimal readConvertedAmount() {
        waitForNonNullValue(RESULT_AMOUNT);
        String txt = driver.findElement(RESULT_AMOUNT).getAttribute("value");
        return DecimalParser.parse(txt);
    }

    private void waitForExchangeResult() {
        // The converted field is populated by a debounced (300 ms) AJAX call
        waitForNonNullValue(RESULT_AMOUNT);
        DriverWait.sleepQuietly(1200);
    }

    private void waitForNonNullValue(By locator) {
        wait.get().until(d -> {
            String v = d.findElement(locator).getAttribute("value");
            return v != null && !v.trim().isEmpty() && !"0".equals(v.trim());
        });
    }

    private void selectByValue(By locator, String value) {
        WebElement element = wait.get().until(ExpectedConditions.elementToBeClickable(locator));
        new Select(element).selectByValue(value);
        LOG.info("Selected {} -> value {}", locator, value);
    }
}
