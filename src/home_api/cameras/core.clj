(ns home-api.cameras.core
  (:require
   clj-slack.chat
   [clojure.data.json :as json]
   [clojure.zip :as zip]
   [clj-http.client :as client]
   [liberator.core :refer [resource defresource]]
   [home-api.common-tools.core :as common-tools]
   [dlink-camera-api.core :as dlink-api]
   [clojure.xml :as xml]))

;;Tiago: Couldn't find it in clj.zip so I just used it directly
;;convenience function, first seen at nakkaya.com later in clj.zip src
(defn- zip-str [s]
  (zip/xml-zip 
   (xml/parse (java.io.ByteArrayInputStream. (.getBytes s)))))

(defn- get-camera-config-for-location
  "Returns the camera config for a camera location"
  [config location]
  (get-in config [:cameras (keyword location)]))

;; Return value from camera's response
(defn- parse-dlink-camera-return-value
  ([xml-body]
   (nth
    (get-in
     (nth
      (get-in
       (nth
        (zip-str
         (get-in xml-body
          [:body])) 0) [:content]) 0) [:content]) 0)))

(defn- camera-request-dlink
  "Returns true if the request to the camera returned 0 (success) or not"
  [request-parameters]
  (apply dlink-api/do-camera-request
         (map
          (fn [x] (nth x 1))
          (select-keys
           request-parameters [:ip :username :password :form-params]))))
  
(defn- camera-request-foscam
  [request-parameters]
   (= "0" (parse-dlink-camera-return-value
           (client/get
            (get request-parameters :url)
            {:socket-timeout 3000 :conn-timeout 3000}))))
  
(defn- camera-request
  [camera-config action]
  (case (get camera-config :brand)
    :foscam (camera-request-foscam
             {:url (get camera-config (keyword action))})
    :dlink (camera-request-dlink
            (merge camera-config
                   {:form-params
                    (get-in camera-config
                            [:form-params (keyword action)])}))))

(defn- handle-ok
  [action ctx notification-fn]
  (if (camera-request (get ctx :camera-config) action)
    (do
      (eval notification-fn)
      (json/write-str {:result true}))
    (json/write-str {:result false})))

(defn- handle-ok-without-notification
  [action ctx]
  (handle-ok action ctx nil))

(defn- handle-ok-with-notification
  [action location camera-state-notifications ctx]
  (handle-ok action ctx `(clj-slack.chat/post-message
                          {:api-url "https://slack.com/api" :token ~(get camera-state-notifications :slack-token)}
                          ~(get camera-state-notifications :slack-channel-id)
                          ~(str "Camera " location " set to " action)
                          {:username ~(get camera-state-notifications :slack-bot-username)})))

;; /cameras/ resource
(defn cameras
  [config location action]
  (let [handle-ok-fn (if (contains? config :camera-state-notifications)
                       (partial handle-ok-with-notification action location (get-in config [:camera-state-notifications]))
                       (partial handle-ok-without-notification action))]
    (resource
     :available-media-types ["text-html"]
     :authorized? (partial common-tools/authorized? config)
     :exists? (fn [ctx]
                (if-let [camera-config
                         (get-camera-config-for-location config location)]
                  {:camera-config camera-config}))
     :handle-exception (fn [_]
                         (json/write-str {:result false, :reason "ERR_UPSTREAM_FAILURE"}))
     :handle-not-found (fn [_]
                         (json/write-str {:result false, :reason "ERR_UNKNOWN_INPUT"}))
     :handle-unauthorized (fn [_]
                            (json/write-str {:result false, :reason "ERR_AUTH_FAILED"}))
     :handle-ok handle-ok-fn)))
