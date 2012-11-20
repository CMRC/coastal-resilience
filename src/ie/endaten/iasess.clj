(ns ie.endaten.iasess
  (:gen-class)
  (:use compojure.core, compojure.route, ring.adapter.jetty, ring.middleware.params,
        hiccup.core, hiccup.form, hiccup.page
        dorothy.core clojure.contrib.math)
  (:require [compojure.route :as route]
            [clojure.xml :as xml] 
            [clojure.contrib.string :as str] 
            [clojure.contrib.math :as math]
            [com.ashafa.clutch :as clutch]
            [incanter.core :as incanter]
            [incanter.charts :as chart])
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream
                    OutputStreamWriter)
           (java.net URLEncoder
                     URLDecoder
                     URL)
           (java.awt.geom Rectangle2D$Double)
           (org.jfree.chart StandardChartTheme)
           (org.jfree.chart.axis CategoryLabelPositions)
           (org.apache.batik.transcoder TranscoderInput
                                        TranscoderOutput)
           (org.apache.batik.dom GenericDOMImplementation)
           (org.apache.batik.svggen SVGGraphics2D)))

#_(clutch/configure-view-server "resilience" (view-server-exec-string))

(def db "resilience")
(def lowlight "grey")
(def highlight "cornflowerblue")
(def positive "red")
(def negative "cornflowerblue")
(def default-data-file "Mike%20-%20FCM%20Cork%20Harbour.csv")
(def default-format "matrix")
(def strengths {:H+ 0.75 :M+ 0.5 :L+ 0.25 :H- -0.75 :M- -0.5 :L- -0.25})

(defn exportChartAsSVG
  [chart]
  ;;Get a DOMImplementation and create an XML document
  (let [domImpl (GenericDOMImplementation/getDOMImplementation)
        document (.createDocument domImpl nil "svg" nil)
        ;; Create an instance of the SVG Generator
        svgGenerator (SVGGraphics2D. document)
        rect (Rectangle2D$Double. 0 0 600 300)
        draw (.draw chart svgGenerator rect)
        ;; Write svg file
        outputStream (ByteArrayOutputStream.)
        out (OutputStreamWriter. outputStream "UTF-8")
        gen (.stream svgGenerator out true)]
    (.flush outputStream)
    (.close outputStream)
    outputStream))
    
(defn base-path [params] (str "/iasess" (when (params :id) "/") (params :id)))

