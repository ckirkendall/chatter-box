(ns chatter-box.channel-close-component
  (:require [chatter-box.event-bus :as bus :refer [Component]]
            [chatter-box.protocol :as p]
            [clojure.core.async :as async :refer  [<! >! chan go put!]]
            [clojure.core.match :refer [match]]))

(declare setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def valid-types #{{:area :system :key :close}
                   {:area :security :key :login-success}})

(def user )

(defn create-channel-close-component []
  (let [in-ch (chan)]
    (reify Component
      (init [_ out-ch] (setup out-ch in-ch))
      (accept-message? [_ msg] ((p/create-filter valid-types) msg))
      (get-channel [_] in-ch))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;; action functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- close-channel [out-ch user]
  (when user
    (put! out-ch (p/logout-message (:username user)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;; message/action dispatcher
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 
(defn- setup [out-chan in-ch]
  (let [user (atom nil)]
    (go
     (while true
       (let [msg (<! in-ch)]
         (match [msg]
                [{:area :system :key :close}] (close-channel out-chan @user)
                [{:area :security :key :login-success}] (reset! user (:user (:data msg)))))))))  
