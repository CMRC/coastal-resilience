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
            [incanter.svg :as svg]
            [incanter.charts :as chart]
            [hiccup.form :as form]
            [hiccup.page :as page])
  (:use [couch-session.core :only (couch-store)] compojure.core, compojure.route, ring.adapter.jetty, ring.middleware.params,
        ring.middleware.session.cookie, ring.middleware.session.store, dorothy.core)
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

(def db "iasess")
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
        rect (Rectangle2D$Double. 0 0 700 500)
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
    "Re-location away from coast"]

   "Physical"
   ["Temperature"
    "Pressure"
    "Precipitation"
    "Wind Speed"
    "Sea Level"]})

(def drivers (get all-concepts "Drivers"))
(def pressures (get all-concepts "Pressures"))
(def state-changes (get all-concepts "State Changes"))
(def impacts (get all-concepts "Welfares"))
(def responses (get all-concepts "Responses"))
(def physical (get all-concepts "Physical"))


(defn if-weight [weight] (if weight weight "0"))
(defn url-weight [weight] (- (mod (Integer/parseInt (if-weight weight)) 7) 3))
(defn inc-weight [weight] (str (mod (inc (Integer/parseInt (if-weight weight))) 7)))
(defn num-weight [weight] (if (= (class weight) String)
                            (float (/ (url-weight weight) 4))
                            weight))
(defn display-weight [weight] (str (num-weight weight)))

(defn get-user [email]
  (clutch/with-db db
    (:value (first (clutch/get-view "users" :by-user {:key email})))))

(defn create-user [email password context]
  (clutch/with-db db
    (clutch/put-document {:user email :username email
                          :password (creds/hash-bcrypt password)
                          :roles #{::iasess} :nodes {} :links {}
			  :context "Iasess Dingle"})))

(defn save-views []
  (clutch/with-db db
    (clutch/save-view "all-users"
                      (clutch/view-server-fns
                       :cljs
                       {:users
                        {:map (fn [doc] (if (aget doc "username") 
                                          (js/emit (aget doc "username") doc)))}}))
    (clutch/save-view "users"
                      (clutch/view-server-fns
                       :cljs
                       {:by-user
                        {:map (fn [doc] (js/emit (aget doc "username") doc))}}))))
#_(save-views)

(defn new-concept [user concept level details doc]
  (clutch/with-db db
    (clutch/update-document
     (merge doc
            {:concepts (merge (:concepts doc)
                              {concept level})
             :nodes (merge (:nodes doc)
                           {(encode-nodename concept) concept})}))))

(defn popup [id body]
  "create a popup menu for creating a new concept"
  [:div
   [:div {:id id :class "popup"}]
   [:div {:id (str id "-in") :class "popup-in"}
    [:a {:href (str "javascript: hideconcept('" id "')") :class "close"} "Close"]
    body]])

