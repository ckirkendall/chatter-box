(ns chatter-box.protocol)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; base data items
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-user [username first last img-url]
  {:username username
   :first first
   :last last
   :img-url img-url})

(defn create-message [area key data]
  {:area area :key key :data data})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; security messages
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn login-message [username password]
  (create-message :security
                  :login
                  {:username username :password password}))

(defn login-failure-message [username error]
  (create-message :security
                  :login-failure
                  {:username username :err error}))

(defn login-success-message [user existing-users]
  (create-message :security :login-success
                  {:user user
                   :existing-users existing-users}))

(defn logout-message [username]
   (create-message :security :logout username))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; user management messages
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-user-message [username password first last img-url]
  (create-message :user :create
                  {:user  (build-user username first last img-url)
                   :password password}))

(defn user-created-message [user]
  (create-message :user :created user))

(defn update-user-message [user]
  (create-message :user :update user))

(defn user-updated-message [user]
  (create-message :user :updated user))


(defn user-error-message [user error]
  (create-message :user :error {:user user :err error}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;chat management messages
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn user-joined-message [user]
  (create-message :messaging :join user))

(defn user-left-message [user]
  (create-message :messaging :left user))


(defn send-chat-message [local-uname  remote-uname msg]
  (create-message :messaging :send-chat {:local local-uname
                                         :remote remote-uname
                                         :text msg}))

(defn receive-chat-message [local-uname remote-uname msg]
  (create-message :messaging :receive-chat {:local local-uname
                                            :remote remote-uname
                                            :text msg}))

(defn chat-error-message [username error]
  (create-message :messaging :error {:username username
                                     :err error}))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; helper functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-filter
  "takes a set of {:area _ :key _} maps
   and returns a predicate that checks
   a message agains its area and key
   existing in the set"
  [msg-set]
  (fn [msg]
    (let [area (:area msg)
          ky (:key msg)]
      (or (msg-set {:area area :key ky})
          (msg-set {:area area :key :all})))))


