(ns zic.fs
  (:require
   [clj-yaml.core :as yaml]
   [clj-http.lite.client :as client]
   [clojure.java.io :as io])
  (:import
   (java.nio.file.attribute
    FileAttribute)
   (java.security
    DigestInputStream
    MessageDigest)
   (java.nio.file
    Files
    Path
    CopyOption
    LinkOption
    OpenOption)
   (java.nio.channels
    FileChannel)
   (java.util.zip
    CheckedInputStream
    CRC32
    ZipFile
    ZipEntry)))

#_{:clj-kondo/ignore [:unused-binding]}
(extend-protocol yaml/YAMLCodec
  java.nio.file.Path
  (encode [data] (str data))
  (decode [data keywords] data))

(defn new-unique-path [^Path base ^java.lang.String pathstr]
  (loop [n 0]
    (let [new-path (.resolve base
                             (if
                              (> n 0)
                               (str pathstr "." n)
                               pathstr))]
      (if (not (Files/exists new-path (into-array LinkOption [])))
        new-path
        (recur (inc n))))))

(defn backup-all!
  [^Path base paths ending]
  (doseq [^java.lang.String path paths]
    (let [ppath (.resolve base path)]
      (when (Files/exists ppath (into-array LinkOption []))
        (Files/move ppath (new-unique-path base (str ppath "." ending))
                    (into-array CopyOption []))))))

(defn remove-files!
  [^Path base paths]
  (doseq [^java.lang.String path paths]
    (let [ppath (.resolve base path)]
      (Files/deleteIfExists ppath))))

(defn dummy-read
  "
  Read the file but don't do anything with the read contents,
  presumably because the stream is part of some checksum operation
  "
  [^java.io.InputStream stream]
  (let [buffer (byte-array 4096)]
    (loop [sentry 1]
      (when (>= sentry 0)
        (recur ^int (.read
                stream
                buffer
                0
                (count buffer)))))))

(defn- bytes->hexstr
  [bites]
  (apply str (map #(format "%02x" %) bites)))

(defn stream-sha256
  "
  Compute the SHA 256 of an input stream.
  "
  [^java.io.InputStream stream]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (with-open [digested
                (DigestInputStream. stream digest)]
      (dummy-read digested))
    (bytes->hexstr (.digest digest))))

(defn stream-crc
  "
  Compute the CRC of the input stream.
  "
  [stream]
  (with-open [checked
              (CheckedInputStream.
               stream
               (CRC32.))]
    (dummy-read checked)
    (.getValue (.getChecksum checked))))

(defn file-sha256!
  "
  Compute the SHA 256 of a file on the filesystem.
  "
  [^Path path]
  (when (Files/exists path (into-array LinkOption []))

    (with-open [file-stream (Files/newInputStream path (into-array OpenOption []))]
      (stream-sha256 file-stream))))

#_(archive-entry-checksums (java.util.zip.ZipFile.
                            (java.io.File. "lighttpd-environment/wwwroot/b.zip"))
                           #{"b/fie.txt"})

(defn archive-entry-checksums
  "
  Compute checksums for a set of files, or all files if unspecified.
  "
  ([^ZipFile zip-file] (archive-entry-checksums zip-file (fn [_] true)))
  ([^ZipFile zip-file path-pred]
   (->> zip-file
        (.entries)
        (enumeration-seq)
        (filter (fn [^ZipEntry entry]
                  (and (not (.isDirectory entry))
                       (path-pred (.getName entry)))))
        (map
         (fn [^ZipEntry entry]
           (with-open [sf (.getInputStream zip-file entry)]
             [(.getName entry) (stream-sha256 sf)])))
        (into (hash-map)))))

(defn archive-contents
  "
  Return the list (or contents) of an archive.
  "
  [^ZipFile zip-file]
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (map
        (fn [^ZipEntry entry]
          {:path (.getName entry)
           :size (.getSize entry)
           :time (.getTime entry)
           :crc (.getCrc entry)
           :is-directory (.isDirectory entry)}))
       (into [])))

#_(archive-contents (ZipFile. (java.io.File. "a.zip")))

(defn download
  "
  Download a file and save it to a destination path.
  Optionally, an auth object may be provided, allowing support for basic, token
  and header authorization.
  "
  [^String resource ^Path dest auth insecure?]
  (if (Files/exists dest (into-array LinkOption []))
    (Long/valueOf 0)
    (let [basic-args
          (if (empty? auth)
            {:as :stream
             :insecure? insecure?}
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
       [^java.io.InputStream in
        (:body (client/get resource basic-args))]
        (Files/copy in
                    dest
                    ^"[Ljava.nio.file.CopyOption;" (into-array
                     CopyOption
                     []))))))

;; Used the repl to test this
(defn file-size!
  "
  Get the size of a file.
  "
  [^Path path]
  (with-open [fchan
              (FileChannel/open path (into-array OpenOption []))]
    (.size fchan)))

#_(verify! (.toAbsolutePath
            (Paths/get "." (into-array String [])))
           {:path "a"
            :size 0
            :is-directory 1})

