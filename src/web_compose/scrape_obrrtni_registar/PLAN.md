# Plan: Scraper as Integrant Component — Daily Schedule with chime

## Context
The scraper (headless Chrome via etaoin) runs as an Integrant component alongside the existing web server. It runs once per day at a fixed wall-clock time (configurable, default 02:00 UTC) using the `chime` scheduling library. Docker starts everything automatically; nREPL on port 7888 for live REPL interaction.

---

## Architecture

```
docker-config (Integrant)
  ::services  → loads config/services.edn
  ::server    → Jetty on :3000 (existing)
  ::browser   → headless Chrome, WAF warm-up on start
  ::nrepl     → nREPL on :7888
  ::scraper   → chime daily schedule (depends on ::browser)
```

---

## Files to Create / Modify

### 1. `deps.edn` — add chime + java-time, new alias

Add to `:deps`:
```clojure
io.github.jimpil/chime  {:mvn/version "0.3.3"}
clojure.java-time/clojure.java-time {:mvn/version "0.3.4"}
```

Add to `:aliases`:
```clojure
:scraper-repl {:extra-deps {nrepl/nrepl {:mvn/version "1.3.1"}}
               :main-opts  ["-m" "web-compose.scrape-obrrtni-registar.docker-repl"]}
```

---

### 2. `src/web_compose/core.clj` — add new Integrant components

Add requires: `nrepl.server`, `etaoin.api`, `chime.core`, `java-time.api`,
              `web-compose.scrape-obrrtni-registar.core`

**`::browser` component** — owns Chrome lifecycle:
```clojure
(defmethod ig/init-key ::browser [_ _]
  (scraper/start-browser!)
  (e/go @scraper/driver scraper/search-url)
  (Thread/sleep 4000)   ; WAF warm-up
  @scraper/driver)

(defmethod ig/halt-key! ::browser [_ _]
  (scraper/stop-browser!))
```

**`::nrepl` component** — owns nREPL lifecycle:
```clojure
(defmethod ig/init-key ::nrepl [_ {:keys [port]}]
  (nrepl/start-server :port port))

(defmethod ig/halt-key! ::nrepl [_ server]
  (.close server))
```

**`::scraper` component** — chime daily schedule:
```clojure
(defmethod ig/init-key ::scraper [_ {:keys [hour]}]
  ;; Run immediately on start, then every day at `hour`:00 UTC
  (let [schedule (chime/chime-at
                   (cons (t/instant)
                         (chime/periodic-seq
                           (-> (t/zoned-date-time (t/zone-id "UTC"))
                               (.withHour hour) (.withMinute 0) (.withSecond 0)
                               t/instant)
                           (t/days 1)))
                   (fn [_] (sched/run-scrape!)))]
    schedule))

(defmethod ig/halt-key! ::scraper [_ schedule]
  (.close schedule))
```

**`docker-config`** — all 5 components:
```clojure
(def docker-config
  {::services {:path services-config-path}
   ::server   {:port (or (some-> (System/getenv "PORT") Integer/parseInt) 3000)
               :services (ig/ref ::services)}
   ::browser  {}
   ::nrepl    {:port 7888}
   ::scraper  {:hour (or (some-> (System/getenv "SCRAPE_HOUR") Integer/parseInt) 2)}})
```

---

### 3. NEW: `src/web_compose/scrape_obrrtni_registar/scheduler.clj`

Contains `run-scrape!` — the actual scrape logic called both by chime and manually from REPL:
```clojure
(ns web-compose.scrape-obrrtni-registar.scheduler
  (:require [web-compose.scrape-obrrtni-registar.core :as scraper]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def output-file (or (System/getenv "OUTPUT_FILE") "/app/output/results.jsonl"))

(defn already-scraped-ids []
  (if (.exists (io/file output-file))
    (->> (slurp output-file)
         clojure.string/split-lines
         (map #(get (json/parse-string %) "id"))
         set)
    #{}))

(defn run-scrape! []
  (println "Scrape started:" (java.util.Date.))
  (let [scraped (already-scraped-ids)
        all-ids (scraper/fetch-all-ids)
        new-ids (remove scraped all-ids)]
    (with-open [w (io/writer output-file :append true)]
      (doseq [id new-ids]
        (try
          (let [result (scraper/scrape-detail id)]
            (.write w (str (json/generate-string result) "\n")))
          (catch Exception e
            (println "Error scraping id" id (.getMessage e))))))
    (println "Scrape done:" (java.util.Date.))))
```

---

### 4. NEW: `src/web_compose/scrape_obrrtni_registar/docker_repl.clj`

Thin entry point — starts Integrant system and blocks:
```clojure
(ns web-compose.scrape-obrrtni-registar.docker-repl
  (:require [integrant.core :as ig]
            [web-compose.core :as core]))

(defn -main [& _]
  (ig/init core/docker-config)
  @(promise))
```

---

### 5. `Dockerfile.scraper` — update CMD

```dockerfile
RUN clojure -P -M:scraper-repl

CMD ["clojure", "-M:scraper-repl"]
```

---

### 6. `docker-compose.yml` — add env vars

```yaml
services:
  scraper:
    build:
      context: .
      dockerfile: Dockerfile.scraper
    ports:
      - "7888:7888"
    volumes:
      - ./src:/app/src
      - ./output:/app/output
    shm_size: '2gb'
    environment:
      - SCRAPE_HOUR=2
      - OUTPUT_FILE=/app/output/results.jsonl
```

---

## chime + Integrant integration

chime's `chime-at` returns a `java.io.Closeable`. Integrant's `halt-key!` just calls `.close` on it:

```
ig/init  → chime-at returns schedule handle → stored as component value
ig/halt! → (.close schedule)               → chime stops, no more runs
```

## Manual REPL trigger

`run-scrape!` is a plain function — call it any time from the REPL:

```clojure
(require '[web-compose.scrape-obrrtni-registar.scheduler :as sched])
(sched/run-scrape!)   ; runs immediately, regardless of schedule
```

---

## Startup Sequence

```
docker compose up --build
  → clojure -M:scraper-repl
    → ig/init docker-config
      → ::services   loads config/services.edn
      → ::server     Jetty starts on :3000
      → ::browser    Chrome starts headless, navigates to search page (WAF, 4s)
      → ::nrepl      nREPL listening on :7888
      → ::scraper    chime: runs immediately + schedules daily at 02:00 UTC

Editor connects → localhost:7888
  → (r/fetch-page-js 0)        ; Chrome already warm
  → (sched/run-scrape!)        ; trigger manually any time
```

---

## Verification
1. `docker compose up --build` — logs show all 5 components starting
2. Connect editor to `localhost:7888`
3. Eval `(r/fetch-page-js 0)` → `{:iTotalRecords 133327 ...}`
4. Eval `(sched/run-scrape!)` → check `./output/results.jsonl` for new lines
5. Each line: `{"id":1018021,"naziv-obrta":"...","prezime-i-ime":"..."}`
