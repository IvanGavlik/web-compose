(ns web-compose.core
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [nrepl.server :as nrepl]
            [etaoin.api :as e]
            [chime.core :as chime]
            [java-time.api :as t]
            [web-compose.config :as cfg]
            [web-compose.handler :as handler]
            [web-compose.scrape-obrrtni-registar.core :as scraper]
            [web-compose.scrape-obrrtni-registar.scheduler :as scheduler]))

(def services-config-path
  (or (System/getenv "SERVICES_CONFIG_PATH")
      "config/services.edn"))

(def config
  {::services {:path services-config-path}
   ::server   {:port     (or (some-> (System/getenv "PORT") Integer/parseInt) 3000)
               :services (ig/ref ::services)}})

(def docker-config
  {::services {:path services-config-path}
   ::server   {:port     (or (some-> (System/getenv "PORT") Integer/parseInt) 3000)
               :services (ig/ref ::services)}
   ::browser  {}
   ::nrepl    {:port 7888}
   ::scraper  {:hour (or (some-> (System/getenv "SCRAPE_HOUR") Integer/parseInt) 2)}})

(defmethod ig/init-key ::services [_ {:keys [path]}]
  (cfg/load-services path))

(defmethod ig/init-key ::server [_ {:keys [port services]}]
  (println (str "Starting server on http://localhost:" port))
  (jetty/run-jetty (handler/create-app services) {:port port :join? false}))

(defmethod ig/halt-key! ::server [_ server]
  (.stop server))

(defmethod ig/init-key ::browser [_ _]
  (println "Starting headless Chrome...")
  (scraper/start-browser!)
  (e/go @scraper/driver scraper/search-url)
  (Thread/sleep 4000)
  (println "Browser ready.")
  @scraper/driver)

(defmethod ig/halt-key! ::browser [_ _]
  (scraper/stop-browser!))

(defmethod ig/init-key ::nrepl [_ {:keys [port]}]
  (println (str "Starting nREPL on port " port))
  (nrepl/start-server :port port :bind "0.0.0.0"))

(defmethod ig/halt-key! ::nrepl [_ server]
  (.close server))

(defmethod ig/init-key ::scraper [_ {:keys [hour]}]
  (println (str "Scheduling daily scrape at " hour ":00 UTC"))
  (let [now    (t/zoned-date-time (t/zone-id "UTC"))
        today  (-> now (.withHour hour) (.withMinute 0) (.withSecond 0) (.withNano 0))
        start  (if (.isBefore (t/instant) (t/instant today))
                 (t/instant today)
                 (t/instant (.plusDays today 1)))
        schedule (chime/chime-at
                   (chime/periodic-seq start (t/duration 1 :days))
                   (fn [_] (scheduler/run-scrape!)))]
    (println (str "Next scrape scheduled for: " start))
    schedule))

(defmethod ig/halt-key! ::scraper [_ schedule]
  (.close schedule))

(defn -main [& args]
  (ig/init config))
