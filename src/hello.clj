(ns hello-world
  (:use compojure.core, compojure.route, ring.adapter.jetty, ring.middleware.params,
        hiccup.core, hiccup.form, hiccup.page
        com.ashafa.clutch.view-server dorothy.core)
  (:require [compojure.route :as route]
            [clojure.xml :as xml] 
            [clojure.contrib.string :as str] 
            [clojure.contrib.math :as math]
            [com.ashafa.clutch :as clutch]
            [incanter.core :as incanter]
            [incanter.charts :as chart])
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream)
           (java.net URLEncoder
                     URLDecoder
                     URL)
           (org.jfree.chart StandardChartTheme)
           (org.jfree.chart.axis CategoryLabelPositions)))

#_(clutch/configure-view-server "resilience" (view-server-exec-string))

(def db "resilience")
(def lowlight "grey")
(def highlight "cornflowerblue")
(def positive "red")
(def negative "cornflowerblue")
(def default-data-file "Mike%20-%20FCM%20Cork%20Harbour.csv")
(def default-format "matrix")
(def strengths {:H+ 0.75 :M+ 0.5 :L+ 0.25 :H- -0.75 :M- -0.5 :L- -0.25})

(defn base-path [params] (str "/resilience/" (params :id)))

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
(defn num-weight [weight] (float (/ (url-weight weight) 4)))
(defn display-weight [weight] (str (num-weight weight)))

(defn create-user [email]
  (clutch/with-db db
    (if-let [user (seq (clutch/get-view "users" :by-user {:key email}))]
      (clutch/put-document (merge (:value (first user)) {:user email}))
      (clutch/put-document {:user email}))))


