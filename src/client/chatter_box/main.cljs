(ns chatter-box.main
  (:require [enfocus.core :as ef :refer [log-debug]]
            [cljs.core.async :as async :refer  [<! >! chan put!]]
            [chatter-box.chat-client :as cc]
            [chatter-box.user-view-component :as uv]
            [chatter-box.chat-view-component :as cv]
            [chatter-box.login-view-component :as lv]
            [cljs.reader :as reader :refer [read-string]]
            [chatter-box.event-bus :as b]
            [chatter-box.protocol :as p]
            [chatter-box.error-view-component :as e]
            [chatter-box.consol-log-component :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(def testing false)
(def ws-url (let [hostname (.-hostname (.-location js/window))]
              (str "ws://" hostname "/async"))) 
 
(defn init []
  (let [c-client (cc/create-chat-client
                  (uv/create-user-view-component)
                  (cv/create-chat-view-component)
                  (lv/create-login-view-component)
                  (log/create-consol-log-component)
                  (e/create-error-view-component)) 
        out-ch (chan)
        in-ch (b/get-channel c-client)
        ws (atom (new js/WebSocket ws-url))]
    (b/init c-client out-ch)
    (set! (.-onmessage @ws)
          (fn [msg]
            (log-debug (pr-str "RECEIVE:" (.-data msg)))
            (put! in-ch (read-string (.-data msg)))))
    (set! (.-onclose @ws)
          (fn [msg] (.reload js/location)))
    (go
     (while true
       (let [msg (<! out-ch)]
         (log-debug (pr-str "SEND:" msg))
         (.send @ws (pr-str msg)))))
    (put! in-ch (p/create-message :view :init nil))))


(set! (.-onload js/window) init)




