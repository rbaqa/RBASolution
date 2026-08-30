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
- **Maven Wrapper** (`./mvnw`) for reproducible builds with **no global Maven**.
- **WebDriverManager** resolves the exact ChromeDriver matching the installed
  Chrome – no hard-coded driver versions.
- **Thread-local `WebDriver`** so tests can run in parallel.
- **Log4j2** structured logging to console + rolling file.
- **Allure + TestNG** reporting with screenshot-on-failure.

---

## 2. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java (JDK) | 8+ (bytecode targets 8) | `JAVA_HOME` set |
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

### 3.2 Run everything (unit + API + Selenium)
```powershell
.\mvnw.cmd test
```

### 3.3 Run a specific suite
```powershell
# API + unit only (no browser needed)
.\mvnw.cmd -Dsuite.file=verify-unit-api.xml test

# Selenium only
.\mvnw.cmd -Dsuite.file=ui-only.xml test
```

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
```

Environment profiles (12-factor): load `config/test-config-<env>.properties` on top of the base config:
```powershell
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

---

## 4. CI/CD status

| CI | Status |
|---|---|
| GitHub Actions | [![RBA QA CI](https://github.com/rbaqa/RBASolution/actions/workflows/ci.yml/badge.svg)](https://github.com/rbaqa/RBASolution/actions/workflows/ci.yml) |

The [`ci.yml`](.github/workflows/ci.yml) workflow:
- Runs on push/PR to `main`/`master` (and via `workflow_dispatch`).
- `test` (Linux): sets up JDK 8 + cached Maven, runs the full suite **headless**,
  uploads Surefire + Allure results and failure screenshots as artifacts.
- `windows-clean-env-run` (Windows): full clean-environment lifecycle for
  servers with no pre-installed dependencies - `setup-env.ps1` provisions
  (JDK 8 + Chrome), all tests run headless, reports are generated and uploaded,
  then `setup-env.ps1 -Uninstall` tears the environment back down so **only
  reports remain**.

---

## 5. Reports

Allure + TestNG reports land under:
```
reports/allure-results/   (raw data, CI consumable)
reports/allure-report/    (generated HTML)
target/surefire-reports/  (classic TestNG XML/HTML)
reports/screenshots/      (UI failure screenshots)
logs/rba-task.log         (structured logs)
```

---

## 6. Reproducibility & note on the "1 EUR = 0.91 GBP" example

The task's example figures reflect a particular trading date and the **Prodajni**
(bank-sells-GBP) rate. Because rates change daily, the tests read and validate the
**live** rate and amount from the same backend the UI calls, asserting positivity and
internal consistency (amount ≈ rate × input) rather than hard-coding a static figure.
