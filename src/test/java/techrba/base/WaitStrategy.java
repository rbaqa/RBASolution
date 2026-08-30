package techrba.base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import techrba.config.ConfigManager;

/**
 * Wait strategies implemented with native Selenium only (no JavaScript
 * Executor) for verifying AJAX-driven UI state.
 *
 * <p>The key helper, {@link #waitForValueStable(WebDriver, By, long)}, does not
 * rely on executing JavaScript to poll XHRs. Instead it observes the value of
 * a result element until it stops changing over a short observation window -
 * a pure WebDriver/WebDriverWait approach that is safe under the project rule
 * of "Selenium only, no JS executor".</p>
 */
public final class WaitStrategy {

    private static final Logger LOG = LogManager.getLogger(WaitStrategy.class);

    private WaitStrategy() {
        // static utility
    }

    /**
     * Waits until the given element's {@code value} attribute is non-empty and
     * stops changing for at least {@code stableWindowMs} consecutive polls,
     * within the overall configured explicit-wait timeout. Useful after actions
     * that trigger debounced backend calls (e.g. the exchange calculator).
     *
     * @return the final stable value
     */
    public static String waitForValueStable(WebDriver driver, By locator, long stableWindowMs) {
        long deadline = System.currentTimeMillis()
                + ConfigManager.getInt("selenium.explicit.timeout.seconds") * 1000L;
        String previous = null;
        long lastChange = System.currentTimeMillis();

        try {
            while (System.currentTimeMillis() < deadline) {
                String current = readValue(driver, locator);
                if (current == null || current.trim().isEmpty() || "0".equals(current.trim())) {
                    previous = null;
                } else if (!current.equals(previous)) {
                    previous = current;
                    lastChange = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastChange >= stableWindowMs) {
                    LOG.debug("Value stable for field {} -> {}", locator, current);
                    return current;
                }
                Thread.sleep(150);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for stable value at {}", locator);
        }
        throw new WebDriverException("Value of field " + locator + " did not stabilise within "
                + ConfigManager.getInt("selenium.explicit.timeout.seconds") + " s");
    }

    private static String readValue(WebDriver driver, By locator) {
        try {
            return driver.findElement(locator).getAttribute("value");
        } catch (WebDriverException e) {
            return null;
        }
    }
}
