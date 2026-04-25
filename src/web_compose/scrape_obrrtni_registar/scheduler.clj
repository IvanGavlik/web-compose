(ns web-compose.scrape-obrrtni-registar.scheduler
  (:require [web-compose.scrape-obrrtni-registar.core :as scraper]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def output-file (or (System/getenv "OUTPUT_FILE") "/app/output/results.jsonl"))

(defn already-scraped-ids []
  (if (.exists (io/file output-file))
    (->> (slurp output-file)
         str/split-lines
         (remove str/blank?)
         (map #(get (json/parse-string %) "id"))
         set)
    #{}))

(defn run-scrape! []
  (println "Scrape started:" (java.util.Date.))
  (let [scraped (already-scraped-ids)
        all-ids (scraper/fetch-all-ids)
        new-ids (remove scraped all-ids)]
    (with-open [w (io/writer output-file :append true)]
      (doseq [id new-ids]
        (try
          (let [result (scraper/scrape-detail id)]
            (.write w (str (json/generate-string result) "\n")))
          (catch Exception e
            (println "Error scraping id" id (.getMessage e))))))
    (println "Scrape done:" (java.util.Date.))))
