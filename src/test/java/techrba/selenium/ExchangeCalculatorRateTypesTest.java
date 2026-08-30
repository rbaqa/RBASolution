package techrba.selenium;

import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.testng.Assert;
import org.testng.annotations.Test;

import techrba.annotation.Requirement;
import techrba.base.BaseCalculatorTest;
import techrba.pages.ExchangeCalculatorPage;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies the three exchange-rate modes of the RBA calculator:
 * <ul>
 *   <li>0 - Kupovni (bank buys the foreign currency)</li>
 *   <li>1 - Srednji (mid-rate)</li>
 *   <li>2 - Prodajni (bank sells the foreign currency)</li>
 * </ul>
 * For EUR -&gt; USD all modes are available. Buys and sells must differ (bank
 * spread), Srednji typically sits between them, and for Srednji the "Za
 * efektivu" section is hidden by the page logic.
 */
public class ExchangeCalculatorRateTypesTest extends BaseCalculatorTest {

    private static final String FROM_EUR = "978";
    private static final String TO_USD = "840";
    private static final int AMOUNT = 100;

    @Test(groups = {"selenium", "ui", "exchange"})
    @Description("Kupovni/Srednji/Prodajni differ and Srednji hides the 'Za efektivu' section")
    @Requirement({"S8"})
    public void rateTypesProduceSensibleResults() {
        ExchangeCalculatorPage calc = openCalculator();
        calc.selectFromCurrency(FROM_EUR).selectToCurrency(TO_USD);

        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        rates.put("KUPOVNI", readRateForMode(calc, 0, "Kupovni"));
        rates.put("SREDNJI", readRateForMode(calc, 1, "Srednji"));
        rates.put("PRODAJNI", readRateForMode(calc, 2, "Prodajni"));

        LOG.info("EUR->USD rates: {}", rates);

        BigDecimal kupovni = rates.get("KUPOVNI");
        BigDecimal prodajni = rates.get("PRODAJNI");

        Assert.assertTrue(kupovni.compareTo(BigDecimal.ZERO) > 0, "Kupovni rate must be positive");
        Assert.assertTrue(prodajni.compareTo(BigDecimal.ZERO) > 0, "Prodajni rate must be positive");

        // Bank spread: the buy and sell rates must differ (otherwise there is no margin).
        Assert.assertNotEquals(kupovni, prodajni,
                "Kupovni and Prodajni rates should differ, but both were " + kupovni);

        // Mid-rate must be positive and, if the spread holds, sit between buy and sell.
        BigDecimal srednji = rates.get("SREDNJI");
        Assert.assertTrue(srednji.compareTo(BigDecimal.ZERO) > 0, "Srednji rate must be positive");
        BigDecimal min = kupovni.min(prodajni);
        BigDecimal max = kupovni.max(prodajni);
        Assert.assertTrue(srednji.compareTo(min) >= 0 && srednji.compareTo(max) <= 0,
                "Srednji rate " + srednji + " should lie between Kupovni " + kupovni
                        + " and Prodajni " + prodajni);
    }

    @Test(groups = {"selenium", "ui", "exchange"}, dependsOnMethods = "rateTypesProduceSensibleResults")
    @Description("Srednji tecaj hides the 'Za efektivu' (acctContainer) section")
    @Requirement({"S8"})
    public void srednjiModeHidesEffectiveSection() {
        ExchangeCalculatorPage calc = openCalculator();
        calc.selectFromCurrency(FROM_EUR).selectToCurrency(TO_USD).enterAmount(AMOUNT);

        // Kupovni/Prodajni show the effective section
        calc.selectRateType(0);
        Assert.assertTrue(calc.isEffectiveSectionVisible(),
                "'Za efektivu' section should be visible for Kupovni mode");

        // Srednji hides it
        calc.selectRateType(1).enterAmount(AMOUNT);
        softAssertNotVisible(calc, "Srednji mode");

        // Prodajni shows it again
        calc.selectRateType(2).enterAmount(AMOUNT);
        Assert.assertTrue(calc.isEffectiveSectionVisible(),
                "'Za efektivu' section should be visible for Prodajni mode");
    }

    @Step("Read rate for mode {modeLabel}")
    private BigDecimal readRateForMode(ExchangeCalculatorPage calc, int mode, String modeLabel) {
        calc.selectRateType(mode).enterAmount(AMOUNT);
        BigDecimal rate = calc.readExchangeRate();
        LOG.info("Mode {} rate = {}", modeLabel, rate);
        return rate;
    }

    private void softAssertNotVisible(ExchangeCalculatorPage calc, String modeLabel) {
        Assert.assertFalse(calc.isEffectiveSectionVisible(),
                "'Za efektivu' section should be hidden for " + modeLabel);
    }
}