package techrba.driver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import techrba.config.ConfigManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the major version of the locally installed Google Chrome so that
 * WebDriverManager can download a ChromeDriver matching the actual browser,
 * rather than the newest stable release.
 *
 * <p>On CI, Chrome can lag the newest published stable release; WebDriverManager
 * then resolves a newer ChromeDriver and session creation fails with
 * {@code "This version of ChromeDriver only supports Chrome version X"}.</p>
 */
final class ChromeVersion {

    private static final Logger LOG = LogManager.getLogger(ChromeVersion.class);

    private ChromeVersion() {
        // static utility
    }

    /**
     * Major Chrome version (e.g. {@code "151"}) of the installed browser, or
     * {@code null} when it cannot be determined (WebDriverManager is left to
     * its own resolution). An explicit {@code browser.chromedriver.version}
     * (config, env {@code ENV_BROWSER_CHROMEDRIVER_VERSION} or
     * {@code -D}) always takes precedence.
     */
    static String matchingMajor() {
        String override = ConfigManager.getOrDefault("browser.chromedriver.version", "").trim();
        if (!override.isEmpty()) {
            LOG.info("ChromeDriver pinned to Chrome {} (browser.chromedriver.version)", firstMajor(override));
            return firstMajor(override);
        }
        String installed = detectInstalled();
        if (installed == null) {
            LOG.warn("Could not detect the installed Chrome version; WebDriverManager will resolve ChromeDriver on its own");
            return null;
        }
        String major = firstMajor(installed);
        LOG.info("Detected installed Chrome {} -> matching ChromeDriver to major {}", installed, major);
        return major;
    }

    private static String firstMajor(String version) {
        int dot = version.indexOf('.');
        return dot > 0 ? version.substring(0, dot) : version;
    }

    private static String detectInstalled() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String fileVersion = runForFirstLine("powershell",
                    "-NoProfile", "-NonInteractive",
                    "-Command", "(Get-Item 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe').VersionInfo.ProductVersion");
            if (isPlausibleVersion(fileVersion)) {
                return fileVersion;
            }
            // Fallback: the Google update beacon key (not always present).
            String reg = runForFirstLine("reg", "query", "HKLM\\SOFTWARE\\Google\\Chrome\\BLBeacon", "/v", "version");
            if (reg != null && reg.contains("REG_SZ")) {
                String[] parts = reg.split("\\s+");
                String version = parts[parts.length - 1];
                if (isPlausibleVersion(version)) {
                    return version;
                }
            }
            return null;
        }
        // Linux (macOS can be added similarly using the Info.plist version).
        for (String binary : new String[]{"google-chrome", "google-chrome-stable", "chromium-browser"}) {
            String line = runForFirstLine(binary, "--version");
            if (line != null && line.trim().matches(".*[\\d.]+$")) {
                String[] tokens = line.trim().split("\\s+");
                String version = tokens[tokens.length - 1];
                if (isPlausibleVersion(version)) {
                    return version;
                }
            }
        }
        return null;
    }

    private static boolean isPlausibleVersion(String version) {
        return version != null && version.matches("[1-9]\\d*(\\.\\d+){1,3}");
    }

    private static String runForFirstLine(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        return line.trim();
                    }
                }
            }
            return null;
        } catch (IOException e) {
            LOG.debug("Chrome version detection command failed: {} - {}", String.join(" ", command), e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}