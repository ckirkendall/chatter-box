(ns chatter-box.chat-component
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

(def valid-types #{{:area :security :key :logout}
                   {:area :security :key :login-success}
                   {:area :messaging :key :send-chat}})


(defn create-chat-component []
  (let [in-ch (chan)]
    (reify Component
      (init [_ out-ch] (setup out-ch in-ch))
      (accept-message? [_ msg] ((p/create-filter valid-types) msg))
      (get-channel [_] in-ch))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; data stores for users and login-users
;; this could be mongo db stors or sql
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;{username {:chan _ :conversations _}}
(def chat-users  (atom {}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;; action functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn chat [out-ch msg]
  (let [local (:local msg)
        remote (:remote msg) 
        ch (:chan (@chat-users remote))]
    (if ch
      (do
        (swap! chat-users (fn [users-map]
                             (-> users-map
                                 (update-in [local :conversations]
                                            conj remote)
                                 (update-in [remote :conversations]
                                            conj local))))
        (put! ch (p/receive-chat-message local remote (:text msg))))
      ;no remote user in chat list
      (put! out-ch (p/chat-error-message
                     local
                     (str remote " is not logged on"))))))


(defn login [out-ch {user :user}]
  (swap! chat-users
         assoc (:username user) {:chan out-ch :conversations #{}}))

(defn logout [uname]
  (let [convs (:conversations (@chat-users uname))
        func (fn [user-map users]
               (if (empty? users)
                 user-map
                 (recur (update-in user-map
                                   [(first users) :conversations]
                                   disj uname) (rest users))))]
    (swap! chat-users #(-> %
                           (dissoc uname)
                           (func convs)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;; message/action dispatcher
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 
(defn- setup [out-chan in-ch]
  (go
   (while true
     (let [msg (<! in-ch)]
       (match [msg]
              [{:area :messaging :key :send-chat}] (chat out-chan (:data msg))
              [{:area :security :key :login-success}] (login out-chan (:data msg))
              [{:area :security :key :logout}] (logout (:data msg)))))))  
