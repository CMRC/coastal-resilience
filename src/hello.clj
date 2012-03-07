(ns hello-world
  (:use compojure.core, ring.adapter.jetty, hiccup.core, hiccup.page-helpers,
        hiccup.form-helpers)
  (:require [compojure.route :as route] 
            [clojure.contrib.string :as str] 
            [clojure.contrib.math :as math])
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream)
           GraphViz
           (java.net URLEncoder
                     URLDecoder
                     URL)))
(def lowlight "grey")
(def highlight "cornflowerblue")
(def positive "red")
(def negative "cornflowerblue")
(def default-data-file "Mike%20-%20FCM%20Cork%20Harbour.csv")
(def default-format "matrix")
(def strengths {:H+ 0.75 :M+ 0.5 :L+ 0.25 :H- -0.75 :M- -0.5 :L- -0.25})


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

(def nodes ["ECONOMIC GROWTH"
            "ENVIRONMENTAL INSTITUTIONS"
            "REGULATORY DRIVERS"
            "PHYSICAL DRIVERS"
            "ECONOMIC DRIVERS"
            "LAND FORM DYNAMICS"
            "EXTRACTION/POLLUTION DYNAMICS"
            "CONSERVATION DYNAMICS"
            "HABITATS/LANDFORMS"
            "SOCIETAL WELLBEING"
            "BIOLOGICAL SERVICES"
            "HABITAT/LANDFORM SERVICES"
            "CULTURAL SERVICES"
            "GOVERNANCE RESPONSES"
            ])

(defn urlify-nodes []
  (map #(vector (encode-nodename %) %) nodes))
  
(def links (ref {}))

(defn if-weight [weight] (if weight weight "0"))
(defn url-weight [weight] (- (mod (Integer/parseInt (if-weight weight)) 7) 3))
(defn inc-weight [weight] (str (mod (inc (Integer/parseInt (if-weight weight))) 7)))
(defn display-weight [weight] (str (float (/ (url-weight weight) 4))))
    
(defn edit-links [params]
  (let [gv (GraphViz.)]
    (.addln gv (.start_graph gv))
    (dorun (map #(.addln gv (str (first %) "[URL=\"/resilience/mode/edit/"
                                 (when-let [node (params "node")]
                                   (str node "/"))
                                 (first %) "\""
                                 (if (= (first %) (params "node")) ",color=blue,style=filled"
                                     )
                                 "];"))
                (urlify-nodes)))
    (dorun (map #(let [w (:weight (get @links [(:head (val %)) (:tail (val %))]))]
                   (.addln gv (str (:tail (val %)) "->" (:head (val %)) "[label=\""
                                   (display-weight w) "\",weight=" (math/abs (url-weight w)) "];"))) @links))
    (when-let [tail (params "tail")]
      (.addln gv (str tail "->" (params "node")
                      "[label=\"" (display-weight (params "weight")) "\",URL=\"/resilience/mode/edit/"
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
                                    :body(String. (.getGraph gv (.getDotSource gv) "cmapx"))})))

(defn edit-links-html [params]
  (case (params "mode")
    "save"     (do (if (= (params "weight") "3") ;;maps to zero
                     (dosync
                      (alter links dissoc @links [(params "node") (params "tail")]))
                     (dosync
                      (alter links conj @links [[(params "node") (params "tail")]
                                                {:head (params "node")
                                                 :tail (params "tail")
                                                 :weight (params "weight")}])))
                   {:status 303
                    :headers {"Location" "/resilience/mode/edit"}})
    "download" {:status 200
                :headers {"Content-Type" "text/csv"
                          "Content-Disposition" "attachment;filename=matrix.csv"}
                :body (str "," (apply str (map #(str % \,) nodes)) "\n"
                           (apply str (map (fn [tail] (str (second tail) \,
                                                           (apply str (map (fn [head] (str (if (= (first tail) (:tail (get @links [(first head) (first tail)])))
                                                                                             (display-weight (:weight (get @links [(first head) (first tail)])))
                                                                                             "0.0") \,))
                                                                           (urlify-nodes)))
                                                           "\n")) (urlify-nodes))))}
    "edit"
    (html5 (:body (edit-links (conj params {"format" "cmapx" })))
           [:a {:href (str "/resilience/mode/save/" (params "tail") "/" (params "node") "/"
                           (if-weight (params "weight")))} "save"]
           " "
           [:a {:href "/resilience/mode/download"} "download"]
           (if-let [node (params "node")]
             (str "<img src=\"/resilience/img/edit/"
                  (when-let [tail (params "tail")] (str tail "/"))
                  node
                  (when-let [weight (params "weight")] (str "/" weight))
                  "\" ismap usemap=\"#G\" />")
             "<img src=\"/resilience/img/edit\" ismap usemap=\"#G\" />"))))
  
;; define routes
(defroutes webservice
  ;;links for editing
  (GET "/resilience/:format/edit" {params :params} (edit-links params))
  (GET "/resilience/:format/edit/:node" {params :params} (edit-links params))
  (GET "/resilience/:format/edit/:tail/:node" {params :params} (edit-links params))
  (GET "/resilience/:format/edit/:tail/:node/:weight" {params :params} (edit-links params))
  (GET "/resilience/mode/:mode" {params :params} (edit-links-html params))
  (GET "/resilience/mode/:mode/:node" {params :params} (edit-links-html params))
  (GET "/resilience/mode/:mode/:tail/:node" {params :params} (edit-links-html params))
  (GET "/resilience/mode/:mode/:tail/:node/:weight" {params :params} (edit-links-html params))
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
