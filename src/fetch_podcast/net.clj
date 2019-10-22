(ns fetch-podcast.net
  (:require [clojure.java.io :as io])
  (:require [clojure.data.codec.base64 :as b64])
  (:require [clj-http.client :as http])
  (:use fetch-podcast.util)
  (:gen-class))


(def user_agent "fetch_podcast/0.5.7")


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
          (let [http_resp
                (http/get url {:headers headers
                               :client-params {"http.useragent" user_agent}
                               :throw-exceptions false}) ]
            (cond
             ; If the response is 304, use cached data.
             (= (http_resp :status) 304)
               (do (if (> verbosity 1)
                     (println "\tGot 304, using cache"))
                   nil)

             ; If the response was 200, stick the new data in the cache.
             (= (http_resp :status) 200)
               (do
                 (if (> verbosity 1)
                   (println "\tFetching"))
                 (let [fname (cache_fname feed)]
                   (io/make-parents fname)
                   (spit fname (http_resp :body)))
                 { (get_key url) (lim_expires (http_resp :headers)) } )

             :else
               (do (if (> verbosity 1)
                     (println (str "Error loading " url)))
                   nil) )))))))
