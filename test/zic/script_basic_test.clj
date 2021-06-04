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
    (let [version (as-> (sh "lein.bat" "print" ":version") it
                    (:out it)
                    (string/trim it)
                    (string/replace it #"\"" ""))
          jarfile (string/join "-"
                               ["target/uberjar/zic"
                                version
                                "standalone.jar"])]
      ;; check that just running it doesn't crash
      (is (= 0 (:exit (sh "java" "-jar" jarfile))))
      ;; check that initializing the database actually creates the file
      (let [db-path (dbg (Paths/get
                          (System/getProperty "user.dir")
                          (into-array [".zic.sqlite3"])))]
        (Files/deleteIfExists db-path)
        (is (= 0 (:exit (dbg (sh "java" "-jar" jarfile "init")))))
        (is (Files/exists db-path (into-array LinkOption [])))))))
