(defproject example "1.0.0-SNAPSHOT"
  :description ""
  :dependencies [[org.clojure/clojure "1.2.0"]
		 [org.clojure/clojure-contrib "1.2.0"]
		 [compojure "0.5.3"]
		 [ring/ring-jetty-adapter "0.3.1"]
		 [hiccup "0.3.6"]
                 [com.ashafa/clutch "0.3.1-SNAPSHOT"]]
  :dev-dependencies [[lein-ring "0.4.5"]]
  :java-source-path  "java"
  :ring {:handler hello}
  :main hello)