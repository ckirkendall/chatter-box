(ns chatter-box.user-component
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

(def valid-types #{{:area :security :key :login}
                   {:area :security :key :logout}
                   {:area :user :key :create}
                   {:area :user :key :update}})


(defn create-user-component []
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

(def users  (atom {}))

(def login-users (atom {}))


;;helper functions
(defn brodcast-message
  "this is a helper function to brodcast to
   a group of channels"
  [msg channels]
  (doseq [ch channels]
    (put! ch msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 
;; action functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-user [out-chan msg]
  (let [username (:username (:user msg))
        entry (@users username)]
    (if-not entry
      (do 
        (swap! users #(assoc % (:username (:user msg)) msg))
        (put! out-chan (p/user-created-message (:user msg))))
      (put! out-chan (p/user-error-message (:user msg)
                                           "username already exists")))))

(defn- update-user [out-chan user]
  (let [username (:username user)
        entry (@users username)]
    (if entry
      (do 
        (swap! users #(assoc-in % [(:username user) :user] user))
        (brodcast-message (p/user-updated-message user)
                          (vals @login-users)))
      (put! out-chan (p/user-error-message user
                                           "user does not exist")))))


(defn- login [out-ch {uname :username pass :password}]
  (let [entry (@users uname)
        passwd (:password entry)]
    (if (and passwd (= passwd pass))
      (let [cur-users (map (comp :user @users) (keys @login-users))]
        (swap! login-users #(assoc % uname out-ch))
        (put! out-ch (p/login-success-message (:user entry) cur-users))
        (brodcast-message (p/user-joined-message (:user entry))
                          (vals (dissoc @login-users uname))))
      (put! out-ch (p/login-failure-message uname "Invalid Login")))))


(defn- logout [uname]
  (let [entry (@users uname)]
    (swap! login-users #(dissoc % uname))
    (brodcast-message (p/user-left-message (:user entry))
                      (vals @login-users))))


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
              [{:area :user :key :create}] (create-user out-chan
                                                        (:data msg))
              [{:area :user :key :update}] (update-user out-chan
                                                        (:data msg))
              [{:area :security :key :login}] (login out-chan
                                                     (:data msg))
              [{:area :security :key :logout}] (logout (:data msg)))))))  
