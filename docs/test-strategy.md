# Test Strategy

## 1. Scope
Automated testing of the three RBA task deliverables plus the surrounding
test infrastructure (reporting, CI/CD, configuration, logging).

## 2. Test levels covered
| Level | What | Where |
|---|---|---|
| **Unit** | Locale-safe decimal parsing; JSON → XML converter | `techrba.unit` |
| **UI (Selenium 4 + TestNG)** | RBA exchange calculator: buy GBP, sell USD | `techrba.selenium` |
| **API (REST-assured)** | RBA `calculateExchangeRate` (Basic REST); Wikipedia search | `techrba.api` |
| **Performance (smoke)** | Wikipedia response time < 5s, repeated calls | `techrba.api` `WikipediaApiTest` |
| **Schema validation** | Wikipedia JSON against a formal JSON Schema | `WikipediaApiTest.wikipediaResponseMatchesJsonSchema` |

## 3. Entry / exit criteria
**Entry:** environment validated and provisioned (`setup-env.ps1` / `setup-env.sh`
checks and auto-installs missing JDK 8 / Chrome on Windows and Linux), config
present, internet available for the API/UI systems under test.
**Exit:** all targeted tests pass; reports generated; no tests disabled.

## 4. Risks & mitigations
| Risk | Mitigation |
|---|---|
| Rate figures change daily | Tests validate **live** backend values + internal consistency, not hard-coded numbers |
| UI is JS-driven / rate rounded to 2 dp | Read authoritative hidden fields; waits on AJAX; rate-rounding-aware tolerance |
| Cookie consent banner overlays page | Conditional OneTrust dismissal (no-op when absent) |
| Wikipedia blocks default user-agents (403) | Send descriptive `User-Agent` |
| Transient browser flakiness | Explicit waits, retry-analyzer (max 2), screenshot on failure |
| Fresh CI runner resolves a ChromeDriver newer than the installed Chrome | `ChromeVersion` detects the installed major and pins the driver to it; `browser.chromedriver.version` forces a specific version |
| Chromedriver shutdown timeout on Windows CI (teardown) | Tolerant teardown: a slow driver-server shutdown is logged as a warning, never fails the suite |

## 5. Negative / edge-case handling
- **Decimal formats**: `DecimalParser` normalises `36,29` / `1.234,56` / `36.29` / API decimals → `BigDecimal`.
- **Empty/absent fields**: guarded (fail with clear messages, or retry).
- **Zero/negative rate**: assert positivity.
- **XML safety**: numeric JSON keys and illegal characters sanitised so output is always well-formed.

## 6. Reporting
- TestNG/Surefire XML+HTML, **Allure** report (steps, screenshots on UI failure),
  structured **Log4j2** logs, CI artifacts on every pipeline run.

## 7. Future / backlog (not in initial scope)
- Selenium **Grid / distributed** execution.
- Contract testing against a formal OpenAPI spec.
- Shift-left security scanning (dependency + secret) in CI.
- Accessibility smoke checks for the calculator.
- Watchdog/Daemon for environment health of the bank's QA environments.

## 8. Tooling matrix
| Concern | Tool |
|---|---|
| Language / runtime | Java 8 bytecode |
| Build | Maven + wrapper |
| UI automation | Selenium 4 + WebDriverManager |
| API testing | REST-assured + JSON-Schema-validator |
| Test orchestration | TestNG (parallel-safe, listeners, retry) |
| Logging | Log4j2 (slf4j façade) |
| Reporting | Allure + Surefire |
| CI/CD | GitHub Actions (Linux gate + Linux & Windows clean-env lifecycles) + optional Docker |
| Config | 12-factor properties + env/system overrides |
