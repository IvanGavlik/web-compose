(ns web-compose.email.validation)

(def ^:private email-re #"^[^@\s]+@[^@\s]+\.[^@\s]+$")

(defn validate-contact [{:keys [name email message]}]
  (let [errors (cond-> {}
                 (or (nil? name) (empty? (str name)))
                 (assoc :name "Name is required")

                 (> (count (str name)) 100)
                 (assoc :name "Name must be 100 characters or fewer")

                 (or (nil? email) (empty? (str email)))
                 (assoc :email "Email is required")

                 (and (not (empty? (str email)))
                      (not (re-matches email-re (str email))))
                 (assoc :email "Invalid email format")

                 (or (nil? message) (empty? (str message)))
                 (assoc :message "Message is required")

                 (< (count (str message)) 10)
                 (assoc :message "Message must be at least 10 characters")

                 (> (count (str message)) 2000)
                 (assoc :message "Message must be 2000 characters or fewer"))]
    (if (empty? errors)
      {:valid true}
      {:errors errors})))
