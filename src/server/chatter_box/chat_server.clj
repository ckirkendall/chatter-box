(ns chatter-box.chat-server
  (:require [chatter-box.event-bus :as bus :refer [Component init accept-message? get-channel]]
            [chatter-box.protocol :as p]
            [clojure.core.async :as async :refer  [<! >! chan go put!]]
            [clojure.core.match :refer [match]]))

(declare setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def external-types #{{:area :security :key :login}
                      {:area :user :key :create-user}
                      {:area :user :key :update-user}
                      {:area :messaging :key :send-chat}})


(defn create-chat-server [& sub-components]
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
       ;(println "MSG:" msg)
       (put! out-ch msg)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; setting up internal sub component structure
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def internal-types #{{:area :security :key :login-failure}
                      {:area :security :key :login-success}
                      {:area :user :key :created}
                      {:area :user :key :updated}
                      {:area :user :key :error}
                      {:area :messaging :key :join}
                      {:area :messaging :key :left}
                      {:area :messaging :key :receive-chat}
                      {:area :messaging :key :error}})


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
