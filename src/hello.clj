(ns hello-world
  (:use compojure.core, ring.adapter.jetty, hiccup.core, hiccup.page-helpers)
  (:require [compojure.route :as route] 
            [clojure.contrib.string :as str] 
            [clojure.contrib.math :as math])
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream)
           GraphViz
           (java.net URLEncoder
                     URLDecoder)))


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
            highlight (some #(or (= id "all")
                                  (= (encode-nodename %1) 
                                     (encode-nodename id))) comparee)
            colour (if highlight (if (> weight 0) "cornflowerblue" "red")
                       "grey")]
        (if (> (math/abs weight) min-weight) 
          (.addln gv 
                  (str nodename "->" 
                       (nth head (+ index 1)) 
                       "[headlabel =\"" (* weight 4) 
                       "\",weight=" (* (math/abs weight) 4) 
                       ",color=" colour 
                       "];")))))))

(defn print-node
  [nodename id strength dir gv]
  (let [url (encode-nodename nodename)
        fillcolour (if id 
                     (if (= url (URLEncoder/encode id)) "cornflowerblue" "gray81") "cornflowerblue")]
    (.addln gv (str nodename " [shape=box,URL=\"/resilience/strength/" strength "/node/" url "/dir/" dir "\" color=" fillcolour ",style=filled];"))))

(defn do-graph
  [id strength dir]
  (def gv (GraphViz.))
  (with-open [fr (java.io.FileReader. "data/Mike - FCM Cork Harbour.csv")
              br (java.io.BufferedReader. fr)]
    (let 	     	
        [ls (line-seq br)
         head (str/split #"\t" (first ls))]
      (.addln gv (.start_graph gv))
      (doseq [nodename (next head)] (print-node nodename id strength dir gv))
      (doseq [line (next ls)] (to-dot line head id strength dir gv))
      (.addln gv (.end_graph gv)))))

(defn do-map
  [id strength dir]
  (do-graph id strength dir)
  (let [graph (.getGraph gv (.getDotSource gv) "cmapx")]
    (String. graph)))

(defn graph-viz
  [id strength dir]
  (do-graph id strength dir)
  (let [graph (.getGraph gv (.getDotSource gv) "gif")
        in-stream (do
                    (ByteArrayInputStream. graph))]
    {:status 200	 
     :headers {"Content-Type" "image/gif"}
     :body in-stream}))

(defn html-doc 
  [id strength dir] 
  (html5
   (do-map id strength dir)
   [:div {:style "float: left;width: 200px"}
    [:ul
     [:li (if (= strength "weak") 
	    "weak"
            [:a {:href (str "/resilience/strength/weak/node/" id)} "weak"])]
     [:li (if (= strength "medium") 
	    "medium"
            [:a {:href (str "/resilience/strength/medium/node/" id)} "medium"])]
     [:li (if (= strength "strong") 
	    "strong"
            [:a {:href (str "/resilience/strength/strong/node/" id)} "strong"])]]]
   [:div {:style "float: left;width: 200px"}
    [:ul
     [:li (if(= dir "in")
            "in"
            [:a {:href (str "/resilience/strength/" strength "/node/" id "/dir/in")} "in"])]
     [:li (if (= dir "out") 
            "out"
            [:a {:href (str "/resilience/strength/" strength "/node/" id "/dir/out")} "out"])]
     [:li (if (= dir "inout") 
            "inout"
            [:a {:href (str "/resilience/strength/" strength "/node/" id "/dir/inout")} "inout"])]]]
   [:div
   (str "<IMG SRC=\"/resilience/strength/" strength "/img/" id "/dir/" dir "\" border=\"0\" ismap usemap=\"#G\" />")]
  )) 


;; define routes
(defroutes webservice
  (GET "/resilience/:id" [id] (html-doc id "weak" "out") )
  (GET "/resilience/" [] (html-doc "all" "weak" "out") )
  (GET "/resilience/strength/:strength" [strength] (html-doc "all" strength "out") )
  (GET "/resilience/strength/:strength/img/:id" [id strength] (graph-viz id strength "out") )
  (GET "/resilience/strength/:strength/img/:id/dir/:dir" [id strength dir] (graph-viz id strength dir) )
  (GET "/resilience/strength/:strength/img/" [strength] (graph-viz "all" strength "out") )
  (GET "/resilience/img/:id" [id] (graph-viz id "weak" "out") )
  (GET "/resilience/img/" [] (graph-viz "all" "weak" "out") )
  (GET "/resilience/node/:id" [id] (html-doc id "weak" "out") )
  (GET "/resilience/strength/:strength/node/:id" [id strength] (html-doc id strength "out") )
  (GET "/resilience/strength/:strength/node/:id/dir/:dir" [id strength dir] (html-doc id strength dir) )
  (GET "/resilience/strength/:strength/node/" [strength] (html-doc "all" strength "out") )
  (GET "/resilience/map.map" [id] (do-map id "weak" "out") ))

(run-jetty webservice {:port 8000})
