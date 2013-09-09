(ns chatter-box.error-view-component
  (:use-macros [cljs.core.match.macros :only [match]])
  (:require [enfocus.core :as ef
             :refer [at content html append prepend after befor do->
                     remove-node add-class read-form this-node from
                     set-attr set-data get-prop get-data set-style
                     remove-attr log-debug]]
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

(def valid-types #{{:area :view :key :init}
                   {:area :security :key :login-failure}
                   {:area :user :key :error}
                   {:area :messaging :key :error}})


(def out-chan (atom nil))
 
(defn create-error-view-component []
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

(defsnippet error-div :compiled
  "resources/public/error.html" ["#error-div"] []
  "#error-div" (set-style :display "none"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; action funtions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 
(defn initialize []
  (at "body"          (append (error-div))
      "#error-btn"    (do->
                       (listen :click
                               #(at "#error-div"
                                    (set-style :display "none"))))))


(defn show-error [msg]
  (at "#error-txt" (content msg)
      "#error-div" (set-style :display "")))


(defn setup [in-ch]
  (go
   (while true
     (let [msg (<! in-ch)]
       (if (and (= (:area msg) :view)
                (= (:key msg) :init))
         (initialize)
         (do (log-debug (pr-str "ERROR:" msg))
             (show-error (:err (:data msg)))))))))

