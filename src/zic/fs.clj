(ns zic.fs
  (:require
   [clj-http.lite.client :as client]
   [clojure.java.io :as io]
   [zic.util :as util])
  (:import
   (java.nio.file.attribute
    FileAttribute)
   (java.nio.file
    Files
    Path
    Paths
    CopyOption
    LinkOption)
   (java.util.zip
    ZipFile
    ZipEntry)))


(defn archive-contents
  [^ZipFile zip-file]
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (map
        (fn [^ZipEntry entry]
          {:name (.getName entry)
           :size (.getSize entry)
           :time (.getTime entry)
           :is-directory? (.isDirectory entry)
           :crc (.getCrc entry)}))
       (into [])))


(defn download
  [^String resource ^Path dest auth]
  (if (Files/exists dest (into-array LinkOption []))
    (Long/valueOf 0)
    (let [basic-args
          (if (empty? auth)
            {:as :stream}
            (into {:as :stream}
                  (when-let [host (.getHost (io/as-url resource))]
                    (when-let [auth-record (get auth (keyword host))]
                      (cond (= (:type auth-record) "basic")
                            [[:basic-auth
                              [(:username auth-record)
                               (:password auth-record)]]]
                            (= (:type auth-record) "header")
                            [[:headers (:headers auth-record)]]
                            (= (:type auth-record) "oauth-token")
                            [[:oauth-token (:oauth-token auth-record)]])))))]
      (with-open
       [in
        (:body (client/get resource basic-args))]
        (Files/copy in
                    dest
                    (into-array
                     CopyOption
                     []))))))


(defn unpack
  [^ZipFile zip-file ^Path dest]
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (map
        (fn [^ZipEntry entry]
          (let [dest-path (.resolve dest (.getName entry))]
            (if (.isDirectory entry)
              (Files/createDirectories dest-path (into-array FileAttribute []))
              (do
                ;; BULLDOZE LOLCATZ W00T
                (Files/deleteIfExists dest-path)
                (with-open [sf (.getInputStream zip-file entry)]
                  (Files/copy sf dest-path (into-array CopyOption [])))))
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
   (let [p (.getParent start-path)]
     (all-parents start-path p)))
  ([f p] (if (nil? p)
           '()
           (cons f (lazy-seq (all-parents p (.getParent p)))))))


(defn find-marking-file
  [start match]
  (let [found
        (some
         (fn [a]
           (some
            #(if (= (str (.getFileName %)) match) % nil)
            (list-files a)))
         (all-parents start))]
    (if (or
         (nil? found)
         (nil? (.getParent found))
         (nil? (.getParent (.getParent found))))
      nil
      found)))