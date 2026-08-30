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
| Transient browser flakiness | Explicit waits, retry-analyzer (max `retry.max.count`), screenshot on failure |
| Fresh CI runner resolves a ChromeDriver newer than the installed Chrome | `ChromeVersion` detects the installed major and pins the driver to it; `browser.chromedriver.version` forces a specific version |
| Chromedriver shutdown timeout on Windows CI (teardown) | Tolerant teardown: a slow driver-server shutdown is logged as a warning, never fails the suite |
| **Live site update breaks tests** | **Explicitly accepted & bounded (see §8).** Full suite runs on a **nightly** schedule so drift is caught within 24 h; smoke gate keeps PR feedback fast; live-only API tests have a **deterministic offline twin** served by WireMock (`-Denv=stub`) to decouple "test logic is correct" from "the live host is healthy" |

## 5. Test tiers & CI right-sizing
The CI runs a **full QA / test build on every trigger** (push, PR, nightly,
manual): static analysis, then the full Windows + Linux clean-environment
lifecycles (all tests), then the Allure report publish. TestNG groups tag the
tests so suites can also be run selectively on demand:

| Tier | Group / suite | CI trigger | Typical duration |
|---|---|---|---|
| **Smoke gate** | `smoke.xml` (`smoke` group: unit + API + core UI test) | every push/PR (fast early feedback) | ~2-3 min |
| **Full regression** | `testng.xml` (every test) | every push/PR + **nightly `0 2 * * *`** + manual dispatch, on both Windows and Linux | ~5-8 min per OS |
| **Deterministic API** | `api-stub.xml` + `-Denv=stub` (WireMock, offline) | every PR (CI asserts it), on demand | seconds |

Every build therefore includes the full clean-environment lifecycles on Windows
and Linux plus the GitHub Pages report publish; PRs get the same full QA signal
(no separate fast-only path), and the cheap smoke gate still gives the quickest
early feedback. The Pages **deploy** itself is gated to `master`.

## 6. Negative / edge-case handling
- **Decimal formats**: `DecimalParser` normalises `36,29` / `1.234,56` / `36.29` / API decimals → `BigDecimal`.
- **Empty/absent fields**: guarded (fail with clear messages, or retry).
- **Zero/negative rate**: assert positivity.
- **XML safety**: numeric JSON keys and illegal characters sanitised so output is always well-formed.

## 7. Reporting
- TestNG/Surefire XML+HTML, **Allure** report (steps, screenshots on UI failure,
  **environment.properties** and **categories.json** companion files),
  structured **Log4j2** logs, CI artifacts on every pipeline run.
- The Allure HTML is automatically published to a stable, **linkable URL on
  GitHub Pages** after every full Linux regression: `https://rbaqa.github.io/RBASolution/`.

## 8. Future / backlog (not in initial scope)
- Selenium **Grid / distributed** execution.
- Contract testing against a formal OpenAPI spec.
- Shift-left security scanning (dependency + secret) in CI.
- Accessibility smoke checks for the calculator.
- Watchdog/Daemon for environment health of the bank's QA environments.

## 9. Tooling matrix
| Concern | Tool |
|---|---|
| Language / runtime | Java 8 bytecode |
| Build | Maven + wrapper |
| UI automation | Selenium 4 + WebDriverManager |
| API testing | REST-assured + JSON-Schema-validator |
| Offline/deterministic API | WireMock (JRE8 build) - `-Denv=stub` |
| Static analysis gate | Checkstyle + SpotBugs (`-Pquality verify`) |
| Test orchestration | TestNG (parallel-safe, listeners, retry) |
| Logging | Log4j2 (slf4j façade) |
| Reporting | Allure + Surefire (+ companion environment/categories files) |
| CI/CD | GitHub Actions: full QA build on every trigger - checkstyle/SpotBugs gate + full Windows/Linux clean-env lifecycles + nightly schedule + GitHub Pages report publish (deploy gated to master) + optional Docker |
| Config | 12-factor properties + env/system overrides |
