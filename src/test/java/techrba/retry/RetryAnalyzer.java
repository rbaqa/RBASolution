package techrba.retry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries flaky tests (e.g. Selenium UI tests) a configurable number of times
 * to guard against unstable environment/browser issues without masking real
 * defects. A test that fails on every retry is still reported as failed.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LogManager.getLogger(RetryAnalyzer.class);
    private static final int MAX_RETRIES = 2;

    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            LOG.warn("Retrying test '{}' (attempt {}/{}) after failure: {}",
                    result.getName(), retryCount, MAX_RETRIES, result.getThrowable() == null
                            ? "unknown" : result.getThrowable().getMessage());
            return true;
        }
        return false;
    }
}
