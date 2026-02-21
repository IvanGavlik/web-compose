FROM clojure:temurin-21-tools-deps-bookworm-slim

WORKDIR /app

# Cache dependencies (layer invalidated only when deps.edn changes)
COPY deps.edn .
RUN clojure -P

# Copy application source
COPY src/ src/

EXPOSE 3000
CMD ["clojure", "-M:run"]
