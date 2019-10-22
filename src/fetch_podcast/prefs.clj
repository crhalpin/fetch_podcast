(ns fetch-podcast.prefs
  (:require [clojure.java.io :as io])
  (:require [taoensso.nippy :as nippy])
  (:use fetch-podcast.util)
  (:gen-class))


(defn read_feeds
  "Read feeds file"
  []
  (let [file_path (do_homedir "~/.fetch_podcast/feeds.clj")
        nfile_path (clojure.string/replace file_path ".clj" ".npy")]
    (if (not (file_exists file_path))
      []
      (if (or (not (file_exists nfile_path))
              (> (.lastModified (io/file file_path))
                 (.lastModified (io/file nfile_path))))
        (let [data (read-string (slurp file_path))]
          (nippy/freeze-to-file nfile_path data)
          data)
        (nippy/thaw-from-file nfile_path)))))


(defn read_pref
  "Read a preference file, if it exists."
  [fname & [default reset] ]
  (let [file_path (do_homedir (str "~/.fetch_podcast/" fname))
        nfile_path (clojure.string/replace file_path ".clj" ".npy")]
    (if (or reset
            (and (not (file_exists file_path))
                 (not (file_exists nfile_path))))
      default
      (if (file_exists nfile_path)
        (nippy/thaw-from-file nfile_path)
        (let [data (read-string (slurp file_path))]
          (io/delete-file file_path)
          (nippy/freeze-to-file nfile_path data)
          data)))))


(defn save_pref
  "Save a preference file."
  [fname data]
  (let [file_path (do_homedir (str "~/.fetch_podcast/" fname))
        nfile_path (clojure.string/replace file_path ".clj" ".npy")]
    (nippy/freeze-to-file nfile_path data)))
