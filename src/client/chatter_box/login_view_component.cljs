(ns chatter-box.login-view-component
  (:use-macros [cljs.core.match.macros :only [match]])
  (:require [enfocus.core :as ef
             :refer [at content html append prepend after befor do->
                     remove-node add-class read-form this-node from
                     set-attr set-data get-prop get-data set-style
                     remove-attr]]
            [enfocus.effects :as effects :refer [fade-in fade-out]]
            [enfocus.events :as events :refer [listen listen-live]]
            [cljs.core.async :as async :refer  [<! >! chan put!]]
            [chatter-box.protocol :as p]
            [chatter-box.event-bus :as bus :refer [Component]]
            [cljs.core.match])
  (:require-macros [enfocus.macros :as em
                    :refer [defaction deftemplate defsnippet clone-for]]
                   [cljs.core.async.macros :refer [go]]))


(declare setup login nav-create-user)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def valid-types #{{:area :security :key :logout}
                   {:area :security :key :login-success}
                   {:area :nav :key :all}
                   {:area :view :key :init}})


(def out-chan (atom nil))
(def user (atom nil))

(defn create-login-view-component []
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

(defsnippet login-page :compiled
  "resources/public/login.html" [".container"] [])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; action funtions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 
(defn initialize []
  (at "body"          (append (login-page))
      "#login-btn"    (do->
                       (remove-attr :href)
                       (listen :click login)) 
      "#user-nav-btn" (do->
                       (remove-attr :href)
                       (listen :click nav-create-user))))


(defn- navigate [{page :key}]
  (if (= page :login)
    (at "#login-container" (set-style :display ""))
    (at "#login-container" (set-style :display "none"))))



(defn- login []
  (let [form (from "#login-form" (read-form))]
    (put! @out-chan (p/login-message (:username form)
                                     (:password form)))))


(defn- logout []
  (put! @out-chan (p/create-message :nav :login nil))
  (reset! user nil))


(defn- login-successfull [usr]
  (reset! user usr)
  (put! @out-chan (p/create-message :nav :chat nil)))


(defn nav-create-user []
  (put! @out-chan (p/create-message :nav :create-user nil)))

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
              [:security :logout] (logout)
              [:view :init] (initialize)
              [:security :login-success] (login-successfull data)
              [:nav _] (navigate msg)
              :else (log-debug (pr-str "NO MATCH FOR:" msg)))))))
