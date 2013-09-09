(ns chatter-box.event-bus
  (:require
   #+clj  [clojure.core.async :as async :refer [<! >! chan go]]
   #+cljs [cljs.core.async :as async :refer  [<! >! chan]])
  #+cljs
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defprotocol Component
  (init [this out-channel])
  (accept-message? [this message])
  (get-channel [this]))


(defn create-bus [& components]
  (let [ch (chan)]
    (doseq [co components] (init co ch))
    (go
     (loop [msg (<! ch)]
       (when msg
         (doseq [co components]
           (when (accept-message? co msg)
             (>! (get-channel co) msg)))
         (recur (<! ch)))))
    ch))






