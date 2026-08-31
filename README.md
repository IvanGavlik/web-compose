# web-compose

## Prerequisites

## Running the app directly

* Web server only (port 3000) `clj -M:run`
* Web server + Chrome + nREPL + scraper `clj -M:scraper-repl` 
  * Chrome need + driver 

Visit [http://localhost:3000](http://localhost:3000). Stop with `Ctrl+C`.

---

## REPL-driven development

### Start a standalone nREPL (without the app)

`clj -M:nrepl`
This starts nREPL on port 7888 but doesn't start the web server or scraper. Useful if you want a bare REPL to
experiment.

### Start the app from the REPL

Once connected, run in the REPL:

```clojure
(require 'dev)

(dev/start)    ; start the server
(dev/stop)     ; stop the server
(dev/restart)  ; restart after code changes
```

### 

## Aliases

| Alias    | Purpose                                  |
|----------|------------------------------------------|
| `:run`   | Run the app as a standalone process      |
| `:nrepl` | Start an nREPL server on port 7888       |
| `:dev`   | Add dev-time deps (ring-devel + nrepl)   |


## Run with docker 

Build:
`docker build -t web-compose .`

Run:
`docker compose up`

or without compose:
`docker run -p 3000:3000 -p 7888:7888 --shm-size=2g -v ./config:/app/config -v ./output:/app/output -e SCRAPE_HOUR=2 -e OUTPUT_FILE=/app/output/results.jsonl web-compose`

Verify:
* Web server `curl http://localhost:3000/`
* scrapper 

## Which repl to use
User Remote Repl

type nRepl context module web compose conect to server host localhost port 7888

## How to reload changes use 

`docker compose restart`

## Registry scraper (obrtni registar)

The `web-compose.scrape-obrrtni-registar` scraper (headless Chrome + daily
scrape job against pretrazivac-obrta.gov.hr) is **off by default**, including
in Docker. It is a local/dev-only tool and **must not be enabled in
production** (Render) — it launches a full Chrome instance and runs an
unattended scraper against a third-party government site.

To turn it on locally, uncomment in `docker-compose.yml`:

```yaml
environment:
  - ENABLE_SCRAPER=true
  - SCRAPE_HOUR=2   # UTC hour for the daily scrape
```

then `docker compose up --build`. Leave it commented out (or unset) anywhere
else, including `render.yaml`.

## TODO

* email send message to both : uspjenso ste poslali upit, and one on the targe (already implemented)
* expose api
