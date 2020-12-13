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

(defn install-package!
  [{:keys [package-name
           package-version
           package-location
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
      (Files/createDirectories staging-path (into-array [])))
    (fs/download download-dest auth)
    (fs/unpack (ZipFile. (.toFile download-dest)) root-path))
  (session/with-database
    db-connection-string
    #(db/add-package! % options)))



