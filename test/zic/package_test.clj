(ns zic.package-test
  (:require
   [zic.package :as pkg]
   [clojure.test :refer [deftest testing is]]))

(defn fighter [node]
  (get {:c [:a :b]
        :m [:n :o :p]
        :n []
        :o []
        :p []
        :u [:v :w :x]
        :v []
        :w []
        :x []
        :b [:a :d]
        :d [:e]
        :e []
        :a [:u]} node))

(defn basic [node]
  (get {:c [:a :b]
        :b [:a]
        :a []} node))

(defn no-edges [_] nil)

(defn cycle-case [node]
  (get {:c [:a :b]
        :b [:a]
        :a [:b]} node))

(deftest ^:unit linearization-tests
  (testing "The empty case"
    (is (= (pkg/linearize
            no-edges
            :c) [:c])))
  (testing "The basic case"
    (is (= (pkg/linearize
            basic
            :c) [:a :b :c])))
  (testing "The fighter case"
    (is (= (pkg/linearize
            fighter
            :c) [:e :v :w :x :d :u :a :b :c])))
  (testing "The cycle case"
    (is (= (pkg/linearize
            cycle-case
            :c)
           [:a :b :c]))))
