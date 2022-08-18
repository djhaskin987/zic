(ns zic.session
  (:require
   [datalevin.core :as d]
   [zic.db :as db])
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

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn with-database
  [connection-string
   f]
  (d/with-conn [conn connection-string db/schema] (f conn)))

(defn with-zic-session
  [connection-string
   ^Path path
   f]
  (println "Then it goes here.")
  (with-database connection-string
    (fn [c] (with-filelock path (fn [] (f c))))))

(defn path-to-connection-string
  [^Path path]
  (str (.toAbsolutePath path)))
