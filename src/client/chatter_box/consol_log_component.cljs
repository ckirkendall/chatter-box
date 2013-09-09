(ns chatter-box.consol-log-component
  (:use-macros [cljs.core.match.macros :only [match]])
  (:require [enfocus.core :as ef :refer [log-debug]]
            [cljs.core.async :as async :refer  [<! >! chan put!]]
            [chatter-box.protocol :as p]
            [chatter-box.event-bus :as bus :refer [Component]])
  (:require-macros [cljs.core.async.macros :refer [go]]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-consol-log-component []
  (let [in-ch (chan)]
    (reify Component
      (init [_ out-ch]
        (go
         (while true
           (let [msg (<! in-ch)]
             (log-debug (pr-str msg))))))
      (accept-message? [_ msg] true)
      (get-channel [_] in-ch))))
