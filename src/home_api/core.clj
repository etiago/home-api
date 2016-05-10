(ns home-api.core
  (:require [liberator.core :refer [resource defresource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [defroutes ANY]]
            [clj-http.client :as client]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log])
  (:use [slingshot.slingshot :only [throw+ try+]]))

;;Tiago: Couldn't find it in clj.zip so I just used it directly
;;convenience function, first seen at nakkaya.com later in clj.zip src
(defn zip-str [s]
  (zip/xml-zip 
   (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

;; Slurp edn file as configuration
(def config
(try+
  (edn/read-string (slurp "resources/config.edn"))
  (catch Object _
    (log/error "Config file (config.edn) not found!")
    (System/exit -1))))

;; Return camera url from location and action
(defn get-camera-url
  ([location action]
   (get-in config [:urls (keyword location) (keyword action)])))

;; Return value from camera's response
(defn parse-camera-return-value
  ([xml-body]
   (nth
    (get-in
     (nth
      (get-in
       (nth
        (zip-str
         (get-in xml-body
          [:body])) 0) [:content]) 0) [:content]) 0)))

;; Return whether camera's return value is 0 or not
(defn camera-request
  ([camera-url]
   (= "0" (parse-camera-return-value
           (client/get camera-url)))))

;; /cameras/ resource
(defresource cameras [location action]
  :available-media-types ["text-html"]
  :authorized? (fn [ctx]
                 (= (get-in config [:api-key])
                    (get-in ctx [:request :query-params "key"])))
  :exists? (fn [ctx] (if-let [url (get-camera-url location action)]
                       {:camera-url url}))
  :handle-exception (fn [_]
                      (json/write-str {:result false, :reason "ERR_UPSTREAM_FAILURE"}))
  :handle-not-found (fn [_]
                      (json/write-str {:result false, :reason "ERR_UNKNOWN_INPUT"}))
  :handle-unauthorized (fn [_]
                         (json/write-str {:result false, :reason "ERR_AUTH_FAILED"}))
  :handle-ok (fn [ctx]
               (if (camera-request (get ctx :camera-url))
                 (json/write-str {:result true})
                 (json/write-str {:result false}))))

;; Bind routes to resources
(defroutes app
  (ANY "/cameras/:location/:action" [location action]
       (cameras location action)))

(def handler              
  (-> app    
      wrap-params))
