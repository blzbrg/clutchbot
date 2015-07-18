(ns clutchbot.irc
  (:require
   [irclj.core :as ircj])
  (:gen-class))

(defn debug_msg
  "Print incoming message for debugging, not suitable for consumption by non-technical users."
  [args]
  (do
    (print "debug message: ")
    (prn (select-keys args [:text :target :command :nick]))))

(def connection (atom nil))

(def my-name (atom "Clutchbot"))

(def channel-opts (atom {}))

(def joined-channels (atom #{}))

(defn channels-in
  "Returns collection of string names of channels bot is currently in."
  []
  @joined-channels)

;; (defn modify-channel-opts
;;   "(channel-name opts) Assign opts to given channel, any previous opts for that channel are lost. (opts) Replace opts for all channels with given name to opts mapping."
;;   ([channel-name opts] (swap! channel-opts #(assoc % channel-name opts)))
;;   ([new-opts] (swap! channel-opts #(new-opts))))

(defn channel-list [_]
  (keys @channel-opts))

;; The functions that take [msg] as a final argument are meant to be
;; partially applied when reading configuration, then the msg argument
;; is to be supplied when processing a message.

(defn is-bangcmd?
  "Whether msg represents a command starting with an exclamation point, such as !vote. These are informal, but traditionally bots respond to them."
  [msg]
  (= (.indexOf (msg :text) "!") 0))


(defn action-if [pred action msg]
  "Use action to respond to msg only if (pred msg) returns true."
  (if (pred msg)
    (action msg)
    {}))

(defn contains-word?
  "(contains-word? word msg) Predicate, returns true of msg contains word."
  [word msg]
  (contains? (msg :split-words) word))

(defn unconditional-reply
  "(unconditional reply phrase msg) Emit reply, regardless of msg input. Takes msg for consistency with other methods. Common usage: (action-if (partial contains-word? \"derp\") (partial unconditional-reply \"you said derp\") msg)."
  [phrase _]
  {:final-reply phrase})

(defn word-trigger
  "(word-trigger trigger response msg) If msg contains trigger, respond with response. Supply first two arguments to get a function that takes a message and generates reply if triggered."
  [trigger-word response-phrase msg]
  (action-if (partial contains-word? trigger-word)
             (partial unconditional-reply)
             msg))

(defn group-trigger
  "(group-trigger op num response trigger-words msg) trigger-words should be reducable. Count number of trigger-words members that appear in msg. If (op count num), responsd with response. Example: (partial group-trigger > 3 \"three activities done\" [\"eat\" \"sleep\" \"breath\" \"excrete\"])"
  [op num response-phrase trigger-words msg]
  (let [found (reduce (fn [so-far word] (if (contains-word? word msg)
                                          (inc so-far)
                                          so-far))
                      0
                      trigger-words)]
    (if (op found num)
      (unconditional-reply response-phrase nil)
      {})))

(defn log-unconditionally
  "(log-unconditionally msg) or (log-unconditionally note msg) Log this message, or log message along with string note. Ignores the message contents."
  ([_] {:log-this true})
  ([note _] {:log-this true :log-note note}))

(defn log-everyone-except
  "(log-everyone-except exclude-nicks msg) Log all messages except those from nicks in set exclude-nicks, these are ignored."
  [exclude-nicks msg]
  (action-if #(not (contains? exclude-nicks (%1 :nick)))
             (partial log-unconditionally)))

(defn log-only
  "(log-only include-nicks msg) Log messages from any nick in set include-nicks, ignore others."
  [include-nicks msg]
  (action-if #(contains? include-nicks (%1 :nick))
             (partial log-unconditionally)))

(defn handle-channel-message
  "(handle-channel-message irc channel nick text) Given irclj irc object, nick string of user who sent message, and string text of the message, return string to send if reply desired, false if no reply desired."
  [_ channel nick text]
  (let [opts (@channel-opts channel)
        init-acc {:nick nick :text text :split-words (into #{} (clojure.string/split text #" "))}
        f (fn [acc p]
            (let [result (conj acc (p acc))]
              (if (result :short-circuit)
                (reduced result)
                result)))
        res (reduce f init-acc (opts :predicates))] ;reduce over predicates, each one transforms init-acc
    (println res)
    ;; log, if predicates determined needed
    (when (res :log-this)
      (if (contains? res :log-note)
        ((opts :logger) nick text (res :log-note))
        ((opts :logger) nick text)))
    ;; generate reply, if predicates produced one
    (if (contains? res :final-reply)
      (res :final-reply)
      false)
    ))


(defn handle-pm [irc nick text]
  ; TODO 
  )

(defn handler
  "Passed as handler to irclj, dispatches to handle-channel-message."
  [irc {text :text nick :nick :as args}]
  (let [response (if (= (:target args) @my-name)
                   (handle-pm irc (:nick args) text)
                   (handle-channel-message irc (:target args) (:nick args) text))]
    ;; (debug_msg args)
    (when response
      (ircj/reply irc args response))))

;; :text what is sent
;; :target (string) name of user or channel
;; :command (string) is "PRIVMSG" for messages to channel too
;; :nick (string) user who sent it


(defn enter-server [address port username]
  (let [new_conn (ircj/connect address port username :callbacks {:privmsg handler} :real-name "Clutchbot")] ;TODO password shit
    (reset! my-name username)
    (println "joined server " address ":" port " with name " username)
    (reset! connection new_conn)))

(defn enter-channel
  "Enter named channel. Accepts opts for this channel."
  [channel-name logger]
  (do ; [opts-plus (assoc channel-opts :logger logger)]
    (swap! channel-opts assoc-in [channel-name :logger] logger)
    (swap! joined-channels conj channel-name)
    (ircj/join @connection channel-name)
    (println "joined channel " channel-name)))
  
(defn part-channel
  "(part-channel channel-name) Gracefully leave channel. (part-channel channel-name parting-message) Gracefully leave with message."
  ([channel-name] (do
                    (ircj/part @connection channel-name)
                    (swap! joined-channels disj channel-name)))
  ([channel-name parting-message] (do
                                    (ircj/part @connection channel-name :message parting-message)
                                    (swap! joined-channels disj channel-name))))

(defn gracefully-leave []
  (reset! connection (ircj/quit @connection)))
