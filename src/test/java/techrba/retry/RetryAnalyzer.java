package techrba.retry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import techrba.config.ConfigManager;
import techrba.listener.TestListener;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Retries flaky tests a configurable number of times to guard against unstable
 * browser/environment issues.
 *
 * <p><b>Scope is intentional:</b> only UI tests tagged with the {@code ui} or
 * {@code flaky} group may be retried. API/unit tests are never retried, so a
 * real backend defect cannot be masked by retries. A test that still fails
 * after all retries is reported as failed.</p>
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LogManager.getLogger(RetryAnalyzer.class);

    private static final Set<String> RETRYABLE_GROUPS =
            new HashSet<>(Arrays.asList("ui", "flaky"));

    private final int maxRetries;
    private int retryCount = 0;

    public RetryAnalyzer() {
        this.maxRetries = ConfigManager.getInt("retry.max.count");
    }

    @Override
    public boolean retry(ITestResult result) {
        if (!isRetryable(result)) {
            return false;
        }
        if (retryCount < maxRetries) {
            retryCount++;
            LOG.warn("Retrying test '{}' (attempt {}/{}) after failure: {}",
                    result.getName(), retryCount, maxRetries, result.getThrowable() == null
                            ? "unknown" : result.getThrowable().getMessage());
            // Capture a screenshot of the failed browser state before retrying
            TestListener.captureScreenshot("retry-" + result.getName() + "-" + retryCount);
            return true;
        }
        return false;
    }

    private boolean isRetryable(ITestResult result) {
        String[] groups = result.getMethod().getGroups();
        for (String g : groups) {
            if (RETRYABLE_GROUPS.contains(g)) {
                return true;
            }
        }
        return false;
    }
}
