(defproject clutchbot "0.1.0-SNAPSHOT"
  :description "Primitive IRC bot with logging and basic automated reply capabilites."
  :url "https://github.com/blzbrg/clutchbot"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"] ; EPL
                 [irclj "0.5.0-alpha4"] ; EPL
                 [compojure "1.3.1"] ; EPL
                 [ring/ring-defaults "0.1.2"] ; mit license
                 [http-kit "2.1.16"] ; Apache license (Clutchbot is not a derivative work)
                 [hiccup "1.0.5"]] ; EPL
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler clutchbot.core/wrapped-handler
         :init clutchbot.core/init-opts
         :destroy clutchbot.core/shutdown-bot}
  :main clutchbot.core
  :aot [clutchbot.core]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]]}})
