# web-compose


## Prerequisites

A
## Running the app directly

```bash
clj -M:run
```

Visit [http://localhost:3000](http://localhost:3000). Stop with `Ctrl+C`.

---

## REPL-driven development

### 1. Start the nREPL server

```bash
clj -M:nrepl
```

This starts an nREPL server on port **7888** with the `dev` namespace on the classpath. 

### 2. Start the app from the REPL

Once connected, run in the REPL:

```clojure
(require 'dev)

(dev/start)    ; start the server
(dev/stop)     ; stop the server
(dev/restart)  ; restart after code changes
```

## Aliases

| Alias    | Purpose                                  |
|----------|------------------------------------------|
| `:run`   | Run the app as a standalone process      |
| `:nrepl` | Start an nREPL server on port 7888       |
| `:dev`   | Add dev-time deps (ring-devel + nrepl)   |

## TODO

* docker file for reander
* how to pass env from reander - docker - app 