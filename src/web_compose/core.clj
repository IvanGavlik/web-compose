(ns web-compose.core
  (:require [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [web-compose.config :as cfg]
            [web-compose.handler :as handler]))

(def config
  {::services {:path "config/services.edn"}
   ::server   {:port 3000 :services (ig/ref ::services)}})

(defmethod ig/init-key ::services [_ {:keys [path]}]
  (cfg/load-services path))

(defmethod ig/init-key ::server [_ {:keys [port services]}]
  (println (str "Starting server on http://localhost:" port))
  (jetty/run-jetty (handler/create-app services) {:port port :join? false}))

(defmethod ig/halt-key! ::server [_ server]
  (.stop server))

(defn -main [& args]
  (ig/init config))
