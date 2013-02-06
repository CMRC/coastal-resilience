(ns ie.endaten.iasess
  (:gen-class)
  (:require [clojure.data.json :as json]
            [compojure.route :as route]
            [compojure.core :as compojure]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [compojure.handler :as handler]
            [clojure.xml :as xml] 
            [clojure.math.numeric-tower :as math]
            [com.ashafa.clutch :as clutch]
            [com.ashafa.clutch.view-server :as view]
            [incanter.core :as incanter]
            [incanter.charts :as chart]
            [hiccup.form :as form]
            [hiccup.page :as page])
  (:use compojure.core, compojure.route, ring.adapter.jetty, ring.middleware.params,
        dorothy.core)
  (:import (java.io ByteArrayOutputStream
                    ByteArrayInputStream
                    OutputStreamWriter)
           (java.net URLEncoder
                     URLDecoder
                     URL)
           (java.awt.geom Rectangle2D$Double)
           (org.jfree.chart StandardChartTheme)
           (org.jfree.chart.axis CategoryLabelPositions)
           (org.apache.batik.transcoder TranscoderInput
                                        TranscoderOutput)
           (org.apache.batik.dom GenericDOMImplementation)
           (org.apache.batik.svggen SVGGraphics2D)))

#_(clutch/configure-view-server "resilience" (view/view-server-exec-string))

(def db "resilience")
(def lowlight "grey")
(def highlight "cornflowerblue")
(def positive "red")
(def negative "cornflowerblue")
(def default-data-file "Mike%20-%20FCM%20Cork%20Harbour.csv")
(def default-format "matrix")
(def strengths {:H+ 0.75 :M+ 0.5 :L+ 0.25 :H- -0.75 :M- -0.5 :L- -0.25})