(def o (Object.))
(defn edit-links-html [params method]
  (locking o
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
            "file" (case (params :element)
                     "Logout" (ring.util.response/redirect "/iasess/logout")
                     "Download" (ring.util.response/redirect "/iasess/mode/download"))
            "setcontext" (do
                           (clutch/update-document
                            (merge doc
                                   {:context (params :context)}))
                                   {:status 303
                                    :headers {"Location" (str (base-path params) "/mode/edit")}})
	    "addnew"    (do
                          (when (> (count (params :element)) 0)
                            (new-concept (params :id) (params :element) (params :level) (params :details) doc))
			  {:status 303
			   :headers {"Location" (str (base-path params) "/mode/edit")}})
	    "addmodel"  (let
			    [adduser (clutch/get-view "users" :by-user {:key (params "model")})]
			  (clutch/update-document
			   (merge doc
				  {:models (merge models
						  {(:id (first adduser)) (params "model")})}))
			  {:status 303
			   :headers {"Location" (str (base-path params) "/mode/edit")}})
	    "more"      (let
			    [adduser (clutch/get-view "users" :by-user {:key (params "model")})]
			  (clutch/update-document
			   (merge doc
				  {:notes (merge (:notes doc)
						 {(encode-nodename (params :element)) (params "more")})}))
			  {:status 303
			   :headers {"Location" (str (base-path params) "/mode/edit")}})
	    "delete"    (do
			  (clutch/update-document
			   (merge doc
				  {:nodes (dissoc nodes (keyword (str (params :node))))}))
			  {:status 303
			   :headers {"Location" (str (base-path params) "/mode/edit")}})
	    "save"      (do (if (= (params :weight) "3") ;;maps to zero
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
            "json"       (if (= :post method)
                           (do
                             (clutch/update-document
                              (let [links (json/read-str (:links params) :key-fn keyword)
                                    pairs (map #(vector (keyword
                                                         (str (get % :tail) (get % :head)))
                                                        %) links)
                                    lmap (reduce conj {} pairs)
                                    pos-nodes (json/read-str (:nodes params) :key-fn keyword)
                                    nmap (reduce conj {} (map #(vector (keyword (get % :id))
                                                                       %) pos-nodes))
                                    ndoc (if (:nodes params) (merge doc {:nodes nmap}) doc)
                                    ldoc (if (:links params) (merge ndoc {:links lmap}) ndoc)]
                                ldoc))
                             {:status 200 :body "ok"})
                           {:status 200 :body (json/write-str {:nodes (to-array (vals (doc :nodes)))
                                                               :links (vals (doc :links))})})
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
                    (apply vector                                               
                           (map                                              ;;value rows
                            (fn [[headk headv]]                              ;;each elem as a potential head
                              (apply vector
                                     (map                                ;;each elem as a potential tail
                                      (fn [[tailk tailv]]
                                        (let [link (get links    ;; tail matches tail, get weight
                                                        (keyword 
                                                         (str (name tailk)
                                                              (name headk))))
                                              p (println link " " headk)]
                                          (if                         ;;loop through links, finding matches
                                              (= (name headk)  ;; value of key,value
                                                 (:head link))
                                            (let [w (:weight link)]
                                              (num-weight w))
                                            0.0)))           ;;no link, =zero
                                      nodes)))
                            nodes))
                    p (println causes)
                    states (incanter/matrix 1 (count nodes) 1)
		    squash (fn [out] (map #(/ 1 (inc (math/expt Math/E (unchecked-negate %)))) out))
		    out (nth (iterate #(squash (incanter/plus (incanter/mmult causes %) %)) states) 10)
		    minusahalf (map #(- % 0.5) out)
		    chart (doto (chart/bar-chart (map :name (vals nodes)) minusahalf :x-label ""
						 :y-label "" :vertical false)
			    (chart/set-theme (StandardChartTheme. "theme"))
			    (.setBackgroundPaint java.awt.Color/white)
			    #_(->
			     .getPlot
			     .getDomainAxis
			     (.setCategoryLabelPositions
			      (CategoryLabelPositions/createUpRotationLabelPositions (/ Math/PI 3.0)))))]
		(exportChartAsSVG chart))
	      "Welcome. Your graph is looking a little empty. Try adding some nodes from the menus above..")
        "edit"
        (page/xhtml
         [:head
          [:title "Iasess - Ireland's Adaptive Social-Ecological Systems Simulator"]
          [:style {:type "text/css"} "@import \"/iasess/css/iasess.css\";"]
         [:body
          (popup "newconcept"
                 [:div {:class "concept-name"}
                  [:h3 "Concept Name"]
                  (form/form-to [:post "/iasess/mode/addnew"]
                                (form/text-field "element")
                                (form/drop-down "level" (keys all-concepts))
                                [:h4 "Details"] (form/text-area "details" "These data will not be saved")
                                [:p (form/submit-button "Submit")])])
          (popup "context"
                 [:div {:class "concept-name"}
                  [:h3 "Context"]
                  (form/form-to [:post "/iasess/mode/setcontext"]
                                (form/text-field "context")
                                [:p (form/submit-button "Submit")])])
          [:div {:class "concepts"}
           (form/form-to {:id "file"}
                         [:get "/iasess/mode/file"]
                         (form/drop-down
                          {:onchange "submitform('file',this,'context')"}
                          "element" [(str "Welcome: " (params :id)) "Set Context..." "Logout" "Download"]))
           (map (fn [[level menustr]]
                  (form/form-to {:id menustr}
                                [:post "/iasess/mode/add"]
                                (form/drop-down
                                 {:onchange (str "addNode(this)")}
                                 "element" (cons menustr (conj level "Custom...")))))
                {drivers "Drivers"
                 pressures "Pressures"
                 state-changes "State Changes"
                 impacts "Welfares"
                 responses "Responses"
                 physical "Physical"})
           [:a {:href "/iasess/mode/edit"} [:span [:b "i"] "asess:coast"]]]
          [:div {:id "pane"}
           [:div {:id "graph"}]
           [:div {:id "mapbar"}
            [:div {:id "map"}
             [:iframe {:width "425" :height "350" :frameborder "0" :scrolling "no" :marginheight "0"
                       :marginwidth "0" :src "http://mangomap.com/maps/5183/Dingle?admin_mode=false#&mini=true"}]]
            [:div {:id "bar"}
             [:div {:id "info-text"} "Information panel: Mouse over Menu, Mapping Panel, or Modelling Panel to begin."]
             (edit-links-html (assoc-in params [:mode] "bar") :get)]]
           [:script {:src "/iasess/js/d3.v3.min.js"}]
           [:script {:src "/iasess/js/underscore-min.js"}]
           [:script {:src "/iasess/js/layout.js"}]]]]))))))
         
