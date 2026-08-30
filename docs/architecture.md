# Architecture Overview

## Purpose
Docs the structure, decisions and layering of the RBA QA automation project so a
reviewer (and future maintainer) can quickly understand what is where and why.

## Project layout

```
rba-task/
├── .github/workflows/ci.yml        # CI/CD pipeline
├── setup-env.ps1                  # environment provisioning (Windows): validate,
│                                  #   auto-install missing JDK/Chrome, teardown
├── setup-env.sh                   # environment provisioning (Linux, bash twin):
│                                  #   auto-install missing JDK/Chrome, teardown
├── pom.xml                        # Maven build (Java 8 bytecode)
├── mvnw / mvnw.cmd / .mvn/        # Maven wrapper (reproducible build)
├── README.md                      # entry point
├── docs/                          # this documentation + strategy + RTM
├── config/                         # editable configuration + quality gate
│   ├── test-config.properties      #   base 12-factor configuration
│   ├── test-config-stub.properties #   'stub' profile = WireMock offline API tier
│   └── checkstyle/checkstyle.xml   #   lenient checkstyle rules (-Pquality)
├── postman/                       # Postman collection + environment
├── docker/                        # optional containerised run
├── reports/                       # reports output (gitignored)
└── src/
    ├── main/java/techrba/
    │   ├── config/ConfigManager.java
    │   ├── util/DecimalParser.java
    │   ├── util/DateSanity.java
    │   └── converter/WikipediaJsonToXml.java
    ├── main/resources/
    │   ├── log4j2.xml
    │   └── config/                        # classpath fallback of config
    │       ├── test-config.properties
    │       └── test-config-stub.properties
    └── test/
        ├── java/techrba/
        │   ├── annotation/Requirement.java    # RTM traceability annotation
        │   ├── base/BaseTest.java            # thread-safe WebDriver lifecycle
        │   ├── base/BaseCalculatorTest.java  # shared calculator test steps
        │   ├── base/DriverWait.java          # shared explicit-wait helpers
        │   ├── base/WaitStrategy.java        # AJAX stable-value waits (no JS)
        │   ├── driver/WebDriverFactory.java  # Strategy: local + grid browsers
        │   ├── driver/DriverType.java        # CHROME | FIREFOX | EDGE
        │   ├── driver/ChromeVersion.java     # pin ChromeDriver to installed Chrome
        │   ├── driver/CapabilitiesBuilder.java
        │   ├── error/UnsupportedBrowserException.java
        │   ├── data/CsvDataProvider.java     # data-driven test data (CSV)
        │   ├── pages/HomePage.java           # Page Object: homepage + navigation
        │   ├── pages/ExchangeCalculatorPage.java  # Page Object: calculator form
        │   ├── selenium/*.java               # UI tests: calculator (data-driven),
        │   │                                 #   currencies, rates, date, switch, invalid
        │   ├── api/BaseApiTest.java          # shared RestAssured spec + http logging
        │   ├── api/WikipediaApiTest.java
        │   ├── api/ExchangeRateApiTest.java
        │   ├── retry/RetryAnalyzer.java, AnnotationTransformer.java
        │   ├── listener/TestListener.java, TestSummaryListener.java
        │   ├── reporting/AllureReporting.java  # environment.properties + categories.json
        │   └── unit/DecimalParserTest.java, WikipediaJsonToXmlTest.java
        └── resources/
            ├── suites/*.xml
            ├── schemas/wikipedia-response-schema.json
            └── testdata/exchange-transactions.csv
```

## Layers & responsibilities

### Configuration layer (`techrba.config`)
`ConfigManager` loads `config/test-config.properties` (editable, external file)
and falls back to a classpath copy, then applies an optional environment
profile overlay (`config/test-config-<env>.properties`) selected by `-Denv=...`
or the `TEST_ENV` env var. Precedence (low → high):
1. profile overlay
2. base properties file
3. environment variable (`ENV_KEY_MADE_UPPER`)
4. system property (`-Dkey=value`)

This is the 12-factor externalisation pattern: no secrets/values hard-coded in tests.

### Driver layer (`techrba.driver`)
`WebDriverFactory` is the only place that constructs a `WebDriver` (Strategy
pattern). `DriverType` (CHROME/FIREFOX/EDGE) decides local vs remote (Selenium
Grid / cloud via `remote.url`), and `CapabilitiesBuilder` centralises browser
tuning (headless, CI flags). Tests never build drivers directly, so switching
browser or targeting a grid is pure configuration.

`DriverType.CHROME` pins WebDriverManager to the Chrome major that is actually
installed: `ChromeVersion` detects it (PowerShell/registry on Windows, the
`--version` flag on Linux) and forces `WebDriverManager.browserVersion(...)`.
This prevents a fresh CI runner from resolving a driver for a newer Chrome than
the one present (`SessionNotCreatedException`). Override any time with
`browser.chromedriver.version` (system property / `ENV_BROWSER_CHROMEDRIVER_VERSION`).

