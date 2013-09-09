(ns chatter-box.core
  (:use  [compojure.core :only [defroutes GET]])
  (:require   [clojure.edn :as edn]
              [chatter-box.chat-server :as cs]
              [chatter-box.chat-component :as c]
              [chatter-box.user-component :as u]
              [chatter-box.event-bus :as b]
              [org.httpkit.server :as httpkit]
              [compojure.handler :as handler]
              [compojure.route :as route]
              [ring.util.response :refer [file-response]]
              [clojure.core.async :as a :refer [alts! <! >! chan timeout go put! take!]]))


(defn read-message [raw]
  (binding [*read-eval* false]
    (read-string raw)))
  

(defn async-handler [ring-request]
  (let [c-server (cs/create-chat-server
                  (c/create-chat-component)
                  (u/create-user-component))
        out-ch (chan)
        in-ch (b/get-channel c-server)]
    (b/init c-server out-ch)
    ;; unified API for WebSocket and HTTP long polling/streaming
    (httpkit/with-channel ring-request channel    ; get the channel
      (httpkit/on-receive channel
                          (fn [raw]     ; two way communication
                            (let [data (read-message raw)]
                              (println "RECIEVE:" data)
                              (put! in-ch data))))
      (go
       (while true
         (let [msg (pr-str(<! out-ch))]
           (println "SEND:" msg)
           (httpkit/send! channel msg))))))) ;echo

(defroutes app-routes
  (GET "/" []
       (file-response "index.html" {:root "resources/public"}))
  (GET "/async" [] async-handler) 
  (route/resources "/") ;resources default to resource/public
  (route/not-found "Not Found"))


(def app
  (-> (handler/site #'app-routes)))


(defn -main [& args]
  (httpkit/run-server app {:port 3000}))
