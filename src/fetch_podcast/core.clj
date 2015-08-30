(ns fetch-podcast.core
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io])
  (:require [clj-http.client :as http])
  (:require [clojure.data.codec.base64 :as b64])
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(defn do_homedir
  "Expand ~/ to current user's HOME."
  [fname]
  (str/replace fname #"^~/" (str (System/getenv "HOME") "/" )) )

(defn file_exists
  "Determine if a file exists."
  [fname]
  (.exists (io/as-file fname)))

(defn read_pref
  "Read a preference file, if it exists."
  [fname & [default reset] ]
  (let [file_path (do_homedir (str "~/.fetch_podcast/" fname))]
    (if (or reset (not (file_exists file_path)))
      default
      (read-string (slurp file_path)))))

(defn save_pref
  "Save a preference file."
  [fname data]
  (spit (do_homedir (str "~/.fetch_podcast/" fname)) data) )

(defn cache_fname
  "Get the cache file name for a given feed."
  [feed]
  (do_homedir (str "~/.fetch_podcast/cache/" (feed :title))))

(defn get_file
  "Copy a URI to a file, if the target did not exist."
  [uri file]
  (if (not (file_exists file))
    (let [http_resp (http/get uri {:as :byte-array})]
      (with-open [out (io/output-stream file)]
        (io/copy (http_resp :body) out)))))

(defn sha256
  "Get a base64(ish) sha256 of a string."
  [x]
  (let [hash (java.security.MessageDigest/getInstance "SHA-256")]
    (. hash update (.getBytes x))
    (let [digest (.digest hash)]
      (-> (String. (b64/encode digest) "UTF-8")
          (str/replace "=" "")
          (str/replace "/" "_") ))))

(defn lim_expires
  "Limit expires headers not to exceed 7 days from now."
  [resp]
  (if (contains? resp "Expires")
    (let [ex (new java.util.Date (resp "Expires"))
          ft (let [cal (java.util.Calendar/getInstance)]
               (. cal setTime (new java.util.Date))
               (. cal add (. java.util.Calendar DATE) 7)
               (. cal getTime)) ]
      (if (pos? (compare ex ft))
        (assoc resp "Expires" ft)
        resp))
    resp))

(defn get_key [x] (keyword (sha256 x)))

(defn parse_item
  "Parse an XML item to produce a map describing the feed."
  [xdata]
  (loop [rc {} elem xdata]
    ; If there are no more elements in this <item>, we're done
    (if (empty? elem)
      rc

      ; Otherwise, see if this element is one of the desired ones.  If
      ; so, add it in to the rc.  For the enclosure, grab the URL
      ; attribute.  Then, move on to the next element.
      (let [hd (first elem) tl (rest elem)]
        (cond
         (contains? #{:title :link :guid :pubDate} (hd :tag) )
           (recur (assoc rc (hd :tag) (first (hd :content))) tl)
         (= (hd :tag) :enclosure)
           (recur (assoc rc :enclosure ((hd :attrs) :url) ) tl )
         :else
           (recur rc tl))))))

(defn parse_feed
  "Parse an XML feed, returning an array of maps describing the enclosures."
  [xdata]
  ; Feeds start out as <rss><channel>, so grab the content of the channel
  (loop [rc [] elem ( (first (xdata :content)) :content ) ]
    ; When there are no more elements in the channel, we're done
    (if (empty? elem)
      rc

      (let [hd (first elem) tl (rest elem)]
        ; If the current element is an item, we want to parse it.
        ; Otherwise, move on to the next element.
        (if (= (hd :tag) :item)
          (recur (conj rc (parse_item (hd :content))) tl )
          (recur rc tl))))))

(defn fetch_feed
  "Fetch an XML feed with caching and If-None-Match/If-Modified-Since."
  [feed cache options]
  (let [verbosity (options :verbosity) ]
    (if (options :cache)
      nil ; If we're only using cached feeds, skip fetching new ones.

      (let [url (feed :feed)
            last_resp (cache (get_key url))
            ; Decide what additional headers should be included when
            ; we fetch this feed, based on the http response from the
            ; last time that we fetched it.
            headers
            (cond
             ; If our cached data is still good, set the headers to
             ; nil to indicate that we should skip the fetch.
             (and (contains? last_resp "Expires")
                  (pos? (compare (new java.util.Date (last_resp "Expires"))
                                 (new java.util.Date))))
               nil

             ; If we have an Etag, ETag, or Last-Modified, then use
             ; that on our next fetch.  Prefer the Etag to the
             ; Last-Modified in the case that we have both.
             (contains? last_resp "Etag")
               {"If-None-Match" (last_resp "Etag") }
             (contains? last_resp "ETag")
               {"If-None-Match" (last_resp "ETag") }
             (contains? last_resp "Last-Modified")
               {"If-Modified-Since" (last_resp "Last-Modified")}

             ; If we have none of those, don't use any additional headers
             :else
               {})]

        (if (> verbosity 1)
          (do (println (str "Updating " (feed :title) " from " url ))))

        (if (nil? headers)
          (do (if (> verbosity 1)
                (println "\tNot expired, using cache"))
              nil)

          ; If we need a new fetch, then do one.
          (let [http_resp (http/get url {:headers headers :throw-exceptions false}) ]
            (cond
             ; If the response is 304, use cached data.
             (= (http_resp :status) 304)
               (do (if (> verbosity 1)
                     (println "\tGot 304, using cache"))
                   nil)

             ; If the response was 200, stick the new data in the cache.
             (= (http_resp :status) 200)
               (do (if (> verbosity 1)
                     (println "\tFetching"))
                   (spit (cache_fname feed) (http_resp :body))
                   { (get_key url) (lim_expires (http_resp :headers)) } )

             :else
               (throw (str "Error loading " url)))))))))

(defn process_feed
  "Process a specified feed, downloading enclosures as required."
  [feed done tgts options]
  (let [verbosity (options :verbosity)
        ep_tgts (if (options :episodes) tgts nil)
        file (cache_fname feed)]

    (if (not (file_exists file))
      (do (if (> verbosity 0)
            (println (str "Skipping " (feed :title) " because "
                          "cache file (" file ") is missing")))
        #{})

      ; Iterate over the items in this feed.
      (loop [new_items #{}
             items (parse_feed (xml/parse file)) ]

        ; If we have no more items then we're done
        (if (empty? items)
          new_items

          ; Otherwise, iterate over the remaining items.
          (let [hd (first items) tl (rest items)
                hv (sha256 (hd :guid))]

            ; If we've already gotten this ep, or if there was a list of eps
            ;  that this is not on, then proceed to the next item
            (if (or (contains? done (keyword hv))
                    (and (not (nil? ep_tgts))
                         (not (contains? ep_tgts hv))) )
              (recur new_items tl)

              ; Otherwise, fetch this ep
              (let [url (hd :enclosure)
                    ; Figure out the target filename by calling the
                    ; name_fn from the feed config.
                    ftgt (do_homedir
                          (str (feed :path) "/"
                               ((eval (feed :name_fn)) hd)) ) ]
                (do
                  (if (> verbosity 0) (println (str hv "\n\t" url) ))
                  (if (not (or (options :catchup)
                               (options :dry-run)))
                    (get_file url ftgt))
                  (if (> verbosity 0)  (println (str "\t-> " ftgt)))
                  (if (options :dry-run)
                    (recur new_items tl)
                    (recur (conj new_items
                                 (get_key (hd :guid))) tl)))))))))))

(defn -main [& args]
  ; Parse arguments
  (let [cli-options [["-v" nil "Verbosity"
                      :id :verbosity
                      :default 0
                      :assoc-fn (fn [m k _] (update-in m [k] inc)) ]
                     ["-c" "--catchup"]
                     ["-d" "--dry-run"]
                     ["-i" "--init"]
                     ["-e" "--episodes"]
                     ["-F" "--force-fetch"]
                     ["-C" "--cache"] ]
        opt_map (parse-opts args cli-options)
        options (opt_map :options)
        tgts    (into #{} (opt_map :arguments)) ]

    (loop
        [feeds (let [feeds (read_pref "feeds.clj" [])]
                 (if (and (not (options :episodes))
                          (not (empty? tgts)) )
                   (filter #(contains? tgts (% :title)) feeds)
                   feeds))
         cache (read_pref "cache_metadata.clj" {} (options :force-fetch))
         done  (read_pref "fetchlog.clj" #{} (options :init)) ]

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
                            (process_feed fhd done tgts options)) ))))))
