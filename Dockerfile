FROM clojure:temurin-21-tools-deps-bookworm-slim

# Install Chrome + ChromeDriver + Xvfb (virtual display for non-headless Chrome)
RUN apt-get update && apt-get install -y \
    wget gnupg unzip curl xvfb \
    && wget -q -O - https://dl.google.com/linux/linux_signing_key.pub \
       | gpg --dearmor > /etc/apt/trusted.gpg.d/google.gpg \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" \
       > /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y google-chrome-stable \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN CHROME_VERSION=$(google-chrome --version | grep -oP '\d+\.\d+\.\d+\.\d+') \
    && echo "Installing ChromeDriver for Chrome $CHROME_VERSION" \
    && wget -q "https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chromedriver-linux64.zip" \
       -O /tmp/chromedriver.zip \
    && unzip /tmp/chromedriver.zip -d /tmp/ \
    && mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/chromedriver \
    && chmod +x /usr/local/bin/chromedriver \
    && rm -rf /tmp/chromedriver* \
    && chromedriver --version

WORKDIR /app

# Cache dependencies (layer invalidated only when deps.edn changes)
COPY deps.edn .
RUN mkdir -p src && clojure -P -M:scraper-repl

COPY src/ src/

RUN mkdir -p /app/output

EXPOSE 3000
EXPOSE 7888

COPY entrypoint.sh .
RUN chmod +x entrypoint.sh

ENTRYPOINT ["./entrypoint.sh"]
