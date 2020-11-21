(ns zic.session-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer :all]
   [zic.session :refer :all]
   [zic.util :refer :all])
  (:import
   (java.nio.file
    Paths)))

(deftest path-to-connection-string-test
  (testing "Blank string still valid"
    (is (string/starts-with?
         (dbg (path-to-connection-string
               (Paths/get
                ""
                (into-array String []))))
         "jdbc:sqlite:"))))
