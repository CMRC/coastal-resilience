(ns hello-world
    (:use compojure.core, ring.adapter.jetty, incanter.core, incanter.stats, incanter.charts,
	  incanter.io ,hiccup.core, hiccup.page-helpers)
    (:require [compojure.route :as route] 
	      [clojure.contrib.string :as str] 
	      [clojure.contrib.math])
    (:import (java.io ByteArrayOutputStream
		      ByteArrayInputStream)
	     GraphViz
	     (java.net URLEncoder)))



(defn to-dot [line head gv]
  (let [linevec (str/split #"\t" line)
       nodename (first linevec)
       idv (map vector (iterate inc 0) (next linevec))]
       (doseq [[index value] idv]
	      (let [weight (Float/parseFloat value)
		   colour (if (> weight 0) "blue" "red")]
		   (if (not= weight 0.0)
		       (.addln gv 
			       (str nodename "->" 
				    (nth head (+ index 1)) 
				    "[headlabel =\"" (* weight 4) 
				    "\",weight=" (* (abs weight) 4) 
				    ",color=" colour 
				    "];")))))))



(defn do-graph
  [id]
  (def gv (GraphViz.))
  (with-open [fr (java.io.FileReader. "data/Mike - FCM Cork Harbour.csv")
	     br (java.io.BufferedReader. fr)]
	     (let 	     	
		 [ls (line-seq br)
		 head (str/split #"\t" (first ls))]
		 (.addln gv (.start_graph gv))
		 (doseq [nodename (next head)] 
			( #(.addln gv (str nodename "[shape=box,URL=" nodename "];"))))
		 (doseq [line (next ls)] (to-dot line head gv))
		 (.addln gv (.end_graph gv)))))

(defn do-map
  [id]
  (do-graph id)
  (let [graph (.getGraph gv (.getDotSource gv) "cmapx")]
       (String. graph)))

(defn graph-viz
  [id]
  (do-graph id)
  (let [graph (.getGraph gv (.getDotSource gv) "gif")
       in-stream (do
		     (ByteArrayInputStream. graph))]
		     {:status 200	 
		     :headers {"Content-Type" "image/gif"}
		     :body in-stream}))
  
(defn html-doc 
  [id] 
  (html4
   (do-map id)
   [:div 
   "<IMG SRC=\"/img/xx\" ismap usemap=\"#G\" />"]
   )) 

  
  ;; define routes
(defroutes webservice
  (GET "/:id" [id] (html-doc id) )
  (GET "/img/:id" [id] (graph-viz id) )
  (GET "/map.map" [id] (do-map id) ))

(run-jetty webservice {:port 8000})
  