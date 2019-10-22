(defproject fetch_podcast "0.5.7"
  :description "Simple command-line podcast fetcher"
  :url "https://github.com/crhalpin/fetch_podcast"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/data.codec "0.1.1"]
                 [com.taoensso/nippy "2.14.0"]
                 [clj-http "3.10.0"] ]
  :main ^:skip-aot fetch-podcast.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
