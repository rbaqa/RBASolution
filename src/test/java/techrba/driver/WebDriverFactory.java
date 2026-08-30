package techrba.driver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import techrba.config.ConfigManager;

import java.net.URL;
import java.time.Duration;

/**
 * Central WebDriver factory (Strategy pattern).
 *
 * <p>Resolves the browser (local or remote on a Selenium Grid / cloud via
 * {@code remote.url}) from configuration and returns a ready-to-use
 * {@link WebDriver} with the agreed implicit/page-load timeouts applied.
 * Callers never construct drivers directly, which keeps browser setup in one
 * place and makes multi-browser + grid execution trivial.</p>
 */
public final class WebDriverFactory {

    private static final Logger LOG = LogManager.getLogger(WebDriverFactory.class);

    private WebDriverFactory() {
        // static factory
    }

    /**
     * Creates a WebDriver for the configured browser. If {@code remote.url} is
     * set, a {@code RemoteWebDriver} is started against that Selenium Grid /
     * cloud endpoint; otherwise a local driver is used.
     */
    public static WebDriver createDriver() {
        DriverType type = DriverType.fromConfig();
        Options options = new Options();

        URL remote = DriverType.remoteUrlOrNull();
        WebDriver driver;
        if (remote != null) {
            LOG.info("Starting {} driver on remote grid: {}", type, remote);
            driver = type.createRemote(remote, options);
        } else {
            LOG.info("Starting local {} driver", type);
            driver = type.createLocal(options);
        }

        applyTimeouts(driver);
        return driver;
    }

    private static void applyTimeouts(WebDriver driver) {
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(
                Duration.ofSeconds(ConfigManager.getInt("selenium.implicit.timeout.seconds")));
        driver.manage().timeouts().pageLoadTimeout(
                Duration.ofSeconds(ConfigManager.getInt("selenium.page.load.timeout.seconds")));
    }

    /** Pre-built capabilities for each supported browser (see {@link CapabilitiesBuilder}). */
    public static final class Options {
        private final CapabilitiesBuilder builder = new CapabilitiesBuilder();

        public ChromeOptions chrome() {
            return builder.chrome();
        }

        public FirefoxOptions firefox() {
            return builder.firefox();
        }

        public EdgeOptions edge() {
            return builder.edge();
        }
    }
}
