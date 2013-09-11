(ns chatter-box.chat-view-test
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
            [chatter-box.chat-view-component :as c]
            [cljs.core.async :as a :refer  [alts! <! >! chan timeout]]))


(defn setup-chan [out-ch]
  (let [cp (c/create-chat-view-component)]
      (b/init cp out-ch)
      (b/get-channel cp)))

(deftest basic-test
  (let [init (p/create-message :view :init :nothing)
        cr1 (p/create-user-message "u1" "p1" "f1" "l1" "i1")
        cr2 (p/create-user-message "u2" "p2" "f2" "l2" "i2")
        user1 (:user (:data cr1))
        user2 (:user (:data cr2))
        ls1 (p/login-success-message user1 (list user2))
        r1 (p/receive-chat-message "u2" "u1" "testing")
        s1 (p/send-chat-message "u1" "u2" "testing2")
        lo1 (p/logout-message "u1")
        out-ch1 (chan)
        in-ch1 (setup-chan out-ch1)]
    (go
     ;;testing initialization
     (>! in-ch1 init)
     (<! (timeout 50))
     (let [text (from "#profile-btn" (get-text))]
       (is (= "Profile" text)))

     ;;testing login success
     (>! in-ch1 ls1)
     (<! (timeout 100))
     (is (= @c/user user1))
     (is (= "f2 l2") (from "#id_u2 span" (get-text)))

     ;;testing receive chat
     (>! in-ch1 r1)
     (<! (timeout 50))
     (is (= [(:data r1)] (from "#id_u2" (get-data :msgs))))

     ;;testing load chat
     (at "#id_u2" #(de/dispatch! % :click {}))
     (<! (timeout 50))
     (let [text (from "#chat-list button:last-child span" (get-text))
           text2 (from "#conv-name" (get-text))]
       (is (= "testing" text))
       (is (= "f2 l2" text2)))
     
     ;;testing send chat
     (at "#chat-msg" (set-prop :value "testing2")
         "#chat-btn" #(de/dispatch! % :click {}))
     (is (= s1
            (first (alts! [(timeout 100) out-ch1]))))
     (is (= [(:data r1) (:data s1)] (from "#id_u2" (get-data :msgs))))
     (is (= "testing2"
            (from "#chat-list button:last-child span" (get-text))))
     
     ;;testing logout
     (>! in-ch1 lo1)
     (<! (timeout 100))
     (is (= nil @c/user))
     (is (= nil @c/active-conv))
     (is (= "Select a User"
            (from "#conv-name" (get-text)))))))
