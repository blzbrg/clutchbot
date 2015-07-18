(ns clutchbot.logger
  (:import (java.time.format DateTimeFormatter FormatStyle)
           (java.time LocalDateTime))
  (:gen-class))

(def loggers (atom #{}))

(def logging-initilized (delay
                         (let [f (clojure.java.io/file "./logs")]
                           (if (.isDirectory f)
                             true
                             (.mkdir f)))))

(defn flush! [cache channel-name]
  (let [separator (System/getProperty "line.separator")
        unlines #(str (clojure.string/join separator %1) separator)
        snapshot (reverse @cache)] ; reverse to put most recent thigns at end of sequence
    (spit (str "./logs/" channel-name ".log") (unlines snapshot) :append true) ; write snapshot to disk
    (println "flushing log of size " (count snapshot))
;;    (swap! cache #(do (println (- (count %1) (count snapshot))) (take (- (count %1) (count snapshot)) %1))))) ; remove snapshot, preserve anything new
    (swap! cache #(take (- (count %1) (count snapshot)) %1)))) ; remove snapshot, preserve anything new

(defn mk-channel-logger
  "Returns a lambda that will, when called, make entries to the log for this channel."
  [channel-name flush-threshhold]
  (if @logging-initilized
    (let [formatter (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM)
          this-log-cache (atom (list (str "Logging began: " (.format (LocalDateTime/now) formatter))))
          this-logger (fn logger
                        ([nick text]
                         (logger nick text nil))
                        ([nick text note]
                         (let [new-log-line (str nick " : " text (if (nil? note) "" (str "  NOTE: " note)))]
                           (swap! this-log-cache #(cons new-log-line %1))
                                        ; if log over size, flush it asynchronously
                           (when (> (count @this-log-cache) flush-threshhold)
                             (println @this-log-cache)
                             (future (flush! this-log-cache channel-name))
                             ))))]
      ;; side effect: store cache for flushing on shutdown
      (swap! loggers #(conj %1 [channel-name this-logger]))
      ;; return the logger
      this-logger)
    nil)) ;; TODO: error handling

(defn flush-all! []
  (map (fn [[channel-name cache]] (flush! cache channel-name)) @loggers))

