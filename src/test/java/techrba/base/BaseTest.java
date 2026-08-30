package techrba.base;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import techrba.config.ConfigManager;

import java.time.Duration;

/**
 * Base class for UI (Selenium) tests.
 *
 * <p>Provides a thread-safe {@link WebDriver} instance using a
 * {@link ThreadLocal} so tests can safely run in parallel via TestNG.
 * WebDriverManager resolves the matching ChromeDriver binary automatically,
 * avoiding hard-coded driver versions. ChromeOptions support headless
 * execution for CI environments.</p>
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
    }

    @BeforeMethod(alwaysRun = true)
    protected synchronized void setUp() {
        if (DRIVER.get() == null) {
            DRIVER.set(createDriver());
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

    /**
     * Builds and configures the ChromeDriver.
     * WebDriverManager downloads/verifies the matching driver binary for the
     * installed Chrome version automatically.
     */
    private WebDriver createDriver() {
        boolean headless = ConfigManager.getBoolean("browser.headless");
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        // English UI for deterministic element text based assertions
        options.addArguments("--lang=en");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driver.manage().timeouts().pageLoadTimeout(
                Duration.ofSeconds(ConfigManager.getInt("selenium.page.load.timeout.seconds")));
        return driver;
    }

    /** Static access for listeners/reporting that need the current thread's driver. */
    public static WebDriver getDriverForReporting() {
        return DRIVER.get();
    }
}
