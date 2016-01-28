# fetch\_podcast

fetch\_podcast is a command-line tool for fetching podcasts.  Unlike many
similar tools, fetch\_podcast allows the user to speicfy names for the
downloaded files.  It is written in [clojure](http://clojure.org/) and uses the
[leiningen](http://leiningen.org/) build system.

## Installation

    $ git clone https://github.com/crhalpin/fetch_podcast.git
    $ cd fetch_podcast
    $ lein uberjar
    $ cp target/uberjar/fetch_podcast-0.5.5-standalone.jar ${SOMEWHERE}

## Usage

    $ java -jar ${SOMEWHERE}/fetch_podcast-0.5.5-standalone.jar Opts FeedsOrEpisodes

When run with no arguments, fetch\_podcast will download any new episodes from
all configured feeds.

## Options

* -v : Verbose mode.  Specify twice for more detail.

* -c : Catch up on podcasts, without downloading them (i.e., mark all read).

* -d : Dry-run, not downloading anything nor marking it read.

* -e : Download specified episodes (rather than specified feeds).

* -C : Use cached XML feeds, rather than downloading new copies.

* -i : Reset list of podcasts already downloaded.

* -F : Force refresh of cached feed XML files.

## Getting started

see doc/intro.md

## Updating

Versions prior to 0.5.5 incorrectly used the 'enclosure' rather than the 'guid'
element to determine if an episode had already been downloaded.  As a result,
changes to the download locations could cause unwanted repeated downloads.
This has been remedied, but users upgrading from a newer version will want to
'rm ~/.fetch\_podcast/fetchlog.clj' and then run fetch\_podcast -c.

## License

Copyright (c) Corey Halpin

Distributed under the 2-Clause BSD license (see LICENSE).
