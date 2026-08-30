package techrba.pages;

import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import techrba.base.DriverWait;
import techrba.base.WaitStrategy;
import techrba.util.DecimalParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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
    private static final By DATE_INPUT = By.id("d");                // exchange date (dd.MM.yyyy)
    private static final By DATE_LABEL = By.id("dateVal");         // UI label mirroring the chosen date
    private static final By SWITCH_CURRENCY_LINK = By.id("switchCurrency");
    private static final By EFFECTIVE_SECTION = By.id("acctContainer"); // shown for Kupovni/Prodajni only

    // Mobiscroll date-picker bubble DOM (opened by clicking #d).
    private static final By DATE_PICKER_DAY = By.cssSelector(".mbsc-cal-day");      // data-full="yyyy-M-d" (M is 0-based)
    private static final By DATE_PICKER_PREV_MONTH = By.cssSelector(".mbsc-cal-prev-m .mbsc-cal-btn-txt");
    private static final By DATE_PICKER_NEXT_MONTH = By.cssSelector(".mbsc-cal-next-m .mbsc-cal-btn-txt");
    private static final By DATE_PICKER_PREV_YEAR = By.cssSelector(".mbsc-cal-prev-y .mbsc-cal-btn-txt");
    private static final By DATE_PICKER_NEXT_YEAR = By.cssSelector(".mbsc-cal-next-y .mbsc-cal-btn-txt");

    private final WebDriver driver;
    private final DriverWait wait;

    public ExchangeCalculatorPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new DriverWait(driver);
    }

    /** Sets the rate mode: 0=Kupovni, 1=Srednji, 2=Prodajni. */
    @Step("Select rate type (kurs) mode {mode}")
    public ExchangeCalculatorPage selectRateType(int mode) {
        selectByValue(RATE_TYPE_DROPDOWN, String.valueOf(mode));
        return this;
    }

    @Step("Select exchange-from currency {currencyId}")
    public ExchangeCalculatorPage selectFromCurrency(String currencyId) {
        selectByValue(FROM_CURRENCY, currencyId);
        return this;
    }

    @Step("Select exchange-to currency {currencyId}")
    public ExchangeCalculatorPage selectToCurrency(String currencyId) {
        selectByValue(TO_CURRENCY, currencyId);
        return this;
    }

    /** Sets the amount to convert. Changing it triggers the backend AJAX call. */
    @Step("Enter amount {amount}")
    public ExchangeCalculatorPage enterAmount(int amount) {
        WebElement input = wait.get().until(ExpectedConditions.visibilityOfElementLocated(AMOUNT_INPUT));
        input.sendKeys(Keys.chord(Keys.CONTROL, "a")); // select current value
        input.sendKeys(String.valueOf(amount));
        // Blur (TAB) instead of pressing Enter so the page does not submit/refresh
        // and re-prepend a leading '0' to the displayed amount (040 / 0100).
        new Actions(driver).sendKeys(Keys.TAB).perform();
        LOG.debug("Amount field '#suma1' value after entering {} = '{}'",
                amount, input.getAttribute("value"));
        waitForExchangeResult();
        return this;
    }

    /** Reads the normalised exchange rate (e.g. 0.830960) after the AJAX reply. */
    @Step("Read the exchange rate")
    public BigDecimal readExchangeRate() {
        waitForNonNullValue(EXCHANGE_RATE);
        String txt = driver.findElement(EXCHANGE_RATE).getAttribute("value");
        LOG.debug("Raw exchange rate field value = '{}'", txt);
        return DecimalParser.parse(txt);
    }

    /** Reads the converted amount (e.g. 33.2384) after the AJAX reply. */
    @Step("Read the converted amount")
    public BigDecimal readConvertedAmount() {
        waitForNonNullValue(RESULT_AMOUNT);
        String txt = driver.findElement(RESULT_AMOUNT).getAttribute("value");
        LOG.debug("Raw converted amount field value = '{}'", txt);
        return DecimalParser.parse(txt);
    }

    /**
     * Swaps the exchange-from and exchange-to currencies by clicking the
     * {@code #switchCurrency} element. The page then triggers a change on all
     * inputs, recalculating for the swapped pair.
     */
    @Step("Switch currencies (swap from/to)")
    public ExchangeCalculatorPage switchCurrencies() {
        WebElement link = wait.get().until(ExpectedConditions.elementToBeClickable(SWITCH_CURRENCY_LINK));
        link.click();
        waitForExchangeResult();
        return this;
    }

    /**
     * Selects an exchange date via the Mobiscroll date picker: opens the calendar
     * bubble by clicking the (readonly) {@code #d} field, navigates to the
     * requested month/year and taps the target day cell. {@code #dateVal} then
     * shows the picked date.
     *
     * <p>Direct typing is impossible because the field is <em>readonly</em> once
     * Mobiscroll is attached, so this is the only realistic user interaction.</p>
     *
     * @param date dotted date string, e.g. {@code 15.03.2026}
     */
    @Step("Select exchange date {date} via the date picker")
    public ExchangeCalculatorPage setDate(String date) {
        LocalDate target = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        YearMonth targetYm = YearMonth.from(target);

        // Open the Mobiscroll calendar bubble.
        wait.get().until(ExpectedConditions.elementToBeClickable(DATE_INPUT)).click();
        // The bubble renders 3 month slides but only the current month is visible;
        // wait until at least one day cell is actually displayed (not just present).
        wait.get().until(d -> d.findElements(DATE_PICKER_DAY).stream().anyMatch(WebElement::isDisplayed));

        // The bubble opens on the month already shown in the field (#d).
        String currentVal = driver.findElement(DATE_INPUT).getAttribute("value");
        LocalDate current = currentVal == null || currentVal.trim().isEmpty()
                ? LocalDate.now()
                : LocalDate.parse(currentVal, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        long monthDiff = ChronoUnit.MONTHS.between(YearMonth.from(current), targetYm);

        // Navigate years first for large jumps, then the remaining months.
        while (monthDiff <= -12) {
            clickDatePickerButton(DATE_PICKER_PREV_YEAR);
            monthDiff += 12;
        }
        while (monthDiff >= 12) {
            clickDatePickerButton(DATE_PICKER_NEXT_YEAR);
            monthDiff -= 12;
        }
        while (monthDiff < 0) {
            clickDatePickerButton(DATE_PICKER_PREV_MONTH);
            monthDiff++;
        }
        while (monthDiff > 0) {
            clickDatePickerButton(DATE_PICKER_NEXT_MONTH);
            monthDiff--;
        }

        // data-full uses a 0-based month: "2026-2-5" for 05.03.2026.
        String full = target.getYear() + "-" + (target.getMonthValue() - 1) + "-" + target.getDayOfMonth();
        By targetDay = By.cssSelector(".mbsc-cal-day[data-full='" + full + "']");
        // The same date can appear as a hidden "diff" cell in the neighbouring slides,
        // so only the displayed cell of the active month is the real one.
        WebElement day = wait.get().until(d -> d.findElements(targetDay).stream()
                .filter(WebElement::isDisplayed)
                .findFirst()
                .orElse(null));
        LOG.debug("Tapping day cell data-full={} (aria-label='{}')", full, day.getAttribute("aria-label"));
        day.click();

        waitForExchangeResult();
        LOG.debug("Date field '#d' value after picker selection '{}' = '{}'", date,
                driver.findElement(DATE_INPUT).getAttribute("value"));
        return this;
    }

    private void clickDatePickerButton(By locator) {
        wait.get().until(ExpectedConditions.elementToBeClickable(locator)).click();
        // Allow the calendar grid to re-render before the next navigation click.
        DriverWait.sleepQuietly(200);
    }

    /** Returns the date the backend accepted ({@code #d} after the last calculation). */
    @Step("Read the applied exchange date")
    public String readAppliedDate() {
        wait.get().until(d -> {
            String v = d.findElement(DATE_INPUT).getAttribute("value");
            return v != null && !v.trim().isEmpty();
        });
        return driver.findElement(DATE_INPUT).getAttribute("value");
    }

    /**
     * Text of the {@code #dateVal} label ("Tecaj na dan:" ... ). The page JS sets
     * it to {@code &nbsp; + $('#d').val()}, so it mirrors the date selected in the
     * picker. The leading non-breaking space is trimmed before returning.
     */
    @Step("Read the displayed date label (#dateVal)")
    public String readSelectedDateLabel() {
        wait.get().until(ExpectedConditions.visibilityOfElementLocated(DATE_LABEL));
        String text = driver.findElement(DATE_LABEL).getText().replace("\u00A0", " ").trim();
        LOG.debug("Date label #dateVal = '{}'", text);
        return text;
    }

    /** Raw (unparsed) text of the amount input -- used to assert NaN handling. */
    @Step("Read the amount input value")
    public String readAmountInputValue() {
        WebElement input = driver.findElement(AMOUNT_INPUT);
        String v = input.getAttribute("value");
        LOG.debug("Amount field '#suma1' raw value = '{}'", v);
        return v;
    }

    /** True when the "Za efektivu" section is visible (Kupovni/Prodajni modes). */
    public boolean isEffectiveSectionVisible() {
        WebElement section = driver.findElement(EFFECTIVE_SECTION);
        return section.isDisplayed();
    }

    /** Current value (currency id) of the from-currency select ({@code #val1}). */
    @Step("Read selected from-currency")
    public String readSelectedFromCurrency() {
        WebElement element = wait.get().until(ExpectedConditions.presenceOfElementLocated(FROM_CURRENCY));
        return new Select(element).getFirstSelectedOption().getAttribute("value");
    }

    /** Current value (currency id) of the to-currency select ({@code #val2}). */
    @Step("Read selected to-currency")
    public String readSelectedToCurrency() {
        WebElement element = wait.get().until(ExpectedConditions.presenceOfElementLocated(TO_CURRENCY));
        return new Select(element).getFirstSelectedOption().getAttribute("value");
    }

    /**
     * Types arbitrary text into the amount field (no blur) so the page's own
     * keyup formatting kicks in. Used to verify the NaN display behaviour.
     */
    @Step("Type text {text} into the amount field")
    public ExchangeCalculatorPage typeAmountText(String text) {
        WebElement input = wait.get().until(ExpectedConditions.visibilityOfElementLocated(AMOUNT_INPUT));
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        input.sendKeys(text);
        return this;
    }

    private void waitForExchangeResult() {
        // The converted field is populated by a debounced (~300 ms) AJAX call;
        // wait until its value stabilises instead of using a fixed sleep.
        WaitStrategy.waitForValueStable(driver, RESULT_AMOUNT, 400L);
        // Harden against the very last millisecond of a concurrent AJAX update.
        WaitStrategy.waitForValueStable(driver, EXCHANGE_RATE, 200L);
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
