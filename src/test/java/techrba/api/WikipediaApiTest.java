package techrba.api;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import techrba.config.ConfigManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * REST / Postman task - Wikipedia basic search.
 *
 * <p>Performs a search for "Raiffeisen" using the MediaWiki API and returns the
 * first 10 results as a JSON object ({@code query.pages}). Validates:
 * <ul>
 *   <li>response code is 200</li>
 *   <li>response time is below 5 seconds</li>
 *   <li>response contains a 'pages' object</li>
 *   <li>some page contains key 'title' with value 'Raiffeisen Bank International'</li>
 * </ul>
 * A JSON Schema validation and a light performance run are included as extras.</p>
 */
public class WikipediaApiTest {

    // Wikipedia rejects default HTTP client user agents with HTTP 403
    private static final String USER_AGENT =
            "RBA-QA-Automation/1.0 (Senior QA automation test; salesforce-tooling@example.com)";

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = ConfigManager.getRequired("wikipedia.api.url");
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static io.restassured.specification.RequestSpecification request() {
        return given().header("User-Agent", USER_AGENT);
    }

    @Test(groups = {"api", "wikipedia"})
    @Description("Wikipedia search for 'Raiffeisen' - validate 200, response time, pages object")
    public void wikipediaSearchReturnsExpectedStructureAndTitle() {
        Response response = request()
                .queryParam("action", "query")
                .queryParam("generator", "search")
                .queryParam("gsrsearch", ConfigManager.getRequired("wikipedia.search.query"))
                .queryParam("gsrlimit", ConfigManager.getInt("wikipedia.first.results"))
                .queryParam("prop", "info")
                .queryParam("format", "json")
                .when()
                .get()
                .then()
                .log().ifValidationFails()
                // 1) response code 200
                .statusCode(200)
                .contentType(ContentType.JSON)
                // 3) response contains a 'pages' object
                .body("query.pages", Matchers.notNullValue())
                .extract()
                .response();

        // 2) response time below 5 seconds
        long elapsed = response.timeIn(TimeUnit.MILLISECONDS);
        long limitMs = ConfigManager.getInt("wikipedia.max.seconds") * 1000;
        System.out.println("Wikipedia response time = " + elapsed + " ms (limit " + limitMs + " ms)");
        Assert.assertTrue(elapsed < limitMs,
                "Response time " + elapsed + " ms exceeds the " + ConfigManager.getInt("wikipedia.max.seconds")
                        + " s limit");

        // 4) some page contains key 'title' with value 'Raiffeisen Bank International'
        JsonPath json = response.jsonPath();
        Map<String, Object> pages = json.getMap("query.pages");
        System.out.println("Wikipedia returned " + pages.size() + " pages");
        boolean found = pages.values().stream()
                .map(v -> (Map<String, Object>) v)
                .anyMatch(p -> ConfigManager.getRequired("wikipedia.expected.title").equals(p.get("title")));
        Assert.assertTrue(found, "No page with title '"
                + ConfigManager.getRequired("wikipedia.expected.title") + "' was found");
    }

    @Test(groups = {"api", "wikipedia"},
            dependsOnMethods = "wikipediaSearchReturnsExpectedStructureAndTitle")
    @Description("Schema validation of the Wikipedia response against a JSON Schema")
    public void wikipediaResponseMatchesJsonSchema() {
        request()
                .queryParam("action", "query")
                .queryParam("generator", "search")
                .queryParam("gsrsearch", ConfigManager.getRequired("wikipedia.search.query"))
                .queryParam("gsrlimit", ConfigManager.getInt("wikipedia.first.results"))
                .queryParam("prop", "info")
                .queryParam("format", "json")
                .when()
                .get()
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/wikipedia-response-schema.json"));
    }

    @Test(groups = {"api", "wikipedia", "performance"})
    @Description("Performance smoke: repeated search calls complete well below the 5 second threshold")
    public void wikipediaPerformanceSmoke() {
        for (int i = 0; i < 5; i++) {
            long start = System.currentTimeMillis();
            request()
                    .queryParam("action", "query")
                    .queryParam("generator", "search")
                    .queryParam("gsrsearch", ConfigManager.getRequired("wikipedia.search.query"))
                    .queryParam("gsrlimit", 5)
                    .queryParam("prop", "info")
                    .queryParam("format", "json")
                    .when()
                    .get()
                    .then()
                    .statusCode(200);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Call #" + (i + 1) + " took " + elapsed + " ms");
            Assert.assertTrue(elapsed < 5000, "Call #" + (i + 1) + " took " + elapsed + " ms (limit 5000)");
        }
    }
}
