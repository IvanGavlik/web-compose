(ns web-compose.handler
  (:require [compojure.core :refer [GET POST routes]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [web-compose.contact :as contact]))

(defn create-app [services]
  (-> (routes
       (GET  "/" [] {:status 200 :body "<h1>Hello, World!</h1>"})
       (POST "/api/contact" req (contact/contact-handler services req))
       (route/not-found {:status 404 :body {:error "Not found"}}))
      (wrap-json-body {:keywords? true})
      wrap-json-response))
