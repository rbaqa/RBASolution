package techrba.api;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import techrba.config.ConfigManager;
import techrba.util.DecimalParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

/**
 * Postman / REST task - Basic REST.
 *
 * <p>Replicates the Selenium exchange calculator example using a pure REST
 * call to the RBA {@code calculateExchangeRate} endpoint, i.e. the same
 * backend invoked by the UI calculator. Two transactions are verified, the
 * same ones driven from Selenium: buy GBP (kupnja funti) and sell USD
 * (prodaja dolara). For each, the exchange rate and the final amount are
 * read and asserted for internal consistency.</p>
 */
public class ExchangeRateApiTest {

    private static final String CALC_RESOURCE_URL = "https://www.rba.hr/alati/tecajni-kalkulator";
    private static final int RESPONSE_TIME_LIMIT_MS = 5000;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = CALC_RESOURCE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test(groups = {"api", "exchange"})
    @Description("Buy GBP via REST - read exchange rate and final amount")
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

    @Test(groups = {"api", "exchange"})
    @Description("Sell USD via REST - read exchange rate and final amount")
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
        JsonPath json = given()
                .queryParam("p_p_id", "tecajKalkulator_WAR_calculatorsportlet")
                .queryParam("p_p_lifecycle", "2")
                .queryParam("p_p_state", "normal")
                .queryParam("p_p_mode", "view")
                .queryParam("p_p_resource_id", "calculateExchangeRate")
                .queryParam("p_p_cacheability", "cacheLevelPage")
                .queryParam("p_p_col_id", "column-4")
                .queryParam("p_p_col_count", "1")
                .contentType(ContentType.URLENC)
                .formParam("source", source)
                .formParam("currency1Id", c1)
                .formParam("currency1Ammount", amount)
                .formParam("currency2Id", c2)
                .formParam("currency2Ammount", "")
                .formParam("date", java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .formParam("type", mode)
                .when()
                .post()
                .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Exchange REST response time = " + elapsed + " ms");
        Assert.assertTrue(elapsed < RESPONSE_TIME_LIMIT_MS,
                "Response time " + elapsed + " ms exceeds limit " + RESPONSE_TIME_LIMIT_MS + " ms");

        BigDecimal rate = DecimalParser.parse(String.valueOf(json.getString("form.exchangeRate")));
        BigDecimal amountConverted = DecimalParser.parse(String.valueOf(json.getString("form.currency2Ammount")));
        return new ExchangeResult(rate, amountConverted);
    }

    private void assertConsistency(String label, int inputAmount, ExchangeResult result) {
        Assert.assertTrue(result.rate.compareTo(BigDecimal.ZERO) > 0, label + ": rate must be positive");
        Assert.assertTrue(result.amount.compareTo(BigDecimal.ZERO) > 0, label + ": amount must be positive");
        BigDecimal expected = result.rate.multiply(BigDecimal.valueOf(inputAmount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = expected.subtract(result.amount).abs().setScale(2, RoundingMode.HALF_UP);
        Assert.assertTrue(diff.compareTo(BigDecimal.valueOf(0.05)) <= 0,
                label + ": amount " + result.amount + " inconsistent with rate " + result.rate
                        + " * " + inputAmount + " (expected " + expected + ")");
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
