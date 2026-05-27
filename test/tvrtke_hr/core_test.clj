(ns tvrtke-hr.core-test
  (:require [clojure.test :refer :all]
            [etaoin.api :as e]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def postmortem (str (io/file "test" "postmortem")))

(def base-url "https://www.tvrtke.hr")

(def next-page {:css "span.SelectedPage + a"})

(def skip-ids #{"FirmaPodaci_divPodaciKontakt"})

(def chrome-opts
  {:path-browser "C:\\Users\\ivang\\Documents\\google-drive\\chrome-149\\chrome-win64\\chrome-win64\\chrome.exe"
   :headless true
   :log-stdout   (str postmortem "\\stdout.log")
   :log-stderr   (str postmortem "\\stderr.log")})

(def data-file (str (io/file "test" "postmortem" "data.edn")))

(defn append-edn [data]
  (spit data-file (str (pr-str data) "\n") :append true))

(defn extract-details [driver name]
  (let [fields (e/query-all driver {:css "[id^='FirmaPodaci_div']"})]
    (append-edn
      (into {:ime name}
            (keep (fn [field]
                    (let [id (e/get-element-attr-el driver field "id")]
                      (when (and id (not (contains? skip-ids id)))
                        (let [key   (keyword (str/replace id "FirmaPodaci_div" ""))
                              value (str/trim (e/get-element-text-el driver field))]
                          [key value]))))
                  fields)))))

(defn navigate-table [driver]
  (let [entries (mapv (fn [item]
                        {:name (e/get-element-text-el driver (e/query-from driver item {:css ".divItemName"}))
                         :href (e/get-element-attr-el driver (e/query-from driver item {:tag :a}) "href")})
                      (e/query-all driver {:css ".divItemSlide2"}))]
    (doseq [{:keys [name href]} entries]
      (e/go driver (str base-url href))
      (e/wait driver 2)
      (when (e/exists? driver {:id "FirmaPodaci_divPodaciKontakt"})
        (extract-details driver name))
      (e/back driver)
      (e/wait driver 1))))

(defn navigate-page
  ([driver] (navigate-page driver true))
  ([driver have-next-page]
   (when (and have-next-page)
     (navigate-table driver)
     (e/click driver next-page)
     (e/wait driver 1)
     (recur driver
            (e/exists? driver next-page)))))

(deftest tvrtke
  (e/with-chrome chrome-opts driver
    (e/with-postmortem driver {:dir postmortem}
      (e/doto-wait 1 driver
        (e/go (str base-url "/"))
        (e/click {:tag :button :fn/text "Prihvaćam"})
        (e/fill {:css "#ddDjelatnost"} "Knji")
        (e/wait-visible {:css "div[option='Knjigovodstvene usluge']"})
        (e/click {:css "div[option='Knjigovodstvene usluge']"})
        (e/click {:tag :button :fn/text "PRETRAŽI"})
        (navigate-page))
      (is (string? (e/get-title driver))))))
