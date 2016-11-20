(ns home-api.lights.core-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging]
            [home-api.lights.core :refer :all]))

(deftest get-bridge-key-test
  (testing "Getting Hue bridge key from config"
    (is (get-bridge-key {:lights
                         {:bridge-key "bridge-key"}})
        "bridge-key")))
