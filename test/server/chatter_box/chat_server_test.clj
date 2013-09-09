(ns chatter-box.chat-server-test
  (:require [chatter-box.protocol :as p]
           [chatter-box.chat-server :as s]
           [chatter-box.chat-component :as c]
           [chatter-box.user-component :as u]
           [chatter-box.event-bus :as b]
           [clojure.test :as test :refer [deftest testing is]]
           [clojure.core.async :as a :refer [alts! <! >! chan timeout go put! take!]]))

(defn setup-chan [out-ch]
  (let [cp (s/create-chat-server
            (c/create-chat-component)
            (u/create-user-component))]
      (b/init cp out-ch)
      (b/get-channel cp)))

(deftest basic-test
  (let [cr1 (p/create-user-message "cu1" "p1" "f1" "l1" "u1")
        cr2 (p/create-user-message "cu2" "p2" "f2" "l2" "u2")
        user1 (:user (:data cr1))
        user2 (:user (:data cr2))
        l1 (p/login-message "cu1" "p1")
        l2 (p/login-message "cu2" "p2")
        o1 (p/logout-message "cu1")
        c1 (p/send-chat-message "cu2" "u1" "test")
        out-ch1 (chan)
        in-ch1  (setup-chan out-ch1) 
        out-ch2 (chan)
        in-ch2 (setup-chan out-ch2)]
    (go
     (testing "create users"
       (>! in-ch1 cr1) 
       (is (= (p/user-created-message user1)
              (first (alts! [(timeout 500) out-ch1]))))
       (>! in-ch2 cr2)
       (is (= (p/user-created-message user2)
              (first (alts! [(timeout 500) out-ch2])))))
     (testing "basic chat"
       (>! in-ch1 l1) ;login
       (is (= (p/login-success-message "cu1" '())
              (first (alts! [(timeout 500) out-ch1]))))
       (>! in-ch2 l2)
       (is (= (p/login-success-message "cu2" (list user1))
              (first (alts! [(timeout 100) out-ch2]))))
       (is (= (p/user-joined-message user2))
              (first (alts! [(timeout 100) out-ch1])))
       (>! in-ch2 c1) ;send chat u2 -> u1
       (let [[msg c] (alts! [(timeout 100) out-ch1])]
         (is (= (p/receive-chat-message "cu2" "cu1" "test")
                msg))))
     (testing "logout/user-left" 
       (>! in-ch1 o1)
       (is (= (p/user-left-message user1)
              (first (alts! [(timeout 100) out-ch2])))))
     (testing "logout"
       (>! in-ch2 c1) ;send chat u2 -> u1
       (is (= (p/chat-error-message "cu2" "cu1 is not logged on")
              (first (alts! [(timeout 100) out-ch2]))))))))
