(ns chatter-box.chat-client
  (:require [enfocus.core :as ef :refer [log-debug]]
            [chatter-box.event-bus :as bus :refer [Component]]
            [chatter-box.protocol :as p]
            [cljs.core.async :as async :refer  [<! >! chan go put!]]
            [cljs.core.match :refer [match]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def external-types #{{:area :security :key :login-failure}
                      {:area :security :key :login-success}
                      {:area :user :key :created}
                      {:area :user :key :updated}
                      {:area :user :key :error}
                      {:area :messaging :key :join}
                      {:area :messaging :key :left}
                      {:area :messaging :key :receive-chat}
                      {:area :messaging :key :error}})


(defn create-chat-client [& sub-components]
  (let [in-ch (chan)]
    (reify Component
      (init [_ out-ch] (setup out-ch in-ch sub-components))
      (accept-message? [_ msg] ((p/create-filter external-types) msg))
      (get-channel [_] in-ch))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; helper functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- pipe-ch [in-ch out-ch]
  (go
   (while true
     (let [msg (<! in-ch)]
       (log-debug (pr-str "MSG:" msg))
       (put! out-ch msg)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up internal sub component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def internal-types  #{{:area :security :key :login}
                      {:area :user :key :create}
                      {:area :user :key :update}
                      {:area :messaging :key :send-chat}})


(defn- create-internal-component [ext-out ext-in]
  (let [in-ch (chan)]
    (reify Component
      (init [_ out-ch]
        (pipe-ch ext-in out-ch)
        (pipe-ch in-ch ext-out))
      (accept-message? [_ msg] ((p/create-filter internal-types) msg))
      (get-channel [_] in-ch))))



(defn- setup [out-ch in-ch sub-components]
  (apply bus/create-bus
         (conj sub-components
               (create-internal-component out-ch in-ch))))
