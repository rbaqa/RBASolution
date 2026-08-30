# RBA QA Automation Task

Senior QA automation test project built for the **RBA (Raiffeisenbank) candidate
task**. Delivers the three required deliverables plus a set of senior-level extras
(test framework, reporting, CI/CD, logging, configuration management, containerisation).

---

## Quick navigation

| Deliverable | Where |
|---|---|
| Selenium test – exchange calculator | [`ExchangeCalculatorTest.java`](src/test/java/techrba/selenium/ExchangeCalculatorTest.java) |
| Postman / REST tests | [`techrba.api`](src/test/java/techrba/api/) + [Postman collection](postman/RBA_QA_Automation.postman_collection.json) |
| Java JSON → XML converter | [`WikipediaJsonToXml.java`](src/main/java/techrba/converter/WikipediaJsonToXml.java) |
| Test configuration | [`config/test-config.properties`](config/test-config.properties) |
| TestNG suites | [`src/test/resources/suites/`](src/test/resources/suites/) |

See also:
- [Architecture overview](docs/architecture.md)
- [Test strategy](docs/test-strategy.md)
- [Requirements traceability matrix (RTM)](docs/traceability-matrix.md)

---

## 1. Architecture overview

```
┌────────────────────────────────────────────────────────────────────┐
│                            CI/CD (GitHub Actions)                 │
│  checkout → setup JDK8 → mvnw test → upload reports/artifacts     │
└────────────────────────────────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────┐
│                        Maven project (Java 8)                     │
│                                                                   │
│  main/java/techrba                                                │
│   ├── config/ConfigManager        (externalised 12-factor config) │
│   ├── util/DecimalParser          (locale-safe number parsing)    │
│   ├── util/DateSanity             (banking-day / date freshness)  │
│   └── converter/WikipediaJsonToXml (JSON → valid XML)             │
│                                                                   │
│  test/java/techrba                                                 │
│   ├── base/BaseTest               (thread-safe WebDriver)          │
│   ├── selenium/ExchangeCalculatorTest  (Selenium 4 + TestNG)      │
│   ├── api/WikipediaApiTest        (REST + schema + perf)           │
│   ├── api/ExchangeRateApiTest     (Basic REST)                     │
│   ├── retry/RetryAnalyzer + AnnotationTransformer                  │
│   ├── listener/TestListener       (reporting + screenshots)        │
│   └── unit/DecimalParserTest, WikipediaJsonToXmlTest               │
└────────────────────────────────────────────────────────────────────┘
```

Key technical decisions:
- **Java 8 bytecode** (`maven.compiler.source/target=8`) per the task specification.
  Compiles natively on JDK 8 in CI (no `--release`, which is unsupported on javac 8).
- **TestNG pinned to 7.5.1** — the last release with Java 8 bytecode (7.6+ is
  compiled for Java 11 and would fail JDK 8 (`cannot access org.testng.annotations.*`)).
- **Maven Wrapper** (`./mvnw`) for reproducible builds with **no global Maven**.
- **ChromeDriver matched to the installed Chrome** at runtime (`ChromeVersion`):
  WebDriverManager is pinned to the detected Chrome major version, so a fresh CI
  runner never resolves a driver for a newer Chrome than the one that is
  installed. Override any time via `browser.chromedriver.version` (env/system prop).
- **Thread-local `WebDriver`** so tests can run in parallel.
- **Resilient browser teardown**: a slow/stuck Chromedriver shutdown on the
  Windows runner is logged as a warning instead of failing the suite.
- **Log4j2** structured logging to console + rolling file.
- **Allure + TestNG** reporting with screenshot-on-failure.

---

## 2. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 8 recommended (bytecode targets 8; `-Pquality` SpotBugs gate requires a JDK 8 runtime) | `JAVA_HOME` set |
| Maven | none required | provided by wrapper |
| Google Chrome | latest | Chromedriver auto-resolved via WebDriverManager |
| Git | any | for version control |
| Internet | required | first build downloads dependencies |

Optionally Docker if using the containerised run.

---

## 3. Execution

### 3.1 One-time environment setup
```powershell
powershell -ExecutionPolicy Bypass -File .\setup-env.ps1
```
Validates Java, Chrome, configuration and connectivity, and **automatically
installs** any missing prerequisite (JDK 8 / Google Chrome) via winget or
Chocolatey, elevating to Administrator only when an install is needed. Maven
itself is self-provisioned by the committed wrapper (`mvnw`). Pass `-SkipInstall`
to validate only.

Teardown (restores a machine to its clean pre-setup state, keeping `reports/`):
```powershell
powershell -ExecutionPolicy Bypass -File .\setup-env.ps1 -Uninstall
```
`-Uninstall` removes **only** what `setup-env.ps1` installed (tracked in
`reports/.setup-installed.json`) plus the downloaded Maven caches; pre-existing
software is never touched.

