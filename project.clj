(defproject example "1.0.0-SNAPSHOT"
  :description "iasess prototype"
  :dependencies [[org.clojure/clojure "1.3.0"]
		 [compojure "1.1.3"]
		 [ring "1.1.6"]
		 [hiccup "1.0.1"]
                 [com.ashafa/clutch "0.3.1-SNAPSHOT"]
                 [dorothy "0.0.3"]]
  :ring {:handler hello}
  :main hello)