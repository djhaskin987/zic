(ns zic.fs
  (:require
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

;; Used the repl to test this
(defn file-size!
  "
  Get the size of a file.
  "
  [^Path path]
  (with-open [fchan
              (FileChannel/open path (into-array OpenOption []))]
    (.size fchan)))

(defn dummy-read
  "
  Read the file but don't do anything with the read contents,
  presumably because the stream is part of some checksum operation
  "
  [stream]
  (let [buffer (byte-array 4096)]
    (loop [sentry 1]
      (when (>= sentry 0)
        (recur (.read
                stream
                buffer
                0
                (count buffer)))))))

(defn- left-pad
  "
  Who needs dependencies, amiright
  "
  [string pad width]
  (let [sz (count string)
        rm (- width sz)
        pad-length (count pad)]
    (if (or (< rm 0)
            (> (rem rm pad-length) 0))
      [false string]
      [true (str
             (apply str (repeat (quot rm pad-length) pad))
             string)])))

(defn- bytes->hexstr
  [bites]
  (apply str (map #(format "%02x" %) bites)))

#_(as-> bites it
    (BigInteger. 1 it)
    (.toString it 16)
    (second (left-pad it "0" (quot (count bites) 2))))

(defn stream-sha256
  "
  Compute the SHA 256 of an input stream.
  "
  [stream]
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
  (with-open [file-stream (Files/newInputStream path (into-array OpenOption []))]
    (stream-sha256 file-stream)))

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
  [^Path base {:keys [path size is-directory checksum]}]
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
                (let [target-checksum (file-sha256! target-path)]
                  (if (not (= target-checksum checksum))
                    {:result :incorrect-checksum
                     :target-checksum target-checksum
                     :source-checksum checksum}
                    {:result :correct}))))))))))

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
  [^ZipFile zip-file ^Path dest]
  (let [violations (crc-violations zip-file)]
    (when (seq violations)
      (throw (ex-info "Zip file contains CRC violations."
                      {:zip-file zip-file
                       :crc-violations violations}))))
  (->> zip-file
       (.entries)
       (enumeration-seq)
       (map
        (fn [^ZipEntry entry]
          (let [dest-path (.resolve dest (.getName entry))
                base-return
                {:path (.getName entry)
                 :size (.getSize entry)
                 :time (.getTime entry)
                 :is-directory (.isDirectory entry)}]
            (if (.isDirectory entry)
              (do
                (Files/createDirectories dest-path (into-array FileAttribute []))
                base-return)
              (do
                ;; BULLDOZE LOLCATZ W00T
                (Files/deleteIfExists dest-path)
                (let [digest (MessageDigest/getInstance "SHA-256")]
                  (with-open [sf (.getInputStream zip-file entry)
                              df (DigestInputStream.
                                  sf
                                  digest)]
                    (Files/copy df dest-path (into-array CopyOption [])))
                  (assoc base-return :checksum (bytes->hexstr (.digest digest)))))))))
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