The same contract exists for Linux (bash), using `apt`/`sudo`:
```bash
./setup-env.sh              # one-time setup (installs JDK 8 + Chrome if missing)
./setup-env.sh --skip-install   # validate only
./setup-env.sh --uninstall      # teardown, keeps reports/
```
Inside GitHub Actions the script also exports `JAVA_HOME`/`PATH` via
`$GITHUB_ENV`/`$GITHUB_PATH`; on a local machine it persists them in a removable
`~/.bashrc` marker block.

### 3.2 Run everything (unit + API + Selenium)
```powershell
.\mvnw.cmd test
```

### 3.3 Run a specific suite / tier
```powershell
# Fast gate: unit + API + core UI (methods tagged with the 'smoke' group) - what CI runs on PRs
.\mvnw.cmd -Dsuite.file=smoke.xml test

# Full regression: every test (default of the Maven build)
.\mvnw.cmd -Dsuite.file=testng.xml test

# API + unit only (no browser needed)
.\mvnw.cmd -Dsuite.file=verify-unit-api.xml test

# Selenium only
.\mvnw.cmd -Dsuite.file=ui-only.xml test

# Deterministic offline API tier: exchange-rate REST tests served by WireMock
# stubs instead of the live site (instant, offline, never flaky)
.\mvnw.cmd -Denv=stub -Dsuite.file=api-stub.xml test
```
Test methods are tagged with **TestNG groups**: `smoke` (fast gate),
`regression` (full suite), plus the functional tags `unit`/`api`/`ui`/`exchange`/
`wikipedia`/`performance`. UI tests optionally carry `flaky` and are auto-retried
(see `RetryAnalyzer`).

### 3.4 Override configuration (12-factor)
Properties can be overridden without editing files:
```powershell
# via system property
.\mvnw.cmd -Dsuite.file=ui-only.xml -Dbase.url=https://www.rba.hr test

# via environment variable  ->  browser.headless  ->  ENV_BROWSER_HEADLESS
$env:ENV_BROWSER_HEADLESS = "true"
.\mvnw.cmd test
```

Browser and grid are fully configurable (see `WebDriverFactory`):
```powershell
# change browser (chrome | firefox | edge)
.\mvnw.cmd -Dbrowser=firefox test

# run on a Selenium Grid / cloud instead of a local browser
.\mvnw.cmd -Dremote.url=http://localhost:4444/wd/hub test

# force a specific ChromeDriver major (when the detected one is unsuitable)
.\mvnw.cmd -Dbrowser.chromedriver.version=151 test
```

Environment profiles (12-factor): load `config/test-config-<env>.properties` on top of the base config:
```powershell
# 'stub' profile = deterministic offline API tier (WireMock replaces the live RBA endpoint)
.\mvnw.cmd -Denv=stub -Dsuite.file=api-stub.xml test

.\mvnw.cmd -Denv=qa test      # or $env:TEST_ENV = "qa"
```

### 3.5 Run the Java JSON → XML converter
```powershell
# args: <input.json> <output.xml>   (defaults to reports/wikipedia-response.*)
.\mvnw.cmd -Dexec.mainClass=techrba.converter.WikipediaJsonToXml exec:java
```

### 3.6 Postman
Import [`postman/RBA_QA_Automation.postman_collection.json`](postman/) and
[`RBA_QA_Automation.postman_environment.json`](postman/) into Postman, then run.

### 3.7 Docker (optional)
```bash
docker compose -f docker/docker-compose.yml up --build
```

### 3.8 Generate Allure report
```powershell
.\mvnw.cmd allure:report      # generates reports/allure-report
.\mvnw.cmd allure:serve       # serves it locally in a browser
```
Each run also writes Allure **companion files** into `reports/allure-results`:
- `environment.properties` - OS, JDK, browser, Git ref, CI run URL and active
  profile, shown in the report's "Environment" tab.
- `categories.json` - classifies every failure (product defect, browser/driver
  problem, external network issue, broken test, ...) so the report is readable
  instead of a flat "failed" list.

