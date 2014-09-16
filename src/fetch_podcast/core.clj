(ns fetch-podcast.core
  (:require [clojure.xml :as xml])
  (:require [clojure.set :as set])
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io])
  (:gen-class))

(defn do_homedir [fname]
  (str/replace fname #"^~" (str (System/getenv "HOME") )) )

(defn read_pref [fname]
  (read-string (slurp (do_homedir (str "~/.fetch_podcast/" fname)))) )

(defn save_pref [fname data]
  (spit (do_homedir (str "~/.fetch_podcast/" fname)) data) )

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

; Construct a hash of included podcast names
(defn get_inclusion_list [args]
  (loop [rc #{} arg_p args]
    (if (empty? arg_p)
      rc
      (if (contains? #{"-v" "-c" "-i"} (first arg_p))
        (recur rc (rest arg_p))
        (recur (conj rc (first arg_p)) (rest arg_p))))))

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

(defn -main [& args]
  ; Parse arguments
  (let [verbose (some #{"-v"} args)
        catchup (some #{"-c"} args)
        reinit  (some #{"-i"} args)
        tgts    (get_inclusion_list args)]

    ;TODO: Print a warning for unknown feeds
    (loop
      [feeds (get_feed_list tgts)
       done  (if reinit #{} (read_pref "fetchlog.clj")) ]

    ; If there were no feeds left to fetch, we're done.
    ; Save a fetchlog and exit
    (if (empty? feeds)
      (save_pref "fetchlog.clj" done)

      ; Otherwise, iterate over the list of feeds
      (let [fhd (first feeds) ftl (rest feeds)]
        (recur ftl
               (set/union done
                          ; foreach feed, iterate over all the items
                          (loop [new_items #{}
                                 items (parse_feed (xml/parse (fhd :feed)))]
                            ; When there are no items left, we're done.
                            ; return the list of items fetched
                            (if (empty? items)
                              new_items

                              ; Otherwise, see if we've fetched the current item yet
                              (let [hd (first items) tl (rest items)]
                                (if (not (contains? done (get_key (hd :enclosure))))
                                  ; if we've not yet snarfed this one,
                                  (let [url (hd :enclosure)
                                        ftgt (do_homedir
                                              (str (fhd :path) "/"
                                                   ((eval (fhd :name_fn)) hd)) ) ]
                                    (do
                                      (if verbose
                                        (do (print (str url " -> " ftgt " ..."))
                                            (flush) ))
                                      (if (not catchup) (copy url ftgt) )
                                      (if verbose (println " done"))
                                      (recur (conj new_items
                                                   (get_key url)) tl)))
                                  (recur new_items tl))))))))))))
