package techrba.error;

/**
 * Thrown when the configured browser is unknown or cannot be resolved
 * (e.g. an unsupported value in {@code browser=...}). Failing fast with a
 * clear message prevents silently running tests against the wrong browser.
 */
public class UnsupportedBrowserException extends RuntimeException {

    public UnsupportedBrowserException(String message) {
        super(message);
    }

    public UnsupportedBrowserException(String message, Throwable cause) {
        super(message, cause);
    }
}
