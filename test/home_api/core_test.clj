(ns home-api.core-test
  (:require [clojure.test :refer :all]
            [home-api.core :refer :all]
            [home-api.lights.core :refer :all]
            [home-api.common-tools.core :refer :all]))



; - core_lights.clj

(deftest pull-bridge-host-from-config
  (testing "Pulling bridge host from the config"
    (is (=
         (get-bridge-host {:lights {:bridge-host "foo"}})
         "foo"))))

(deftest get-light-ids-for-name-test
  (testing "Getting light ids from the config based on a name"
    (is (=
         (get-light-ids-for-name
          {:lights {:name-to-id {:light-foo "this-is-a-light-id"}}}
          "light-foo")
         #{"this-is-a-light-id"}))
    (is (=
         (get-light-ids-for-name
          {:lights 
           {:name-to-id {}
            :group-to-name {:light-foo #{"this-is-a-light-id"
                                         "this-is-another-light-id"}}}}
          "light-foo")
         #{"this-is-a-light-id"
           "this-is-another-light-id"}))
    ; The test below is a regression from an actual bug
    (is (=
         (get-light-ids-for-name
          {:lights 
           {:group-to-name
            {:light-foo #{"this-is-a-light-id"
                          "this-is-another-light-id"}}}}
          "light-foo")
         #{"this-is-a-light-id"
           "this-is-another-light-id"}))
    (is (nil?
         (get-light-ids-for-name
          {:lights {:name-to-id {:light-foo "this-is-a-light-id"
                                 :light-bar "this-is-another-light-id"}}}
          "light-not-existing")))))
                                 

(deftest send-request-to-hue-test
  (testing "Sending a request to Hue"
    (defn mock-client-fn
      [uri payload]
      (and
       (is
        (= uri
          "http://mock-host/api/mock-key/lights/1/state"))
       (is
        (= payload
          {:body "{\"foo\":\"bar\"}"}))))
    (is
     (= (send-request-to-hue
         "1"
         mock-client-fn
         "mock-host"
         "mock-key"
         {:foo "bar"})
        true))))
