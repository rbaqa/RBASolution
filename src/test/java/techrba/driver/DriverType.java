package techrba.driver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import io.github.bonigarcia.wdm.WebDriverManager;
import techrba.config.ConfigManager;
import techrba.error.UnsupportedBrowserException;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Supported browser types for the {@link WebDriverFactory}, following a simple
 * Strategy pattern: each entry knows how to produce a local or remote
 * WebDriver instance for its browser.
 */
public enum DriverType {

    CHROME {
        @Override
        public WebDriver createLocal(WebDriverFactory.Options options) {
            WebDriverManager.chromedriver().setup();
            return new ChromeDriver(options.chrome());
        }

        @Override
        public WebDriver createRemote(URL url, WebDriverFactory.Options options) {
            return new RemoteWebDriver(url, options.chrome());
        }
    },

    FIREFOX {
        @Override
        public WebDriver createLocal(WebDriverFactory.Options options) {
            WebDriverManager.firefoxdriver().setup();
            return new FirefoxDriver(options.firefox());
        }

        @Override
        public WebDriver createRemote(URL url, WebDriverFactory.Options options) {
            return new RemoteWebDriver(url, options.firefox());
        }
    },

    EDGE {
        @Override
        public WebDriver createLocal(WebDriverFactory.Options options) {
            WebDriverManager.edgedriver().setup();
            return new EdgeDriver(options.edge());
        }

        @Override
        public WebDriver createRemote(URL url, WebDriverFactory.Options options) {
            return new RemoteWebDriver(url, options.edge());
        }
    };

    /**
     * Resolves the configured browser name (e.g. {@code browser=chrome}) to a
     * {@link DriverType}, robust to casing/whitespace. Unknown values fail
     * fast with a clear message so misconfiguration is visible immediately.
     */
    public static DriverType fromConfig() {
        String name = ConfigManager.getRequired("browser").trim().toUpperCase();
        for (DriverType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new UnsupportedBrowserException(
                "Unsupported browser '" + name + "'. Supported: chrome, firefox, edge.");
    }

    public abstract WebDriver createLocal(WebDriverFactory.Options options);

    public abstract WebDriver createRemote(URL url, WebDriverFactory.Options options);

    /** Resolves the optional Selenium Grid / cloud URL from configuration. */
    static URL remoteUrlOrNull() {
        String url = ConfigManager.getOrDefault("remote.url", "");
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        try {
            return new URL(url.trim());
        } catch (MalformedURLException e) {
            throw new UnsupportedBrowserException("Invalid remote.url value '" + url + "'", e);
        }
    }
}
