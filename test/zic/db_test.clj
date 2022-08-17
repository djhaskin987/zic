(ns zic.db-test
  (:require
   [zic.db :as db]
   [datalevin.core :as d]
   [clojure.test :refer [deftest testing is]])
  (:import
   [java.nio.file Files Paths Path]))

(defonce test-db (Files/createTempFile
                  "zic_test"
                  "db"
                  (into-array java.nio.file.attribute.FileAttribute [])))

(defonce c (d/get-conn (str (.toAbsolutePath test-db)) db/schema))

(deftest "Basic operations"
  (testing "That the schema works"
    (is (= {:datoms-transacted 4}
           (d/transact!
            c
            [{:package/name "a"
              :package/version "0.1.0"
              :package/location "https://djhaskin987.me:8443/a.zip"
              :package/metadata
              {:mood :rare}}])))
    (is (= {:datoms-transacted 8}
           (d/transact!
            c
            [{:package/name "c"
              :package/version "0.1.0"
              :pacakge/location "https://djhaskin987.me:8443/c.zip"
              :package/metadata {:zic {:config-files ["c/echo.txt"]}}

              :package/files "echo"}
             {:db/id "echo"
              :file/path "c/echo.txt"
              :file/size 13
              :file/class :config-file}])))

    (is (= {:datoms-transacted 0}
           (d/transact!
            c
            [{:package/name "c"
              :package/version "0.1.0"
              :pacakge/location "https://djhaskin987.me:8443/c.zip"
              :package/metadata {:zic {:config-files ["c/echo.txt"]}}

              :package/files "echo"}
             {:db/id "echo"
              :file/path "c/echo.txt"
              :file/size 13
              :file/class :config-file}]))))
  (testing "package-id basic operations"
    (is (= 1 (db/package-id c "a")))
    (is (= nil (db/package-id c "not-exist")))
    (is (= 1
           (count
            (d/q
             '[:find ?e
               :in $
               :where [?e :package/name "c"]]
             (d/db c)))))
    (is (= (type (d/entity (d/db c) [:package/name "c"])) datalevin.entity.Entity))
    (is (= (:db/id (d/entity (d/db c) [:package/name "c"])) 2)))
  (testing "owned-by?"
    (is (= (-> (d/q '[:find (pull ?e [{:package/_files [:package/name]}]) :in $ :where [?e :file/path "c/echo.txt"]] (d/db c))
               first
               first
               :package/_files
               :package/name)
           "c"))
    (is (= (db/owned-by? c "c/echo.txt")
           "c")))
  (testing "package-files!"
    (is (= (db/package-files! c (db/package-id c "c"))
           [#:file{:size 13, :class :config-file, :path "c/echo.txt"}]))))
