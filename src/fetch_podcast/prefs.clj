(ns fetch-podcast.prefs
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io])
  (:require [taoensso.nippy :as nippy])
  (:require [fetch-podcast.util :as fp-util])
  (:gen-class))


(defn read_feeds
  "Read feeds file"
  []
  (let [file_path (fp-util/do_homedir "~/.fetch_podcast/feeds.clj")
        nfile_path (str/replace file_path ".clj" ".npy")]
    (if (not (fp-util/file_exists file_path))
      []
      (if (or (not (fp-util/file_exists nfile_path))
              (> (.lastModified (io/file file_path))
                 (.lastModified (io/file nfile_path))))
        (let [data (read-string (slurp file_path))]
          (nippy/freeze-to-file nfile_path data)
          data)
        (nippy/thaw-from-file nfile_path)))))


(defn read_pref
  "Read a preference file, if it exists."
  [fname & [default reset] ]
  (let [file_path (fp-util/do_homedir (str "~/.fetch_podcast/" fname))
        nfile_path (str/replace file_path ".clj" ".npy")]
    (if (or reset
            (and (not (fp-util/file_exists file_path))
                 (not (fp-util/file_exists nfile_path))))
      default
      (if (fp-util/file_exists nfile_path)
        (nippy/thaw-from-file nfile_path)
        (let [data (read-string (slurp file_path))]
          (io/delete-file file_path)
          (nippy/freeze-to-file nfile_path data)
          data)))))


(defn save_pref
  "Save a preference file."
  [fname data]
  (let [file_path (fp-util/do_homedir (str "~/.fetch_podcast/" fname))
        nfile_path (str/replace file_path ".clj" ".npy")]
    (nippy/freeze-to-file nfile_path data)))
