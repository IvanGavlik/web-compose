#!/bin/bash
# Start virtual display so Chrome runs without --headless (better WAF fingerprint)
Xvfb :99 -screen 0 1920x1080x24 -ac &
export DISPLAY=:99
sleep 3
exec clojure -M:scraper-repl