(defn encode-nodename
  [nodename]
  (URLEncoder/encode (str/replace-re #"[^a-zA-Z0-9]" "" nodename)))

(def drivers       ["Environmental Legislation and Policy"
                    "Tourism and Recreation"
                    "Residential Development"
                    "Fisheries"
                    "Agriculture"
                    "Commerce, Industry & Manufacturing"
                    "Aquaculture"])

(def pressures     ["Roads and Transport Infrastructure"
                    "Terrestrial Traffic"
                    "Coastal Population Growth"
                    "Coastal Squeeze"
                    "Coastal Access Points"
                    "Port and Marina Facilities"
                    "Marine Traffic"
                    "Commuter Belts /Urban Sprawl"
                    "EROSION"
                    "COASTAL INUNDATION/FLOODING"
                    "DROUGHT"
                    "Local Coastal Processes (OTHER)"
                    "Terrestrial Surface Water Pollution"
                    "Marine Pollution"
                    "Commercial Fishing"
                    "Soil Contamination"
                    "Demand for Resource Access"
                    "Enforcement: Environmental Protection"])

(def state-changes ["Benthos"
                    "Cliff Systems"
                    "Sea Water Quality"
                    "Avoidance of Harmful Algal Blooms"
                    "River Systems"
                    "Wetlands"
                    "Dune Systems"
                    "Ecological Niches: Native Species"
                    "Local Employment"
                    "Community Cohesion"
                    "Integrated Coastal Development"])

(def impacts       ["Food Provision: Marine Organisms"
                    "Food Provision: Terrestrial Agriculture"
                    "Inshore Marine Productivity"
                    "Bioremediation: Waste Processing and Removal"
                    "Flood Protection: Storm surge/Tidal/Fluvial"
                    "Sea-Level Rise Buffering"
                    "Raw Material Provision"
                    "Fresh Water Supply"
                    "Marine Transport and Navigation"
                    "Coastal Amenity: Leisure/Recreation"
                    "Habitable Land: Secure Coastal Development"
                    "Cultural Heritage"])

(def responses     ["NGO Protest"
                    "Civil Society Lobbying"
                    "Voluntary Community Initiatives"
                    "Champions"
                    "Seek Investment: EU/National/Private"
                    "Economic Diversification"
                    "Increased commercial Exploitation"
                    "Increased Exploitation: Other Marine Sp."
                    "Individual Insurance Cover"
                    "Local Authority Planning/Zoning"
                    "Introduction/Enforcement of bye-laws"
                    "Payment of EU Fines"
                    "Construction of Coastal/Flood Defences"
                    "Re-location away from coast"])

(defn if-weight [weight] (if weight weight "0"))
(defn url-weight [weight] (- (mod (Integer/parseInt (if-weight weight)) 7) 3))
(defn inc-weight [weight] (str (mod (inc (Integer/parseInt (if-weight weight))) 7)))
(defn num-weight [weight] (if (= (class weight) String)
                            (float (/ (url-weight weight) 4))
                            weight))
(defn display-weight [weight] (str (num-weight weight)))

(defn create-user [email]
  (clutch/with-db db
    (if-let [user (seq (clutch/get-view "users" :by-user {:key email}))]
      (clutch/put-document (merge (:value (first user)) {:user email}))
      (clutch/put-document {:user email :nodes {} :links {}}))))


(defn edit-links [params nodes links]
  (clutch/with-db db
    (let [node-types [:drivers :pressures :state-changes :impacts :responses ]
          g {}
          nodes-graph (reduce
                       #(if (some #{(second %2)} drivers)
                          (assoc-in %1 [:drivers (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "lightblue"
                                     :fillcolor "white"})
                          (if (some #{(second %2)} responses)
                            (assoc-in %1 [:responses (first %2)]
                                      {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                       :shape :circle
                                       :width "1"
                                       :fixedsize :true
                                       :fontsize "10"
                                       :style :filled
                                       :color "skyblue"
                                       :fillcolor "white"})
                            (if (some #{(second %2)} pressures) 
                              (assoc-in %1 [:pressures (first %2)]
                                        {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                         :shape :circle
                                         :width "1"
                                         :fixedsize :true
                                         :fontsize "10"
                                         :style :filled
                                         :color "steelblue"
                                         :fillcolor "white"})
                              (if (some #{(second %2)} impacts) 
                                (assoc-in %1 [:impacts (first %2)]
                                          {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                           :shape :circle
                                           :width "1"
                                           :fixedsize :true
                                           :fontsize "10"
                                           :style :filled
                                           :color "brown"
                                           :fillcolor "white"})
                                (if (some #{(second %2)} state-changes) 
                                  (assoc-in %1 [:state-changes (first %2)]
                                            {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                             :shape :circle
                                             :width "1"
                                             :fixedsize :true
                                             :fontsize "10"
                                             :style :filled
                                             :color "beige"
                                             :fillcolor "white"}))))))
                       g nodes)
          links-graph (reduce #(let [w (:weight (get links (keyword (str (:head (val %2)) (:tail (val %2))))))
                                     weight (if (= (class w) String) (num-weight w) w)]
                                 (if (and (nodes (keyword (:head (val %2))))
                                          (nodes (keyword (:tail (val %2)))))
                                   (try
                                     (assoc-in %1 [[(:tail (val %2)) (:head (val %2))]]
                                               {:tooltip (str weight)
                                                :weight (str (math/abs weight))
                                                :penwidth (str (math/abs weight))
                                                :color (if (> weight 0) "blue" "red")
                                                :constraint :true
                                                :head_lp "10,10"})
                                     (catch java.lang.ClassCastException e
                                       (println e)))
                                   %1))
                              {} links)
          nodes-subgraph (fn [node-type] (into [{:rank :same}] (for [[k v] (node-type nodes-graph)] [k v])))
          links-subgraph (into [{:splines :ortho :ranksep "1.2" :stylesheet "/iasess/css/style.css"
                                 :ratio :expand}]
                               (for [[[j k] v] links-graph] [(keyword j) (keyword k) v]))
          dot-out (dot (digraph (apply vector (concat
                                               (map #(subgraph % (nodes-subgraph %)) node-types)
                                               links-subgraph))))]
      (cond
       (= (params :format) "img") (render dot-out {:format :svg :layout :dot})
       (= (params :format) "dot")   {:status 200	 
                                     :headers {"Content-Type" "txt"}
                                     :body dot-out}))))

(defn save-views []
  (clutch/with-db db
    (clutch/save-view "users"
                      (clutch/view-server-fns
                       :clojure
                       {:by-user
                        {:map (fn [doc] [[(:user doc) doc]])}}))))
;;(save-views)


(defn edit-links-html [params]
  (clutch/with-db db
    (let [if-count (fn [c] (if (:count c) (:count c) 1))
          doc (clutch/get-document (params :id))
          models (:models doc)
          avg-weights (fn [f s] (merge f {:weight (/ (+ (* (num-weight (:weight f)) (if-count f))
                                                        (* (num-weight (:weight s)) (if-count s)))
                                                     (+ (if-count f) (if-count s)))
                                          :count (+
                                                  (if-count f)
                                                  (if-count s))}))
          links (->>
                 (:links doc)
                 (merge
                  (reduce #(let [d (clutch/get-document (name (first %2)))
                                 m (merge-with avg-weights
                                               %1 (:links d))]
                             m)
                          {}
                          models)))
          nodes (->
                 (:nodes doc)
                 (merge
                  (reduce #(let [d (clutch/get-document (name (first %2)))
                                 m (merge %1 (:nodes d))]
                             m)
                          {}
                          models)))
          losers (map #(:user (:value %)) (clutch/get-view "users" :by-user))
          users (remove #(or (nil? %) (= % "") (= % (:user doc))) losers)
          adduser (clutch/get-view "users" :by-user {:key (params "model")})]
      (case (params :mode)
        "login"    (let [id (:_id (create-user (params "email")))]
                     {:status 303
                      :headers {"Location" (str (base-path (assoc params :id id)) "/mode/edit")}})
        "add"      (do
                     (clutch/update-document
                      (merge doc
                             {:nodes (merge nodes
                                            {(encode-nodename (params "element")) (params "element")})}))
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "addmodel" (let
                       [adduser (clutch/get-view "users" :by-user {:key (params "model")})]
                     (clutch/update-document
                      (merge doc
                             {:models (merge models
                                             {(:id (first adduser)) (params "model")})}))
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "delete"   (do
                     (clutch/update-document
                      (merge doc
                             {:nodes (dissoc nodes (keyword (str (params :node))))}))
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "save"     (do (if (= (params :weight) "3") ;;maps to zero
                         (clutch/put-document
                          (merge doc
                                 {:links (dissoc (:links doc)
                                                 (keyword (str (params :node)
                                                               (params :tail))))}))
                         (clutch/put-document
                          (merge doc
                                 {:links
                                  (merge (:links doc)
                                         {(keyword (str (params :node)
                                                        (params :tail)))
                                          {:head (params :node)
                                           :tail (params :tail)
                                           :weight (params :weight)}})})))
                       {:status 303
                        :headers {"Location" (str (base-path params) "/mode/edit")}})
        "download" 
        {:status 200
         :headers {"Content-Type" "text/tab-separated-values"
                   "Content-Disposition" "attachment;filename=matrix.tsv"}
         :body (str "\t" (apply str (map #(str (second %) "\t") nodes)) "\n"     ;;header row
                    (apply str                                               
                           (map                                               ;;value rows
                            (fn [tail]                                        ;;each elem as a potential tail
                              (str (second tail) "\t"                           ;;elem name at start of row
                                   (apply str
                                          (map                                ;;each elem as a potential head
                                           (fn [head]
                                             (str (if                         ;;loop through links, finding matches
                                                      (= (name (first tail))  ;; value of key,value
                                                         (:tail (get links    ;; tail matches tail, get weight
                                                                     (keyword 
                                                                      (str (name (first head))
                                                                           (name (first tail)))))))
                                                    (display-weight           ;;cell value
                                                     (:weight (get links (keyword
                                                                          (str (name (first head))
                                                                               (name (first tail)))))))
                                                    "0.0") "\t"))               ;;no link, =zero
                                           nodes))
                                   "\n"))
                            nodes)))}
        "bar"
        (if (seq nodes)
          (let [causes
                (incanter/trans
                 (apply vector                                               
                        (map                                               ;;value rows
                         (fn [head]                                        ;;each elem as a potential head
                           (apply vector
                                  (map                                ;;each elem as a potential tail
                                   (fn [tail]
                                     (if                         ;;loop through links, finding matches
                                         (= (name (first head))  ;; value of key,value
                                            (:tail (get links    ;; tail matches tail, get weight
                                                        (keyword 
                                                         (str (name (first tail))
                                                              (name (first head)))))))
                                       (let [w (:weight (get links (keyword
                                                                       (str (name (first tail))
                                                                            (name (first head))))))]
                                         (num-weight w))
                                       0.0))               ;;no link, =zero
                                   nodes)))
                         nodes)))
                states (incanter/matrix 1 (count nodes) 1)
                squash (fn [out] (map #(/ 1 (inc (expt Math/E (unchecked-negate %)))) out))
                out (nth (iterate #(squash (incanter/plus (incanter/mmult causes %) %)) states) 10)
                minusahalf (map #(- % 0.5) out)
                chart (doto (chart/bar-chart (vals nodes) minusahalf :x-label ""
                                             :y-label "")
                        (chart/set-theme (StandardChartTheme. "theme"))
                        (->
                         .getPlot
                         .getDomainAxis
                         (.setCategoryLabelPositions
                          (CategoryLabelPositions/createUpRotationLabelPositions (/ Math/PI 3.0)))))
                out-stream (ByteArrayOutputStream.)
                in-stream (do
                            (incanter/save chart out-stream :width 600 :height 400)
                            (ByteArrayInputStream. 
                             (.toByteArray out-stream)))]
            (exportChartAsSVG chart))
          "add some nodes")
        "edit"
        (xhtml
         [:head
          [:script {:src "/iasess/js/script.js"}]
          [:style {:type "text/css"} "@import \"/iasess/css/style.css\";"]]
         [:body
          [:div
           [:div {:class "menu"}
            (form-to [:get (str (base-path params) "/mode/download")]
                     (submit-button "Download"))
            (form-to [:post (str (base-path params) "/mode/login")]
                     (text-field "email"
                                 (if-let [doc (clutch/get-document (params :id))]
                                   (:user doc)
                                   ""))
                     (submit-button "Login"))
            (form-to [:post (str (base-path params) "/mode/addmodel")]
                     (drop-down "model" users)
                     (submit-button "Add"))]
           [:div {:class "menu"}
            (form-to [:post (str (base-path params) "/mode/add")]
                     (drop-down "element" drivers)
                     (submit-button "Add"))
            (form-to [:post (str (base-path params) "/mode/add")]
                     (drop-down "element" pressures)
                     (submit-button "Add"))
            (form-to [:post (str (base-path params) "/mode/add")]
                     (drop-down "element" state-changes)
                     (submit-button "Add"))
            (form-to [:post (str (base-path params) "/mode/add")]
                     (drop-down "element" impacts)
                     (submit-button "Add"))
            (form-to [:post (str (base-path params) "/mode/add")]
                     (drop-down "element" responses)
                     (submit-button "Add"))]]
          [:div {:id "pane"}
           [:div {:id "graph"}
            (edit-links (assoc-in params [:format] "img") nodes links)]
           [:div {:id "bar"}
            (edit-links-html (assoc-in params [:mode] "bar"))
            "<iframe width=\"100%\" height=\"100%\" frameborder=\"0\" scrolling=\"no\" marginheight=\"0\" marginwidth=\"0\" src=\"http://maps.google.com/maps?q=dingle&hl=en&sll=37.0625,-95.677068&t=h&output=embed\"></iframe>"]]
          [:script {:src "/iasess/js/script.js"}]])))))
  
  ;; define routes
  (defroutes webservice
  ;;links for editing
  (GET "/iasess/:id/:format/edit/:node" {params :params} (edit-links params))
  (GET "/iasess/:id/:format/edit/:tail/:node" {params :params} (edit-links params))
  (GET "/iasess/:id/:format/edit/:tail/:node/:weight" {params :params} (edit-links params))
  (GET "/iasess/mode/:mode" {params :params} (edit-links-html (assoc params "id" "guest")))
  (GET "/iasess/:id/mode/:mode" {params :params} (edit-links-html params))
  (POST "/iasess/:id/mode/:mode" {params :params} (edit-links-html params))
  (POST "/iasess/mode/:mode" {params :params} (edit-links-html (assoc params "id" "guest")))
  (GET "/iasess/:id/mode/:mode/:node" {params :params} (edit-links-html params))
  (GET "/iasess/:id/mode/:mode/:tail/:node" {params :params} (edit-links-html params))
  (GET "/iasess/:id/mode/:mode/:tail/:node/:weight" {params :params} (edit-links-html params))
  (GET "/iasess" {params :params} (edit-links-html (assoc params :id "guest" :mode "edit")))
  
  (resources "/iasess"))

(defn -main
  "Run the jetty server."
  [& args]
  (run-jetty (wrap-params webservice) {:port 8000}))
