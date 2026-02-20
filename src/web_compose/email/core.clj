(ns web-compose.email.core
  (:require [postal.core :as postal]
            [clojure.tools.logging :as log]))

(defn send-contact! [{:keys [gmail-user gmail-app-pass contact-to]}
                     {:keys [name email message]}]
  (try
    (postal/send-message
     {:host "smtp.gmail.com"
      :port 587
      :tls  true
      :user gmail-user
      :pass gmail-app-pass}
     {:from    gmail-user
      :to      contact-to
      :subject (str "Contact form message from " name)
      :body    (str "Name:    " name "\n"
                    "Email:   " email "\n\n"
                    message)})
    (log/info "Email sent from" email)
    :ok
    (catch Exception e
      (log/error e "Failed to send email")
      {:error (.getMessage e)})))
