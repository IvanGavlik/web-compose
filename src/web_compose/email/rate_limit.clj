(ns web-compose.email.rate-limit)

(def ^:private max-requests 3)
(def ^:private window-ms (* 15 60 1000)) ; 15 minutes

(defonce ^:private state (atom {}))

(defn allow? [ip]
  (let [now   (System/currentTimeMillis)
        entry (get @state ip)]
    (if (or (nil? entry)
            (> (- now (:window-start entry)) window-ms))
      (do (swap! state assoc ip {:count 1 :window-start now})
          true)
      (if (< (:count entry) max-requests)
        (do (swap! state update-in [ip :count] inc)
            true)
        false))))
