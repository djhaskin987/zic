(ns zic.package
  (:require
   [zic.db :as db]
   [zic.session :as session]
   [zic.fs :as fs])
  (:import
   (java.nio.file
    Files
    Path)
   (java.util.zip
    ZipFile)))


(defn package-info!
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
  (when download-package
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
          (fs/unpack (ZipFile. (.toFile download-dest)) root-path)))))
  (session/with-database
    db-connection-string
    #(db/add-package! % options)))
