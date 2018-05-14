(defproject cjoudant "0.1.0-SNAPSHOT"
  :description "Cjoudant: a tiny Cloudant client library for Clojure"
  :url "https://github.com/xpqz/cjoudant"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/"}
 :dependencies [[org.clojure/clojure "1.7.0"]
                [cheshire "5.7.1"]
                [org.clojure/core.async "0.3.443"]
                [http-kit "2.2.0"]]
  :main ^:skip-aot cjoudant.client
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
