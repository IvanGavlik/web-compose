FROM clojure:temurin-21-tools-deps-bookworm-slim

WORKDIR /app

# Cache dependencies (layer invalidated only when deps.edn changes)
COPY deps.edn .
RUN mkdir -p src && clojure -P -M:scraper-repl

COPY src/ src/

RUN mkdir -p /app/output

EXPOSE 3000
EXPOSE 7888


# Web server only — no Chrome, no scraper, no nREPL. See deps.edn :run alias
# and web-compose.core/config (as opposed to docker-config).
CMD ["clojure", "-M:run"]