(defn edit-links [params]
  (clutch/with-db db
    (let [node-types [:drivers :pressures :state-changes :impacts :responses ]
          g {}
          links (:links (clutch/get-document (params :id)))
          nodes (:nodes (clutch/get-document (params :id)))
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
          links-graph (reduce #(let [w (:weight (get links (keyword (str (:head (val %2)) (:tail (val %2))))))]
                                 (if (and (nodes (keyword (:head (val %2))))
                                            (nodes (keyword (:tail (val %2)))))
                                   (assoc-in %1 [[(:tail (val %2)) (:head (val %2))]]
                                             {:tooltip (display-weight w)
                                              :weight (str (math/abs (url-weight w)))
                                              :penwidth (str (math/abs (url-weight w)))
                                              :color (if (> (url-weight w) 0) "blue" "red")
                                              :constraint :true
                                              :head_lp "10,10"})
                                   %1))
                              {} links)
          nodes-subgraph (fn [node-type] (into [{:rank :same}] (for [[k v] (node-type nodes-graph)] [k v])))
          links-subgraph (into [{:splines :true :ranksep "1.2" :stylesheet "/css/style.css"}]
                               (for [[[j k] v] links-graph] [(keyword j) (keyword k) v]))
          dot-out (dot (digraph (apply vector (concat
                                               (map #(subgraph % (nodes-subgraph %)) node-types)
                                               #_(reduce ;;draw nodes
                                                (fn [nts nt]
                                                  (reduce
                                                   (fn [m v] (cons v m))
                                                   nts
                                                   (nodes-subgraph nt))) [] node-types)
                                               links-subgraph
                                               #_(map #(vector % {:style :invis}) (conj node-types :end))
                                               #_(reduce ;;links from node-type names to each node of that type
                                                (fn [nts nt]
                                                  (reduce
                                                   (fn [m v] (cons [nt (first v) {:style :invis}] m))
                                                   nts
                                                   (nodes-subgraph nt))) [] node-types)
                                               #_[(conj node-types :end {:style :invis :weight "3"})]))))]
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
    (let [doc (clutch/get-document (params :id))
          links (:links doc)
          nodes (:nodes doc)
          p(println params)]
      (case (params :mode)
        "login"    (let [id (:_id (create-user (params "email")))]
                     {:status 303
                      :headers {"Location" (str "/resilience/" id "/mode/edit")}})
        "add"      (do
                     (clutch/update-document
                      (merge doc
                             {:nodes (merge nodes
                                            {(encode-nodename (params "element")) (params "element")})}))
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
                                 {:links (dissoc links
                                                 (keyword (str (params :node)
                                                               (params :tail))))}))
                         (clutch/put-document
                          (merge doc
                                 {:links
                                  (merge links
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
        (let [causes
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
                                    (num-weight           ;;cell value
                                     (:weight (get links (keyword
                                                          (str (name (first tail))
                                                               (name (first head)))))))
                                    0.0))               ;;no link, =zero
                                nodes)))
                      nodes))
              states (incanter/matrix 1 (count nodes) 1)
              out (incanter/plus (incanter/mmult causes states) states)
              chart (doto (chart/bar-chart (vals nodes) out :x-label ""
                                           :y-label "")
                      (chart/set-theme (StandardChartTheme. "theme"))
                      (->
                       .getPlot
                       .getDomainAxis
                       (.setCategoryLabelPositions
                        (CategoryLabelPositions/createUpRotationLabelPositions (/ Math/PI 6.0)))))
              out-stream (ByteArrayOutputStream.)
              in-stream (do
                          (incanter/save chart out-stream :width 600 :height 400)
                          (ByteArrayInputStream. 
                           (.toByteArray out-stream)))]
          {:status 200
           :headers {"Content-Type" "image/png"}
           :body in-stream})
        "edit"
        (xhtml
         [:head
          [:script {:src "/js/script.js"}]
          [:style {:type "text/css"} "@import \"/css/style.css\";"]]
         [:body (edit-links (conj params {:format "cmapx" }))
          [:div
           [:div {:style "clear: both;margin: 20px"}
            [:div {:style "float: left;margin-right: 10px"}
             (form-to [:get (str (base-path params) "/mode/save/" (params :tail) "/" (params :node) "/"
                                 (if-weight (params :weight)))]
                      (submit-button "Save"))]
            [:div {:style "float: left;margin-right: 10px"}
             (form-to [:get (str (base-path params) "/mode/download")]
                      (submit-button "Download"))]
            [:div {:style "float: left;margin-right: 10px"}
             (form-to [:post (str (base-path params) "/mode/login")]
                      (text-field "email"
                                  (if-let [doc (clutch/get-document (params :id))]
                                    (:user doc)
                                    ""))
                      (submit-button "Login"))]]
           [:div {:style "clear: both;margin: 20px"}
            [:div {:style "float: left;margin-right: 10px"}
             "Drivers"
             (form-to [:post (str (base-path params) "/mode/add")]
                      (drop-down "element" drivers)
                      (submit-button "Add"))]
            [:div {:style "float: left;margin-right: 10px"}
             "Pressures"
             (form-to [:post (str (base-path params) "/mode/add")]
                      (drop-down "element" pressures)
                      (submit-button "Add"))]
            [:div {:style "float: left;margin-right: 10px"}
             "State Changes"
             (form-to [:post (str (base-path params) "/mode/add")]
                      (drop-down "element" state-changes)
                      (submit-button "Add"))]
            [:div {:style "float: left;margin-right: 10px"}
             "Impacts"
             (form-to [:post (str (base-path params) "/mode/add")]
                      (drop-down "element" impacts)
                      (submit-button "Add"))]
            [:div {:style "float: left;margin-right: 10px"}
             "Responses"
             (form-to [:post (str (base-path params) "/mode/add")]
                      (drop-down "element" responses)
                      (submit-button "Add"))]]]
          [:div {:style "clear: both;float: left;width 60%"}
           (edit-links (assoc-in params [:format] "img"))]
          [:div {:style "float: right;width 40%"}
           [:img {:src (str (base-path params) "/mode/bar")}]]]
         [:script {:src "/js/script.js"}])))))

;; define routes
(defroutes webservice
  ;;links for editing
  (GET "/resilience/:id/:format/edit" {params :params} (edit-links params))
  (GET "/resilience/:id/:format/edit/:node" {params :params} (edit-links params))
  (GET "/resilience/:id/:format/edit/:tail/:node" {params :params} (edit-links params))
  (GET "/resilience/:id/:format/edit/:tail/:node/:weight" {params :params} (edit-links params))
  (GET "/resilience/mode/:mode" {params :params} (edit-links-html (assoc params "id" "guest")))
  (GET "/resilience/:id/mode/:mode" {params :params} (edit-links-html params))
  (POST "/resilience/:id/mode/:mode" {params :params} (edit-links-html params))
  (POST "/resilience/mode/:mode" {params :params} (edit-links-html (assoc params "id" "guest")))
  (GET "/resilience/:id/mode/:mode/:node" {params :params} (edit-links-html params))
  (GET "/resilience/:id/mode/:mode/:tail/:node" {params :params} (edit-links-html params))
  (GET "/resilience/:id/mode/:mode/:tail/:node/:weight" {params :params} (edit-links-html params))
  (GET "/resilience/test" {params :params} (edit-links-html {"mode" "edit"}))
  
  (resources "/"))


(run-jetty (wrap-params webservice) {:port 8000})