### Utilities (`techrba.util`)
`DecimalParser` normalises currency figures expressed with European comma
(`36,29`), Croatian/European grouping (`1.234,56`) or US/API dot (`0.830960`) into
`BigDecimal`. This keeps currency assertions robust against locale formatting.

`DateSanity` checks that the exchange date shown by the calculator is fresh
(not stale) and warns when the run lands on a non-banking day, so a date-driven
assertion is not silently wrong over weekends/holidays.

### Conversion (`techrba.converter`)
`WikipediaJsonToXml` converts a Wikipedia API JSON response into a **guaranteed
valid XML** document. It sanitises every JSON key into a legal XML element name
(numeric pageids are prefixed, e.g. `_7353998`) and wraps array elements in
`<item>`. Both a CLI `main` and a unit-tested API are provided.

### UI test layer (`techrba.base` + `techrba.pages` + `techrba.selenium`)
The Selenium tests follow the **Page Object Model (POM)**:

- `BaseTest` supplies a **thread-local `WebDriver`** (parallel-safe), resolves
  ChromeDriver via WebDriverManager (pinned to the installed Chrome major by
  `ChromeVersion`) and configures Chrome Options including headless for CI.
  `DriverWait` centralises explicit waits (timeout from config). Teardown is
  tolerant: Chromedriver shutdown timeouts (a known Windows-CI quirk) are logged
  as warnings instead of failing the suite.
- `BaseCalculatorTest` shares the steps common to all calculator tests
  (open page, select currency pair, enter amount, read rate/amount).
- `HomePage` (Page Object) encapsulates the homepage: `open()`,
  `dismissCookieBannerIfPresent()` (OneTrust consent overlay) and
  `openExchangeCalculator()` (clicks the button, switches to the new tab).
- `ExchangeCalculatorPage` (Page Object) encapsulates the calculator form
  controls and results: `selectRateType`, `selectFromCurrency`,
  `selectToCurrency`, `enterAmount`, `readExchangeRate`, `readConvertedAmount`.
- `ExchangeCalculatorTest` is the test orchestration layer - it only talks in
  terms of "buy GBP" / "sell USD" and never touches raw locators/WebElements,
  keeping tests readable and robust to UI rework.

Because Page Objects centralise locators, a UI change is fixed in exactly one
place per page.

### API test layer (`techrba.api`)
- `WikipediaApiTest` – verifies the 200, <5s response time, `pages` object,
  expected title `Raiffeisen Bank International`, JSON-Schema validation and a
  performance smoke run. A proper `User-Agent` is sent (Wikipedia returns 403
  otherwise).
- `ExchangeRateApiTest` – the **Basic REST** deliverable: replicates the Selenium
  calculator example by calling the same `calculateExchangeRate` REST endpoint the
  UI uses, for both buy- and sell-transactions. When `api.stub=true` (profile
  `-Denv=stub`) the endpoint is served **offline by WireMock** with recorded
  RBA-style JSON responses, so the REST tests are deterministic, instant and
  independent of live-site health; live runs keep the real endpoint.

### Cross-cutting (retry/listener)
- `RetryAnalyzer` + `AnnotationTransformer` retry transient flaky tests (max 2),
  without masking real defects (still reported failed after retries).
- `TestListener` logs every test lifecycle event and captures a screenshot on UI
  failure into both Allure and `reports/screenshots`.
- `AllureReporting` writes the Allure **companion files** into
  `reports/allure-results` on every run: `environment.properties` (OS, JDK,
  browser, Git ref, CI run URL, active profile) and `categories.json` (failure
  classification: product defect / browser-driver problem / external network /
  broken test / skipped), captured from `WebDriverFactory` at driver creation.

## Test isolation & determinism
- Config externalised (no hard-coded URLs/values in tests).
- No static sleeps where waits can be used; explicit `WebDriverWait` +
  `ExpectedConditions`.
- Thread-safe drivers enable parallel execution via the TestNG suite
  (`parallel="tests"`, thread-count).

## Report toolchain
Surefire (TestNG) → classic reports; Allure adapter captures steps, attachments
and screenshots → `allure:report` / `allure:serve`. Both are uploaded as CI
artifacts on every run. `environment.properties` + `categories.json` make the
Allure HTML self-describing. On merges/nightly the workflow publishes the full
report to a stable linkable URL via **GitHub Pages** (`https://rbaqa.github.io/RBASolution/`).

## Test tiers & CI/CD
Tests are tagged with TestNG groups (`smoke` / `regression` + functional tags)
and executed in tiers - see [`test-strategy.md`](test-strategy.md) §5 and
[`../.github/workflows/ci.yml`](../.github/workflows/ci.yml) for the full matrix.
The `-Pquality` Maven profile binds a lenient Checkstyle + SpotBugs gate to
`verify`; CI runs it on every trigger. The pinned SpotBugs 4.7.3.2 cannot parse
JDK 17+ standard-library class files, so the gate must run on the **JDK 8**
runtime the project targets (as the CI `quality` job does) - checkstyle is JDK
agnostic. `setup-env` installs JDK 8 only when no JDK is present; it will not
replace an existing JDK 17+, so a local run on a modern JDK needs a dedicated
JDK 8.
