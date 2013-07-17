(defproject iasess "0.0.1"
  :description "iasess prototype"
  :dependencies [[org.clojure/data.json "0.2.0"]
                 [org.clojure/clojure "1.4.0"]
		 [compojure "1.1.3"]
		 [ring "1.1.6"]
		 [hiccup "1.0.2"]
                 [com.ashafa/clutch "0.4.0-RC1"]
                 [dorothy "0.0.3"]
                 [incanter "1.5.1"]
                 [org.clojars.pallix/batik "1.7.0"]
                 [org.clojure/math.numeric-tower "0.0.1"]
                 [com.cemerick/friend "0.1.3"]
                 [couch-session "1.1.1"]
                 [org.clojure/clojurescript "0.0-1011"]]
  :main ie.endaten.iasess
  :ring {:handler ie.endaten.iasess/secured-app})