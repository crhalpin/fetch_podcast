# Introduction to fetch\_podcast

## Creating feeds.clj

fetch\_podcast reads a list of feeds from \~/.fetch\_podcast/feeds.clj.
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
          (-> (item :enclosure)
              (clojure.string/split #"/")
              (reverse)
              (second)
              (Integer/parseInt))
          (-> (item :title)
              (clojure.string/split #":")
              (second)
              (clojure.string/trim)
              (clojure.string/replace #"[^\w ]+" "")
              (clojure.string/replace #" +" "_")))) }
    ]

The above defines two feeds.  The first, "FLOSS\_Weekly", will be
downloaded to "\~/Podcasts/FLOSS\_Weekly", pulled from
"http://leo.am/podcasts/floss", and which uses a name function that
just takes the last element of the enclosure from the podcast feed.
The second, "PenAddict", will be downloaded to "\~/Podcasts/PenAddict",
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

You will probably want to run fetch\_podcast with the "-dv" options, to make
sure that your naming functions work as desired.  In verbose mode,
fetch\_podcast outputs three lines for each episodes.  The first is a unique
identifier, the second is the URL of the enclosure, and the third is the
filename determined from your naming function.

You can get a list of episodes for particular feeds by naming them on the
command line (e.g., "-dv PenAddict").  Individual episodes can be downloaded by
using the "-e" option and providing their unique identifier.

Adding a new feed typically works as follows:

  - (edit feeds.clj)

  - `fetch\_podcast -dv NewFeed` to be sure your naming is working as desired

  - `fetch\_podcast -Cv -n3 NewFeed` to download the 3 most recent episodes.

  - `fetch\_podcast -Ccv NewFeed` to mark all older episodes read.

Once everything is configured, it should be sufficient to periodically
run fetch\_podcast (e.g. from `cron`) to download new episodes from your feeds.

