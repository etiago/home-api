(ns home-api.core
  (:gen-class :main true)
  (:require
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.reload :as reload]
   [clojure.tools.logging :as log]
   [compojure.core :refer [defroutes ANY POST]]
   [ring.adapter.jetty :as jetty]
   [home-api.lights.core :as lights-resource]
   [home-api.commands.core :as commands-resource]
   [home-api.cameras.core :as cameras-resource]
   [home-api.common-tools.core :as common-tools])
  (:use [slingshot.slingshot :only [throw+ try+]]))

;; Bind routes to resources
(defn app-routes
  [static-config]
  (compojure.core/routes
   (ANY "/lights/:light-or-group-name" [light-or-group-name]
        (lights-resource/lights
         static-config
         light-or-group-name))
   (ANY "/cameras/:location/:action" [location action]
        (cameras-resource/cameras
         static-config
         location
         action))
   (POST "/commands"
         {form-params :form-params}
         (commands-resource/commands
          static-config
          form-params))))

(defn -main []
  (jetty/run-jetty (wrap-params
                    (app-routes
                     (common-tools/get-config "resources/config.edn")))
                    {:port 3000}))
