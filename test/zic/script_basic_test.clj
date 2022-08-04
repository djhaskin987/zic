(ns zic.script-basic-test
  (:require
   [zic.util :refer [dbg]]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [clojure.test :refer [deftest testing is]])
  (:import
   (java.nio.file
    Files
    LinkOption
    Paths)))

(deftest ^:integration basic-tests
  (testing "That running the program works"
    (let [libname (-> (sh "scripts/name")
                      :out
                      string/trim)
          libversion (-> (sh "scripts/version")
                    :out
                    string/trim)
          jarfile (str
                   "target/"
                   libname
                   "-"
                   libversion
                   "-standalone.jar")]
      ;; check that just running it doesn't crash
      (is (= 0 (:exit (sh "java" "-jar" jarfile))))
      ;; check that initializing the database actually creates the file
      (let [db-path (dbg (Paths/get
                          (System/getProperty "user.dir")
                          (into-array [".zic.mv.db"])))]
        (Files/deleteIfExists db-path)
        (is (= 0 (:exit (dbg (sh "java" "-jar" jarfile "init")))))
        (is (Files/exists db-path (into-array LinkOption [])))))))
