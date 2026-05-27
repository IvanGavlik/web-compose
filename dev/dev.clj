(ns dev
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [web-compose.core :as core]
            [portal.api :as p]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.data.csv :as csv]))

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


(def portal (p/open))

(defn read-edn [path]
  (with-open [r (java.io.PushbackReader.
                 (io/reader path))]
             (edn/read r)))

(defn read-all-edn [file]
      (with-open [r (java.io.PushbackReader.
                     (io/reader file))]
                 (doall
                  (take-while
                   #(not= ::eof %)
                   (repeatedly
                    #(edn/read {:eof ::eof} r))))))

(defn normalize-value [value replace]
  (if value
    (let [new-value (-> value
                        (str/replace replace "")
                        (str/trim))]
      (if (= "" new-value)
        nil
        new-value))))

(def normalize-item
  (fn [map-item]
    (-> map-item
        (assoc :Mob (normalize-value (:Mob map-item) "Mobitel:"))
        (assoc :Zaposlenih (normalize-value (:Zaposlenih map-item) "Zaposlenih:"))
        (assoc :Web (normalize-value (:Web map-item) "Web stranica:"))
        (assoc :GodinaOsnivanja (normalize-value (:GodinaOsnivanja map-item) "Godina osnivanja:"))
        (assoc :KontaktOsoba (normalize-value (:KontaktOsoba map-item) "Odgovorna osoba:"))
        (assoc :Ime (:ime map-item))
        (dissoc :ime)
        (dissoc :Logo)
        (dissoc :OstaliPodaciNaslov)
        (dissoc :RadnoVrijeme)
        (dissoc :Vlasnistvo))))

(defn write-csv [path row-data]
  (let [columns (keys (first row-data))
        headers (map name columns)
        rows (mapv #(mapv % columns) row-data)]
    (with-open [file (io/writer path)]
      (csv/write-csv file (cons headers rows)))))

(defn test-postmortem-data-edn-into-csv []
  (let [path "C:\\Users\\ivang\\Projects\\web-compose\\test\\postmortem\\data.edn"
        data (read-all-edn path)
        normalize-data (map normalize-item data)
        path-csv "C:\\Users\\ivang\\Projects\\web-compose\\test\\postmortem\\data.csv"]
    (write-csv path-csv normalize-data)))

(comment
  (test-postmortem-data-edn-into-csv))