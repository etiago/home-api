(ns home-api.common-tools.core
  (:require
   [clojure.edn :as edn]
   [clojure.tools.logging :as log])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn authorized?
  [config ctx]
  (let [config-api-key (get-in config [:api-key])
        ctx-api-key (get-in ctx [:request :query-params "key"])]
    (and (some? config-api-key)
         (some? ctx-api-key)
         (= config-api-key ctx-api-key))))

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
      (clojure.tools.logging/error (str "Config file (" config-path ") not found!")))))
