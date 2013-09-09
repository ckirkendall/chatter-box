(ns chatter-box.user-view-test
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
            [chatter-box.user-view-component :as u]
            [cljs.core.async :as a :refer  [alts! <! >! chan timeout]]))


(defn setup-chan [out-ch]
  (let [cp (u/create-user-view-component)]
      (b/init cp out-ch)
      (b/get-channel cp)))

(deftest basic-test
  (let [init (p/create-message :view :init :nothing)
        cr1 (p/create-user-message "u1" "p1" "f1" "l1" "i1")
        user1 (:user (:data cr1))
        uc1 (p/user-created-message user1)
        up1 (p/user-updated-message (assoc user1 :first "F1"))
        ls1 (p/login-success-message user1 {})
        lo1 (p/logout-message "u1")
        out-ch1 (chan)
        in-ch1 (setup-chan out-ch1)]
    (go
     ;;testing initialization
     (>! in-ch1 init)
     (<! (timeout 100))
     (let [text (from "#create-user-btn" (get-text))
           text2 (from "#update-user-btn" (get-text))]
       (is (= "Create User" text))
       (is (= "Save" text2)))

     
     ;;testing login succesfull
     (>! in-ch1 ls1)
     (<! (timeout 100))
     (is (= user1 @u/user))

     
     ;;testing clicking the update user button
     (at "#up-first"        (set-prop :value "F1")
         "#up-last"         (set-prop :value "l1")
         "#up-img-url"      (set-prop :value "i1")
         "#update-user-btn" #(de/dispatch! % :click {}))
     (is (= (p/update-user-message (assoc user1 :first "F1"))
            (first (alts! [(timeout 100) out-ch1]))))

     ;;testing clicking the create user button
     (at "#cr-username"     (set-prop :value "u1")
         "#cr-password"     (set-prop :value "p1")
         "#cr-password2"    (set-prop :value "p1")
         "#cr-first"        (set-prop :value "f1")
         "#cr-last"         (set-prop :value "l1")
         "#cr-img-url"      (set-prop :value "i1")
         "#create-user-btn" #(de/dispatch! % :click {}))
     (is (= cr1
            (first (alts! [(timeout 100) out-ch1]))))

     ;;testing user updated
     (>! in-ch1 up1)
     (is (= (p/create-message :nav :chat "user updated")
            (first (alts! [(timeout 100) out-ch1]))))

     ;;testing user created
     (>! in-ch1 uc1)
     (is (= (p/create-message :nav :login "user created")
            (first (alts! [(timeout 100) out-ch1]))))
     
     ;;testing logout 
     (>! in-ch1 lo1)
     (<! (timeout 100))
     (is (= nil @u/user)))))
