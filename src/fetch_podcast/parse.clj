(ns fetch-podcast.parse
  (:gen-class))


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
