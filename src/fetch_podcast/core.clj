(ns fetch-podcast.core
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [clojure.string :as str])
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:use fetch-podcast.util)
  (:use fetch-podcast.parse)
  (:use fetch-podcast.prefs)
  (:use fetch-podcast.net)
  (:gen-class))


(defn name_fn [name_fn_raw feed hv]
  "Apply a feed's name function with a faillback when it fails"
  (do_homedir
   (str (feed :path) "/"
        (try (name_fn_raw feed)
             (catch Exception e
               (-> (feed :enclosure)
                   (get_fname)
                   (#(str (subs hv 0 10) "-" %))))))))


(defn process_feed
  "Process a specified feed, downloading enclosures as required."
  [feed done tgts options]
  (let [verbosity (options :verbosity)
        file (cache_fname feed)
        name_fn_raw (eval (feed :name_fn))
        ; Episodes be skipped over when:
        skip? (fn [options tgts done item_cnt hv]
                (or
                 ; 1) This ep has already been fetched
                 (contains? done (keyword hv))
                 ; 2) We've fetched enough episodes
                 (and (contains? options :number)
                      (>= item_cnt (options :number)))
                 ; 3) A list of eps was provided and this ep is
                 ; not on it
                 (and (options :episodes)
                      (not (contains? tgts hv))))) ]

    (if (not (file_exists file))
      (do (if (> verbosity 0)
            (println (str "Skipping " (feed :title) " because "
                          "cache file (" file ") is missing")))
        #{})

      ; Iterate over the items in this feed.
      (loop [new_items #{}
             items (parse_feed (xml/parse file))
             item_cnt 0]

        ; If we have no more items then we're done
        (if (empty? items)
          new_items

          ; Otherwise, iterate over the remaining items.
          (let [hd (first items)
                tl (rest items)
                hv (sha256 (hd :guid)) ]

            (if (skip? options tgts done item_cnt hv)
              (recur new_items tl item_cnt)

              ; Otherwise, fetch this episode
              (let [url (hd :enclosure)
                    ftgt (name_fn name_fn_raw hd hv)]
                (do
                  (if (> verbosity 0)
                    (println (str hv "\n\t" url) ))
                  (if (not (or (options :catchup)
                               (options :dry-run)))
                    (get_file url ftgt))
                  (if (> verbosity 0)
                    (println (str "\t-> " ftgt)))
                  (if (options :dry-run)
                    (recur new_items tl (inc item_cnt) )
                    (recur
                     (conj new_items (get_key (hd :guid)))
                     tl
                     (inc item_cnt)) ))))))))))


(defn feed_list
  "Read the list of configured feeds, filtering as necessary"
  [options tgts]
  (let [feeds (read_feeds)]
    (if (and (not (options :episodes))
             (not (empty? tgts)) )
      (filter #(contains? tgts (% :title)) feeds)
      feeds)))


(defn -main [& args]
  ; Parse arguments
  (let [cli-options [["-v" nil "Verbosity"
                      :id :verbosity
                      :default 0
                      :update-fn inc ]
                     ["-n" "--number NUM" :parse-fn #(Integer/parseInt %) ]
                     ["-c" "--catchup"]
                     ["-d" "--dry-run"]
                     ["-i" "--init"]
                     ["-e" "--episodes"]
                     ["-F" "--force-fetch"]
                     ["-C" "--cache"] ]
        opt_map (parse-opts args cli-options)
        options (opt_map :options)
        tgts    (into #{} (opt_map :arguments)) ]
    (System/setProperty "http.agent" user_agent)
    (loop
        [feeds (feed_list options tgts)
         cache (read_pref "cache_metadata.clj" {} (options :force-fetch))
         done  (read_pref "fetchlog.clj" #{} (options :init)) ]

      ; If there were no feeds left to fetch, we're done.
      ; Save a fetchlog and exit
      (if (empty? feeds)
        (do
          (save_pref "fetchlog.clj" done)
          (save_pref "cache_metadata.clj" cache) )

        ; Otherwise, process the current feed
        (let [fhd (first feeds)
              ftl (rest feeds)
              new_cache
                (merge cache (fetch_feed fhd cache options))
              new_done
                (set/union done (process_feed fhd done tgts options)) ]
          (recur ftl new_cache new_done) )))))
