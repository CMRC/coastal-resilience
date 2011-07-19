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

(defn to-dot [line head id strength gv]
  (let [linevec (str/split #"\t" line)
       nodename (first linevec)
       idv (map vector (iterate inc 0) (next linevec))]
       (doseq [[index value] idv]
	      (let [weight (Float/parseFloat value)
		   min-weight (if (= strength "strong") 
				  0.6 
				(if (= strength "medium") 0.3 0.0))
		   colour (if (or (not id) (= (encode-nodename nodename) 
					      (encode-nodename id)))
			      (if (> weight 0) "cornflowerblue" "red")
			    "grey")]
			    (if	(> (math/abs weight) min-weight) 
				(.addln gv 
					(str nodename "->" 
					     (nth head (+ index 1)) 
					     "[headlabel =\"" (* weight 4) 
					     "\",weight=" (* (math/abs weight) 4) 
					     ",color=" colour 
					     "];")))))))

(defn print-node
  [nodename id strength gv]
  (let [url (encode-nodename nodename)
       fillcolour (if id 
		      (if (= url (URLEncoder/encode id)) "cornflowerblue" "gray81") "cornflowerblue")]
		      (.addln gv (str nodename " [shape=box,URL=\"/resilience/strength/" strength "/node/" url "\" color=" fillcolour ",style=filled];"))))

(defn do-graph
  [id strength]
  (def gv (GraphViz.))
  (with-open [fr (java.io.FileReader. "data/Mike - FCM Cork Harbour.csv")
	     br (java.io.BufferedReader. fr)]
	     (let 	     	
		 [ls (line-seq br)
		 head (str/split #"\t" (first ls))]
		 (.addln gv (.start_graph gv))
		 (doseq [nodename (next head)] (print-node nodename id strength gv))
		 (doseq [line (next ls)] (to-dot line head id strength gv))
		 (.addln gv (.end_graph gv)))))

(defn do-map
  [id strength]
  (do-graph id strength)
  (let [graph (.getGraph gv (.getDotSource gv) "cmapx")]
       (String. graph)))

(defn graph-viz
  [id strength]
  (do-graph id strength)
  (let [graph (.getGraph gv (.getDotSource gv) "gif")
       in-stream (do
		     (ByteArrayInputStream. graph))]
		     {:status 200	 
		     :headers {"Content-Type" "image/gif"}
		     :body in-stream}))
  
(defn html-doc 
  [id strength] 
  (html5
   (do-map id strength)
   [:div
   [:ul
   [:li (if (= strength "weak") 
	    "weak"
	  [:a {:href (str "/resilience/strength/weak/node/" id)} "weak"])]
   [:li (if (= strength "medium") 
	    "medium"
	   [:a {:href (str "/resilience/strength/medium/node/" id)} "medium"])]
   [:li (if (= strength "strong") 
	    "strong"
	   [:a {:href (str "/resilience/strength/strong/node/" id)} "strong"])]]
   (str "<IMG SRC=\"/resilience/strength/" strength "/img/" id "\" border=\"0\" ismap usemap=\"#G\" />")]
   )) 

  
  ;; define routes
(defroutes webservice
  (GET "/resilience/:id" [id] (html-doc id "weak") )
  (GET "/resilience/" [] (html-doc nil "weak") )
  (GET "/resilience/strength/:strength" [strength] (html-doc nil strength) )
  (GET "/resilience/strength/:strength/img/:id" [id strength] (graph-viz id strength) )
  (GET "/resilience/strength/:strength/img/" [strength] (graph-viz nil strength) )
  (GET "/resilience/img/:id" [id] (graph-viz id "weak") )
  (GET "/resilience/img/" [] (graph-viz nil "weak") )
  (GET "/resilience/node/:id" [id] (html-doc id "weak") )
  (GET "/resilience/strength/:strength/node/:id" [id strength] (html-doc id strength) )
  (GET "/resilience/strength/:strength/node/" [strength] (html-doc nil strength) )
  (GET "/resilience/map.map" [id] (do-map id "weak") ))

(run-jetty webservice {:port 8000})
  