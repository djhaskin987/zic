(ns zic.session
  (:require
   [next.jdbc :as jdbc])
  (:import
   (java.nio.channels
    FileChannel)
   (java.nio.file
    StandardOpenOption
    Path)))

(defn with-filelock
  "
  Surrounds the invocation of the function `f` with a file lock on the path of
  your choosing. This path's contents, if any, will be DELETED and the path
  will be deleted upon closure of the lock. Don't give me a file you intend to
  use later or before for something other than locking.
  "
  [^Path path
   f]
  (let [channel
        (FileChannel/open
         path
         (into-array
          StandardOpenOption
          [StandardOpenOption/CREATE
           StandardOpenOption/READ
           StandardOpenOption/WRITE
           StandardOpenOption/TRUNCATE_EXISTING
           StandardOpenOption/DELETE_ON_CLOSE]))]
    (if-let [lock (.tryLock channel)]
      (try
        (f)
        (finally
          (.release lock)
          (.close channel)))
      (throw
       (ex-info
        (str
         "Could not acquire file lock on `"
         path
         "`. "
         "Probably another zic process is running.")
        {:path path})))))

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
                                       COMMIT
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
            (swap! problem (fn [_] true))
            (throw e))
          (finally
            (if @problem
              (reset t)
              (close t))))))))

(defn with-zic-session
  [connection-string
   ^Path path
   f]
  (with-database connection-string
    (fn [c] (with-filelock path (fn [] (f c))))))

(defn path-to-connection-string
  [^Path path]
  (str "jdbc:h2:file:"
       (.toAbsolutePath path)))
