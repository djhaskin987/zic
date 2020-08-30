(ns zic.session
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [next.jdbc :as jdbc]))

(defprotocol OpenClose
  (open [this] "opens resource")
  (close [this] "closes resource"))

(defprotocol Reset
  (reset [this] "resets resource"))

(defrecord JdbcTransaction
  [connection]
  OpenClose
  (open
    [this]
    (jdbc/execute! (:connection this) [
                                       "
                                       BEGIN TRANSACTION
                                       "
                                       ])
    this)
  (close
    [this]
    (jdbc/execute! (:connection this) [
                                       "
                                       COMMIT TRANSACTION
                                       "
                                       ])
    this)
  Reset
  (reset
    [this]
    (jdbc/execute! (:connection this) [
                                       "ROLLBACK"
                                       ]
                   )
    this)
  )

(defn with-database
  [
   connection-string
   f
   ]
  (let [datasource
        (jdbc/get-datasource
          {
           :jdbcUrl connection-string
           })]
    (with-open [
                c (jdbc/get-connection datasource)
                ]
      (let [jt (->JdbcTransaction c)
            t (open jt)
            problem (atom false)]
        (try
          (f (:connection t))
          (catch Exception e
            (swap! problem (fn [x] true))
            (throw e))
          (finally
            (if @problem
              (reset t)
              (close t))))))))

(defn path-to-connection-string
  [path]
  (as-> path it
    (io/file it)
    (.toURI it)
    (.toURL it)
    (.toString it)
    (string/replace it #"^file://" "jdbc:sqlite:")))
