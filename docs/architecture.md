# Architecture Overview

## Purpose
Docs the structure, decisions and layering of the RBA QA automation project so a
reviewer (and future maintainer) can quickly understand what is where and why.

## Project layout

```
rba-task/
├── .github/workflows/ci.yml        # CI/CD pipeline
├── setup-env.ps1                  # environment pre-requisite checker (Windows)
├── pom.xml                        # Maven build (Java 8 bytecode)
├── mvnw / mvnw.cmd / .mvn/        # Maven wrapper (reproducible build)
├── README.md                      # entry point
├── docs/                          # this documentation + strategy + RTM
├── config/test-config.properties  # editable 12-factor configuration
├── postman/                       # Postman collection + environment
├── docker/                        # optional containerised run
├── reports/                       # reports output (gitignored)
└── src/
    ├── main/java/techrba/
    │   ├── config/ConfigManager.java
    │   ├── util/DecimalParser.java
    │   └── converter/WikipediaJsonToXml.java
    ├── main/resources/
    │   ├── log4j2.xml
    │   └── config/test-config.properties   # classpath fallback of config
    └── test/
        ├── java/techrba/
        │   ├── base/BaseTest.java            # thread-safe WebDriver lifecycle
        │   ├── base/DriverWait.java          # shared explicit-wait helpers
        │   ├── pages/HomePage.java           # Page Object: homepage + navigation
        │   ├── pages/ExchangeCalculatorPage.java  # Page Object: calculator form
        │   ├── selenium/ExchangeCalculatorTest.java
        │   ├── api/WikipediaApiTest.java
        │   ├── api/ExchangeRateApiTest.java
        │   ├── retry/RetryAnalyzer.java, AnnotationTransformer.java
        │   ├── listener/TestListener.java
        │   └── unit/DecimalParserTest.java, WikipediaJsonToXmlTest.java
        └── resources/
            ├── suites/*.xml
            └── schemas/wikipedia-response-schema.json
```

## Layers & responsibilities

### Configuration layer (`techrba.config`)
`ConfigManager` loads `config/test-config.properties` (editable, external file)
and falls back to a classpath copy. Precedence (low → high):
1. properties file
2. environment variable (`ENV_KEY_MADE_UPPER`)
3. system property (`-Dkey=value`)

This is the 12-factor externalisation pattern: no secrets/values hard-coded in tests.

### Utilities (`techrba.util`)
`DecimalParser` normalises currency figures expressed with European comma
(`36,29`), Croatian/European grouping (`1.234,56`) or US/API dot (`0.830960`) into
`BigDecimal`. This keeps currency assertions robust against locale formatting.

### Conversion (`techrba.converter`)
`WikipediaJsonToXml` converts a Wikipedia API JSON response into a **guaranteed
valid XML** document. It sanitises every JSON key into a legal XML element name
(numeric pageids are prefixed, e.g. `_7353998`) and wraps array elements in
`<item>`. Both a CLI `main` and a unit-tested API are provided.

### UI test layer (`techrba.base` + `techrba.pages` + `techrba.selenium`)
The Selenium tests follow the **Page Object Model (POM)**:

- `BaseTest` supplies a **thread-local `WebDriver`** (parallel-safe), uses
  `WebDriverManager` to resolve ChromeDriver, configures Chrome Options (headless
  option for CI), and enforces implicit waits. `DriverWait` centralises explicit
  waits (timeout from config).
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
  UI uses, for both buy- and sell-transactions.

### Cross-cutting (retry/listener)
- `RetryAnalyzer` + `AnnotationTransformer` retry transient flaky tests (max 2),
  without masking real defects (still reported failed after retries).
- `TestListener` logs every test lifecycle event and captures a screenshot on UI
  failure into both Allure and `reports/screenshots`.

## Test isolation & determinism
- Config externalised (no hard-coded URLs/values in tests).
- No static sleeps where waits can be used; explicit `WebDriverWait` +
  `ExpectedConditions`.
- Thread-safe drivers enable parallel execution via the TestNG suite
  (`parallel="tests"`, thread-count).

## Report toolchain
Surefire (TestNG) → classic reports; Allure adapter captures steps, attachments
and screenshots → `allure:report` / `allure:serve`. Both are uploaded as CI
artifacts on every run.
