(ns home-api.core
  (:gen-class :main true)
  (:require
   clj-slack.chat
   [liberator.core :refer [resource defresource]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.reload :as reload]
   [compojure.core :refer [defroutes ANY]]
   [clj-http.client :as client]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.tools.logging :as log]
   [ring.adapter.jetty :as jetty]
   [dlink-camera-api.core :as dlink-api]
   [clojure.java.io :as io])
  (:use [slingshot.slingshot :only [throw+ try+]]))



(defn authorized?
  [config ctx]
  (= (get-in config [:api-key])
     (get-in ctx [:request :query-params "key"])))

(defn- expand-single-light-group
  "Given a single group tuple [<name> <set>] and the name-to-id lookup
  map, returns a map with references in <set> expanded" 
  [single-group name-to-id]
  {(first single-group)
   (set
    (filter
     identity
     (reduce
      (partial #(conj %2 (get %1 %3)) name-to-id)
      #{}
      (second single-group))))})

(defn expand-light-groups
  "Returns a new config with the groups under [:lights :group-to-name]
  expanded to their actual camera IDs"
  [config]
  (assoc-in
   config
   [:lights :group-to-name]
   (reduce
    #(merge %1
            (expand-single-light-group
             %2
             (get-in config [:lights :name-to-id])))
    {}
    (get-in config [:lights :group-to-name]))))

(defn get-config
  [config-path]
  (try+
   (expand-light-groups
    (edn/read-string
     (slurp config-path)))
   (catch Object _
     (log/error (str "Config file (" config-path ") not found!")))))

(load "core_cameras")
(load "core_lights")

;; Bind routes to resources
(defn app-routes
  [static-config]
  (compojure.core/routes
   (ANY "/lights/:light-or-group-name" [light-or-group-name]
        (lights
         static-config
         light-or-group-name))
   (ANY "/cameras/:location/:action" [location action]
        (cameras
         static-config
         location
         action))))

(defn -main []
  (jetty/run-jetty (wrap-params
                    (app-routes
                     (get-config "resources/tiago-config.edn")))
                    {:port 3000}))
;; (defn -main []
;;    (jetty/run-jetty (reload/wrap-reload handler) {:port 8080}))
