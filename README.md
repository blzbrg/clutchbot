# clutchbot

This is a very simple IRC bot capable of logging and some automated responses. It should run in any
environment with a Java runtime environment, but it is not yet suitable for distribution in compiled
form. I wrote it to teach myself, but publish it in the unusual event someone finds some use for
it. More features are probably coming, but I wouldn't hold my breath.

# Using the web ui
TODO...should be reasonably straightforward.

# Writing configration files

Configuration for the bot overall is read from `global_opts.txt`. By default, configuration for
individual channels is read from `clutch_opts/`, though this can be overridden in
`global_opts.txt`. Inside `clutch_opts/` are individual files named for IRC channels, including the
preceeding hash. If we wanted to configure how the bot behaves in the channel `foo`, we would make a
file `#foo`.

Each line in a configuration file is a separate option. Each line contains sections of text
separated by a | ("pipe") character. If you want to use a | in the text (rather than to separate
sections) you must put a \ ("backslash") right before it.

An example line that is allowed in `global_opts.txt` is `controls-theme |
light-orange`.

## Automated Responses
Clutchbot can send automated responses in a channel when triggered by things that someone says. The
ways to set this up are described below, with examples.

### Trigger a phrase when a word is said.

If the configuration file contains `word-trigger | time | My goodness look at the time`, whenever
someone says "time" the bot will say "My goodness look at the time". The trigger word (the second
section) is case sensitive, for now, but I probably will change this.

### Trigger a phrase when some variable number of words are said
`group-trigger | Daily reminder that Rare Pepes are protected under international Intellectual
Property law. | >= | 2 | rare | pepe | ultra` would cause the bot to say an obnoxious meme whenever
someone says 2 or more of the words "rare", "pepe", or "ultra". The end of the line is a list of
words, separated by bars, that the bot should look for. It counts how many appear, then uses the
math operator in the third section to compare that number to the number in the fourth section. Here,
it checks whether the number of words found is greater than or equal to 2.
### Available operators
* `>`/`<` : number of words in message is greater than/less than...
* `=` : number of words in message is exactly...
* `<=`/`>=` : number of words is less than or equal/greater than or equal (written &le;/&ge in math)...

## Logging

Log files are placed in logs/name_of_channel.log, where the leading hash is included in the channel
name. Only one line starting with `who-to-log` should be in each channel configuration file.

### Flushing logs

To reduce system load, logs are stored in memory until they reach a certain size or the bot is shut
down from the web ui. That size is controlled from `global_opts.txt` with a line like
`log-flush-threshhold | 20`. 20 is the default, which is a conservative value. In channels where
messages come fast, it is probably advisable to use a larger number. If you find the bot often
crashing and losing logs you should tell me about the problem and try using a lower thresshold so
that less logs are lost when the bot crashes.

### Everyone

Write `who-to-log | everyone`

### All except some nicknames

Write `who-to-log | everyone-except | nickname1 | nickname2 | ...` The list of nicknames from whom
messages should not be logged can be long.

### Only some nicknames

Write `who-to-log | only | nickname1 | nickname2 | ...`. Logs only messages from named users.

Written in Clojure. Uses the [IRCLJ](https://github.com/Raynes/irclj) library for IRC. Web control panel uses [Compojure](https://github.com/weavejester/compojure), [Ring](https://github.com/ring-clojure/ring), [httpkit](http://www.http-kit.org/), and [Hiccup](https://github.com/weavejester/hiccup).
