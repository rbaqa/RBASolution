package techrba.listener;

import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import techrba.annotation.Requirement;
import techrba.config.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prints a compact "QA dashboard" summary of the whole run once the suite
 * finishes: one row per test with class/method, linked requirements (from the
 * {@link Requirement} annotation) and PASS/FAIL/SKIP status, plus timing.
 * The same table is written to {@code test.summary.file} so it can be captured
 * as a CI artefact.
 */
public class TestSummaryListener implements ITestListener {

    private static final Logger LOG = LogManager.getLogger(TestSummaryListener.class);

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void onTestStart(ITestResult result) {
        // nothing to do
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        record(result, "PASS");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        record(result, "FAIL");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        record(result, "SKIP");
    }

    private void record(ITestResult result, String status) {
        rows.add(new Row(
                result.getTestClass().getName(),
                result.getName(),
                status,
                result.getEndMillis() - result.getStartMillis(),
                requirementsOf(result)));
    }

    private static String requirementsOf(ITestResult result) {
        Requirement req = result.getMethod().getConstructorOrMethod().getMethod()
                .getAnnotation(Requirement.class);
        if (req == null || req.value().length == 0) {
            return "-";
        }
        return String.join(",", Arrays.asList(req.value()));
    }

    @Override
    public void onFinish(ITestContext context) {
        long total = context.getAllTestMethods().length;
        long passed = context.getPassedTests().size();
        long failed = context.getFailedTests().size();
        long skipped = context.getSkippedTests().size();

        StringBuilder sb = new StringBuilder();
        sb.append("\n============== TEST SUMMARY (QA DASHBOARD) ===============\n");
        String header = String.format("%-18s %-6s %-10s %-14s",
                "Test", "Status", "Ms", "Requirements");
        sb.append(header).append("\n");
        sb.append("-------------------------------------------------------------\n");
        int i = 1;
        for (Row r : rows) {
            sb.append(String.format("%-2d %-16.16s %-6s %-10d %-14s",
                    i++, r.name, r.status, r.durationMs, r.requirements)).append("\n");
        }
        sb.append("-------------------------------------------------------------\n");
        sb.append(String.format("TOTAL: %d | PASS: %d | FAIL: %d | SKIP: %d%n",
                total, passed, failed, skipped));
        sb.append("=============================================================\n");

        System.out.println(sb);
        writeToFile(sb.toString());

        // Attach to Allure report as a plain text attachment for traceability
        Allure.addAttachment("Test Summary (QA Dashboard)", sb.toString());
    }

    private void writeToFile(String content) {
        String file = ConfigManager.getOrDefault("test.summary.file", "reports/test-summary.txt");
        try {
            Path path = Paths.get(file);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            LOG.info("Test summary written to {}", path.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not write test summary to {}: {}", file, e.getMessage());
        }
    }

    /** One summary row. */
    private static final class Row {
        final String className;
        final String name;
        final String status;
        final long durationMs;
        final String requirements;

        Row(String className, String name, String status, long durationMs, String requirements) {
            this.className = className;
            this.name = className + "." + name;
            this.status = status;
            this.durationMs = durationMs;
            this.requirements = requirements;
        }
    }
}
