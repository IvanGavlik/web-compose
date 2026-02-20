(ns dev
  (:require [integrant.core :as ig]
            [web-compose.core :as core]))

(defonce system nil)

(defn start []
  (alter-var-root #'system (fn [_] (ig/init core/config)))
  :started)

(defn stop []
  (when system
    (ig/halt! system)
    (alter-var-root #'system (fn [_] nil)))
  :stopped)

(defn restart []
  (stop)
  (start))
