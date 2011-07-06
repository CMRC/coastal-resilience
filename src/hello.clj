(ns hello-world
  (:use compojure.core, ring.adapter.jetty, incanter.core, incanter.stats, incanter.charts,
	incanter.io ,hiccup.core)
  (:require [compojure.route :as route])
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream),
		    GraphViz))


;; Pass a map as the first argument to be 
;; set as attributes of the element
(defn html-doc 
  [title & body] 
  (html 
       [:div 
	[:h2 
	 [:a {:href "/"} 
          "Generate a normal sample"]]]
       body)) 

(defn graph-viz
  [id]
  (let [gv (new GraphViz)]
       (.addln gv (.start_graph gv))	
       (.addln gv "A -> B;")
       (.addln gv "A -> C;")
       (.addln gv (.end_graph gv))
       (.println System/out (.getDotSource gv))
       (let [graph (.getGraph gv (.getDotSource gv) "gif")
	    in-stream (do
			  (new ByteArrayInputStream graph))]
			  {:status 200	 
			  :headers {"Content-Type" "image/gif"}
			  :body in-stream})))


;; define routes
(defroutes webservice
  (GET "/:doi" [id] (graph-viz id) ))

(run-jetty webservice {:port 8080})
  