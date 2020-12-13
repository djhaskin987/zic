(ns zic.fs
  (:require
   [clj-http.lite.client :as client]
   [clojure.java.io :as io]
   [zic.util :as util])
  (:import
   (java.nio.file
    Files
    Path
    Paths)
   (java.util.zip
    ZipFile)))

(defn archive-contents
  [^ZipFile zip-file]
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (map
        (fn [entry]
          {:name (.getName entry)
           :size (.getSize entry)
           :time (.getTime entry)
           :is-directory? (.isDirectory entry)
           :crc (.getCrc entry)}))
       (into [])))

(defn download
  [^String resource ^Path dest auth]
  (let [basic-args
        (if (empty? auth)
          {:as :stream}
          (into {:as :stream}
                (when-let [host (.getHost (io/as-url resource))]
                  (when-let [auth-record (get auth host)]
                    (cond (= (:type auth-record) :basic)
                          [[:basic-auth
                            [(:username auth-record)
                             (:password auth-record)]]]
                          (= (:type auth-record) :header)
                          [[:headers (:headers auth-record)]]
                          (= (:type auth-record) :oauth-token)
                          [[:oauth-token (:oauth-token auth-record)]])))))]
    (with-open
     [in
      (client/get resource basic-args)]
      (Files/copy in
                  dest
                  (into-array [])))))

(defn unpack
  [^ZipFile zip-file ^Path dest]
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (map
        (fn [entry]
          (let [dest-path (.resolve dest (.getName entry))]
            (if (.isDirectory entry)
              (Files/createDirectories dest-path (into-array []))
              (with-open [sf (.getInputStream zip-file entry)]
                (Files/copy sf dest-path (into-array []))))
            {:name (.getName entry)
             :crc (.getCrc entry)
             :size (.getSize entry)
             :is-directory (.isDirectory entry)})))
       (into [])))

(defn list-files
  [^Path p]
  (let [stream (Files/newDirectoryStream p)]
    (into [] (iterator-seq (.iterator stream)))))

(defn all-parents
  ([start-path]
   (let [f (Paths/get start-path (into-array [""]))
         p (.getParent f)]
     (all-parents f p)))
  ([f p] (if (nil? p)
           '()
           (cons f (lazy-seq (all-parents p (.getParent p)))))))

(defn find-marking-file
  [start match]
  (let [found
        (some
         (fn [a]
           (some
            #(if (= (str %) match) % nil)
            (util/dbg (list-files a))))
         (util/dbg (all-parents (util/dbg start))))]
    (if (or
         (nil? found)
         (nil? (.getParent found))
         (nil? (.getParent (.getParent found))))
      nil
      found)))

(defn ensure-exists
  [place]
  (Files/exists (Paths/get place (into-array []))
                (into-array [])))
