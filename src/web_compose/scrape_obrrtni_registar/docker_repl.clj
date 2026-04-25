(ns web-compose.scrape-obrrtni-registar.docker-repl
  (:require [integrant.core :as ig]
            [web-compose.core :as core]))

(defn -main [& _]
  (ig/init core/docker-config)
  @(promise))
