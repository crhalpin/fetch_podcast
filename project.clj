(defproject fetch_podcast "0.5.6"
  :description "Simple command-line podcast fetcher"
  :url "https://github.com/crhalpin/fetch_podcast"
  :license {:name "BSD 2-Clause"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/data.codec "0.1.0"]
                 [clj-http "2.0.1"] ]
  :main ^:skip-aot fetch-podcast.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
