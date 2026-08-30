# Self-contained build & test image for the RBA QA suite.
# Runs the full suite (unit + API + Selenium) headless in a container.
#
# Build:   docker build -t rba-qa -f docker/Dockerfile .
# Run:     docker run --rm -v "$(pwd)/reports:/workspace/reports" rba-qa
FROM bellsoft/liberica-openjdk-debian:8

# Install Maven, Chrome and the matching Chromedriver is fetched at runtime by WebDriverManager
RUN apt-get update && apt-get install -y --no-install-recommends \
        maven curl unzip xvfb \
        fonts-liberation libasound2 libatk-bridge2.0-0 libatk1.0-0 \
        libc6 libcairo2 libcups2 libdbus-1-3 libexpat1 libfontconfig1 \
        libgbm1 libgtk-3-0 libnspr4 libnss3 libpango-1.0-0 libpangocairo-1.0-0 \
        libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxcursor1 libxdamage1 \
        libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 \
        xdg-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

# Headless Selenium execution
ENV ENV_BROWSER_HEADLESS=true

# Run the full suite (unit + API + Selenium)
CMD ["./mvnw", "-B", "-Dsuite.file=testng.xml", "test"]
