package techrba.pages;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import techrba.base.DriverWait;
import techrba.config.ConfigManager;

/**
 * Page Object for the RBA homepage.
 *
 * <p>Encapsulates navigating to the site, dismissing the cookie-consent banner
 * and opening the "Tecajni kalkulator" (which loads in a new browser tab).</p>
 */
public class HomePage {

    private static final Logger LOG = LogManager.getLogger(HomePage.class);

    private static final By CALCULATOR_BUTTON = By.cssSelector("a[href*='tecajni-kalkulator']");
    private static final By COOKIE_ACCEPT_BUTTON = By.id("onetrust-accept-btn-handler");

    private final WebDriver driver;
    private final DriverWait wait;

    public HomePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new DriverWait(driver);
    }

    public HomePage open() {
        String url = ConfigManager.getRequired("app.base.url");
        LOG.info("Opening application URL: {}", url);
        driver.get(url);
        return this;
    }

    /**
     * RBA uses a OneTrust consent banner that overlays the page and can
     * intercept clicks. Accepts cookies when the banner is present and is a
     * no-op otherwise, so the flow is robust on first visits and in CI
     * environments already accepting cookies.
     */
    public HomePage dismissCookieBannerIfPresent() {
        try {
            if (driver.findElements(COOKIE_ACCEPT_BUTTON).size() > 0) {
                wait.get().until(ExpectedConditions.elementToBeClickable(COOKIE_ACCEPT_BUTTON)).click();
                LOG.info("Accepted OneTrust cookie banner");
                DriverWait.sleepQuietly(800);
            }
        } catch (Exception e) {
            LOG.warn("Cookie banner not dismissed (continuing): {}", e.getMessage());
        }
        return this;
    }

    /**
     * Clicks the "Tecajni kalkulator" button and switches to the new tab it opens.
     *
     * @return an {@link ExchangeCalculatorPage} bound to the calculator tab
     */
    public ExchangeCalculatorPage openExchangeCalculator() {
        WebElement btn = wait.get().until(ExpectedConditions.elementToBeClickable(CALCULATOR_BUTTON));
        btn.click();

        // The calculator button opens a new tab (target=_blank)
        String originalWindow = driver.getWindowHandle();
        String newWindow = wait.get().until(d -> d.getWindowHandles().stream()
                .filter(h -> !h.equals(originalWindow))
                .findFirst()
                .orElse(null));
        driver.switchTo().window(newWindow);
        LOG.info("Switched to calculator tab");

        wait.get().until(ExpectedConditions.presenceOfElementLocated(ExchangeCalculatorPage.FROM_CURRENCY_DROPDOWN));
        // Small settle delay for the initial AJAX state (defaults loaded)
        DriverWait.sleepQuietly(1000);
        return new ExchangeCalculatorPage(driver);
    }
}
