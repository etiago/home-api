(ns home-api.common-tools.core-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging]
            [home-api.common-tools.core :refer :all]))

(deftest authorization-with-key-in-query-string
  (testing "Verify key in HTTP query string against api-key in config"
    (let [test-config {:api-key "foo"}
          allowed-query {:request
                         {:query-params
                          {"key" (get test-config :api-key)}}}
          not-allowed-query (assoc-in
                             allowed-query
                             [:request :query-params "key"] "foo2")
          empty-query {:request
                       {:query-params
                        {}}}]
      (is (authorized? test-config
                       allowed-query))
      (is (not
           (authorized? test-config
                        not-allowed-query)))
      (is (not
           (authorized? test-config
                        empty-query)))
      (is (not
           (authorized? {}
                        empty-query))))))


;; Expose private functions
(def expand-single-light-group #'home-api.common-tools.core/expand-single-light-group)

(deftest single-group-expansion-in-config
  (testing "Single light groups expansion in config files"
    (is (=
         {:foo #{"abc" "xyz"}}
         (expand-single-light-group
          [:foo #{:key-for-abc :key-for-xyz}]
          {:key-for-abc "abc"
           :key-for-xyz "xyz"})))
    (is (=
         {:foo #{"abc"}}
          (expand-single-light-group
           [:foo #{:key-for-abc :key-for-xyz}]
           {:key-for-abc "abc"})))
    (is (=
         {:foo #{}}
          (expand-single-light-group
           [:foo #{:key-for-abc :key-for-xyz}]
           {})))))

(deftest multiple-group-expansion-in-config
  (testing "Multiple light groups expansion in config files"
    (is (=
         (expand-light-groups
          {:lights
           {:name-to-id
            {:bar1 "id1"
             :bar2 "id2"}
            :group-to-name
            {:foo #{:bar1 :bar2}}}})
         {:lights
          {:name-to-id
           {:bar1 "id1"
            :bar2 "id2"}
           :group-to-name
           {:foo #{"id1" "id2"}}}}))))

(deftest get-config-test
  (testing "Getting the config"
    (with-redefs-fn
      {#'clojure.core/slurp (fn [path] "{:foo \"bar\"}")}
      #(is (=
            (get-config "some-path")
            {:foo "bar"
             :lights {:group-to-name {}}})))
    (with-redefs-fn
      {#'clojure.tools.logging/log* (fn [logger level throwable msg] msg)}
      #(is (=
            (get-config "some-path")
            "Config file (some-path) not found!")))))
