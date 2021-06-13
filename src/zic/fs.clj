(ns zic.fs
  (:require
   [buddy.core.hash :as hash]
   [buddy.core.codecs :refer bytes->hex]
   [clj-http.lite.client :as client]
   [clojure.java.io :as io])
  (:import
   (java.nio.file.attribute
    FileAttribute)
   (java.math
    BigInteger)
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
           :crc (.getCrc entry)
           :size (.getSize entry)
           :time (.getTime entry)
           :is-directory (.isDirectory entry)}))
       (into [])))

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

(defn stream-crc
  "
  Compute the CRC of the input stream.
  "
  [stream]
  (with-open [checked
              (CheckedInputStream.
               stream
               (CRC32.))]
    (let [buffer (byte-array 4096)]
      (loop [sentry 1]
        (when (>= sentry 0)
          (recur (.read
                  checked
                  buffer
                  0
                  (count buffer)))))
      (.getValue (.getChecksum checked)))))

(defn file-crc!
  "
  Compute the CRC of a file on the filesystem.
  "
  [^Path path]
  (with-open [file-stream (Files/newInputStream path (into-array OpenOption []))]
    (stream-crc file-stream)))

;; Used the repl to test this
(defn verify!
  "
  Verify a path on the filesystem.
  "
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
  (as-> bites it
    (BigInteger. 1 it)
    (.toString it 16)
    (second (left-pad it "0" 32))))

(defn unpack
  "
  Unpack a zip file to a destination path.
  "
  [^ZipFile zip-file ^Path dest]
  (let [violations (crc-violations zip-file)]
    (when (seq crc-violations)
      (throw (ex-info "Zip file contains CRC violations."
                      {:zip-file zip-file
                       :crc-violations violations}))))
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
                (let [digest (MessageDigest/getInstance "SHA-256")]
                  (with-open [sf (.getInputStream zip-file entry)
                              df (DigestInputStream.
                                  sf
                                  digest)]
                    (Files/copy df dest-path (into-array CopyOption [])))
                  {:path (.getName entry)
                   :crc (.getCrc entry)
                   :sha256 (bytes->hexstr (.digest digest))
                   :size (.getSize entry)
                   :time (.getTime entry)
                   :is-directory (.isDirectory entry)}))))))
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
