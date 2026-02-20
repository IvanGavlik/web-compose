(ns web-compose.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn load-services [path]
  (log/info "Loading services config from" path)
  (-> path io/file slurp edn/read-string))

(defn lookup [services app-id service-id]
  (get-in services [(keyword app-id) (keyword service-id) :data]))
