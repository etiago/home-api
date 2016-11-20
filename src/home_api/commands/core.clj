(ns home-api.commands.core
  (:require
   [clojure.data.json :as json]
   [clj-http.client :as client]
   [liberator.core :refer [resource defresource]]
   [ring.util.codec :as codec]
   [home-api.common-tools.core :as common-tools]
   [home-api.lights.core :as lights-resource])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn- post-command-ok
  [config ctx]
  (let [luis-url
        (if (= "en" (get ctx :language))
          (get-in config [:commands :luis-language-to-endpoints :en])
          (get-in config [:commands :luis-language-to-endpoints :cn]))
        luis-response
          (json/read-str
           (get
            (client/get
             (str luis-url (codec/url-encode (get ctx :command))))
            :body)
           :key-fn keyword)
        luis-intent
        (get
         (nth (get luis-response :intents) 0)
         :intent)]
    (when (and (not= luis-intent "None")
              (not= 0 (count (get luis-response :entities))))
      (doall
       (map
        (fn [light-setting]
          (lights-resource/handle-light-request
           #(get (second light-setting) :on)
           client/put
           config
           (lights-resource/get-light-ids-for-name
            config
            (first light-setting))
           (second light-setting)))
        (get
         (get-in config [:commands
                         :luis-state-to-light-states
                         (keyword luis-intent)])
         (get (nth (get luis-response :entities) 0) :type)))))))
    
(defn commands
  [config form-params]
  (resource
   :allowed-methods [:post :get]
   :available-media-types ["application/x-www-form-urlencoded"]
   :authorized? #(common-tools/authorized? config %)
   :malformed? #(let [fp (get-in % [:request :form-params])]
                  (if (or (not (contains? fp "command"))
                          (not (contains? fp "language")))
                    true
                    [false {:command (get fp "command")
                            :language (get fp "language")}]))
   :post! #(post-command-ok config %)))

