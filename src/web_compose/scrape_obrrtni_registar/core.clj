(ns web-compose.scrape-obrrtni-registar.core
  (:require [etaoin.api :as e]
            [hickory.core :as hick]
            [hickory.select :as sel]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.util Base64]))

(def search-url  "https://pretrazivac-obrta.gov.hr/pretraga")
(def detail-base "https://pretrazivac-obrta.gov.hr/detalji.htm?id=")

;; ---------------------------------------------------------------------------
;; Search parameter builder
;; ---------------------------------------------------------------------------

(def default-search-params
  {:redirectUrl              nil
   :id                       nil
   :recaptchaToken           nil
   ;; Obrt (business) search
   :obrtNaziv                nil
   :obrtMbo                  nil
   :obrtTduId                nil
   :obrtBrojObrtnice         nil
   :obrtBrRegUloska          nil
   :obrtVrsta                nil
   :obrtObavljanje           nil
   :obrtEmail                nil
   :obrtWwwAdresa            nil
   :obrtUlica                nil
   :obrtKucniBroj            nil
   :obrtNaseljeId            nil
   :obrtOpcinaIliGradId      nil
   :obrtZupanijaId           nil
   ;; Obrt status flags
   :obrtStanjeURadu          true
   :obrtStanjeMirovanje      true
   :obrtStanjePrivObust      false
   :obrtStanjeBezPocetka     false
   :obrtStanjeOdjava         false
   :obrtStanjePreseljen      false
   :obrtStanjaList           [1 4]
   ;; Vlasnik (owner) search
   :vlasnikImePrezime        nil
   :vlasnikMbg               nil
   :vlasnikOib               nil
   :pretraziVlasnikaUPasivi  false
   :vlasnikUlica             nil
   :vlasnikKucniBroj         nil
   :vlasnikNaseljeId         nil
   :vlasnikOpcinaIliGradId   nil
   :vlasnikZupanijaId        nil
   ;; Pogon (branch) search
   :pogonNaziv               nil
   :pogonObavljanje          nil
   :pogonEmail               nil
   :pogonWwwAdresa           nil
   :pogonUlica               nil
   :pogonKucniBroj           nil
   :pogonNaseljeId           nil
   :pogonOpcinaIliGradId     nil
   :pogonZupanijaId          nil
   :pogonStanjeURadu         true
   :pogonStanjePrivObust     true
   :pogonStanjeBezPocetka    true
   :pogonStanjaList          [1 2 3]
   ;; Activity (djelatnost)
   :djelatnostId             nil
   :djelatnost2025Id         nil
   :pretezitaDjelatnost      false
   :pretezitaDjelatnost2025  false
   :djelatnostIdLista        nil
   :djelatnost2025IdLista    nil
   :grupaDjelatnosti         []
   :grupaDjelatnosti2025     []
   :action                   nil})

(defn encode-search-param
  "Takes a search-params map (merged with defaults) and returns a base64-encoded JSON string."
  [params]
  (let [merged (merge default-search-params params)
        json-str (json/generate-string merged)]
    (.encodeToString (Base64/getEncoder) (.getBytes json-str "UTF-8"))))

(def default-search-param
  "Pre-encoded default: obrtStanjeURadu=true, obrtStanjeMirovanje=true, all pogon statuses."
  (encode-search-param {}))

;; ---------------------------------------------------------------------------
;; Browser (etaoin) — one shared Chrome instance
;; ---------------------------------------------------------------------------

(defonce driver (atom nil))

(def stealth-args
  [;; No --headless — Chrome runs on Xvfb virtual display (better WAF fingerprint)
   "--no-sandbox"
   "--disable-dev-shm-usage"
   "--window-size=1920,1080"
   "--disable-blink-features=AutomationControlled"
   "--use-gl=swiftshader"])

(defn start-browser!
  "No args = Docker headless Chrome.
   Pass a path = local dev with visible Chrome window."
  ([]
   (reset! driver (e/chrome {:path-driver "/usr/local/bin/chromedriver"
                              :args stealth-args})))
  ([local-driver-path]
   (reset! driver (e/chrome {:path-driver local-driver-path
                              :args stealth-args}))))

(defn stop-browser! []
  (when @driver (e/quit @driver))
  (reset! driver nil))

;; ---------------------------------------------------------------------------
;; Search / pagination via JS fetch inside the browser (same-origin, no WAF)
;; ---------------------------------------------------------------------------

