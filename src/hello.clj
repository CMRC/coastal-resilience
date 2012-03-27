(ns hello-world
  (:use compojure.core, ring.adapter.jetty, hiccup.core, hiccup.page-helpers,
        hiccup.form-helpers
        com.ashafa.clutch.view-server)
  (:require [compojure.route :as route] 
            [clojure.contrib.string :as str] 
            [clojure.contrib.math :as math]
            [com.ashafa.clutch :as clutch])
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream)
           GraphViz
           (java.net URLEncoder
                     URLDecoder
                     URL)))

(clutch/configure-view-server "resilience" (view-server-exec-string))

(def db "resilience")
(def lowlight "grey")
(def highlight "cornflowerblue")
(def positive "red")
(def negative "cornflowerblue")
(def default-data-file "Mike%20-%20FCM%20Cork%20Harbour.csv")
(def default-format "matrix")
(def strengths {:H+ 0.75 :M+ 0.5 :L+ 0.25 :H- -0.75 :M- -0.5 :L- -0.25})

(defn base-path [params] (str "/resilience/" (params "id")))

(defn encode-nodename
  [nodename]
  (URLEncoder/encode (str/replace-re #"[^a-zA-Z0-9]" "" nodename)))

(defn node-string
  [nodename strength url dir format data-file fillcolour]
   (str nodename " [shape=box,URL=\"/resilience/strength/"
        strength "/node/" url "/dir/" dir "/format/" format "/data/" data-file "\",color="
        fillcolour ",style=filled];"))
   
(defn to-dot [line head id strength dir gv]
  (let [linevec (str/split #"\t" line)
        nodename (first linevec)
        idv (map vector (iterate inc 0) (next linevec))]
    (doseq [[index value] idv]
      (let [weight (Float/parseFloat value)
            arrowhead (nth head (+ index 1))
            comparees  (list nodename arrowhead)
            comparee (if (= dir "in") (remove #{nodename} comparees)
                         (if (= dir "out") (remove #{arrowhead} comparees)
                             comparees))
            min-weight (if (= strength "strong") 
                         0.6 
                         (if (= strength "medium") 0.3 0.0))
            highlighted (some #(or (= id "all")
                                  (= (encode-nodename %1) 
                                     (encode-nodename id))) comparee)
            weight-factor 10
            colour (if highlighted (if (> weight 0) positive negative)
                       lowlight)]
        (if (> (math/abs weight) min-weight) 
          (.addln gv 
                  (str nodename "->" 
                       (nth head (+ index 1)) 
                       "[headlabel =\"" (* weight 4) 
                       "\",weight=" (* (math/abs weight) weight-factor) 
                       ",color=" colour 
                       "];")))))))

(defn dot-from-lifemap [line id strength dir data-file format gv]
  (let [linevec (str/split #"," line)
        nodename (first linevec)
        url (encode-nodename nodename)
        arrowhead (nth linevec 2)
        fillcolour (if (not= "all" id) 
                     (if (= url (URLEncoder/encode id)) highlight lowlight) highlight)
        arrowhead-fillcolour (if (not= "all" id) 
                     (if (= (encode-nodename arrowhead) (URLEncoder/encode id)) highlight lowlight) highlight)
        weight ((keyword (nth linevec 1)) strengths)
        comparees  (list nodename arrowhead)
        comparee (if (= dir "in") (remove #{nodename} comparees)
                     (if (= dir "out") (remove #{arrowhead} comparees)
                         comparees))
        min-weight (if (= strength "strong") 
                     0.6 
                     (if (= strength "medium") 0.3 0.0))
        highlighted (some #(or (= id "all")
                               (= (encode-nodename %1) 
                                  (encode-nodename id))) comparee)
        weight-factor 10
        colour (if highlighted (if (> weight 0) positive negative)
                   lowlight)
        dot-string (str "\"" nodename "\" -> " 
                        "\"" arrowhead "\""
                        " [headlabel =\"" (* weight 4) 
                        "\",weight=" (* (math/abs weight) weight-factor) 
                        ",color=" colour 
                        "];")
        nodename-string (node-string (str "\"" nodename "\"") strength url dir format data-file fillcolour)
        arrowhead-string (node-string (str "\"" arrowhead "\"") strength (encode-nodename arrowhead) dir format data-file arrowhead-fillcolour)]
    (.addln gv arrowhead-string)
    (.addln gv nodename-string) 
    (if (> (math/abs weight) min-weight) 
      (.addln gv dot-string))))

(defn print-node
  [nodename id strength dir data-file format gv]
  (let [url (encode-nodename nodename)
        fillcolour (if (not= "all" id) 
                     (if (= url (URLEncoder/encode id)) highlight lowlight) highlight)]
    (.addln gv (node-string nodename strength url dir format data-file fillcolour ))))

(defn do-graph
  [id strength dir data-file format]
  (def gv (GraphViz.))
  (with-open [fr (if (re-find #"^http:" data-file)
                   (java.io.InputStreamReader. (.openStream (URL. data-file)))
                   (java.io.FileReader. (str "data/" (URLDecoder/decode data-file))))
              br (java.io.BufferedReader. fr)]
    (if (= format "LifeMap")
      (let 	     	
          [ls (line-seq br)]
        (.addln gv (.start_graph gv))
        (doseq [line ls] (dot-from-lifemap line id strength dir data-file format gv))
        (.addln gv (.end_graph gv))
        #_(println (.getDotSource gv)))
      (let 	     	
          [ls (line-seq br)
           head (str/split #"\t" (first ls))]
        (.addln gv (.start_graph gv))
        (doseq [nodename (next head)] (print-node nodename id strength dir data-file format gv))
        (doseq [line (next ls)] (to-dot line head id strength dir gv))
        (.addln gv (.end_graph gv))))))

(defn do-map
  [id strength dir data-file format]
  (do-graph id strength dir data-file format)
  (let [graph (.getGraph gv (.getDotSource gv) "cmapx")]
    (String. graph)))

(defn graph-viz
  ([id strength dir data-file format]
     (graph-viz id strength dir "no" data-file format))
  ([id strength dir link data-file format]
     (do-graph id strength dir data-file format)
     (let [graph (.getGraph gv (.getDotSource gv) "png")
           in-stream (do
                       (ByteArrayInputStream. graph))]
       {:status 200	 
        :headers {"Content-Type" "image/png"}
        :body in-stream})))

(defn html-doc 
  ([id strength dir data-file format]
     (html-doc id strength dir "no" data-file format))
  ([id strength dir link data-file format]
     (html5
      (do-map id strength dir data-file format)
      [:div {:style "float: left;width: 400px"}
       (form-to [:get "/resilience/new"]
                (text-field "URL" data-file)
                (text-field "Format" format)
                (submit-button "Submit"))]
      [:div {:style "float: left;width: 200px"}
       [:ul
        (doall (map #(html5 [:li (if (= strength %)
                                   %
                                   [:a {:href (str "/resilience/strength/" % "/node/" id "/dir/" dir "/format/" format "/data/" data-file)} %])])
                    ["weak" "medium" "strong"]))]]
      (if (not= id "all")
        [:div
         [:div {:style "float: left;width: 200px"}
          [:ul
           (doall
            (map
             #(html5 [:li
                      (if(= dir %)
                        %
                        [:a {:href (str "/resilience/strength/" strength "/node/" id "/dir/" % "/format/" format "/data/" data-file)} %])])
             ["in" "out" "inout"]))]]
         [:div {:style "float: left;width: 200px"}
          [:ul [:li [:a {:href (str "/resilience/strength/" strength "/node/all/dir/inout/format/" format "/data/" data-file)} "all"]]]]])
      [:div {:style "clear: both"}
       (str "<IMG SRC=\"/resilience/strength/" strength "/img/" id "/dir/" dir "/format/" format "/data/" data-file "\" border=\"0\" ismap usemap=\"#G\" />")])))

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
                    "Commuter Belts/Urban Sprawl"
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
                    "Fresh Water Supply "
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
(defn display-weight [weight] (str (float (/ (url-weight weight) 4))))

(defn create-user [email]
  (clutch/with-db db
    (if-let [user (seq (clutch/get-view "users" :by-user {:key email}))]
      (clutch/put-document (merge (:value (first user)) {:user email}))
      (clutch/put-document {:user email}))))

(defn edit-links [params]
  (clutch/with-db db
    (let [gv (GraphViz.)
          links (:links (clutch/get-document (params "id")))
          nodes (:nodes (clutch/get-document (params "id")))]
      (.addln gv (.start_graph gv))
      (dorun (map #(.addln gv (str (first %) "[shape=box,URL=\"" (base-path params) "/mode/edit/"
                                   (when-let [node (params "node")]
                                     (str node "/"))
                                   (name (first %)) "\""
                                   (if (= (name (first %)) (params "node")) ",color=blue,style=filled"
                                       )
                                   ",label=\"" (second %) "\"];"))
                  nodes))
      (dorun (map #(let [w (:weight (get links (keyword (str (:head (val %)) (:tail (val %))))))]
                     (.addln gv (str (:tail (val %)) "->" (:head (val %)) "[label=\""
                                     (display-weight w) "\",weight=" (math/abs (url-weight w)) "];"))) links))
      (when-let [tail (params "tail")]
        (.addln gv (str tail "->" (params "node")
                        "[label=\"" (display-weight (params "weight")) "\",URL=\"" (base-path params) "/mode/edit/"
                        tail "/"
                        (params "node")
                        "/" (inc-weight (params "weight")) "\",weight=0,color=blue,style=dashed]")))
      (.addln gv (.end_graph gv))
      (cond
       (= (params "format") "img") (let [graph (.getGraph gv (.getDotSource gv) "png")
                                         in-stream (do
                                                     (ByteArrayInputStream. graph))]
                                     {:status 200	 
                                      :headers {"Content-Type" "image/png"}
                                      :body in-stream})
       (= (params "format") "dot")   {:status 200	 
                                      :headers {"Content-Type" "txt"}
                                      :body(.getDotSource gv)}
       (= (params "format") "cmapx") {:status 200	 
                                      :headers {"Content-Type" "txt"}
                                      :body(String. (.getGraph gv (.getDotSource gv) "cmapx"))}))))

(defn save-views []
  (clutch/with-db db
    (clutch/save-view "users"
                      (clutch/view-server-fns
                       :clojure
                       {:by-user
                        {:map (fn [doc] [[(:user doc) doc]])}}))))

(defn edit-links-html [params]
  (clutch/with-db db
    (let [doc (clutch/get-document (params "id"))
          links (:links doc)
          nodes (:nodes doc)]
      (case (params "mode")
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
        "save"     (do (if (= (params "weight") "3") ;;maps to zero
                         (clutch/put-document
                          (merge doc
                                 {:links (dissoc links
                                                 (keyword (str (params "node")
                                                               (params "tail"))))}))
                          (clutch/put-document
                          (merge doc
                                 {:links
                                  (merge links
                                         {(keyword (str (params "node")
                                                        (params "tail")))
                                          {:head (params "node")
                                           :tail (params "tail")
                                           :weight (params "weight")}})})))
                       {:status 303
                        :headers {"Location" (str (base-path params) "/mode/edit")}})
        "download" 
        {:status 200
         :headers {"Content-Type" "text/csv"
                   "Content-Disposition" "attachment;filename=matrix.csv"}
         :body (str "," (apply str (map #(str (second %) \,) nodes)) "\n"
                    (apply str (map (fn [tail] (str (second tail) \,
                                                    (apply str (map (fn [head] (str (if (= (first tail) (:tail (get links [(first head) (first tail)])))
                                                                                      (display-weight (:weight (get links [(first head) (first tail)])))
                                                                                      "0.0") \,))
                                                                    nodes))
                                                    "\n")) nodes)))}
        "edit"
        (html5 (:body (edit-links (conj params {"format" "cmapx" })))
               [:div {:style "float: left;margin-right: 10px"}
                (form-to [:get (str (base-path params) "/mode/save/" (params "tail") "/" (params "node") "/"
                                    (if-weight (params "weight")))]
                         (submit-button "Save"))]
               [:div {:style "float: left;margin-right: 10px"}
                (form-to [:get (str (base-path params) "/mode/download")]
                         (submit-button "Download"))]
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
                         (submit-button "Add"))]
               [:div {:style "float: left;margin-right: 10px"}
                "email"
                (form-to [:post (str (base-path params) "/mode/login")]
                         (text-area "email"
                                    (if-let [doc (clutch/get-document (params "id"))]
                                      (:user doc)
                                      ""))
                         (submit-button "Login"))]
               [:div {:style "clear: both;margin: 20px"}
                (if-let [node (params "node")]
                  (str "<img src=\"" (base-path params) "/img/edit/"
                       (when-let [tail (params "tail")] (str tail "/"))
                       node
                       (when-let [weight (params "weight")] (str "/" weight))
                       "\" ismap usemap=\"#G\" />")
                  [:img {:src (str (base-path params) "/img/edit") :ismap "" :usemap "#G" :style "width:800;height:600"}])])))))
  
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
  (GET "/resilience/:id/mode/:mode/:node" {params :params} (edit-links-html params))
  (GET "/resilience/:id/mode/:mode/:tail/:node" {params :params} (edit-links-html params))
  (GET "/resilience/:id/mode/:mode/:tail/:node/:weight" {params :params} (edit-links-html params))
  (GET "/resilience/test" {params :params} (edit-links-html {"mode" "edit"}))
  
  (GET ["/resilience/strength/:strength/node/:id/dir/:dir/new/:link/format/:format/data/:data-file" :data-file #".*$"] [id strength dir link data-file format] (html-doc id strength dir link data-file format) )
  (GET ["/resilience/strength/:strength/node/:id/dir/:dir/format/:format/data/:data-file" :data-file #".*$"] [id strength dir link data-file format] (html-doc id strength dir data-file format) )
  (GET ["/resilience/strength/:strength/node/:id/dir/:dir/data/:data-file" :data-file #".*$"] [id strength dir data-file] (html-doc id strength dir data-file default-format) )
  (GET "/resilience/strength/:strength/node/:id/dir/:dir" [id strength dir] (html-doc id strength dir default-data-file default-format) )
  (GET "/resilience/strength/:strength/node/:id" [id strength] (html-doc id strength "out" default-data-file default-format) )
  (GET "/resilience/strength/:strength/node/" [strength] (html-doc "all" strength "out" default-data-file default-format) )
  (GET "/resilience/strength/:strength" [strength] (html-doc "all" strength "out" default-data-file default-format) )
  (GET "/resilience/node/:id" [id] (html-doc id "weak" "out" default-data-file default-format) )
  (GET "/resilience/" [] (html-doc "all" "weak" "out" default-data-file default-format) )
  (GET ["/resilience/data/:data-file" :data-file #".*$"] [data-file] (html-doc "all" "weak" "out" data-file default-format) )
  (GET "/resilience/" [] (html-doc "all" "weak" "out" default-data-file default-format) )
  ;;probably don't need half of these
  (GET ["/resilience/strength/:strength/img/:id/dir/:dir/format/:format/data/:data-file" :data-file #".*$"] [id strength dir data-file format] (graph-viz id strength dir data-file format) )
  (GET ["/resilience/strength/:strength/img/:id/dir/:dir/data/:data-file" :data-file #".*$"] [id strength dir link data-file] (graph-viz id strength dir link data-file default-format) )
  (GET ["/resilience/strength/:strength/img/:id/dir/:dir/new/:link/format/:format/data/:data-file" :data-file #".*$"] [id strength dir link data-file format] (graph-viz id strength dir link data-file format) )
  (GET ["/resilience/strength/:strength/img/:id/dir/:dir/new/:linkdata/:data-file" :data-file #".*$"] [id strength dir data-file] (graph-viz id strength dir data-file default-format) )
  (GET "/resilience/strength/:strength/img/:id/dir/:dir" [id strength dir] (graph-viz id strength dir default-data-file default-format) )
  (GET "/resilience/strength/:strength/img/:id" [id strength] (graph-viz id strength "out" default-data-file default-format) )
  (GET "/resilience/strength/:strength/img/" [strength] (graph-viz "all" strength "out" default-data-file default-format) )
  (GET "/resilience/img/:id" [id] (graph-viz id "weak" "out") )
  (GET "/resilience/img/" [] (graph-viz "all" "weak" "out") )
  (GET "/resilience/new" {params :params} (html-doc "all" "weak" "out" (params "URL") (params "Format"))))


(run-jetty webservice {:port 8000})
