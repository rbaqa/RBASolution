package techrba.base;

import techrba.pages.ExchangeCalculatorPage;
import techrba.pages.HomePage;

/**
 * Base class for tests that exercise the RBA exchange-rate calculator via the
 * UI. Provides a convenient {@link #openCalculator()} so each test only states
 * what it wants to verify, not how to reach the calculator.
 */
public abstract class BaseCalculatorTest extends BaseTest {

    protected ExchangeCalculatorPage openCalculator() {
        return new HomePage(getDriver())
                .open()
                .dismissCookieBannerIfPresent()
                .openExchangeCalculator();
    }
}
