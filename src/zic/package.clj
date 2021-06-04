(ns zic.package
  (:require
   [zic.db :as db]
   [zic.session :as session]
   [zic.fs :as fs]
   [clojure.string :as str])
  (:import
   (java.nio.file
    Files
    Path)
   (java.util.zip
    ZipFile)))

(defn get-package-files!
  [{:keys [db-connection-string]
    :as options}]
  (session/with-database
    db-connection-string
    #(db/package-files! % options)))

(defn verify-package-files!
  [{:keys [root-path]
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
      (into {} it))
    (throw (ex-info (str/join "\n"
                              ["Could not extract file information from database."
                               "Perhaps the database is missing or the project path is incorrect."])
                    {:options options}))))

(defn get-package-info!
  [{:keys [db-connection-string]
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
