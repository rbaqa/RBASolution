package techrba.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes the Allure "companion" files into the Allure results directory so the
 * generated HTML report carries useful context:
 * <ul>
 *   <li>{@code environment.properties} - OS, JDK, browser, CI run and profile
 *       recorded for whatever executed (pure API runs report no browser)</li>
 *   <li>{@code categories.json} - classifies failures into product defect,
 *       browser/driver problem, external network issue etc. so the report
 *       overview page stays readable instead of a flat list of "failed"</li>
 * </ul>
 * The files are written on every {@code onFinish}; the last write wins, which
 * makes the content race-free regardless of suite parallelism.
 */
public final class AllureReporting {

    private static final Logger LOG = LogManager.getLogger(AllureReporting.class);
    private static final Path RESULTS_DIR = Paths.get("reports/allure-results");

    private static volatile String browserName;
    private static volatile String browserVersion;

    private AllureReporting() {
        // static utility
    }

    /** Captures browser identity from the driver capabilities (set per driver creation). */
    public static void recordBrowser(Capabilities capabilities) {
        if (capabilities == null) {
            return;
        }
        try {
            browserName = capabilities.getBrowserName();
            browserVersion = capabilities.getBrowserVersion();
        } catch (RuntimeException e) {
            LOG.debug("Could not read browser capabilities: {}", e.getMessage());
        }
    }

    /** Writes (overwrites) both companion files into the Allure results directory. */
    public static void writeCompanionFiles() {
        writeEnvironmentProperties();
        writeCategoriesJson();
    }

    private static void writeEnvironmentProperties() {
        StringBuilder sb = new StringBuilder();
        append(sb, "Browser", nullToNa(browserName));
        append(sb, "Browser.Version", nullToNa(browserVersion));
        append(sb, "OS.Name", System.getProperty("os.name", "n/a"));
        append(sb, "OS.Version", System.getProperty("os.version", "n/a"));
        append(sb, "OS.Arch", System.getProperty("os.arch", "n/a"));
        append(sb, "Java.Version", System.getProperty("java.version", "n/a"));
        append(sb, "Java.TargetBytecode", "8");
        append(sb, "TestNG.Version", "7.5.1");
        append(sb, "Allure.Version", "2.24.0");
        append(sb, "App.Under.Test", "https://www.rba.hr");
        append(sb, "Test.Profile", testProfile());
        append(sb, "Git.Branch", envOrNa("GITHUB_REF_NAME"));
        append(sb, "Git.Sha", envOrNa("GITHUB_SHA"));
        append(sb, "CI.Run.URL", ciRunUrl());

        write(sb.toString(), "environment.properties");
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value).append(System.lineSeparator());
    }

    private static String testProfile() {
        String profile = System.getProperty("env");
        if (profile == null || profile.isEmpty()) {
            profile = System.getenv("TEST_ENV");
        }
        boolean stub = "true".equalsIgnoreCase(System.getProperty("api.stub"))
                || "true".equalsIgnoreCase(System.getenv("API_STUB"));
        return stub ? "stub (wiremock)" : (profile == null || profile.isEmpty() ? "live" : profile);
    }

    private static String ciRunUrl() {
        String server = System.getenv("GITHUB_SERVER_URL");
        String repo = System.getenv("GITHUB_REPOSITORY");
        String runId = System.getenv("GITHUB_RUN_ID");
        if (server != null && repo != null && runId != null) {
            return server + "/" + repo + "/actions/runs/" + runId;
        }
        return "local run";
    }

    private static String envOrNa(String key) {
        String v = System.getenv(key);
        return v == null ? "n/a" : v;
    }

    private static String nullToNa(String v) {
        return v == null ? "n/a" : v;
    }

    private static final String CATEGORIES_JSON = "[\n"
            + "  {\n"
            + "    \"name\": \"Product defect\",\n"
            + "    \"matchedStatuses\": [\"failed\"],\n"
            + "    \"messageRegex\": \".*(AssertionError|expected but was|should be|must be).*\"\n"
            + "  },\n"
            + "  {\n"
            + "    \"name\": \"Browser / WebDriver problem\",\n"
            + "    \"matchedStatuses\": [\"broken\"],\n"
            + "    \"messageRegex\": \".*(NoSuchElementException|ElementNotInteractableException|StaleElementReferenceException|TimeoutException|WebDriverException|SessionNotCreatedException).*\"\n"
            + "  },\n"
            + "  {\n"
            + "    \"name\": \"External service / network failure\",\n"
            + "    \"matchedStatuses\": [\"broken\"],\n"
            + "    \"messageRegex\": \".*(ConnectException|UnknownHostException|SocketTimeoutException|Read timed out|connection refused).*\"\n"
            + "  },\n"
            + "  {\n"
            + "    \"name\": \"Broken test\",\n"
            + "    \"matchedStatuses\": [\"broken\"],\n"
            + "    \"messageRegex\": \".*(NullPointerException|ClassCastException|IllegalArgumentException|StringIndexOutOfBoundsException).*\"\n"
            + "  },\n"
            + "  {\n"
            + "    \"name\": \"Ignored / skipped\",\n"
            + "    \"matchedStatuses\": [\"skipped\"]\n"
            + "  },\n"
            + "  {\n"
            + "    \"name\": \"Other failure\",\n"
            + "    \"matchedStatuses\": [\"failed\", \"broken\"]\n"
            + "  }\n"
            + "]";

    private static void writeCategoriesJson() {
        write(CATEGORIES_JSON, "categories.json");
    }

    private static void write(String content, String fileName) {
        try {
            Files.createDirectories(RESULTS_DIR);
            Path file = RESULTS_DIR.resolve(fileName);
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            LOG.info("Wrote Allure companion file {}", file.toAbsolutePath());
        } catch (IOException e) {
            LOG.warn("Could not write Allure companion file {}: {}", fileName, e.getMessage());
        }
    }
}