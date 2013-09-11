(ns chatter-box.user-view-component
  (:use-macros [cljs.core.match.macros :only [match]])
  (:require [enfocus.core :as ef
             :refer [at content html append prepend after befor do->
                     remove-node add-class read-form this-node from
                     set-attr set-data get-prop get-data set-style
                     remove-attr set-prop log-debug]]
            [enfocus.effects :as effects :refer [fade-in fade-out]]
            [enfocus.events :as events :refer [listen listen-live]]
            [cljs.core.async :as async :refer  [<! >! chan put!]]
            [chatter-box.protocol :as p]
            [chatter-box.event-bus :as bus :refer [Component]]
            [cljs.core.match])
  (:require-macros [enfocus.macros :as em
                    :refer [defaction deftemplate defsnippet clone-for]]
                   [cljs.core.async.macros :refer [go]]))


(declare setup create-user update-user)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def valid-types #{{:area :security :key :logout}
                   {:area :security :key :login-success}
                   {:area :user :key :created}
                   {:area :user :key :updated}
                   {:area :nav :key :all}
                   {:area :view :key :init}})


(def out-chan (atom nil))
(def user (atom nil))

(defn create-user-view-component []
  (let [in-ch (chan)]
    (reify Component
      (init [_ out-ch]
        (reset! out-chan out-ch)
        (setup in-ch))
      (accept-message? [_ msg] ((p/create-filter valid-types) msg))
      (get-channel [_] in-ch))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; snippets and templates
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defsnippet update-user-page :compiled
  "resources/public/update_user.html" [".container"] []
   "#update-user-container" (set-style :display "none"))

(defsnippet create-user-page :compiled
  "resources/public/create_user.html" [".container"] []
   "#create-user-container" (set-style :display "none"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; action functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defaction initialize []
  "body" (append (create-user-page)
                 (update-user-page))
  "#create-user-btn" (do->
                      (remove-attr :href)
                      (listen :click create-user))
  "#update-user-btn" (do->
                      (remove-attr :href)
                      (listen :click update-user))) 


(defn navigate [{page :key}]
  (let [[create-dis update-dis] (cond
                                (= page :create-user) ["" "none"]
                                (= page :update-user) ["none" ""]
                                :else ["none" "none"])]    
    (at "#create-user-container" (set-style :display create-dis)
        "#update-user-container" (set-style :display update-dis))
    (when (empty? update-dis)
      (at "#up-first"   (set-prop :value (:first @user))
          "#up-last"    (set-prop :value (:last @user))
          "#up-img-url" (set-prop :value (:img-url @user))))))


(defn user-error [msg]
  (put! @out-chan (p/user-error-message nil msg)))


(defn- validate-create-user-form [form]
  (cond
   (not= (:password form) (:password2 form)) "passwords need to match"
   (empty? (:username form)) "username required"
   (empty? (:password form)) "password required"
   (empty? (:first form)) "first name required"
   :else :valid))


(defn- validate-update-user-form [form]
  (cond
   (empty? (:first form)) "first name required")
   :else :valid)


(defn- create-user []
  (let [form (from "#create-user-form" (read-form))
        val (validate-create-user-form form)]
    (if (= val :valid)
      (put! @out-chan
            (p/create-user-message
             (:username form)
             (:password form)
             (:first form)
             (:last form)
             (:img-url form)))
      (user-error val))))


(defn- update-user []
  (let [form (from "#update-user-form" (read-form))
        val (validate-update-user-form form)]
    (if (= val :valid)
      (put! @out-chan
            (p/update-user-message (assoc @user
                                     :first (:first form)
                                     :last (:last form)
                                     :img-url (:img-url form))))
      (user-error val))))


(defn- user-created []
  (put! @out-chan  (p/create-message :nav :login "user created")))

(defn- user-updated [usr]
  (when (= (:username usr)
         (:username @user))
    (reset! user usr)
    (put! @out-chan (p/create-message :nav :chat "user updated"))))

(defn- login-success [msg]
  (reset! user (:user msg)))

(defn- logout []
  (reset! user nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Message dispatcher
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- setup [in-ch]
  (go
   (while true
     (let [{area :area ky :key data :data :as msg} (<! in-ch)]
       (match [area ky]
              [:nav _] (navigate msg)
              [:view :init] (initialize)
              [:security :logout] (logout)
              [:security :login-success] (login-success data)
              [:user :created] (user-created)
              [:user :updated] (user-updated data))))))

