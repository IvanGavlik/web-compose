(ns web-compose.handler
  (:require [compojure.core :refer [GET POST routes]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.cors :refer [wrap-cors]]
            [web-compose.contact :as contact]))

(defn create-app [services]
  (-> (routes
       (GET  "/" [] {:status 200 :body "<h1>Hello, World!</h1>"})
       (POST "/api/contact" req (contact/contact-handler services req))
       (route/not-found {:status 404 :body {:error "Not found"}}))
      (wrap-json-body {:keywords? true})
      wrap-json-response
      (wrap-cors :access-control-allow-origin  [#".*"]
                 :access-control-allow-methods [:get :post :options]
                 :access-control-allow-headers ["Content-Type"])))
