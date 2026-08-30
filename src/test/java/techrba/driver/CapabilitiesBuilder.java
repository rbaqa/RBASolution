package techrba.driver;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import techrba.config.ConfigManager;

/**
 * Builds browser capabilities (options) from configuration, so all browser
 * tuning lives in one place. Consumes {@code browser.headless} and a common set
 * of arguments for CI stability. Each browser type has its own builder but they
 * share the common argument set.
 */
public final class CapabilitiesBuilder {

    private final boolean headless;

    public CapabilitiesBuilder() {
        this.headless = ConfigManager.getBoolean("browser.headless");
    }

    public ChromeOptions chrome() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(commonArguments());
        return options;
    }

    public FirefoxOptions firefox() {
        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            // FirefoxOptions has a dedicated headless flag (newer Selenium 4)
            options.addArguments("-headless");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--lang", "en");
        return options;
    }

    public EdgeOptions edge() {
        EdgeOptions options = new EdgeOptions();
        options.addArguments(commonArguments());
        return options;
    }

    private java.util.List<String> commonArguments() {
        java.util.List<String> args = new java.util.ArrayList<>();
        if (headless) {
            args.add("--headless=new");
        }
        args.add("--no-sandbox");
        args.add("--disable-dev-shm-usage");
        // English UI for deterministic text-based assertions
        args.add("--lang=en");
        return args;
    }
}
