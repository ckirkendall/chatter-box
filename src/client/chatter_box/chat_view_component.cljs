(ns chatter-box.chat-view-component
  (:use-macros [cljs.core.match.macros :only [match]])
  (:require [enfocus.core :as ef
             :refer [at content html append prepend after befor do->
                     remove-node add-class read-form this-node from
                     set-attr set-data get-prop get-data set-style
                     remove-class remove-attr log-debug]]
            [enfocus.effects :as effects :refer [fade-in fade-out]]
            [enfocus.events :as events :refer [listen listen-live]]
            [cljs.core.async :as async :refer  [<! >! chan put!]]
            [chatter-box.protocol :as p]
            [chatter-box.event-bus :as bus :refer [Component]]
            [cljs.core.match])
  (:require-macros [enfocus.macros :as em
                    :refer [deftemplate defsnippet defaction clone-for]]
                   [cljs.core.async.macros :refer [go]]))


(declare setup load-conversation nav-update-user logout send-chat)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def valid-types #{{:area :security :key :logout}
                   {:area :security :key :login-success}
                   {:area :messaging :key :receive-chat}
                   {:area :messaging :key :join}
                   {:area :messaging :key :left}
                   {:area :nav :key :all}
                   {:area :view :key :init}})


(def out-chan (atom nil))
(def user (atom nil))
(def active-conv (atom nil))

(defn create-chat-view-component []
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


(defsnippet chat-window-page :compiled
  "resources/public/chat_window.html" [".container"] []
  "#chat-container" (set-style :display "none")
  "#user-list *"    (remove-node)
  "#chat-list *"    (remove-node)
  "#conv-name"      (content "Select a Users")
  "#chat-form-div"  (set-style :display "none"))


(defsnippet chat-user-element :compiled
  "resources/public/chat_window.html" ["#user-list > *:first-child"] [usr]
  "button"     (do-> (set-attr :id (str "id_" (:username usr)))
                     (set-data :user usr)
                     (listen :click #(load-conversation usr)))
  "img"        (set-attr :src (:img-url usr))
  ".full_name" (content (str (:first usr) " " (:last usr))))


(defsnippet chat-message-element :compiled
  "resources/public/chat_window.html" ["#chat-list > *:first-child"] [usr text]
  "button"   (do->
              (remove-class :remote)
              (remove-class :local)
              (add-class (if (= usr @user)
                           :local
                           :remote)))
  "img"      (set-attr :src (:img-url usr))
  ".message" (content text))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; chat window actions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defaction initialize []
  "body"         (append (chat-window-page))
  "#profile-btn" (do->
                  (remove-attr :href)
                  (listen :click nav-update-user))
  "#logout-btn"  (do->
                  (remove-attr :href)
                  (listen :click logout))
  "#chat-btn"    (listen :click send-chat))


(defn navigate [{page :key}]
  (let [display (if (= page :chat) "" "none")]    
    (at "#chat-container" (set-style :display display))))


(defn load-conversation [r-usr]
  (log-debug (pr-str "LOAD-CONV-USR:" r-usr))
  (let [r-id (str "#id_" (:username r-usr))
        messages (from r-id (get-data :msgs))]
    (log-debug (pr-str "LOAD MESSAGES:" messages))
    (reset! active-conv r-usr)
    (at
     "#conv-name"     (content (:username r-usr))
     "#chat-form-div" (set-style :display "")
     "#chat-list"     (content
                       (for [msg messages]
                         (if (= (:local msg) (:username @user))
                           (chat-message-element @user (:text msg))
                           (chat-message-element r-usr (:text msg)))))
     r-id             (remove-class "btn-success"))))


(defn user-join [r-usr]
  (let [id (str "#id_" (:username r-usr))]
    (at "#user-list" (append (chat-user-element r-usr))
        id           (set-data :msgs [])))) 



(defaction user-left [r-usr]
  (str "id_" (:username r-usr)) (remove-node))


(defn receive-chat [{remote :remote local :local text :text :as msg}]
  (let [rm-but-id (if (= remote (:username @user))
                    (str "#id_" local) 
                    (str "#id_" remote))
        messages (from rm-but-id (get-data :msgs))]
    (at rm-but-id (set-data :msgs (conj messages msg)))
    (if (or (= remote (:username @active-conv))
            (= local (:username @active-conv)))
      (let [usr (if (= local (:username @user)) @user @active-conv)]
        (at "#chat-list" (append (chat-message-element usr text))))
      (at rm-but-id (do-> (remove-class "btn-success")
                          (add-class "btn-success"))))))


(defn send-chat []
  (let [text (from "#chat-msg" (get-prop :value))
        msg (p/send-chat-message (:username @user)
                                 (:username @active-conv)
                                 text)]
    (log-debug (pr-str "SEND_CHAT:" msg))
    (put! @out-chan msg)
    (receive-chat (:data msg))))


(defn login-success [{usr :user e-users :existing-users }]
  (reset! user usr)
  (doseq [r-usr e-users]
    (user-join r-usr)))


(defn logout []
  (reset! user nil)
  (reset! active-conv nil)
  (at "#user-list *" (remove-node)
      "#chat-list *" (remove-node)
      "#conv-name"   (content "Select a Users"))
  (put! @out-chan (p/logout-message (:username @user))))

(defn nav-update-user []
  (put! @out-chan (p/create-message :nav :update-user nil)))
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
              [:messaging :receive-chat] (receive-chat data)
              [:messaging :join] (user-join data)
              [:messaging :left] (user-left data))))))
