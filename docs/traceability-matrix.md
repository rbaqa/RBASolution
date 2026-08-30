# Requirements Traceability Matrix (RTM)

Maps every requirement from the original task to its automated test case and
current status, so a reviewer can verify full coverage at a glance.

Legend: ✅ Implemented (passing) · 🟡 Implemented (needs environment/browser) · ⬜ Not in scope

## A. Selenium (Java + TestNG)
| ID | Requirement | Test | Status |
|---|---|---|---|
| S1 | New project created | Project scaffold (Maven) | ✅ |
| S2 | Project placed on git | `git init` + commit | ✅ |
| S3 | Use Selenium only (no JavaScript Executor) | All `selenium/` code uses WebDriver only | ✅ |
| S4 | Use Java only | Java 8 bytecode | ✅ |
| S5 | Run via TestNG | `suites/testng.xml`, TestNG annotations | ✅ |
| S6 | Open `www.rba.hr` | `HomePage.open()` | ✅ |
| S7 | Select "Tecajni kalkulator" | `HomePage.openExchangeCalculator()` | ✅ |
| S8 | Buy GBP (kupnja funti) - read rate & final amount | `ExchangeCalculatorTest` (data row BUY_GBP) | ✅ |
| S9 | Sell USD (prodaja dolara) - read rate & final amount | `ExchangeCalculatorTest` (data row SELL_USD) | ✅ |
| S10 | e.g. "40 EUR -> 36.29 GBP" style consistency | consistency assert (rate × input ≈ amount) | ✅ |

## B. Postman / REST
| ID | Requirement | Test | Status |
|---|---|---|---|
| P1 | Basic REST - replicate Selenium example via REST | `ExchangeRateApiTest` (buy + sell) | ✅ |
| P2 | Wikipedia search 'Raiffeisen' - first 10 results JSON | `wikipediaSearchReturnsExpectedStructureAndTitle` | ✅ |
| P3 | response code = 200 | `statusCode(200)` | ✅ |
| P4 | response time < 5 seconds | `response.timeIn(...) < 5000` | ✅ |
| P5 | response contains 'pages' object | `body("query.pages", notNullValue())` | ✅ |
| P6 | a page contains key 'title' = 'Raiffeisen Bank International' | `titles.contains(expected)` | ✅ |
| Bonus | JSON Schema validation | `wikipediaResponseMatchesJsonSchema` | ✅ |
| Bonus | Performance smoke | `wikipediaPerformanceSmoke` | ✅ |

## C. Java program
| ID | Requirement | Test | Status |
|---|---|---|---|
| J1 | JDK 1.8.261 (or similar) | `maven.compiler.release=8` | ✅ |
| J2 | Convert Wikipedia JSON results to valid XML | `WikipediaJsonToXml.convert(...)` | ✅ |
| J3 | Output is valid/well-formed XML | `WikipediaJsonToXmlTest` (numeric keys, arrays, escaping) | ✅ |
| Bonus | CLI usage (`<in> <out>`) | `main(...)` | ✅ |

## D. Senior-quality extras (proactive)
| ID | Extras | Status |
|---|---|---|
| E1 | Reproducible environment (Maven wrapper; `setup-env.ps1` auto-installs missing JDK 8/Chrome) | ✅ |
| E2 | CI/CD pipeline (GitHub Actions) - Linux headless gate + Windows clean-env lifecycle (provision → test → report → teardown) | ✅ |
| E3 | Allure + TestNG reporting, screenshot on failure | ✅ |
| E4 | Logging framework (Log4j2) | ✅ |
| E5 | Configuration management (12-factor) | ✅ |
| E6 | Retry analyzer for flaky tests | ✅ |
| E7 | Decimal-format / edge-case handling (BigDecimal, locale) | ✅ |
| E8 | Environment validation (`@BeforeSuite`) | ✅ |
| E9 | Schema validation | ✅ |
| E10 | Documentation (this + strategy + architecture) | ✅ |
| E11 | Parallel-capable tests (thread-local drivers) | ✅ |
| E12 | Containerised run (Docker, optional) | ✅ |
| E13 | Page Object Model (HomePage + ExchangeCalculatorPage) | ✅ |
| E14 | Data-driven UI test (CSV `@DataProvider`) | ✅ |
| E15 | `WebDriverFactory` multi-browser (Chrome/Firefox/Edge) | ✅ |
| E16 | Selenium Grid / remote execution (`remote.url`) | ✅ |
| E17 | Retry scoped to UI/flaky only (never masks API defects) | ✅ |
| E18 | HTTP-level API logging (RestAssured filters → Log4j2) | ✅ |
| E19 | SoftAssert in API tests (no early-fail hiding defects) | ✅ |
| E20 | Environment profiles (`-Denv=<env>`) | ✅ |
| E21 | QA summary dashboard listener (`TestSummaryListener`) | ✅ |
| E22 | `@Requirement` RTM traceability annotation + binding | ✅ |
| E23 | `WaitStrategy` AJAX stable-value wait (no JS executor) | ✅ |
| E24 | Exchange-date freshness check (`DateSanity`) | ✅ |
| E25 | Auto-install of missing prerequisites (`setup-env.ps1`, winget/Chocolatey, UAC-elevated) | ✅ |
| E26 | Clean-environment teardown (`setup-env.ps1 -Uninstall`, marker-tracked, never touches pre-existing software) | ✅ |
| E27 | Windows clean-env CI job - only reports remain after teardown | ✅ |
