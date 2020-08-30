(ns zic.core-test
  (:require
    [clojure.test :refer :all]
    [clojure.string :as string]
    [clojure.java.shell :refer [sh]])
  )

(deftest a-test
  (testing "FIXME, I fail."
    (let [version (as-> (sh "lein.bat" "print" ":version") it
                    (:out it)
                    (string/trim it)
                    (string/replace it #"\"" ""))
          result (sh "java" "-jar"
                     (string/join "-"
                                  ["target/uberjar/zic"
                                   version
                                   "standalone.jar"]))]
      (is (= 0 (:exit result))))))
