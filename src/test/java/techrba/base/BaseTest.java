package techrba.base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import techrba.config.ConfigManager;
import techrba.driver.WebDriverFactory;
import techrba.util.DateSanity;

/**
 * Base class for UI (Selenium) tests.
 *
 * <p>Provides a thread-safe {@link WebDriver} instance using a
 * {@link ThreadLocal} so tests can safely run in parallel via TestNG.
 * Browser selection and driver creation are delegated to
 * {@link WebDriverFactory} (local or remote Selenium Grid / cloud).</p>
 */
public abstract class BaseTest {

    protected static final Logger LOG = LogManager.getLogger(BaseTest.class);

    /**
     * Thread-local driver so each parallel test thread gets its own browser.
     * Marked volatile to avoid half-initialised visibility across threads.
     */
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    /** Verifies mandatory environment pre-conditions before any test runs. */
    @BeforeSuite(alwaysRun = true)
    public void validateEnvironment() {
        try {
            String url = ConfigManager.getRequired("app.base.url");
            LOG.info("Environment validation OK -> app URL = {}", url);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Environment/pre-requisite validation failed: " + e.getMessage(), e);
        }
        DateSanity.warnIfNonBankingDay();
    }

    @BeforeMethod(alwaysRun = true)
    protected synchronized void setUp() {
        if (DRIVER.get() == null) {
            DRIVER.set(WebDriverFactory.createDriver());
        }
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            driver.quit();
            DRIVER.remove();
        }
    }

    protected WebDriver getDriver() {
        return DRIVER.get();
    }

    /** Static access for listeners/reporting that need the current thread's driver. */
    public static WebDriver getDriverForReporting() {
        return DRIVER.get();
    }
}
