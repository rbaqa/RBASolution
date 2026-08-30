package techrba.listener;

import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import techrba.base.BaseTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central TestNG listener capturing rich reporting on test lifecycle:
 * <ul>
 *   <li>Logs each test outcome at INFO/ERROR level</li>
 *   <li>Captures a browser screenshot on failure and attaches it to Allure + disk</li>
 *   <li>Embeds the screenshot as an Allure attachment for the HTML report</li>
 * </ul>
 */
public class TestListener implements ITestListener {

    private static final Logger LOG = LogManager.getLogger(TestListener.class);
    private static final String SCREENSHOT_DIR = "reports/screenshots";

    @Override
    public void onTestStart(ITestResult result) {
        LOG.info("TEST START: {}.{}", result.getTestClass().getName(), result.getName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOG.info("TEST PASS: {}.{}", result.getTestClass().getName(), result.getName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOG.error("TEST FAIL: {}.{} -> {}", result.getTestClass().getName(),
                result.getName(),
                result.getThrowable() == null ? "" : result.getThrowable().getMessage());
        captureScreenshot(result.getName());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOG.warn("TEST SKIP: {}.{}", result.getTestClass().getName(), result.getName());
    }

    @Override
    public void onStart(ITestContext context) {
        LOG.info("SUITE START: {}", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        LOG.info("SUITE FINISH: {} (total={}, passed={}, failed={}, skipped={})",
                context.getName(),
                context.getAllTestMethods().length,
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
    }

    /**
     * Attempts to take a screenshot of the active WebDriver (thread-scoped).
     * If no driver is available (pure API test) it is silently skipped.
     */
    public static void captureScreenshot(String testName) {
        WebDriver driver = BaseTest.getDriverForReporting();
        if (driver instanceof TakesScreenshot) {
            try {
                byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Allure.addAttachment("screenshot-" + testName, "image/png",
                        new ByteArrayInputStream(bytes), "png");
                Path dir = Paths.get(SCREENSHOT_DIR);
                Files.createDirectories(dir);
                Path file = dir.resolve(sanitize(testName) + ".png");
                Files.write(file, bytes);
                LOG.info("Screenshot saved to {}", file.toAbsolutePath());
            } catch (Exception e) {
                LOG.warn("Failed to capture screenshot for {}: {}", testName, e.getMessage());
            }
        }
    }

    private static String sanitize(String name) {
        return name == null ? "unknown" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
