(ns fetch-podcast.core
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io])
  (:require [clj-http.client :as http])
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(defn do_homedir [fname]
  (str/replace fname #"^~" (str (System/getenv "HOME") )) )

(defn read_pref [fname]
  (read-string (slurp (do_homedir (str "~/.fetch_podcast/" fname)))) )

(defn save_pref [fname data]
  (spit (do_homedir (str "~/.fetch_podcast/" fname)) data) )

(defn cache_fname [feed]
  (do_homedir (str "~/.fetch_podcast/cache/" (feed :title))))

(defn copy [uri file]
  (with-open [in (io/input-stream uri)
              out (io/output-stream file)]
    (io/copy in out)) )

(defn sha256 [x]
  (let [hash (java.security.MessageDigest/getInstance "SHA-256")]
    (. hash update (.getBytes x))
    (let [digest (.digest hash)]
      (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))))

(defn get_key [x]
  (keyword (sha256 x)))

; Parse feed XML to return an array of elements
(defn parse_feed [xdata]
  (loop [rc [] elem ( (first (xdata :content)) :content ) ]
    (if (empty? elem)
      rc
      (let [hd (first elem) tl (rest elem)]
        (if (= (hd :tag) :item)
          (recur (conj rc
                       (loop [rc {} elem (hd :content)]
                         (if (empty? elem)
                           rc
                           (let [hd (first elem) tl (rest elem)]
                             (cond
                              (contains? #{:title :link :guid :pubDate} (hd :tag) )
                                (recur (assoc rc (hd :tag) (first (hd :content))) tl)
                              (= (hd :tag) :enclosure)
                                (recur (assoc rc :enclosure ((hd :attrs) :url) ) tl )
                              :else
                                (recur rc tl)))))) tl )
          (recur rc tl))))))

; Construct a list of feeds to fetch
(defn get_feed_list [tgts]
  (loop [rc [] ifeeds (read_pref "feeds.clj")]
    (cond
     (empty? ifeeds)
       rc
     (or (empty? tgts)
         (contains? tgts ((first ifeeds) :title)))
       (recur (conj rc (first ifeeds)) (rest ifeeds))
     :else
       (recur rc (rest ifeeds)))) )

; Fetch a copy of the given feed
(defn fetch_feed [feed cache options]
  (let [verbose (options :verbose)]
    (let [url (feed :feed)
          last_resp (cache (get_key url))
          headers
          (cond
           (nil? last_resp) {}
           (contains? last_resp "Expires")
             (if (pos? (compare (new java.util.Date (last_resp "Expires"))
                                (new java.util.Date)))
               nil
               {} )
           (contains? last_resp "Etag")
             {"If-None-Match" (last_resp "Etag") }
           (contains? last_resp "Last-Modified")
             {"If-Modified-Since" (last_resp "Last-Modified")} )]
      (if verbose (do (println (str "Updating " (feed :title) " from " url ))))
      (if (nil? headers)
        (do (println "\tCached copy not expired, using that")
            nil) ; Not expired, do nothing
        (let [http_resp (http/get url {:headers headers :throw-exceptions false}) ]
          (cond
           (= (http_resp :status) 304)
             (do (println "\tGot 304 not modified, using cache")
                 nil) ; Not fetched, do nothing
           (= (http_resp :status) 200)
             (do
               (println "\tFetched new copy")
               (spit (cache_fname feed) (http_resp :body))
               { (get_key url) (http_resp :headers) } )
           :else
             (throw (str "Error loading " url))))))))

; Process a specified feed, downloading enclosures as required
(defn process_feed [feed done options]
  (let [verbose (options :verbose)
        catchup (options :catchup)]
    (loop [new_items #{}
           items (parse_feed (xml/parse (cache_fname feed))) ]

      (if (empty? items)
        new_items

        (let [hd (first items) tl (rest items)]
          (if (contains? done (get_key (hd :enclosure)))
            (recur new_items tl)

            (let [url (hd :enclosure)
                  ftgt (do_homedir
                        (str (feed :path) "/"
                             ((eval (feed :name_fn)) hd)) ) ]
              (do
                (if verbose (println url))
                (if (not catchup) (copy url ftgt) )
                (if verbose (println (str "\t" ftgt)))
                (recur (conj new_items
                             (get_key url)) tl)))))))))

(defn -main [& args]
  ; Parse arguments
  (let [cli-options  [["-v" "--verbose"]
                      ["-c" "--catchup"]
                      ["-i" "--init"]
                      ["-F" "--force-fetch"] ]
        opt_map (parse-opts args cli-options)
        options (opt_map :options)
        reinit  (options :init)
        refetch (options :force-fetch)
        tgts    (into #{} (opt_map :arguments)) ]

    ;TODO: Print a warning for unknown feeds
    (loop
      [feeds (get_feed_list tgts)
       cache (if refetch {} (read_pref "cache_metadata.clj"))
       done  (if reinit #{} (read_pref "fetchlog.clj")) ]

    ; If there were no feeds left to fetch, we're done.
    ; Save a fetchlog and exit
    (if (empty? feeds)
      (do
        (save_pref "fetchlog.clj" done)
        (save_pref "cache_metadata.clj" cache) )

      ; Otherwise, process the current feed
      (let [fhd (first feeds) ftl (rest feeds)]
        (recur ftl
               (merge cache (fetch_feed fhd cache options))
               (set/union done
                          (process_feed fhd done options)) ))))))
