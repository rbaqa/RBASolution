package techrba.api;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import techrba.annotation.Requirement;
import techrba.config.ConfigManager;
import techrba.util.DecimalParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Postman / REST task - Basic REST.
 *
 * <p>Replicates the Selenium exchange calculator example using a pure REST
 * call to the RBA {@code calculateExchangeRate} endpoint, i.e. the same
 * backend invoked by the UI calculator. Two transactions are verified, the
 * same ones driven from Selenium: buy GBP (kupnja funti) and sell USD
 * (prodaja dolara). For each, the exchange rate and the final amount are
 * read and asserted for internal consistency using SoftAssert.</p>
 */
public class ExchangeRateApiTest extends BaseApiTest {

    private static final String CALC_RESOURCE_URL = "https://www.rba.hr/alati/tecajni-kalkulator";
    private static final int RESPONSE_TIME_LIMIT_MS = 5000;

    /**
     * Deterministic offline tier: when {@code api.stub=true} (e.g. run with
     * {@code -Denv=stub}, see {@code config/test-config-stub.properties}) the
     * postman/REST example is served by a local WireMock server with recorded
     * RBA-style responses instead of the live site, so REST tests run offline,
     * fast and deterministically. Live runs keep {@code CALC_RESOURCE_URL}.
     */
    private static final boolean STUB_MODE = ConfigManager.getBoolean("api.stub");
    private static WireMockServer wireMockServer;

    @Override
    protected io.restassured.specification.RequestSpecification buildDefaultSpec() {
        if (STUB_MODE) {
            ensureStubServerStarted();
        }
        io.restassured.specification.RequestSpecification base = super.buildDefaultSpec();
        base.baseUri(STUB_MODE ? wireMockServer.baseUrl() : CALC_RESOURCE_URL);
        base.contentType(ContentType.URLENC);
        return base;
    }

    @AfterClass(alwaysRun = true)
    public void stopStubServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            LOG.info("Stopped WireMock calendar stub server");
        }
    }

    /** Lazy-start the WireMock server and its recorded response stubs once. */
    private static synchronized void ensureStubServerStarted() {
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(options().dynamicPort());
            wireMockServer.start();
            configureStubs();
            LOG.info("STUB MODE: serving recorded RBA responses from {}",
                    wireMockServer.baseUrl());
        }
    }

    /**
     * Records the RBA-style JSON responses the UI/REST calculator expects
     * ({@code form.exchangeRate}, {@code form.currency2Ammount}) for the two
     * smoke transactions - buy GBP (currency1Id 978) and sell USD (currency1Id
     * 840). Values are mutually consistent: amount = rate * input.
     */
    private static void configureStubs() {
        // Discriminate the two transactions by the sent URL-encoded form body and
        // reply with a recorded RBA-style JSON payload (form.exchangeRate /
        // form.currency2Ammount). WireMock string matchers use a full-string
        // regex, so a plain substring check ('containing') is used.
        wireMockServer.stubFor(post(urlPathEqualTo("/"))
                .withRequestBody(containing("currency1Id=978"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"form\":{\"exchangeRate\":\"0.8327\",\"currency2Ammount\":\"33.31\"}}")));

        wireMockServer.stubFor(post(urlPathEqualTo("/"))
                .withRequestBody(containing("currency1Id=840"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"form\":{\"exchangeRate\":\"0.8314\",\"currency2Ammount\":\"83.14\"}}")));
    }

    @Test(groups = {"api", "exchange", "smoke", "regression"})
    @Description("Buy GBP via REST - read exchange rate and final amount")
    @Requirement({"P1"})
    public void buyGbpViaRest() {
        int amount = ConfigManager.getInt("calc.buy.amount");
        ExchangeResult result = calculateExchange(
                "FIRST",
                ConfigManager.getString("calc.buy.currency1.id"), amount,
                ConfigManager.getString("calc.buy.currency2.id"),
                ConfigManager.getInt("calc.buy.mode"));
        System.out.println("BUY GBP (REST): 1 EUR = " + result.rate + " GBP; " + amount + " EUR = "
                + result.amount + " GBP");
        assertConsistency("BUY GBP (REST)", amount, result);
    }

    @Test(groups = {"api", "exchange", "smoke", "regression"})
    @Description("Sell USD via REST - read exchange rate and final amount")
    @Requirement({"P1"})
    public void sellUsdViaRest() {
        int amount = ConfigManager.getInt("calc.sell.amount");
        ExchangeResult result = calculateExchange(
                "FIRST",
                ConfigManager.getString("calc.sell.currency1.id"), amount,
                ConfigManager.getString("calc.sell.currency2.id"),
                ConfigManager.getInt("calc.sell.mode"));
        System.out.println("SELL USD (REST): 1 USD = " + result.rate + " EUR; " + amount + " USD = "
                + result.amount + " EUR");
        assertConsistency("SELL USD (REST)", amount, result);
    }

    @Step("Calculate exchange: currency {c1} amount {amount} -> currency {c2}, mode {mode}")
    private ExchangeResult calculateExchange(String source, String c1, int amount,
                                             String c2, int mode) {
        long start = System.currentTimeMillis();
        JsonPath json = givenSpec().spec(currentSpec())
                .queryParam("p_p_id", "tecajKalkulator_WAR_calculatorsportlet")
                .queryParam("p_p_lifecycle", "2")
                .queryParam("p_p_state", "normal")
                .queryParam("p_p_mode", "view")
                .queryParam("p_p_resource_id", "calculateExchangeRate")
                .queryParam("p_p_cacheability", "cacheLevelPage")
                .queryParam("p_p_col_id", "column-4")
                .queryParam("p_p_col_count", "1")
                .formParam("source", source)
                .formParam("currency1Id", c1)
                .formParam("currency1Ammount", amount)
                .formParam("currency2Id", c2)
                .formParam("currency2Ammount", "")
                .formParam("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .formParam("type", mode)
                .when()
                .post()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Exchange REST response time = " + elapsed + " ms");
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(elapsed < RESPONSE_TIME_LIMIT_MS,
                "Response time " + elapsed + " ms exceeds limit " + RESPONSE_TIME_LIMIT_MS + " ms");
        soft.assertAll();

        BigDecimal rate = DecimalParser.parse(String.valueOf(json.getString("form.exchangeRate")));
        BigDecimal amountConverted = DecimalParser.parse(String.valueOf(json.getString("form.currency2Ammount")));
        return new ExchangeResult(rate, amountConverted);
    }

    @Step("Assert REST consistency for {label}")
    private void assertConsistency(String label, int inputAmount, ExchangeResult result) {
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(result.rate.compareTo(BigDecimal.ZERO) > 0,
                label + ": rate must be positive, was " + result.rate);
        soft.assertTrue(result.amount.compareTo(BigDecimal.ZERO) > 0,
                label + ": amount must be positive, was " + result.amount);
        BigDecimal expected = result.rate.multiply(BigDecimal.valueOf(inputAmount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = expected.subtract(result.amount).abs().setScale(2, RoundingMode.HALF_UP);
        soft.assertTrue(diff.compareTo(BigDecimal.valueOf(0.05)) <= 0,
                label + ": amount " + result.amount + " inconsistent with rate " + result.rate
                        + " * " + inputAmount + " (expected " + expected + ")");
        soft.assertAll();
    }

    /** Rate + converted amount returned by the RBA backend. */
    private static final class ExchangeResult {
        final BigDecimal rate;
        final BigDecimal amount;

        ExchangeResult(BigDecimal rate, BigDecimal amount) {
            this.rate = rate;
            this.amount = amount;
        }
    }
}
