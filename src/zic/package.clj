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

(defn download-package!
  [{:keys [package-name
           package-version
           package-location
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
    (when (not (Files/exists staging-path (into-array
                                           java.nio.file.LinkOption
                                           [])))
      (Files/createDirectories staging-path (into-array
                                             java.nio.file.attribute.FileAttribute
                                             [])))
    (fs/download package-location download-dest auth)
    download-dest))

(defn package-file-conflict
  [c
   ^Path
   downloaded-archive]
  (some
   (fn [rec]
     (when (not (:is-directory rec))
       (when-let [package (db/owned-by?! c (:path rec))]
         (assoc rec :package package))))
   (fs/archive-contents downloaded-archive)))

(defn install-package!
  [{:keys [package-name
           download-package
           db-connection-string
           ^Path
           root-path
           ^Path
           lock-path]
    :as options}]
  (session/with-zic-session
    db-connection-string
    lock-path
    (fn [c]
      (when (db/get-package-id! c package-name)
        (throw (ex-info (str "Package already installed: `" package-name "`")
                        {:options options})))

      (let [package-files
            (if download-package
              (let [downloaded-zip
                    (-> (download-package! options)
                        (.toFile)
                        (ZipFile.))]
                (when-let [conflict (package-file-conflict c downloaded-zip)]
                  (throw (ex-info (str "File `" (:path conflict) "` is already owned by package `" (:package conflict) "`")
                                  {:conflict conflict :options options})))
                (fs/unpack downloaded-zip root-path))
              [])]
        (db/add-package!
         c
         options
         package-files)))))