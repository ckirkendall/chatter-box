(ns chatter-box.user-component-test
  (:require [chatter-box.protocol :as p]
           [chatter-box.user-component :as u]
           [chatter-box.event-bus :as b]
           [clojure.test :as test :refer [deftest testing is]]
           [clojure.core.async :as a :refer [alts! <! >! chan timeout go put! take!]]))

(defn setup-chan [out-ch]
  (let [cp (u/create-user-component)]
      (b/init cp out-ch)
      (b/get-channel cp)))

(deftest basic-test
  (let [cr1 (p/create-user-message "u1" "p1" "f1" "l1" "u1")
        cr2 (p/create-user-message "u2" "p2" "f2" "l2" "u2")
        user1 (:user (:data cr1))
        user2 (:user (:data cr2))
        out-ch1 (chan)
        in-ch1  (setup-chan out-ch1)
        out-ch2 (chan)
        in-ch2 (setup-chan out-ch2)]
    (go
     (testing "create user"
       (>! in-ch1 cr1)
       (let [[msg c] (alts! [(timeout 100) out-ch1])]
         (is (= (p/user-created-message user1)
                msg))))
     (testing "login successful" 
       (>! in-ch1 (p/login-message (:username user1)                                    (:password (:data cr1))))
       (is (= (p/login-success-message (:user user1) [])
              (first (alts! [(timeout 100) out-ch1])))))
     (testing "user join"
       (>! in-ch2 cr2)
       (is (first (alts! [(timeout 100) out-ch2])))
       (>! in-ch2 (p/login-message (:username user2)
                                   (:password (:data cr2))))
       (is (= (p/login-success-message (:user user2) (list user1))
            (first (alts! [(timeout 100) out-ch2]))))
       ;user join for user2 should go out to all login
       ;members other than the user2
       (is (= (p/user-joined-message user2)
              (first (alts! [(timeout 100) out-ch1])))))
     (testing "user update"
       (let [up-user2 (assoc user2 :last "LL2")]
         (>! in-ch1 (p/update-user-message up-user2))
         ;message should go out to all login members
         (is (= (p/user-updated-message up-user2)
                (first (alts! [(timeout 100) out-ch2]))))
         (is (= (p/user-updated-message up-user2)
                (first (alts! [(timeout 100) out-ch1]))))))
     (testing "logout"
       (>! in-ch1 (p/logout-message (:username user1)))
       ;message should go out to all remaining login users
       (is (= (p/user-left-message user1)
              (first (alts! [(timeout 100) out-ch2])))))
     (testing "login error"
       (>! in-ch1 (p/login-message (:username user1) "bad-pass"))
       (is (= (p/login-failure-message (:username user1) "Invalid Login")
              (first (alts! [(timeout 100) out-ch1]))))))))
