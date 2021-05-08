(ns zic.package
  (:require
    [zic.util :as util]
   [zic.db :as db]
   [zic.session :as session]
   [zic.fs :as fs])
  (:import
   (java.nio.file
    Files
    Path)
   (java.util.zip
    ZipFile)))

(defn get-package-files!
  [{:keys [package-name
           db-connection-string]
    :as options}]
  (session/with-database
    db-connection-string
    #(db/package-files! % options)))

(defn verify-package-files!
  [{:keys [package-name
           root-path
           db-connection-string]
    :as options}]
  (if-let [package-file-info (get-package-files! options)]
    (as-> package-file-info it
      (map (fn [x] (assoc
                      (fs/verify! root-path x)
                      :path
                      (:path x)))
              it)
      (group-by :result it)
      (dissoc it :correct)
      (map (fn [[k v]] [k (mapv (fn [y] (dissoc y :result)) v)]) it)
      (into {} it)
      )))

(defn get-package-info!
  [{:keys [package-name
           db-connection-string]
    :as options}]
  (session/with-database
    db-connection-string
    #(db/package-info! % options)))

(defn install-package!
  [{:keys [package-name
           package-version
           package-location
           download-package
           db-connection-string
           ^Path
           root-path
           ^Path
           staging-path
           ^Path
           lock-path]
    :as options}]
  (let [package-files
        (if download-package
          (let [fname
                (if-let [[_ fname] (re-matches #"/([^/]+)$" package-location)]
                  fname
                  (str
                    package-name
                    "-"
                    package-version
                    ".zip"))
                download-dest (.resolve staging-path fname)
                auth (:download-authorizations options)]
            (session/with-filelock
              lock-path
              (fn []
                (when (not (Files/exists staging-path (into-array
                                                        java.nio.file.LinkOption
                                                        [])))
                  (Files/createDirectories staging-path (into-array
                                                          java.nio.file.attribute.FileAttribute
                                                          [])))
                (fs/download package-location download-dest auth)
                (fs/unpack (ZipFile. (.toFile download-dest)) root-path))))
          [])]
  (session/with-database
    db-connection-string
    (fn [c]
      (db/add-package!
        c
        options
        package-files)))))
