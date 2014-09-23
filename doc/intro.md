# Introduction to fetch_podcast

## Creating feeds.clj

fetch\_podcast reads a list of feeds from ~/.fetch_podcast/feeds.clj.
This file contains a clojure vector where each element is a map that
describes a feed.  Each map must contain 'title', 'path', 'feed', and
'name\_fn'.  For example:

    [{:title "FLOSS_Weekly"
      :path "~/Podcasts/FLOSS_Weekly"
      :feed "http://leo.am/podcasts/floss"
      :name_fn (fn [item]
        (-> (item :enclosure)
            (clojure.string/split #"/")
            (reverse)
            (first) ))}
     {:title "PenAddict"
      :path "~/Podcasts/PenAddict"
      :feed "http://relay.fm/penaddict/feed"
      :name_fn (fn [item]
        (format "%05d-%s.mp3"
          (Integer/parseInt (nth (reverse
            (clojure.string/split (item :enclosure) #"/")) 1))
          (-> (nth (clojure.string/split (item :title) #":") 1)
            (clojure.string/trim)
            (clojure.string/replace #"[^\w ]+" "")
            (clojure.string/replace #" +" "_")) )) }
    ]

The above defines two feeds.  The first, "FLOSS\_Weekly", will be
downloaded to "~/Podcasts/FLOSS\_Weekly", pulled from
"http://leo.am/podcasts/floss", and which uses a name function that
just takes the last element of the enclosure from the podcast feed.
The second, "PenAddict", will be downloaded to "~/Podcasts/PenAddict",
pulled from "http://relay.fm/penaddict/feed", with a somewhat more
complex naming function.

An excerpt of the XML from this second feed looks like:

    <item>
      <title>The Pen Addict 1: Glossary</title>
      <enclosure url="http://relay-fm.herokuapp.com/penaddict/1/listen.mp3"
        length="16211569" type="audio/mp3"/>

As you might expect from the above, all of the enclosures in this feed
use "listen.mp3" as the filename, so we cannot actually use that
because each new episode would overwrite the last.  Instead, we take
the second to last path component ("1" in this example), convert it to
a 5 digit number, then tack on the part of the title from after the
colon.

## Downloading podcasts

Before the first time you run fetch\_podcast, you will need to create
all the directories mentioned in your feeds.clj, as well as
"~/.fetch\_podcast/cache".

You will probably want to run fetch\_podcast with the "-dv" options, to make
sure that your naming functions work as desired.

You can also specify feed names (PenAddict or FLOSS_Weekly in the
above example) to only fetch particular feeds.  Note that the "-i"
option clears the list of fetched files entirely, not just for the
feed specified.

Once everything is configured, it should be sufficient to periodically
run fetch\_podcast to download new episodes from your feeds.