(defn auth-edit-links-html [req]
  "Some dodgy stuff here with rebinding *identity*. This is because of the clutch store messing up keywords"
  (let [id (get-in req [:session "identity"])
        auth (assoc-in id [:current] (keyword (:current id)))
        p3 (set! friend/*identity* auth)]
    (friend/authorize #{"ie.endaten.iasess/iasess"}
                      (edit-links-html (assoc (:params req) :id (:current id)) (:request-method req)))))

(defn login [params  error]
        (page/html5
         [:head
          [:title "Iasess - Ireland's Adaptive Social-Ecological Systems Simulator"]
          [:style {:type "text/css"} "@import \"/iasess/css/iasess.css\";"]]
         [:body
          [:h2 "Climate Impact & Adaptation Scoping Tool"]
	  [:div {:class "register"}
	   [:h3 "Registered users please login"]
	  (form/form-to [:post "/iasess/login"]
			"Username " (form/text-field "username")
			" Password " (form/password-field "password")
			(form/submit-button "Login"))]
	  [:div {:class "register"}
	   [:h3 "New users please register"]
	   (form/form-to [:post "/iasess/mode/adduser"]
			  "Username " (form/text-field "username")
			  " Password " (form/password-field "password")
			  (form/submit-button "Register"))
           [:em error]
           [:p "This tool is designed to help you in scoping for climate impacts that may affect your area/sector of operation and in assessing the effectiveness of adaptation options.  This is achieved by enabling you to: develop a systematic understanding of the area/sector which you are assessing; analyse projected climate impacts on your area/sector and assess the degree to which key elements and process are vulnerable to the effects of climate change; and make an initial evaulation of the efficiency and cumulative consequences of current or proposed adaptation measures.."]]]))

;;(defonce my-session (cookie-store {:key "1234abcdqwer    "}))
(def store
  (clutch/get-database (com.ashafa.clutch.utils/url db)))

(defroutes webservice
  ;;links for editing
  (ANY "/iasess/login" request (login (request :params) ""))
  (ANY "/iasess/mode/:mode" request (auth-edit-links-html request))
  (GET "/iasess/mode/:mode/:node" request (auth-edit-links-html request))
  (GET "/iasess/mode/:mode/:tail/:node/:weight" request (auth-edit-links-html request))
  (GET "/iasess" request (ring.util.response/redirect "/iasess/mode/edit"))
  (ANY "/iasess/logout" request (update-in (ring.util.response/redirect "/iasess/login") [:session] dissoc ::identity))
  (resources "/iasess"))


(defn my-workflow [{:keys [uri request-method params]}]
  (do
    (when (and (= uri "/iasess/mode/adduser")
               (= request-method :post))
      (if (seq (get-user (params :username)))
        {:status 200 :headers {} :body (login params "User exists")}
        (do
          (create-user (params :username) (params :password) (params :context))
          (workflows/make-auth {:username (params :username)
                                :password (creds/hash-bcrypt (params :password))
                                :roles #{::iasess}}))))))

(def secured-app
  (handler/site
   (friend/authenticate
    webservice
    {:credential-fn (partial creds/bcrypt-credential-fn get-user) 
     :workflows [my-workflow (workflows/interactive-form)] 
     :login-uri "/iasess/login" 
     :unauthorized-redirect-uri "/iasess/login" 
     :default-landing-uri "/iasess/mode/edit"})
   {:session {:store (couch-store store)}}))

(defn -main
  "Run the jetty server."
  [& args]
  (run-jetty secured-app {:port (Integer. (get (System/getenv) "PORT" "8000"))}))
