(in-ns 'home-api.core)

(defn get-bridge-host
  "Returns the bridge's host from config"
  [config]
  (get-in config [:lights :bridge-host]))

(defn get-bridge-key
  [config]
  (get-in config [:lights :bridge-key]))

(defn filter-light-data-by-ids
  "Returns true if light-id matches :uniqueid in the right element of the 
  light-tuple"
  [light-ids light-tuple]
  (contains? light-ids
             (get (nth light-tuple 1) :uniqueid)))

(defn get-lights-state-tuples
  [bridge-host bridge-key light-ids]
  (filter
   (partial filter-light-data-by-ids light-ids)
   (json/read-str (get
                   (client/get
                    (str "http://" bridge-host "/api/" bridge-key "/lights"))
                   :body)
                  :key-fn keyword)))

(defn get-hue-light-numbers
  [light-states]
  (reduce
   (fn [numbers state]
     (conj numbers (name (first state))))
   []
   light-states))

(defn light-is-on?
  [light-states light-number]
  (get-in
   (get
    light-states
    (keyword light-number))
   [:state :on]))

(defn change-light-state
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
  [state-fn client-verb-fn config light-ids request-body]
  (let [bridge-host (get-bridge-host config)
        bridge-key (get-bridge-key config)
        light-states (get-lights-state-tuples
                      bridge-host
                      bridge-key
                      light-ids)]
    (doseq
        [light-number (get-hue-light-numbers light-states)] 
      (future
        (change-light-state
         light-number
         client-verb-fn
         bridge-host
         bridge-key
         (merge (dissoc request-body :action)
                {:on (state-fn (into {} light-states) light-number)}))))))

(def light-request-callbacks
  {:toggle (partial handle-light-request
                    (fn [light-states light-number]
                      (not (light-is-on? light-states light-number))))
   :on (partial handle-light-request
                (fn [_ _] true))
   :off (partial handle-light-request
                 (fn [_ _] false))})

(defn handle-light-resource-verb
  "Handles Liberators POST and PUT requests"
  [client-verb-fn config callbacks ctx]
  (let [request-body (json/read-str
                      (slurp (get-in ctx [:request :body]))
                      :key-fn keyword)]
    ((get callbacks
          (keyword (get request-body :action)))
     client-verb-fn
     config
     (get ctx :light-ids)
     request-body)))

(defn get-light-ids-for-name
  "Returns the light config for a camera name"
  [config name]
  (let [ids (get-in
             (conj
              (get-in config [:lights :name-to-id])
              (get-in config [:lights :group-to-name]))
             [(keyword name)])]
    (if (string? ids)
      (merge #{} ids)
      ids)))

(defresource lights [config light-or-group-name]
  :allowed-methods [:post :put :get]
  :available-media-types ["text-html"]
  :authorized? (partial authorized? config)
  :exists? (fn [ctx]
             (if-let [light-ids (get-light-ids-for-name
                                 config
                                 light-or-group-name)]
               {:light-ids light-ids}))
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
  :handle-ok (fn [ctx] (json/write-str {:result true}))
  :post! (partial handle-light-resource-verb
                  client/post
                  config
                  light-request-callbacks)
  :put! (partial handle-light-resource-verb
                 client/put
                 config
                 light-request-callbacks))
