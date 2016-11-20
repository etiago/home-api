(ns home-api.lights.core
  (:require
   [clojure.data.json :as json]
   [clj-http.client :as client]
   [liberator.core :refer [resource defresource]]
   [home-api.common-tools.core :as common-tools]))

(defn get-bridge-host
  "Returns the bridge's host from config"
  [config]
  (get-in config [:lights :bridge-host]))

(defn get-bridge-key
  "Returns the bridge's host key from config"
  [config]
  (get-in config [:lights :bridge-key]))

(defn is-light-uniqueid-in-set?
  "Returns true if light-id matches :uniqueid in the right element of the 
  light-tuple"
  [light-id-set light-tuple]
  (contains? light-id-set
             (get (nth light-tuple 1) :uniqueid)))

(defn get-lights-state-tuples-by-id
  "Slice the Hue lights state and return a sequence with the states for the
  light ids in the specified set. Essentially getting light states from ids."
  [bridge-host bridge-key light-id-set]
  (into []
        (filter
         #(is-light-uniqueid-in-set? light-id-set %)
         (json/read-str (get
                         (client/get
                          (str "http://" bridge-host "/api/" bridge-key "/lights"))
                         :body)
                        :key-fn keyword))))

(defn get-hue-light-numbers
  "From a sequence of light state tuples from Hue, return an array of their 
  numbers."
  [light-states]
  (reduce
   #(conj %1 (name (first %2)))
   []
   light-states))

(defn light-is-on?
  "Based on the light-states from Hue, return whether a light with the specified
  number is on"
  [light-states light-number]
  (get-in
   (get
    light-states
    (keyword light-number))
   [:state :on]))

(defn send-request-to-hue
  "Sends a request to Hue (pass in client/post or client/put) for a particular
  light number, with the specified body"
  [light-number client-verb-fn bridge-host bridge-key request-body]
  (client-verb-fn
   (str
    "http://"
    bridge-host
    "/api/"
    bridge-key
    "/lights/"
    light-number
    "/state")
   {:body
    (json/write-str request-body)}))

(defn handle-light-request
  [state-fn client-verb-fn config light-id-set request-body]
  (let [bridge-host (get-bridge-host config)
        bridge-key (get-bridge-key config)
        light-states (get-lights-state-tuples-by-id
                      bridge-host
                      bridge-key
                      light-id-set)]
    (doseq
        [light-number (get-hue-light-numbers light-states)] 
      (future
        (send-request-to-hue
         light-number
         client-verb-fn
         bridge-host
         bridge-key
         (merge (dissoc request-body :action)
                {:on (state-fn (into {} light-states) light-number)}))))))

(def state-functions
  {:toggle #(not (light-is-on? %1 %2))
   :on (fn [_ _] true)
   :off (fn [_ _] false)})

(defn handle-light-resource-verb
  "Handles Liberators POST and PUT requests"
  [client-verb-fn config ctx]
  (let [request-body (json/read-str
                      (slurp (get-in ctx [:request :body]))
                      :key-fn keyword)]
    (handle-light-request
     (get state-functions
          (keyword (get request-body :action)))
     client-verb-fn
     config
     (get ctx :selected-light-id-set)
     request-body)))

(defn get-light-ids-for-name
  "Returns the light config for a camera name"
  [config name]
  (let [ids (get-in
             (conj
              (into {}
                     (get-in config [:lights :name-to-id]))
              (into {}
                    (get-in config [:lights :group-to-name])))
             [(keyword name)])]
    (if (string? ids)
      (merge #{} ids)
      ids)))

(defresource lights [config light-or-group-name]
  :allowed-methods [:post :put :get]
  :available-media-types ["text-html"]
  :authorized? #(common-tools/authorized? config %)
  :exists? (fn [_]
             (if-let [light-ids (get-light-ids-for-name
                                 config
                                 light-or-group-name)]
               {:selected-light-id-set light-ids}))
  :handle-exception (fn [_]
                      (json/write-str
                       {:result false
                        :reason "ERR_UPSTREAM_FAILURE"}))
  :handle-not-found (fn [_]
                      (json/write-str
                       {:result false
                        :reason "ERR_UNKNOWN_INPUT"}))
  :handle-unauthorized (fn [_]
                         (json/write-str {:result false
                                          :reason "ERR_AUTH_FAILED"}))
  :handle-ok (fn [_] (json/write-str {:result true}))
  :post! #(handle-light-resource-verb client/post config %)
  :put! #(handle-light-resource-verb client/put config %))
