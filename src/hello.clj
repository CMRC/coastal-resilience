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
            weight-factor 40
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

(defn dot-from-lifemap [line head id strength dir gv]
  (let [linevec (str/split #"," line)
        nodename (first linevec)
        weight ((keyword (nth linevec 1)) strengths)
        arrowhead (nth linevec 2)
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
        weight-factor 1
        colour (if highlighted (if (> weight 0) positive negative)
                   lowlight)
        dot-string (str "\"" nodename "\" -> " 
                        "\"" arrowhead "\""
                        " [headlabel =\"" (* weight 4) 
                        "\",weight=" (* (math/abs weight) weight-factor) 
                        ",color=" colour 
                        "];")]
    (if (> (math/abs weight) min-weight) 
      (.addln gv dot-string))))

(defn print-node
  [nodename id strength dir data-file gv]
  (let [url (encode-nodename nodename)
        fillcolour (if (not= "all" id) 
                     (if (= url (URLEncoder/encode id)) highlight lowlight) highlight)]
    (.addln gv (str nodename " [shape=box,URL=\"/resilience/strength/"
                    strength "/node/" url "/dir/" dir "/data/" data-file "\",color="
                    fillcolour ",style=filled];"))))

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
        #_(doseq [nodename (next head)] (print-node nodename id strength dir data-file gv))
        (doseq [line ls] (dot-from-lifemap line ["head"] id strength dir gv))
        (.addln gv (.end_graph gv))
        #_(println (.getDotSource gv)))
      (let 	     	
          [ls (line-seq br)
           head (str/split #"\t" (first ls))]
        (.addln gv (.start_graph gv))
        (doseq [nodename (next head)] (print-node nodename id strength dir data-file gv))
        (doseq [line (next ls)] (to-dot line head id strength dir gv))
        (.addln gv (.end_graph gv))))))

(defn do-map
  [id strength dir data-file format]
  (do-graph id strength dir data-file format)
  (let [graph (.getGraph gv (.getDotSource gv) "cmapx")]
    (String. graph)))

(defn graph-viz
  [id strength dir data-file format]
  (do-graph id strength dir data-file format)
  (let [graph (.getGraph gv (.getDotSource gv) "gif")
        in-stream (do
                    (ByteArrayInputStream. graph))]
    {:status 200	 
     :headers {"Content-Type" "image/gif"}
     :body in-stream}))

(defn html-doc 
  [id strength dir data-file format] 
  (html5
   (do-map id strength dir data-file format)
   [:div {:style "float: left;width: 400px"}
    (form-to [:get "/resilience/new"] (text-field "URL" data-file)
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
    (str "<IMG SRC=\"/resilience/strength/" strength "/img/" id "/dir/" dir "/format/" format "/data/" data-file "\" border=\"0\" ismap usemap=\"#G\" />")]))

;; define routes
(defroutes webservice
  (GET ["/resilience/strength/:strength/node/:id/dir/:dir/format/:format/data/:data-file" :data-file #".*$"] [id strength dir data-file format] (html-doc id strength dir data-file format) )
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
  (GET ["/resilience/strength/:strength/img/:id/dir/:dir/data/:data-file" :data-file #".*$"] [id strength dir data-file] (graph-viz id strength dir data-file default-format) )
  (GET "/resilience/strength/:strength/img/:id/dir/:dir" [id strength dir] (graph-viz id strength dir default-data-file default-format) )
  (GET "/resilience/strength/:strength/img/:id" [id strength] (graph-viz id strength "out" default-data-file default-format) )
  (GET "/resilience/strength/:strength/img/" [strength] (graph-viz "all" strength "out" default-data-file default-format) )
  (GET "/resilience/img/:id" [id] (graph-viz id "weak" "out") )
  (GET "/resilience/img/" [] (graph-viz "all" "weak" "out") )
  (GET "/resilience/new" {params :params} (html-doc "all" "weak" "out" (params "URL") default-format)) )

(run-jetty webservice {:port 8000})