(defn get-users []
  (try
    (clutch/with-db db
      (reduce #(assoc %1 (:key %2) (:value %2)) {} (clutch/get-view "all-users" :users)))))

(defn exportChartAsSVG
  [chart]
  ;;Get a DOMImplementation and create an XML document
  (let [domImpl (GenericDOMImplementation/getDOMImplementation)
        document (.createDocument domImpl nil "svg" nil)
        ;; Create an instance of the SVG Generator
        svgGenerator (SVGGraphics2D. document)
        rect (Rectangle2D$Double. 0 0 600 300)
        draw (.draw chart svgGenerator rect)
        ;; Write svg file
        outputStream (ByteArrayOutputStream.)
        out (OutputStreamWriter. outputStream "UTF-8")
        gen (.stream svgGenerator out true)]
    (.flush outputStream)
    (.close outputStream)
    outputStream))
    
(defn base-path [params] "/iasess")

(defn encode-nodename
  [nodename]
  (URLEncoder/encode (clojure.string/replace nodename #"[^a-zA-Z0-9]" "")))

(def all-concepts
  {"Drivers"
   ["Environmental Legislation and Policy"
    "Tourism and Recreation"
    "Residential Development"
    "Fisheries"
    "Agriculture"
    "Commerce, Industry & Manufacturing"
    "Aquaculture"]
   
   "Pressures"
   ["Roads and Transport Infrastructure"
    "Terrestrial Traffic"
    "Coastal Population Growth"
    "Coastal Squeeze"
    "Coastal Access Points"
    "Port and Marina Facilities"
    "Marine Traffic"
    "Commuter Belts /Urban Sprawl"
    "EROSION"
    "COASTAL INUNDATION/FLOODING"
    "DROUGHT"
    "Local Coastal Processes (OTHER)"
    "Terrestrial Surface Water Pollution"
    "Marine Pollution"
    "Commercial Fishing"
    "Soil Contamination"
    "Demand for Resource Access"
    "Enforcement: Environmental Protection"]

   "State Changes"
   ["Benthos"
    "Cliff Systems"
    "Sea Water Quality"
    "Avoidance of Harmful Algal Blooms"
    "River Systems"
    "Wetlands"
    "Dune Systems"
    "Ecological Niches: Native Species"
    "Local Employment"
    "Community Cohesion"
    "Integrated Coastal Development"]
  
   "Welfares"
   ["Food Provision: Marine Organisms"
    "Food Provision: Terrestrial Agriculture"
    "Inshore Marine Productivity"
    "Bioremediation: Waste Processing and Removal"
    "Flood Protection: Storm surge/Tidal/Fluvial"
    "Sea-Level Rise Buffering"
    "Raw Material Provision"
    "Fresh Water Supply"
    "Marine Transport and Navigation"
    "Coastal Amenity: Leisure/Recreation"
    "Habitable Land: Secure Coastal Development"
    "Cultural Heritage"]

   "Responses"
   ["NGO Protest"
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
    "Re-location away from coast"]})

(def drivers (get all-concepts "Drivers"))
(def pressures (get all-concepts "Pressures"))
(def state-changes (get all-concepts "State Changes"))
(def impacts (get all-concepts "Welfares"))
(def responses (get all-concepts "Responses"))


(defn if-weight [weight] (if weight weight "0"))
(defn url-weight [weight] (- (mod (Integer/parseInt (if-weight weight)) 7) 3))
(defn inc-weight [weight] (str (mod (inc (Integer/parseInt (if-weight weight))) 7)))
(defn num-weight [weight] (if (= (class weight) String)
                            (float (/ (url-weight weight) 4))
                            weight))
(defn display-weight [weight] (str (num-weight weight)))

(defn get-user [email]
  (clutch/with-db db
    (println "email: " email)
    (println "users: " (clutch/get-view "users" :by-user {:key email}))
    (:value (first (clutch/get-view "users" :by-user {:key email})))))

(defn create-user [email password context]
  (clutch/with-db db
    (clutch/put-document {:user email :username email
                          :password (creds/hash-bcrypt password)
                          :roles #{::iasess} :nodes {} :links {}
			  :context context})))


(defn edit-links [params nodes links concepts]
  (clutch/with-db db
    (let [node-types [:drivers :pressures :state-changes :impacts :responses ]
          level (fn [concept] (if-let [l (first (filter #(some #{concept} (second %)) all-concepts))]
                                (key l)
                                ((keyword concept) concepts)))
          g {}
          nodes-graph (reduce
                       #(case (level (second %2))
                          nil
                          (assoc-in %1 [:drivers (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "lightblue"
                                     :fillcolor "white"})
                          "Drivers"
                          (assoc-in %1 [:drivers (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "lightblue"
                                     :fillcolor "white"})
                          "Pressures"
                          (assoc-in %1 [:pressures (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "skyblue"
                                     :fillcolor "white"})
                          "State Changes"
                          (assoc-in %1 [:state-changes (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "steelblue"
                                     :fillcolor "white"})
                          "Welfares"
                          (assoc-in %1 [:impacts (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "beige"
                                     :fillcolor "white"})
                          "Responses"
                          (assoc-in %1 [:responses (first %2)]
                                    {:label (apply str (interpose "\\n" (clojure.string/split (second %2) #" ")))
                                     :shape :circle
                                     :width "1"
                                     :fixedsize :true
                                     :fontsize "10"
                                     :style :filled
                                     :color "brown"
                                     :fillcolor "white"}))
                       g nodes)
          links-graph (reduce #(let [w (:weight (get links (keyword (str (:head (val %2)) (:tail (val %2))))))
                                     weight (if (= (class w) String) (num-weight w) w)]
                                 (if (and (nodes (keyword (:head (val %2))))
                                          (nodes (keyword (:tail (val %2)))))
                                   (try
                                     (assoc-in %1 [[(:tail (val %2)) (:head (val %2))]]
                                               {:tooltip (str weight)
                                                :weight (str (math/abs weight))
                                                :fontsize "10"
                                                :headlabel (str weight)
                                                :penwidth (if (= weight 0.0) "1" (str (math/abs weight)))
                                                :color (if (> weight 0) "blue"
                                                           (if (= weight 0.0) "grey" "red"))
                                                :labelfontcolor (if (> weight 0) "blue"
                                                                    (if (= weight 0.0) "grey" "red"))
                                                :labeldistance "2"})
                                     (catch java.lang.ClassCastException e
                                       (println e)))
                                   %1))
                              {} links)
          nodes-subgraph (fn [node-type] (into [{:rank :same}] (for [[k v] (node-type nodes-graph)] [k v])))
          links-subgraph (into [{:splines :true :stylesheet "/iasess/css/style.css"
                                 :size "12,7"}]
                               (for [[[j k] v] links-graph] [(keyword j) (keyword k) v]))
          dot-out (dot (digraph "iasess" (apply vector (concat
                                                        (map #(subgraph % (nodes-subgraph %)) node-types)
                                                        links-subgraph))))]
      (cond
       (= (params :format) "img") (render dot-out {:format :svg :layout :dot})
       (= (params :format) "dot")   {:status 200	 
                                     :headers {"Content-Type" "txt"}
                                     :body dot-out}))))

(defn save-views []
  (clutch/with-db db
    (clutch/save-view "all-users"
                      (clutch/view-server-fns
                       :clojure
                       {:users
                        {:map (fn [doc] (if (:username doc) [[(:username doc) doc]]))}}))
    (clutch/save-view "users"
                      (clutch/view-server-fns
                       :clojure
                       {:by-user
                        {:map (fn [doc] [[(:username doc) doc]])}}))))
#_(save-views)

(defn new-concept [user concept level doc]
  (clutch/with-db db
    (clutch/update-document
     (merge doc
            {:concepts (merge (:concepts doc)
                              {concept level})
             :nodes (merge (:nodes doc)
                           {(encode-nodename concept) concept})}))))
  

(defn edit-links-html [params]
  (clutch/with-db db
    (let [if-count (fn [c] (if (:count c) (:count c) 1))
          doc (get-user (params :id))
          models (:models doc)
          avg-weights (fn [f s] (merge f {:weight (/ (+ (* (num-weight (:weight f)) (if-count f))
                                                        (* (num-weight (:weight s)) (if-count s)))
                                                     (+ (if-count f) (if-count s)))
                                          :count (+
                                                  (if-count f)
                                                  (if-count s))}))
          links (->>
                 (:links doc)
                 (merge
                  (reduce #(let [d (clutch/get-document (name (first %2)))
                                 m (merge-with avg-weights
                                               %1 (:links d))]
                             m)
                          {}
                          models)))
          nodes (->
                 (:nodes doc)
                 (merge
                  (reduce #(let [d (clutch/get-document (name (first %2)))
                                 m (merge %1 (:nodes d))]
                             m)
                          {}
                          models)))
          concepts (->
                    (:concepts doc)
                    (merge
                     (reduce #(let [d (clutch/get-document (name (first %2)))
                                    m (merge %1 (:concepts d))]
                                m)
                             {}
                             models)))
          losers (map #(:user (:value %)) (clutch/get-view "users" :by-user))
          users (remove #(or (nil? %) (= % "") (= % (:user doc))) losers)
          adduser (clutch/get-view "users" :by-user {:key (params "model")})]
      (case (params :mode)
        "add"      (do
                     (clutch/update-document
                      (merge doc
                             {:nodes (merge nodes
                                            {(encode-nodename (params :element)) (params :element)})}))
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "addnew"   (do
                     (new-concept (params :id) (params :element) (params :level) doc)
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "addmodel" (let
                       [adduser (clutch/get-view "users" :by-user {:key (params "model")})]
                     (clutch/update-document
                      (merge doc
                             {:models (merge models
                                             {(:id (first adduser)) (params "model")})}))
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "more" (let
                   [adduser (clutch/get-view "users" :by-user {:key (params "model")})]
                 (clutch/update-document
                  (merge doc
                         {:notes (merge (:notes doc)
                                        {(encode-nodename (params :element)) (params "more")})}))
                 {:status 303
                  :headers {"Location" (str (base-path params) "/mode/edit")}})
        "delete"   (do
                     (clutch/update-document
                      (merge doc
                             {:nodes (dissoc nodes (keyword (str (params :node))))}))
                     {:status 303
                      :headers {"Location" (str (base-path params) "/mode/edit")}})
        "save"     (do (if (= (params :weight) "3") ;;maps to zero
                         (clutch/put-document
                          (merge doc
                                 {:links (dissoc (:links doc)
                                                 (keyword (str (params :node)
                                                               (params :tail))))}))
                         (clutch/put-document
                          (merge doc
                                 {:links
                                  (merge (:links doc)
                                         {(keyword (str (params :node)
                                                        (params :tail)))
                                          {:head (params :node)
                                           :tail (params :tail)
                                           :weight (params :weight)}})})))
                       {:status 303
                        :headers {"Location" (str (base-path params) "/mode/edit")}})
        "download" 
        {:status 200
         :headers {"Content-Type" "text/tab-separated-values"
                   "Content-Disposition" "attachment;filename=matrix.tsv"}
         :body (str "\t" (apply str (map #(str (second %) "\t") nodes)) "\n"     ;;header row
                    (apply str                                               
                           (map                                               ;;value rows
                            (fn [tail]                                        ;;each elem as a potential tail
                              (str (second tail) "\t"                           ;;elem name at start of row
                                   (apply str
                                          (map                                ;;each elem as a potential head
                                           (fn [head]
                                             (str (if                         ;;loop through links, finding matches
                                                      (= (name (first tail))  ;; value of key,value
                                                         (:tail (get links    ;; tail matches tail, get weight
                                                                     (keyword 
                                                                      (str (name (first head))
                                                                           (name (first tail)))))))
                                                    (display-weight           ;;cell value
                                                     (:weight (get links (keyword
                                                                          (str (name (first head))
                                                                               (name (first tail)))))))
                                                    "0.0") "\t"))               ;;no link, =zero
                                           nodes))
                                   "\n"))
                            nodes)))}
        "bar"
        (if (seq nodes)
          (let [causes
                (incanter/trans
                 (apply vector                                               
                        (map                                               ;;value rows
                         (fn [head]                                        ;;each elem as a potential head
                           (apply vector
                                  (map                                ;;each elem as a potential tail
                                   (fn [tail]
                                     (if                         ;;loop through links, finding matches
                                         (= (name (first head))  ;; value of key,value
                                            (:tail (get links    ;; tail matches tail, get weight
                                                        (keyword 
                                                         (str (name (first tail))
                                                              (name (first head)))))))
                                       (let [w (:weight (get links (keyword
                                                                       (str (name (first tail))
                                                                            (name (first head))))))]
                                         (num-weight w))
                                       0.0))               ;;no link, =zero
                                   nodes)))
                         nodes)))
                states (incanter/matrix 1 (count nodes) 1)
                squash (fn [out] (map #(/ 1 (inc (math/expt Math/E (unchecked-negate %)))) out))
                out (nth (iterate #(squash (incanter/plus (incanter/mmult causes %) %)) states) 10)
                minusahalf (map #(- % 0.5) out)
                chart (doto (chart/bar-chart (vals nodes) minusahalf :x-label ""
                                             :y-label "")
                        (chart/set-theme (StandardChartTheme. "theme"))
                        (.setBackgroundPaint java.awt.Color/lightGray)
                        (->
                         .getPlot
                         .getDomainAxis
                         (.setCategoryLabelPositions
                          (CategoryLabelPositions/createUpRotationLabelPositions (/ Math/PI 6.0)))))
                out-stream (ByteArrayOutputStream.)
                in-stream (do
                            (incanter/save chart out-stream :width 600 :height 150)
                            (ByteArrayInputStream. 
                             (.toByteArray out-stream)))]
            (exportChartAsSVG chart))
          "<h2>Things you can do from here...</h2>
<h3>Add some nodes..</h3>
<p>Use the drop down selectors on the second row to select a concept, then Add</p>")
        "edit"
        (page/xhtml
         [:head
          [:title "Iasess - Ireland's Adaptive Social-Ecological Systems Simulator"]
          [:script {:type "text/javascript"}
           (str "var dojoConfig = { parseOnLoad: true };"
		"var group = '"
		(if-let [c( doc :context)] c "Iasess Dingle") 
		"';")]
          [:script {:src "http://serverapi.arcgisonline.com/jsapi/arcgis/3.3compact"}]
          [:script {:src "/iasess/js/esri.js"}]
          [:style {:type "text/css"} "@import \"http://serverapi.arcgisonline.com/jsapi/arcgis/3.3/js/esri/css/esri.css\";"]
          [:style {:type "text/css"} "@import \"/iasess/css/layout.css\";"]
          [:style {:type "text/css"} "@import \"/iasess/css/iasess.css\";"]]
         [:body
          [:ul {:id "nav"}
           [:li [:a "File"]
            [:ul
             (when-not (= (params :id) "guest")
                 [:div [:li [:a {:href "/iasess/logout"} "Logout"]]
                  [:li [:a "Add User"]
                   (form/form-to {:id "adduser" :class "add-text"}
                                 [:post "/iasess/mode/adduser"]
                                 [:a {:href (str "javascript: submitform(\""
                                                 "\")")}
                                  (form/text-field "username")
                                  (form/hidden-field "password" "friend")])]])
             [:li [:a {:href "/iasess/mode/download"} "Download"]]]]
           (map (fn [[level menustr]]
                  (vector :li [:a {:href "#":onmouseover
                                   (str "infotext(\"Information Panel: Add "
                                        menustr "\")")} menustr]
                          [:ul
                           (map (fn [concept]
                                  (vector :li
                                          (form/form-to {:id (encode-nodename concept)
                                                         :class "concept"}
                                                        [:post (str (base-path params) "/mode/add")]
                                                        (form/hidden-field "element" concept)
                                                        [:a {:href (str "javascript: submitform(\""
                                                                        (encode-nodename concept)
                                                                        "\")")
                                                             :onmouseover
                                                             (str "infotext(\"Information Panel: Add "
                                                                  menustr " concept " concept "\")")} concept])
                                          (form/form-to {:class "add-text"}
                                                        [:get (str (base-path params) "/mode/more")]
                                                        (form/hidden-field "element" concept)
                                                        [:a
                                                         {:onmouseover
                                                          "infotext(\"Information Panel: Add your own text\")"}
                                                         (form/text-field "more"
                                                                          (if-let [more ((keyword concept) (:notes doc))]
                                                                            more
                                                                            "Additional text..."))])))
                                level)
                           [:li [:a {:href "#"
                                     :onmouseover
                                     (str "infotext(\"Information Panel: Add your own "
                                          menustr " concept\")")} "Custom"]
                            (form/form-to {:class "add-text" :id level :autocomplete "off"}
                                     [:post (str (base-path params) "/mode/addnew")]
                                      (form/hidden-field "level" menustr)
                                      [:a {:onmouseover
                                           "infotext(\"Information Panel: Concept name (Enter)\")"}(form/text-field "element" "")])]]))
                {drivers "Drivers"
                 pressures "Pressures"
                 state-changes "State Changes"
                 impacts "Welfares"
                 responses "Responses"})
           [:li [:a [:span {:id "user"} "Welcome: " (params :id)] [:span [:b "i"] "asess:coast"]]]]
          [:div {:id "pane"}
           [:div {:id "graph"}
            (edit-links (assoc-in params [:format] "img") nodes links concepts)]
           [:div {:class "container_12"}
            [:div {:id "mapSection" :class "grid_12 rounded"}
             [:div {:id "mainMap"}
              [:div {:class "gallery-nav"}
               [:div {:class "gallery-nav-right" :onclick "getNext();"}]
               [:div {:class "gallery-nav-left" :onclick "getPrevious();"}]]]]]]
          [:div {:id "bar"}
           [:div {:id "info-text"} "Information panel: Mouse over Menu, Mapping Panel, or Modelling Panel to begin."]
           (edit-links-html (assoc-in params [:mode] "bar"))]
          [:script {:src "/iasess/js/script.js"}]])
        {:status 303
         :headers {"Location" (str (base-path params) "/mode/edit")}}))))

(defn auth-edit-links-html [req]
  (friend/authorize #{"ie.endaten.iasess/iasess"}
                    (edit-links-html (assoc (:params req) :id (:current (friend/identity req))))))

(defn login [params  error]
        (page/html5
         [:head
          [:title "Iasess - Ireland's Adaptive Social-Ecological Systems Simulator"]
          [:script {:src "/iasess/js/script.js"}]
          [:style {:type "text/css"} "@import \"/iasess/css/iasess.css\";"]]
         [:body
          [:h2 "Iasess - Ireland's Adaptive Social-Ecological Systems Simulator"]
	  [:div {:class "register"}
	   [:h3 "Registered users please login"]
	  (form/form-to [:post "/iasess/login"]
			(form/text-field "username")
			(form/password-field "password")
			(form/submit-button "Login"))]
	  [:div {:class "register"}
	   [:h3 "New users please register"]
	   (form/form-to [:post "/iasess/mode/adduser"]
			  (form/text-field "username")
			  (form/password-field "password")
			  "Map Context"
			  (form/text-field "context")
			  (form/submit-button "Register"))]
	  [:em error]]))


(defroutes webservice
  ;;links for editing
  (ANY "/iasess/login" request (login (request :params) ""))
  (ANY "/iasess/mode/:mode" request (auth-edit-links-html request))
  (GET "/iasess/mode/:mode/:node" request (auth-edit-links-html request))
  (GET "/iasess/mode/:mode/:tail/:node/:weight" request (auth-edit-links-html request))
  (GET "/iasess" request (ring.util.response/redirect "/iasess/mode/edit"))
  (friend/logout (ANY "/iasess/logout" request (ring.util.response/redirect "/iasess/login")))
  
  (resources "/iasess"))

(defn my-workflow [{:keys [uri request-method params]}]
  (do
    (println params)
    (when (and (= uri "/iasess/mode/adduser")
               (= request-method :post))
      (if (seq (get-user (params :username)))
	{:status 200 :headers {} :body (login params "User exists")}
        (do
          (create-user (params :username) (params :password) (params :context))
          (workflows/make-auth {:username (params :username)
                                :password (creds/hash-bcrypt (params :password))
                                :roles #{"ie.endaten.iasess/iasess"}}))))))

(def secured-app
  (handler/site
   (friend/authenticate
    webservice
    {:credential-fn (partial creds/bcrypt-credential-fn get-user) 
     :workflows [my-workflow (workflows/interactive-form)] 
     :login-uri "/iasess/login" 
     :unauthorized-redirect-uri "/iasess/login" 
     :default-landing-uri "/iasess/mode/edit"})))

(defn -main
  "Run the jetty server."
  [& args]
  (run-jetty (wrap-params secured-app) {:port (Integer. (get (System/getenv) "PORT" "8000"))}))