;; aaData column indices returned by the DataTables endpoint:
;; 0 = id  1 = naziv-pogona  2 = naziv-obrta  3 = vlasnik  4 = adresa  5 = status
(defn fetch-page-js
  "Executes the DataTables POST inside the browser via fetch — same-origin so WAF passes it.
   search-param may be a pre-encoded base64 string or a params map (will be encoded)."
  ([offset] (fetch-page-js offset 100 default-search-param))
  ([offset page-size search-param]
   (let [encoded (if (map? search-param)
                   (encode-search-param search-param)
                   search-param)]
     (when-not (str/starts-with? (e/get-url @driver) search-url)
       (e/go @driver search-url)
       (e/wait 2))
     (let [js (str "
       const params = new URLSearchParams({
         sEcho:1, iColumns:6, sColumns:'',
         iDisplayStart:" offset ", iDisplayLength:" page-size ",
         mDataProp_0:0,mDataProp_1:1,mDataProp_2:2,mDataProp_3:3,mDataProp_4:4,mDataProp_5:5,
         iSortingCols:1,iSortCol_0:0,sSortDir_0:'asc',
         bSortable_0:true,bSortable_1:true,
         bSortable_2:false,bSortable_3:false,bSortable_4:false,bSortable_5:false,
         iRecordsTotal:0,sortKolona:'nazivPogona',sortSmjer:'asc',
         searchParam:'" encoded "'
       });
       const r = await fetch('/pretraga?izvrsiDohvat', {
         method:'POST',
         headers:{'Content-Type':'application/x-www-form-urlencoded; charset=UTF-8',
                  'X-Requested-With':'XMLHttpRequest'},
         body: params.toString()
       });
       return await r.json();
     ")
           result (e/js-async @driver js [])]
       result))))

(defn total-records [page-data]
  (get page-data "iTotalRecords"))

(defn extract-rows
  "Returns a seq of maps with the 6 list-page columns for each row."
  [page-data]
  (map (fn [[id naziv-pogona naziv-obrta vlasnik adresa status]]
         {:id           id
          :naziv-pogona naziv-pogona
          :naziv-obrta  naziv-obrta
          :vlasnik      vlasnik
          :adresa       adresa
          :status       status})
       (get page-data "aaData")))

(defn extract-ids [page-data]
  (map :id (extract-rows page-data)))

(defn fetch-all-rows
  "Fetches all pages and returns a lazy seq of row maps."
  ([] (fetch-all-rows 100 default-search-param))
  ([page-size search-param]
   (let [first-page (fetch-page-js 0 page-size search-param)
         total      (total-records first-page)]
     (println "Total records:" total)
     (concat
      (extract-rows first-page)
      (mapcat (fn [offset]
                (println "Fetching offset" offset "/" total)
                (extract-rows (fetch-page-js offset page-size search-param)))
              (range page-size total page-size))))))

(defn fetch-all-ids
  ([] (fetch-all-ids 100 default-search-param))
  ([page-size search-param]
   (map :id (fetch-all-rows page-size search-param))))

;; ---------------------------------------------------------------------------
;; Detail page scraping
;; ---------------------------------------------------------------------------

(defn node-text [node]
  (cond
    (string? node) (str/trim node)
    (map? node)    (str/trim (apply str (map node-text (:content node))))
    :else          ""))

(defn label->value [rows label-text]
  (->> rows
       (some (fn [row]
               (when (map? row)
                 (let [tds (->> (:content row)
                                (filter #(and (map? %) (= :td (:tag %)))))]
                   (when (= label-text (some-> tds first node-text))
                     (some-> tds second node-text))))))))

(defn parse-detail [html]
  (let [tree (-> html hick/parse hick/as-hickory)
        rows (sel/select (sel/tag :tr) tree)
        lv   (partial label->value rows)]
    {:naziv-obrta      (lv "Naziv obrta:")
     :prezime-i-ime    (lv "Prezime i ime:")
     :oib              (lv "OIB:")
     :mbg              (lv "MBG:")
     :mbo              (lv "MBO:")
     :vrsta-obrta      (lv "Vrsta obrta:")
     :obavljanje-obrta (lv "Obavljanje obrta:")
     :broj-obrtnice    (lv "Broj obrtnice:")
     :br-reg-uloska    (lv "Br. reg. uloška:")
     :ulica            (lv "Ulica:")
     :kucni-broj       (lv "Kućni broj:")
     :naselje          (lv "Naselje:")
     :opcina-ili-grad  (lv "Općina/Grad:")
     :zupanija         (lv "Županija:")
     :email            (lv "E-mail:")
     :www              (lv "WWW adresa:")}))

(defn fetch-detail [id]
  (e/go @driver (str detail-base id))
  (e/wait-visible @driver {:tag :div :class "detaljiContainer"} {:timeout 10})
  (let [html (e/get-source @driver)]
    (parse-detail html)))

(defn scrape-detail [id]
  (assoc (fetch-detail id) :id id))

;; ---------------------------------------------------------------------------
;; REPL entry points
;; ---------------------------------------------------------------------------

(comment
  ;; 1. Start browser
  ;; Option A: Docker (chromedriver on PATH)
  (start-browser!)
  ;; Option B: local explicit path
  (start-browser! "C:/path/to/chromedriver.exe")

  ;; 2. Navigate to the search page so WAF challenge completes once
  (e/go @driver search-url)
  (e/wait 3)

  ;; 3. Build a custom search — e.g. only active businesses in Zagreb county (id=21)
  (encode-search-param {:obrtZupanijaId 21 :obrtStanjeMirovanje false})

  ;; 4. Test one page — returns raw DataTables JSON
  (def first-page (fetch-page-js 0))
  (total-records first-page)    ; => 133327
  (extract-rows first-page)     ; => ({:id .. :naziv-pogona .. :vlasnik .. :adresa .. :status ..} ...)
  (extract-ids first-page)

  ;; 5. Fetch all rows from the list (no detail page visits needed for basic data)
  (def all-rows (into [] (fetch-all-rows)))
  ;; or with a custom search:
  (def all-rows (into [] (fetch-all-rows 100 {:obrtZupanijaId 21})))

  ;; 6. Fetch full detail for one business
  (scrape-detail 1018021)
  ;; => {:id 1018021 :naziv-obrta "..." :prezime-i-ime "..." :oib "..." :zupanija "..." ...}

  ;; 7. Full detail run for a small batch
  (def results (mapv scrape-detail (take 5 (map :id all-rows))))

  ;; 8. Cleanup
  (stop-browser!)
  )
