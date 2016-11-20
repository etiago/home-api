(ns home-api.location.core
  (:require
   [clojure.data.json :as json]
   [clj-http.client :as client]
   [liberator.core :refer [resource defresource]]
   [ring.util.codec :as codec]
   [home-api.common-tools.core :as common-tools]
   [home-api.lights.core :as lights-resource]
   [clj-time.core :as t]
   [clj-time.format :as f]
   [clj-time.local :as l])
  (:use [slingshot.slingshot :only [throw+ try+]]))




(def
  person-locations
  (atom {}))

(defn- someone-arrived-home
  [config ctx old-location]
  (let [location-name (get ctx :location-name)]
    (and
     (= "home" location-name)
     (not= old-location location-name))))

(def built-in-formatter (f/formatters :date-time-no-ms))
(defn- is-after-sunset
  [config]
  (t/after?
   (l/local-now)
   (t/to-time-zone
    (f/parse
     built-in-formatter
     (get-in
      (json/read-str
       (get
        (client/get "http://api.sunrise-sunset.org/json?lat=51.9995814&lng=4.3301033&formatted=0")
        :body)
       :key-fn keyword)
      [:results :sunset]))
    (t/time-zone-for-offset 1))))

(defn- turn-on-entrance-light
  [config]
  (lights-resource/handle-light-request
           #(true)
           client/put
           config
           (lights-resource/get-light-ids-for-name
            config
            :entrance-hall)
           {:on true :bri 254}))

(defn- store-new-location
  [config ctx]
  (swap! person-locations merge
         {(get ctx :person-id) (get ctx :location-name)}))

(defn- post-command-ok
  [config ctx]
  (let [person-id (get ctx :person-id)
        old-location (get-in @person-locations [person-id])]
    (future (store-new-location config ctx))
    (when (and
           (someone-arrived-home config ctx old-location)
           (is-after-sunset config))
      (future (turn-on-entrance-light config)))))

(defn location
  [config form-params]
  (resource
   :allowed-methods [:post]
   :available-media-types ["application/x-www-form-urlencoded"]
   :authorized? #(common-tools/authorized? config %)
   :malformed? #(let [fp (get-in % [:request :form-params])]
                  (if (or (not (contains? fp "person-id"))
                          (not (contains? fp "location-name")))
                    true
                    [false {:person-id (get fp "person-id")
                            :location-name (get fp "location-name")}]))
   :post! #(post-command-ok config %)))

(defn- get-location-for-person
  [person-id ctx]
  (json/write-str (get @person-locations person-id)))

(defn location-for-person-id
  [config person-id]
  (resource
   :allowed-methods [:get]
   :available-media-types ["application/json"]
   :authorized? #(common-tools/authorized? config %)
   :handle-ok #(get-location-for-person person-id %)))
