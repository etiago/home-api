(ns home-api.lights.core-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging]
            [clojure.data.json :as json]
            [home-api.lights.core :refer :all]))

(deftest get-bridge-key-test
  (testing "Getting Hue bridge key from config"
    (is (get-bridge-key {:lights
                         {:bridge-key "bridge-key"}})
        "bridge-key")))

(deftest is-light-uniqueid-in-set?-test
  (testing "Is the uniqueid present in the provided list of ids"
    (testing "Set contains the provided uniqueid"
      (is (is-light-uniqueid-in-set?
           #{"existing-id" "some-other-id"}
           [:1 {:uniqueid "existing-id"}])))
    (testing "Set does not contain the provided uniqueid"
      (is (not (is-light-uniqueid-in-set?
                #{"existing-id" "some-other-id"}
                [:1 {:uniqueid "non-existing-id"}]))))
    (testing "Set is empty"
      (is (not (is-light-uniqueid-in-set?
                #{}
                [:1 {:uniqueid "non-existing-id"}]))))))

(deftest get-lights-state-tuples-by-id-test
  (testing "Do we get the Hue data for the lights in the set"
    (let [client-get-fn
          (fn [_] {:body (json/write-str
                          {"1" {:uniqueid "some-id"}
                           "2" {:uniqueid "some-other-id"}
                           "3" {:uniqueid "yet-another-id"}})})]
      (testing "Data contains more lights and we only get one"
        (with-redefs-fn
          {#'clj-http.client/get client-get-fn}
          #(is (=
                (get-lights-state-tuples-by-id
                 "foo"
                 "bar"
                 #{"some-id"})
                [[:1 {:uniqueid "some-id"}]]))))
      (testing "Data contains more lights and we get multiples"
        (with-redefs-fn
          {#'clj-http.client/get client-get-fn}
          #(is (=
                (get-lights-state-tuples-by-id
                 "foo"
                 "bar"
                 #{"some-id" "yet-another-id"})
                [[:1 {:uniqueid "some-id"}]
                 [:3 {:uniqueid "yet-another-id"}]]))))
      (testing "Data contains lights and we get existing and non-existing id"
        (with-redefs-fn
          {#'clj-http.client/get client-get-fn}
          #(is (=
                (get-lights-state-tuples-by-id
                 "foo"
                 "bar"
                 #{"some-id" "non-existing-id"})
                [[:1 {:uniqueid "some-id"}]]))))
      (testing "Data contains lights and we get a non-existing id"
        (with-redefs-fn
          {#'clj-http.client/get client-get-fn}
          #(is (=
                (get-lights-state-tuples-by-id
                 "foo"
                 "bar"
                 #{"non-existing-id"})
                [])))))))

(deftest get-hue-light-numbers-test
  (testing "Do we get the light numbers from Hue data"
    (testing "when Hue data has multiple lights"
      (is (=
           (get-hue-light-numbers
            [[:1 {:foo "bar"}]
             [:2 {:lorem "ipsum"}]])
           ["1" "2"])))
    (testing "when Hue data has no lights"
      (is (=
           (get-hue-light-numbers
            [])
           [])))))

(deftest light-is-on?-test
  (testing "Can we check whether a light is on"
    (testing "when the light is on"
      (is (light-is-on?
           {:1 {:state {:on true}}}
           "1")))
    (testing "when the light is off"
      (is (not (light-is-on?
                {:1 {:state {:on false}}}
                "1"))))
    (testing "when the light does not exist"
      (is (nil? (light-is-on?
                 {:1 {:state {:on false}}}
                 "2"))))))

