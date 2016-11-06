(defproject home-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler home-api.core/handler
         :main home-api.core}
  :main home-api.core
  :profiles {:uberjar {:aot :all}}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [compojure "1.5.1"]
                 [liberator "0.14.1"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-codec "1.0.1"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [clj-http "3.3.0"]
                 [slingshot "0.12.2"]
                 [org.tiago/dlink-camera-api "0.3.0"]
                 [org.julienxx/clj-slack "0.5.4"]])
