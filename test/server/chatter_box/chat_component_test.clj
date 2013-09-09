(ns chatter-box.chat-component-test
  (:require [chatter-box.protocol :as p]
           [chatter-box.chat-component :as c]
           [chatter-box.event-bus :as b]
           [clojure.test :as test :refer [deftest testing is]]
           [clojure.core.async :as a :refer [alts! <! >! chan timeout go put! take!]]))

(defn setup-chan [out-ch]
  (let [cp (c/create-chat-component)]
      (b/init cp out-ch)
      (b/get-channel cp)))

(deftest basic-test
  (let [user1 (p/build-user "u1" "f1" "l1" "i1")
        user2 (p/build-user "u2" "f2" "l2" "i2")
        user3 (p/build-user "u3" "f3" "l3" "i3")
        l1 (p/login-success-message user1 [])
        o1 (p/logout-message "u1")
        l2 (p/login-success-message user2 [])
        l3 (p/login-success-message user3 [])
        c1 (p/send-chat-message "u2" "u1" "test")
        c2 (p/send-chat-message "u3" "u4" "test")
        out-ch1 (chan)
        in-ch1  (setup-chan out-ch1) 
        out-ch2 (chan)
        in-ch2 (setup-chan out-ch2)
        out-ch3 (chan)
        in-ch3 (setup-chan out-ch3)]
    (go
     (testing "basic chat"
       (>! in-ch1 l1) ;login
       (>! in-ch2 l2)
       (>! in-ch3 l3)
       (<! (timeout 100))
       (>! in-ch2 c1) ;send chat u2 -> u1
       (let [[msg c] (alts! [(timeout 100) out-ch1])]
         (is (= (p/receive-chat-message "u2" "u1" "test")
                msg))
         ;no message should be sent to user3
         (is (not (first (alts! [(timeout 100) out-ch3]))))))
     (testing "failed chat" 
       (>! in-ch3 c2)
       (is (= (p/chat-error-message "u3" "u4 is not logged on")
              (first (alts! [(timeout 100) out-ch3])))))
     (testing "logout"
       (>! in-ch1 o1)
       (<! (timeout 100))
       (>! in-ch2 c1) ;send chat u2 -> u1
       (is (= (p/chat-error-message "u2" "u1 is not logged on")
              (first (alts! [(timeout 100) out-ch2]))))))))
