(ns zic.fs
  (:require
   [clj-http.lite.client :as client]
   [clojure.java.io :as io])
  (:import
   (java.nio.file.attribute
    FileAttribute)
   (java.nio.file
    Files
    Path
    CopyOption
    LinkOption
    OpenOption)
   (java.util.zip
    CRC32
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

;; exceedingly janky. try to use with-open or something.
(defn file-size!
  [^Path path]
    (let [fchan
          (FileChannel/open path (into-array OpenOption []))
          sz (.size fchan)]
      (.close fchan)
      sz))

;; exceedingly janky. try to use with-open or something.
;; https://www.baeldung.com/java-checksums
(defn file-crc!
  [^Path path]
  (let [checked
        (CheckedInputStream.
          (Files/newInputStream path (into-array OpenOption []))
          (CRC32.))
        buffer (make-array Byte/TYPE 4096)]
    (loop [sentry 1]
      (when (>= sentry 0)
        (recur (.read
                 checkedInputStream
                 0
                 (.length buffer)))))
    (let [result (.getValue (.getChecksum checked))]
      (.close checked)
      result)))

(defn verify!
  [^Path base {:keys [path crc size is-directory]}]
  (let [target-path (.resolve base path)]
        (if
          (not (Files/exists target-path (into-array LinkOption [])))
          {:result :file-missing}
          (let [is-target-dir (Files/isDirectory target-path (into-array LinkOption []))]
            (if is-directory
              (if (not is-target-dir)
                {:result :path-not-directory}
                {:result :correct})
              (if is-target-dir
                {:result :path-not-file}
                (let [target-size (file-size! target-path)]
                  (if (not (= target-size size))
                    {:result :incorrect-size
                     :target-path-size target-size}
                    (let [target-crc (file-crc! target-path)]
                      (if (not (= target-crc crc))
                        {:result :incorrect-crc
                         :target-crc target-crc}
                        {:result :correct}))))))))))

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
            {:path (.getName entry)
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
