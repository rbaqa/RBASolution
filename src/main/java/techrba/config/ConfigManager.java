package techrba.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Central configuration manager (12-factor style).
 *
 * <p>Loads the base configuration from {@code config/test-config.properties} and
 * allows every property to be overridden via:
 * <ul>
 *   <li>Environment variable: {@code ENV_<key dots replaced by underscores in UPPER>}</li>
 *   <li>System property ({@code -Dkey=value})</li>
 * </ul>
 * Precedence (lowest -&gt; highest): file &lt; environment &lt; system property.
 */
public final class ConfigManager {

    private static final Logger LOG = LogManager.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "config/test-config.properties";
    private static final String ENV_PREFIX = "ENV_";
    private static final String PROFILE_SYSTEM_PROP = "env";
    private static final String PROFILE_ENV_VAR = "TEST_ENV";

    private static final Properties PROPS = new Properties();
    private static volatile boolean loaded;

    private ConfigManager() {
        // static utility
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        // 1) Prefer an external, editable file (12-factor): config/test-config.properties
        boolean externalLoaded = false;
        Path external = Paths.get(CONFIG_FILE);
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                PROPS.load(in);
                externalLoaded = true;
                LOG.info("Loaded external configuration: {}", external.toAbsolutePath());
            } catch (IOException e) {
                LOG.warn("Failed to load external configuration {}: {}", external.toAbsolutePath(), e.getMessage());
            }
        }
        // 2) Fall back to the classpath copy
        if (!externalLoaded) {
            try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (in != null) {
                    PROPS.load(in);
                    LOG.info("Loaded configuration from classpath: {}", CONFIG_FILE);
                } else {
                    LOG.warn("Configuration file {} not found on classpath", CONFIG_FILE);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load configuration file " + CONFIG_FILE, e);
            }
        }
        // 3) Environment profile overlay (12-factor): -Denv=qa (or TEST_ENV=qa)
        //    loads config/test-config-<profile>.properties on top of the base file.
        String profile = profileName();
        if (profile != null && !profile.isEmpty()) {
            loadProfile(profile);
        }
        loaded = true;
    }

    /** Active environment profile from -Denv=... or the TEST_ENV env var (may be empty). */
    private static String profileName() {
        String fromProp = System.getProperty(PROFILE_SYSTEM_PROP);
        if (fromProp != null && !fromProp.trim().isEmpty()) {
            return fromProp.trim();
        }
        String fromEnv = System.getenv(PROFILE_ENV_VAR);
        return fromEnv == null ? "" : fromEnv.trim();
    }

    private static void loadProfile(String profile) {
        String fileName = "config/test-config-" + profile + ".properties";
        // external first, then classpath
        Path external = Paths.get(fileName);
        if (Files.exists(external)) {
            loadInto(external.toAbsolutePath().toString(), () -> Files.newInputStream(external),
                    "environment profile (external)");
            return;
        }
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in != null) {
                loadInto(fileName, () -> in, "environment profile (classpath)");
            } else {
                LOG.warn("Environment profile file '{}' not found - continuing with base config only", fileName);
            }
        } catch (IOException e) {
            LOG.warn("Failed to load profile '{}': {}", fileName, e.getMessage());
        }
    }

    private static void loadInto(String sourceName, InputStreamSupplier supplier, String kind) {
        try (InputStream in = supplier.get()) {
            Properties overlay = new Properties();
            overlay.load(in);
            PROPS.putAll(overlay);
            LOG.info("Loaded {}: {}", kind, sourceName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + kind + " " + sourceName, e);
        }
    }

    /** Small functional interface to defer opening the stream (avoid double-open issues). */
    private interface InputStreamSupplier {
        InputStream get() throws IOException;
    }

    public static String get(String key) {
        ensureLoaded();
        String system = System.getProperty(key);
        if (system != null) {
            return system;
        }
        String env = System.getenv(envName(key));
        if (env != null) {
            return env;
        }
        return PROPS.getProperty(key);
    }

    public static String getString(String key) {
        return get(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(getRequired(key));
    }

    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(getRequired(key));
    }

    public static String getRequired(String key) {
        String value = get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required configuration property: " + key);
        }
        return value;
    }

    private static String envName(String key) {
        // key "app.base.url" -> "ENV_APP_BASE_URL"
        return ENV_PREFIX + key.replace('.', '_').toUpperCase();
    }

    /** Getter used to make imported values obvious for Sonar/readability. */
    public static String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
