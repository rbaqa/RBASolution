package techrba.base;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import techrba.config.ConfigManager;

import java.time.Duration;

/**
 * Shared Selenium wait the Page Objects and tests rely on. Centralises the
 * explicit-wait timeout (from configuration) and small sleep helpers so there
 * is a single source of truth for wait behaviour.
 */
public final class DriverWait {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public DriverWait(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver,
                Duration.ofSeconds(ConfigManager.getInt("selenium.explicit.timeout.seconds")));
    }

    public WebDriverWait get() {
        return wait;
    }

    public WebDriver driver() {
        return driver;
    }

    /** Waits until the value of the given field is populated (non-empty, non-"0"). */
    public void waitForNonNullValue(By locator) {
        wait.until(d -> {
            String v = d.findElement(locator).getAttribute("value");
            return v != null && !v.trim().isEmpty() && !"0".equals(v.trim());
        });
    }

    public static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
