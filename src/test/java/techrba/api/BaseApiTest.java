package techrba.api;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.BeforeClass;

import techrba.config.ConfigManager;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import static io.restassured.RestAssured.given;

/**
 * Base class for API/REST tests. Centralises the RestAssured {@link RequestSpecification}
 * (base URI, media types, HTTP logging) so individual tests only describe their specific
 * endpoint and assertions.
 *
 * <p>HTTP-level logging is enabled here via {@link RequestLoggingFilter} and
 * {@link ResponseLoggingFilter}, whose output is routed through a custom
 * {@link PrintStream} into the Log4j2 logger {@code techrba.api.HttpLog}. Whether every
 * exchange or only failing ones are printed is controlled by {@code api.http.log.level}
 * ({@code ALL} vs. {@code BASELINE}). This gives a single switch to troubleshoot any API
 * test with the same timestamped log output as the rest of the suite.</p>
 */
public abstract class BaseApiTest {

    protected static final Logger LOG = LogManager.getLogger(BaseApiTest.class);

    /** RestAssured spec that all API tests build on (set per-class in subclasses). */
    protected RequestSpecification spec;

    @BeforeClass
    public void initBaseApi() {
        this.spec = buildDefaultSpec();
    }

    protected RequestSpecification buildDefaultSpec() {
        // Route RestAssured HTTP logging into the Log4j2 "techrba.api.HttpLog" logger.
        Logger httpLog = LogManager.getLogger("techrba.api.HttpLog");
        PrintStream logStream = asUtf8Stream(new Log4j2OutputStream(httpLog));

        RequestSpecBuilder builder = new RequestSpecBuilder();
        String level = ConfigManager.getOrDefault("api.http.log.level", "BASELINE").toUpperCase();

        if ("ALL".equals(level)) {
            builder.addFilter(new RequestLoggingFilter(LogDetail.ALL, logStream));
            builder.addFilter(new ResponseLoggingFilter(LogDetail.ALL, logStream));
        } else {
            // BASELINE: only print request/response when validation would fail.
            builder.addFilter(new RequestLoggingFilter(LogDetail.METHOD, logStream));
            builder.addFilter(new ResponseLoggingFilter(LogDetail.STATUS, logStream));
            builder.setConfig(io.restassured.config.RestAssuredConfig.config()
                    .logConfig(io.restassured.config.LogConfig.logConfig()
                            .enableLoggingOfRequestAndResponseIfValidationFails()));
        }
        return builder.build();
    }

    private static PrintStream asUtf8Stream(OutputStream out) {
        try {
            return new PrintStream(out, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported (should never happen)", e);
        }
    }

    protected static RequestSpecification givenSpec() {
        return given();
    }

    /**
     * Bridges bytes written by RestAssured's logging filters into a Log4j2 logger,
     * keeping all HTTP traffic in the unified, timestamped application log.
     */
    private static final class Log4j2OutputStream extends OutputStream {
        private final Logger logger;
        private final StringBuilder line = new StringBuilder();

        Log4j2OutputStream(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                flushLine();
            } else {
                line.append((char) b);
            }
        }

        @Override
        public void flush() {
            if (line.length() > 0) {
                flushLine();
            }
        }

        private void flushLine() {
            String text = line.toString();
            if (!text.isEmpty()) {
                logger.info(text);
            }
            line.setLength(0);
        }
    }
}
