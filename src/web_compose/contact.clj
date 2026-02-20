(ns web-compose.contact
  (:require [clojure.tools.logging :as log]
            [web-compose.config :as cfg]
            [web-compose.email.validation :as v]
            [web-compose.email.rate-limit :as rl]
            [web-compose.email.core :as email]))

(defn- client-ip [request]
  (or (get-in request [:headers "x-forwarded-for"])
      (:remote-addr request)
      "unknown"))

(defn contact-handler [services request]
  (let [ip         (client-ip request)
        body       (:body request)
        app-id     (:app-id body)
        service-id (:service-id body)]
    (log/info "Contact request from" ip "app:" app-id "service:" service-id)

    (if-not (rl/allow? ip)
      (do (log/warn "Rate limit exceeded for" ip)
          {:status 429
           :body   {:error "Too many requests. Please try again later."}})

      (let [svc-data (cfg/lookup services app-id service-id)]
        (if-not svc-data
          (do (log/warn "Unknown app-id/service-id" app-id service-id)
              {:status 400
               :body   {:error (str "Unknown app-id '" app-id "' or service-id '" service-id "'")}})

          (let [result (v/validate-contact body)]
            (if (:errors result)
              (do (log/warn "Validation failed for" ip (:errors result))
                  {:status 422
                   :body   {:errors (:errors result)}})

              (let [send-result (email/send-contact! svc-data body)]
                (if (= send-result :ok)
                  {:status 200
                   :body   {:status "ok"}}
                  {:status 500
                   :body   {:error "Failed to send message. Please try again."}})))))))))
