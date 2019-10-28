(ns fetch-podcast.util
  (:require [clojure.string :as str])
  (:require [clojure.data.codec.base64 :as b64])
  (:require [clojure.java.io :as io])
  (:gen-class))


(defn do_homedir
  "Expand ~/ to current user's HOME."
  [fname]
  (str/replace fname #"^~/" (str (System/getenv "HOME") "/" )) )


(defn file_exists
  "Determine if a file exists."
  [fname]
  (.exists (io/as-file fname)))


(defn sha256
  "Get a base64(ish) sha256 of a string."
  [x]
  (let [hash (java.security.MessageDigest/getInstance "SHA-256")]
    (. hash update (.getBytes x))
    (let [digest (.digest hash)]
      (-> (String. (b64/encode digest) "UTF-8")
          (str/replace "=" "")
          (str/replace "/" "_") ))))


(defn get_key [x] (keyword (sha256 x)))


(defn cache_fname
  "Get the cache file name for a given feed."
  [feed]
  (do_homedir (str "~/.fetch_podcast/cache/" (feed :title))))


(defn get_file
  "Copy a URI to a file, if the target did not exist."
  [uri file]
  (if (not (file_exists file))
    (do
      (io/make-parents file)
      (with-open [in  (io/input-stream uri)
                  out (io/output-stream file)]
        (io/copy in out)))))


(defn strip_nonword
  "Squeeze and replace non-word characters."
  [x]
  (-> x (str/trim)
        (str/replace #"[^\w ]+" "")
        (str/replace #" +" "_")))


(defn get_fname
  "Get the filename from the end of a link."
  [x]
  (-> x (str/split #"/")
        (reverse)
        (first)
        (str/split #"[?#]")
        (first) ))
