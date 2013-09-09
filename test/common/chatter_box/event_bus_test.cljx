(ns chatter-box.event-bus-test
  #+cljs
  (:require-macros [cemerick.cljs.test :refer [deftest testing is]]
                   [cljs.core.async.macros :refer [go]])
  (:require
   [chatter-box.event-bus :as bus :refer [create-bus Component init accept-message? get-channel]]
   #+clj [clojure.test :as test :refer [deftest testing is]]
   #+cljs [cemerick.cljs.test :as t]
   #+clj [clojure.core.async :as a :refer [alts! <! >! chan timeout go]]
   #+cljs [cljs.core.async :as a :refer  [alts! <! >! chan timeout]]))



(defrecord TestComponent [channel filt]
  Component
  (init [_ ch] ch)
  (accept-message? [_ msg] (filt msg))
  (get-channel [_] channel))



(defn basic-test [filt check message]
  (let [my-co (TestComponent. (chan) filt)
        bus-chan (create-bus my-co)]
    (go (>! bus-chan message)
        (let [[msg _] (alts! [(timeout 100) (:channel my-co)])]
          (is (check msg message))))))


(deftest bus-echo-test
  (testing "bus basic echo test"
    (basic-test (fn [msg] true) = "testing")))


(deftest bus-filter-test 
  (testing "negative filter"
    (basic-test :good-key
                (fn [in out] (nil? in))
                {:bad-key true}))
  (testing "positive filter"
    (basic-test :good-key
                =
                {:good-key true})))


(deftest bus-multi-test
  (testing "multiple component test"
    (let [co1 (TestComponent. (chan) :good-key)
          co2 (TestComponent. (chan) :good-key)
          co3 (TestComponent. (chan) :bad-key)
          bus-chan (create-bus co1 co2 co2)
          message {:good-key true}]
      (go 
       (>! bus-chan message)
       (let [[msg _] (alts! [(timeout 100) (:channel co1)])]
         (is (= msg message)))
       (let [[msg _] (alts! [(timeout 100) (:channel co2)])]
         (is (= msg message)))
       (let [[msg _] (alts! [(timeout 100) (:channel co3)])]
         (is (nil? msg)))))))



