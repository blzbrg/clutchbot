(ns clutchbot.core
  (:require [compojure.route :only [files-not-found]]
            [compojure.handler :refer [site]]
            [compojure.core :refer [defroutes GET POST DELETE ANY context]]
            [ring.middleware.defaults :as ringdef]
            [ring.middleware.resource]
            [hiccup.core :refer :all]
            [hiccup.page :refer :all]
            [hiccup.form :as form]
            [hiccup.element :as element]
            [org.httpkit.server]
            [clutchbot.irc]
            [clutchbot.parser :as parser]
            [clutchbot.logger :as logger])
  (:gen-class))

(def opts^{:doc "Options that affect the entire bot operation"}
  (atom {:are-default true
         :log-flush-threshhold 20
         :port 1337
         :localhost-only true
         :channel-opts-directory "clutch_opts"
         :controls-theme "light-pink"}))

;; (def channel-opts^{:doc "Map from channel name to options map for that channel"}
;;   (atom {}))

(defn replace-opts-from-file
  "Replace global opts from file path."
  [path-of-file]
  (if (.canRead (clojure.java.io/file path-of-file))
    (do
      (println "Reading global opts from global_opts.txt")
      (swap! opts conj (parser/file->global-opts path-of-file)))
    (println "Couldn't fine global_opts.txt, using defaults.")))

(defn replace-channel-opts-from-directory
  "Replace opts for all channels with the opts directory identified by path. Options for channel #x are in file named #x"
  [path-of-dir]
  (->> path-of-dir
       (parser/server-dir->channel-opts)
       (swap! clutchbot.irc/channel-opts conj)))

(defn channel-names-loaded
  "Returns seq of names of channels that have opts loaded"
  []
  (keys (deref clutchbot.irc/channel-opts)))

(def done-running-promise^{:doc "Fulfilled with true when it is time to shut down"}
  (promise))

(def connection-state^{:doc ":connected or :not-connected"}
  (atom :not-connected))

(defn finish-running [] (deliver done-running-promise true))

(defn done-running? []
  (and (realized? done-running-promise) @done-running-promise))

(defn shutdown-bot
  "Immeditately flush logs and shut down bot."
  []
  (do (finish-running)
      (clutchbot.irc/gracefully-leave)
      (logger/flush-all!)
      (shutdown-agents)
      (System/exit 0)))

(defn hiccup-cols
  "(hiccup-cols left right left-widdth right-width) Makes hiccup column structure. If width arguments omitted uses defaults."
  ([left right] (hiccup-cols left right 250 250))
  ([left right lwidth rwidth]
   [:div {:style "display: flex; flex-direction: row; justify-content: space-between;"}
    [:div {:style (str "width:" lwidth "px;")} left]
    [:div {:style (str "width:" rwidth "px;")} right]]))

(defn form [symb]
  (case symb
    :launch (form/form-to [:post "/launch"]
                          [:div
                           (form/label "bot_name" "Nickname")
                           (form/text-field {:value "Clutchbot"}  "bot_name")]
                          [:div
                           (form/label "address" "Server address")
                           (form/text-field "address")]
                          [:div
                           (form/label "port" "Server port")
                           (form/text-field "port")]
                          (form/submit-button "Connect!"))
    :join [:div#join_form
           [:h2 "Join single channel"]
           (form/form-to [:post "/single-channel-join"]
                         [:div
                          (form/label "channel_name" "Channel name")
                          (form/text-field "channel-name")]
                         (form/label "conf" "Configuration (what would go in the configuration file for this channel)")
                         [:div (form/text-area "conf")]
                         (form/submit-button "Add channel"))
           [:h2 "Join multiple channels"]
           (form/form-to [:post "/multi-channel-join"]
                         [:div
                          "Channels with conf loaded from files:"
                          (element/unordered-list
                           (map (fn [channel-name]
                                  [:div
                                   (form/check-box {:id channel-name} "channels-to-join[]" false channel-name)
                                   (form/label channel-name channel-name)])
                                (channel-names-loaded)))]
                         (form/submit-button "Join checked channels"))]
    :channel-list [:div
                   [:h2 "Channels bot is in"]
                   (let [in (clutchbot.irc/channels-in)]
                     (if (empty? in)
                       [:div "Bot is not in any channels"]
                       (form/form-to [:post "/multi-channel-part"]
                                     [:div
                                      (element/unordered-list
                                       (map (fn [channel-name]
                                              [:span
                                               (form/check-box "channels-to-part[]" false channel-name)
                                               (form/label channel-name channel-name)])
                                            (clutchbot.irc/channels-in)))
                                      (form/submit-button "Leave checked channels")])))]
    :run-control [:div#run_control
                  [:h2 "Overall bot control"]
                  [:div
                   "Stops this control website and disconnects from irc server."
                   (form/form-to [:post "/run-control"]
                                      (form/hidden-field "action" "shutdown")
                                      (form/submit-button "Shutdown bot"))]
                  [:div
                   "Reload all channel configuration files. Immediately applies to channels bot is currently in, applies to any newly joined channels. Note, if you typed in config here to join channel #x, then made a config file for #x, this will lose what you typed in favor of what is in the file."
                   (form/form-to [:post "/run-control"]
                                 (form/hidden-field "action" "reload-confs")
                                 (form/submit-button "Reload channel confs"))]
                  ]))

