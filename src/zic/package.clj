(ns zic.package
  (:require
   [zic.db :as db]
   [zic.fs :as fs])
  (:import
   (java.nio.file
    Files
    Path
    Paths)
   (java.util.zip
    ZipFile)))

(defn install-package!
  [{:keys [package-name
           package-version
           package-location
           package-metadata
           package-dependencies
           db-connection-string
           ^Path
           root-path
           ^Path
           staging-path]
    :as options}]
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
    (when (not (Files/exists staging-path (into-array [])))
      (Files/createDirectories staging-path (into-arrary [])))
    (fs/download download-dest auth)
    (fs/unpack (ZipFile. (.toFile download-dest)) root-path))
  (session/with-database
    db-connection-string
    #(db/add-package! % options)))



