# fetch\_podcast

fetch\_podcast is a command-line tool for fetching podcasts.  Unlike many
similar tools, fetch\_podcast allows the user to speicfy names for the
downloaded files.  It is written in [clojure](http://clojure.org/) and uses the
[leiningen](http://leiningen.org/) build system, but has no other dependencies.

## Installation

    $ git clone https://github.com/crhalpin/fetch_podcast.git
    $ cd fetch_podcast
    $ lein uberjar
    $ cp target/uberjar/fetch_podcast-0.1.0-standalone.jar ${SOMEWHERE}

## Usage

    $ java -jar ${SOMEWHERE}/fetch_podcast-0.1.0-standalone.jar [args]

When run with no arguments, fetch\_podcast will download any new episodes from
all configured feeds.

## Options

* -v : verbose mode

* -c : catch up on podcasts, without downloading them (i.e., mark all read)

* -i : re-initialize list of see podcasts

## Getting started

see doc/intro.md

## License

Copyright (c) Corey Halpin

Distributed under the 2-Clause BSD license (see LICENSE).