### 3.9 Quality gate (static analysis)
```powershell
.\mvnw.cmd -Pquality verify      # checkstyle + SpotBugs over main AND test sources (no tests run)
```
`-Pquality` binds `checkstyle:check` (lenient config in
[`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml)) and
`spotbugs:check` to the `verify` phase. CI runs this on every trigger.

> **JDK note:** run this profile with the **JDK 8** the project targets (as the CI
> `quality` job does). The pinned SpotBugs 4.7.3.2 bundles an ASM that cannot
> parse JDK 17+ standard-library class files (it fails locally with
> `Unsupported class file major version`). `setup-env` installs JDK 8 only when
> a JDK is already absent - it will **not** replace an installed JDK 17+ - so, if
> your machine has a modern JDK, run the gate on a JDK 8 (e.g. via the `quality`
> CI job or a dedicated JDK 8). Checkstyle itself is JDK agnostic. See
> [`docs/architecture.md`](docs/architecture.md).

---

## 4. CI/CD status

| CI | Status |
|---|---|
| GitHub Actions | [![RBA QA CI](https://github.com/rbaqa/RBASolution/actions/workflows/ci.yml/badge.svg)](https://github.com/rbaqa/RBASolution/actions/workflows/ci.yml) |

The [`ci.yml`](.github/workflows/ci.yml) workflow is **tiered** so PR feedback
stays fast while full validation still runs on every merge:

| Tier | Runs on | Scope |
|---|---|---|
| `quality` | every trigger | checkstyle + SpotBugs (`./mvnw -Pquality verify`) |
| `test` (smoke gate) | every trigger (push, PR, nightly, manual) | `smoke.xml` - unit + API + core UI, headless; also asserts the deterministic WireMock stub tier |
| `windows-clean-env-run` | merge to master, nightly, manual | full `testng.xml` regression in a **clean provisioned environment** (setup → test → report → teardown) |
| `linux-clean-env-run` | merge to master, nightly, manual | full regression in a clean provisioned environment |
| `allure-report-pages` | after the Linux full regression | regenerates the Allure HTML and publishes it to **GitHub Pages** |

- PRs run **only** `quality` + the smoke gate (~2-3 min); full Windows/Linux
  clean-environment lifecycles run on push to `master`, on the **nightly
  schedule** (`0 2 * * *`), and on `workflow_dispatch`.
- Live-site drift is caught daily: the nightly full regression exercises the
  real bank site (and Wikipedia) end-to-end.

#### Live Allure report (GitHub Pages)

**https://rbaqa.github.io/RBASolution/**

Published by the `allure-report-pages` job from the last full Linux regression.
**One-time repo setup required:** GitHub → Settings → Pages → *Deploy from a
branch* → `gh-pages / root`.

---

## 5. Reports

Allure + TestNG reports land under:
```
reports/allure-results/   (raw data + environment.properties + categories.json)
reports/allure-report/    (generated HTML)
target/surefire-reports/  (classic TestNG XML/HTML)
reports/screenshots/      (UI failure screenshots)
logs/rba-task.log         (structured logs)
```
CI publishes the Allure report to a permanent, linkable URL via GitHub Pages:
**https://rbaqa.github.io/RBASolution/** (see section 4).

---

## 6. Reproducibility & note on the "1 EUR = 0.91 GBP" example

The task's example figures reflect a particular trading date and the **Prodajni**
(bank-sells-GBP) rate. Because rates change daily, the tests read and validate the
**live** rate and amount from the same backend the UI calls, asserting positivity and
internal consistency (amount ≈ rate × input) rather than hard-coding a static figure.

---

## 7. Live-site flakiness: what is expected and how it is handled

The Selenium suite and the REST tests validate the **live RBA site** (and the
Wikipedia API), so occasional environment-driven failures are an accepted,
**explicit** part of the strategy rather than a surprise:

**Accepted sources of flakiness**
- The live site being temporarily slow, rate-limited or unavailable.
- A dev/maintenance deploy or backend/API change happening mid-run.
- Browser driver/site drift that CI provisioning cannot fully predict.
- The UI polling a date-specific rate that changes daily.

**How the suite keeps genuine failures reliable**
1. **Value integrity**: assertions validate *properties* of the data (positive,
   internally consistent rate × amount) instead of hard-coded fixtures.
2. **Explicit waits** everywhere; page-load + explicit + implicit timeouts are
   config tuneable. Screenshots and HTTP logs are captured on failure.
3. **Retry policy** (`RetryAnalyzer`): only `ui`/`flaky` tests are retried (max
   `retry.max.count`), so a transient UI blip does not fail a green build, while
   genuine assertion failures stay red.
4. **Tolerant teardown**: a stuck Chromedriver shutdown on CI is logged, not
   fatal.
5. **Deterministic offline tier** (`-Denv=stub`): the REST exchange-rate tests
   can run against recorded **WireMock** responses - no network, no site, always
   green - proving the test logic itself is sound independently of the live host.
6. **Tiered CI**: PRs see the fast smoke gate; the full suite runs nightly so a
   genuine regression is caught within 24h even if the live site misbehaves at a
   given moment.
7. **Live runs are monitored, not hidden**: a red nightly run that is a site
   outage is labelled as external failure in Allure (see `categories.json`) and
   re-run - a red PR found to be purely environmental is retried once before
   investigation.