;; Used the repl to test this
(defn verify!
  "
  Verify a path on the filesystem.
  "
  [^Path base {:keys [^java.lang.String path size class checksum] :as path-info}]
  path-info
  (let [target-path (.resolve base path)
        is-target-dir (Files/isDirectory target-path (into-array LinkOption []))
        target-exists (Files/exists target-path (into-array LinkOption []))]
    (cond
      (= class :ghost-file)
      (if is-target-dir
        {:result :path-not-file}
        {:result :correct})
      (= class :config-file)
      (if is-target-dir
        {:result :path-not-file}
        (if target-exists
          {:result :correct}
          {:result :config-file-missing}))
      (= class :directory)
      (if is-target-dir
        {:result :correct}
        {:result :path-not-directory})
      (= class :normal-file)
      (if is-target-dir
        {:result :path-not-file}
        (if target-exists
          (let [target-size (file-size! target-path)]
            (if (not (= target-size size))
              {:result :size-discrepancy
               :target-path-size target-size}
              (let [target-checksum (file-sha256! target-path)]
                (if (not (= target-checksum checksum))
                  {:result :checksum-discrepancy
                   :target-checksum target-checksum
                   :source-checksum checksum}
                  {:result :correct}))))
          {:result :file-missing}))
      :else
      (throw (ex-info (str "Unknown file class `" class "`.")
                      {:base base :path-info path-info})))))

#_(crc-violations (java.util.zip.ZipFile. (java.io.File. "lighttpd-environment/wwwroot/bad.zip")))

(defn crc-violations
  "
  Returns a list of all CRC violations in a zip file.
  "
  [^ZipFile zip-file]
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (filter (fn [^ZipEntry entry] (not (.isDirectory entry))))
       (map (fn [^ZipEntry entry]
              (let [computed-crc
                    (with-open [sf (.getInputStream zip-file entry)]
                      (stream-crc sf))]
                (when (not (= computed-crc
                              (.getCrc entry)))
                  {:path (.getName entry)
                   :stored-crc (.getCrc entry)
                   :computed-crc computed-crc}))))
       (remove nil?)
       (into [])))

#_(unpack (java.util.zip.ZipFile. (java.io.File. "lighttpd-environment/wwwroot/bad.zip"))
          (java.nio.file.Paths/get "a" (into-array String [])))

(defn unpack
  "
  Unpack a zip file to a destination path.
  "
  [^ZipFile zip-file ^Path dest & {:keys [put-aside put-aside-ending exclude exclude-sum-pool]
                                   :or {put-aside {}
                                        put-aside-ending ".new"
                                        exclude {}
                                        exclude-sum-pool {}}}]
  (let [violations (crc-violations zip-file)]
    (when (seq violations)
      (throw (ex-info "Zip file contains CRC violations."
                      {:zip-file zip-file
                       :crc-violations violations}))))
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (mapv
        (fn [^ZipEntry entry]
          (let [entry-name (.getName entry)
                base-return
                {:path entry-name
                 :size (.getSize entry)
                 :time (.getTime entry)
                 :is-directory (.isDirectory entry)}]
            (if (exclude entry-name)
              (if-let [exclude-sum (get exclude-sum-pool entry-name)]
                (assoc base-return :checksum exclude-sum)
                base-return)
              (let [dest-path (.resolve dest ^java.lang.String (if (put-aside entry-name)
                                               (str (new-unique-path dest
                                                                (str entry-name put-aside-ending)))
                                               entry-name))]
                (if (.isDirectory entry)
                  (do
                    (Files/createDirectories dest-path (into-array FileAttribute []))
                    base-return)
                  (do
                     ;; BULLDOZE LOLCATZ W00T
                    (when (not (put-aside entry-name))
                      (Files/deleteIfExists dest-path))
                    (let [digest (MessageDigest/getInstance "SHA-256")]
                      (with-open [sf (.getInputStream zip-file entry)
                                  df (DigestInputStream.
                                      sf
                                      digest)]
                        (Files/copy df ^Path dest-path ^"[Ljava.nio.file.CopyOption;" (into-array CopyOption [])))
                      (assoc base-return :checksum (bytes->hexstr (.digest digest)))))))))))
       (into [])))

(defn list-files
  [^Path p]
  (let [stream (Files/newDirectoryStream p)]
    (into [] (iterator-seq (.iterator stream)))))

(defn all-parents
  ([^Path start-path]
   (let [p (.getParent start-path)]
     (all-parents start-path p)))
  ([f ^Path p] (if (nil? p)
           '()
           (cons f (lazy-seq (all-parents p (.getParent p)))))))

(defn find-marking-file
  [start ^Path match]
  (let [^Path found
        (some
         (fn [a]
           (some
            (fn [^Path p] (if (= (str (.getFileName p)) match) p nil))
            (list-files a)))
         (all-parents start))]
    (if (or
         (nil? found)
         (nil? (.getParent found))
         (nil? (.getParent (.getParent found))))
      nil
      found)))
