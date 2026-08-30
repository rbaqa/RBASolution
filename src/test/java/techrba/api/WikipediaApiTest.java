package techrba.api;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import techrba.annotation.Requirement;
import techrba.config.ConfigManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

/**
 * REST / Postman task - Wikipedia basic search.
 *
 * <p>Performs a search for "Raiffeisen" using the MediaWiki API and returns the
 * first 10 results as a JSON object ({@code query.pages}). Validates:
 * response code 200, response time below 5 seconds, presence of a 'pages'
 * object and a page titled 'Raiffeisen Bank International'. Uses SoftAssert so
 * every check is evaluated (no early failure hiding other defects). A JSON
 * Schema validation and a light performance run are included as extras.</p>
 */
public class WikipediaApiTest extends BaseApiTest {

    // Wikipedia rejects default HTTP client user agents with HTTP 403
    private static final String USER_AGENT =
            "RBA-QA-Automation/1.0 (Senior QA automation test; salesforce-tooling@example.com)";

    @Override
    protected io.restassured.specification.RequestSpecification buildDefaultSpec() {
        io.restassured.specification.RequestSpecification base = super.buildDefaultSpec();
        base.header("User-Agent", USER_AGENT);
        base.baseUri(ConfigManager.getRequired("wikipedia.api.url"));
        base.queryParam("action", "query");
        base.queryParam("format", "json");
        return base;
    }

    /** Reusable Wikipedia search spec (generator=search + prop=info, per the task). */
    @Step("Build Wikipedia search spec")
    private io.restassured.specification.RequestSpecification searchSpec(int limit) {
        return givenSpec().spec(currentSpec())
                .queryParam("generator", "search")
                .queryParam("gsrsearch", ConfigManager.getRequired("wikipedia.search.query"))
                .queryParam("gsrlimit", limit)
                .queryParam("prop", "info");
    }

    @Test(groups = {"api", "wikipedia", "smoke", "regression"})
    @Description("Wikipedia search for 'Raiffeisen' - validate 200, response time, pages object, title")
    @Requirement({"P2", "P3", "P4", "P5", "P6"})
    public void wikipediaSearchReturnsExpectedStructureAndTitle() {
        String expectedTitle = ConfigManager.getRequired("wikipedia.expected.title");
        long limitMs = ConfigManager.getInt("wikipedia.max.seconds") * 1000;
        SoftAssert soft = new SoftAssert();

        Response response = searchSpec(ConfigManager.getInt("wikipedia.first.results"))
                .when()
                .get()
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("query.pages", Matchers.notNullValue())
                .extract()
                .response();

        // 1) response code 200
        soft.assertEquals(response.getStatusCode(), 200, "HTTP status code should be 200");

        // 2) response time below 5 seconds
        long elapsed = response.timeIn(TimeUnit.MILLISECONDS);
        System.out.println("Wikipedia response time = " + elapsed + " ms (limit " + limitMs + " ms)");
        soft.assertTrue(elapsed < limitMs, "Response time " + elapsed + " ms exceeds the "
                + ConfigManager.getInt("wikipedia.max.seconds") + " s limit");

        // 3) response contains a 'pages' object
        JsonPath json = response.jsonPath();
        Map<String, Object> pages = json.getMap("query.pages");
        soft.assertNotNull(pages, "query.pages should not be null");
        System.out.println("Wikipedia returned " + (pages == null ? 0 : pages.size()) + " pages");

        // 4) some page contains key 'title' with value 'Raiffeisen Bank International'
        boolean found = pages != null && pages.values().stream()
                .map(v -> (Map<String, Object>) v)
                .anyMatch(p -> expectedTitle.equals(p.get("title")));
        soft.assertTrue(found, "No page with title '" + expectedTitle + "' was found");

        soft.assertAll();
    }

    @Test(groups = {"api", "wikipedia", "smoke", "regression"},
            dependsOnMethods = "wikipediaSearchReturnsExpectedStructureAndTitle")
    @Description("Schema validation of the Wikipedia response against a JSON Schema")
    public void wikipediaResponseMatchesJsonSchema() {
        searchSpec(ConfigManager.getInt("wikipedia.first.results"))
                .when()
                .get()
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/wikipedia-response-schema.json"));
    }

    @Test(groups = {"api", "wikipedia", "performance", "smoke", "regression"})
    @Description("Performance smoke: repeated search calls complete well below the 5 second threshold")
    public void wikipediaPerformanceSmoke() {
        SoftAssert soft = new SoftAssert();
        for (int i = 0; i < 5; i++) {
            long start = System.currentTimeMillis();
            searchSpec(5)
                    .when()
                    .get()
                    .then()
                    .statusCode(200);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Call #" + (i + 1) + " took " + elapsed + " ms");
            soft.assertTrue(elapsed < 5000, "Call #" + (i + 1) + " took " + elapsed + " ms (limit 5000)");
        }
        soft.assertAll();
    }
}
