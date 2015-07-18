(ns clutchbot.parser
  (:require [clojure.string :as str]
            [clutchbot.irc :refer [word-trigger group-trigger log-everyone-except log-only log-unconditionally]])
  (:gen-class))

(defn str->op-fn
  "Convert string representation of comparison operator to a function."
  [text-op]
  (case text-op
    ">" >
    "<" <
    "<=" <=
    ">=" >=
    "=" =))

(defn get-action-maker [str-fn-select]
  (case str-fn-select
    "group-trigger" (fn [response-phrase op num & trigger-strings]
                      (partial group-trigger (str->op-fn op) (Integer/parseInt num) response-phrase trigger-strings))
    "word-trigger" (fn [trigger response] (partial word-trigger trigger response))
    ;; "trigger-phrase" (fn [trigger response] (partial trigger-phrase trigger response))
    "who-to-log" (fn [mode & nicks] (let [nick-set (into #{} nicks)]
                                      (case mode
                                        "everyone" (partial log-unconditionally)
                                        "everyone-except" (partial log-everyone-except nick-set)
                                        "only" (partial log-only nick-set))))
    ))

(defn line->tokens
  "split line on '|' except where preceeded by backslash, strips whitespace from ends of fields."
  [line]
  (map str/trim (str/split line #"[^\\]\|")))

(defn line->action 
  "Given a single config line, returns single corresponding action."
  [line]
  (let [row-tokens (line->tokens line)
        my-action-maker (get-action-maker (first row-tokens))]
    (apply my-action-maker (rest row-tokens))))

(defn string->actions
  "Convert multi-line string into ordered collection of actions."
  [input]
  (map line->action (str/split-lines input)))

(defn string->channel-opts
  "Takes string multi-line string and returns channel-opts hash."
  [inp]
  {:predicates (string->actions inp)})

(defn file-path->channel-opts
  "Takes file path and returns channel-opts hash."
  [path]
  (string->channel-opts (slurp path)))
  
(defn server-dir->channel-opts
  "Takes dir of channel configuration files and returns map of channel names to channel-opts maps."
  [dirpath]
  (->> dirpath
       (clojure.java.io/file)
       ;; makes a sequence of files in the directory
       (file-seq)
       ;; only include actual files
       (filter (fn [member-file] (.isFile member-file)))
       ;; parse each file, making channel-opts
       (map (fn [member-file] ; makes each member file into [filename opts]
              (let [fname (.getName member-file)
                    fpath (.getPath member-file)]
                [fname (file-path->channel-opts fpath)])))
       ;; merge parsed files together into global opts
       (into {}))) ; will make map from filename to opts


(defn file->global-opts
  "Create global opts map of option symbols to values from file path. Valid option symbols are: :log-flush-thresshold (integer), :port (integer), :localhost-only (boolean)."
  [fpath]
  (let [valid-opts #{:log-flush-threshhold :port :localhost-only :controls-theme :channel-opts-directory}]
    (->> (slurp fpath)
         (clojure.string/split-lines)
         (map line->tokens)
         (filter #(== (count %1) 2))
         (map (fn [[key val]] [(keyword key) val]))
         (filter (fn [[key _]] (contains? valid-opts key)))
         (into {}))))
