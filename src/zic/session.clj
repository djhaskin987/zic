(ns zic.session
  (:require
   [clojure.string :as string]
   [next.jdbc :as jdbc]
   [zic.util :refer :all])
  (:import
   (java.nio.file
    Path
    Paths)))

(defprotocol OpenClose

  (open
    [this]
    "opens resource")

  (close
    [this]
    "closes resource"))

(defprotocol Reset

  (reset
    [this]
    "resets resource"))

(defrecord JdbcTransaction
           [connection]

  OpenClose

  (open
    [this]
    (jdbc/execute! (:connection this) ["
                                       BEGIN TRANSACTION
                                       "])
    this)

  (close
    [this]
    (jdbc/execute! (:connection this) ["
                                       COMMIT TRANSACTION
                                       "])
    this)

  Reset

  (reset
    [this]
    (jdbc/execute! (:connection this) ["ROLLBACK"])

    this))

(defn with-database
  [connection-string
   f]
  (let [datasource
        (jdbc/get-datasource
         {:jdbcUrl connection-string})]
    (with-open [c (jdbc/get-connection datasource)]
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
  [^Path path]
  (str "jdbc:sqlite:"
       (.toAbsolutePath path)))
