(ns home-api.core-test
  (:require [clojure.test :refer :all]
            [home-api.core :refer :all]))

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