(defn mk-head
  "Make hiccup structure for head element"
  [title]
  [:head [:title title] (include-css "controls.css" (str "theme-" (@opts :controls-theme) ".css"))])

(defroutes clutch_routes
  (GET "/" [mock-connected]
       (if (or (= :connected @connection-state) (= mock-connected "yes")) 
         (html5 (mk-head "Bot running")
                [:body
                 [:div#content
                 (form :join)
                 (form :channel-list)
                 (form :run-control)]])
         (html5 (mk-head "Bot ready to connect to server")
                [:body
                 [:div#content
                 (form :launch)
                  (form :run-control)]])))
  (POST "/launch" [bot_name channel_name address port]
        (do
          (reset! connection-state :connected)
          (clutchbot.irc/enter-server address (Integer/parseInt port) bot_name)
          (ring.util.response/redirect-after-post "/")))
  (POST "/run-control" [action]
        (case action
          "shutdown" (do (shutdown-bot)
                         "Shutting down.")
          "reload-confs" (replace-channel-opts-from-directory (@opts :channel-opts-directory))))
  (POST "/multi-channel-join" [channels-to-join] ;; channels-to-join: collection of checkbox values
        (do
          (doseq [ch-nm channels-to-join]
            (clutchbot.irc/enter-channel ch-nm (logger/mk-channel-logger ch-nm (@opts :log-flush-threshhold))))
          (ring.util.response/redirect-after-post "/")))

  (POST "/single-channel-join" [channel-name conf]
        (let [temp-opts (clutchbot.parser/string->channel-opts conf)]
          (swap! clutchbot.irc/channel-opts assoc channel-name temp-opts)
          (clutchbot.irc/enter-channel channel-name (logger/mk-channel-logger channel-name (@opts :log-flush-threshhold)))
          (ring.util.response/redirect-after-post "/")))
  (POST "/multi-channel-part" [channels-to-part]
        (do
          (doseq [ch-nm channels-to-part]
                (clutchbot.irc/part-channel ch-nm))
          (ring.util.response/redirect-after-post "/")))
  ) ;end routes

(def wrapped-handler
  (let [my-ring-opts (-> ringdef/site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc :session false :cookies false))]
    (-> (ringdef/wrap-defaults clutch_routes my-ring-opts)
        (ring.middleware.resource/wrap-resource ""))))
   
(defn start_webserver []
  (let [port-snapshot (@opts :port)
        ;;security (assoc (ringdef/site-defaults :security) :anti-forgery false)
        ;;ring-settings (assoc ringdef/site-defaults :session false :cookies false :security security)
        ;;wrapped-handler (ringdef/wrap-defaults clutch_routes ring-settings)
        ]
    (println "Starting server on port " port-snapshot)
    (org.httpkit.server/run-server wrapped-handler (if (@opts :localhost-only)
                                                     {:port port-snapshot :ip "127.0.0.1"}
                                                     {:port port-snapshot}
                                                     ))))

(defn init-opts
  []
  (do
    (replace-opts-from-file "global_opts.txt")
    (println "loading channel opts from " (@opts :channel-opts-directory))
    (replace-channel-opts-from-directory (@opts :channel-opts-directory))
    (apply println "loaded channel opts for: " (channel-names-loaded))))

(defn -main []
  (do
    (init-opts)
    (start_webserver)))
