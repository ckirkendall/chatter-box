(ns chatter-box.login-view-test
  (:require-macros [cemerick.cljs.test :refer [deftest testing is]]
                   [cljs.core.async.macros :refer [go]])
  (:require [chatter-box.event-bus :as b :refer [create-bus Component]]
            [enfocus.core :as ef
             :refer [at content html append prepend after befor do->
                     remove-node add-class read-form this-node from
                     set-attr set-data get-prop get-data set-style
                     get-text set-prop]]
            [domina.events :as de]
            [chatter-box.protocol :as p]
            [cemerick.cljs.test :as t]
            [chatter-box.login-view-component :as l]
            [cljs.core.async :as a :refer  [alts! <! >! chan timeout]]))


(defn setup-chan [out-ch]
  (let [cp (l/create-login-view-component)]
      (b/init cp out-ch)
      (b/get-channel cp)))

(deftest basic-test
  (let [init (p/create-message :view :init :nothing)
        cr1 (p/create-user-message "u1" "p1" "f1" "l1" "u1")
        user1 (:user (:data cr1))
        ls1 (p/login-success-message user1 {})
        lo1 (p/logout-message "u1")
        out-ch1 (chan)
        in-ch1 (setup-chan out-ch1)]
    (go
     ;;testing initialization
     (>! in-ch1 init)
     (<! (timeout 100))
     (let [text (from "#login-btn" (get-text))]
       (is (= "Sign in " text)))
     
     ;;testing clicking the login button
     (at "#login-username" (set-prop :value "u1")
         "#login-password" (set-prop :value "p1")
         "#login-btn" #(de/dispatch! % :click {}))
     (is (= (p/login-message "u1" "p1")
            (first (alts! [(timeout 100) out-ch1]))))

     ;;testing login succesfull
     (>! in-ch1 ls1)
     (is (= (p/create-message :nav :chat nil)
            (first (alts! [(timeout 100) out-ch1]))))

     ;;testing logout
     (>! in-ch1 lo1)
     (<! (timeout 100))
     (is (= nil @l/user)))))
