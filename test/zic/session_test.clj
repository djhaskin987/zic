(ns zic.session-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest testing is]]
   [zic.session :refer [path-to-connection-string]]
   [zic.util :refer [dbg]])
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
         "jdbc:h2:file:"))))
