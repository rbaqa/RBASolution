package techrba.unit;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import techrba.util.DecimalParser;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unit tests for {@link DecimalParser} - the locale-robust number parser.
 * Covers Croatian/European comma, US dot and plain API decimals.
 */
public class DecimalParserTest {

    @DataProvider(name = "parseCases")
    public Object[][] parseCases() {
        return new Object[][]{
                {"36,29", "36.29"},                    // HR/European comma
                {"0,91", "0.91"},                      // European comma small
                {"1.234,56", "1234.56"},               // European grouped
                {"36.29", "36.29"},                    // US dot
                {"0.830960", "0.830960"},              // plain API decimal
                {"1 234,56", "1234.56"},               // space group + comma
        };
    }

    @Test(dataProvider = "parseCases", groups = {"unit", "smoke", "regression"})
    public void parseHandlesLocaleFormats(String input, String expected) {
        BigDecimal actual = DecimalParser.parse(input);
        Assert.assertEquals(actual, new BigDecimal(expected).setScale(4, RoundingMode.HALF_UP),
                "Parsing failed for input: " + input);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, groups = {"unit", "smoke", "regression"})
    public void parseThrowsOnEmptyInput() {
        DecimalParser.parse("");
    }
}
