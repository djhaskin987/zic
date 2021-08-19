(ns zic.package
  (:require
   [zic.db :as db]
   [zic.fs :as fs]
   [zic.session :as session]
   [clojure.string :as str]
   [clojure.set :as set]
   [serovers.core :as serovers])
  (:import
   (java.nio.file
    Files
    Path
    Paths)
   (java.util.zip
    ZipFile)))

(defn get-package-files!
  [{:keys [package-name
           db-connection-string]}]
  (session/with-database
    db-connection-string
    (fn [c]
      (let [package-id (db/get-package-id! c package-name)]
        (if (nil? package-id)
          nil
          (db/package-files! c package-id))))))

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
  [{:keys [db-connection-string package-name]}]
  (session/with-database
    db-connection-string
    (fn [c]
      (dissoc (db/package-info! c package-name) :id))))

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
    (ZipFile. (.toFile download-dest))))

(defn decide-config-fate
  [old current nw]
  (cond
    (nil? current)
    :install
    (nil? old)
    :put-aside
    (nil? nw)
    :do-nothing
    (and (not (= old current))
         (not (= nw current))
         (not (= old nw)))
    :put-aside
    (and (not (= old current))
         (= nw old))
    :do-nothing
    :else
    :install))

(defn package-file-conflicts
  [c
   package-name
   archive-contents]
  (seq (remove nil?
               (doall (map (fn [rec]
                             (when (not (:is-directory rec))
                               (when-let [package (db/owned-by?! c (:path rec))]
                                 (when (not (= package-name package))
                                   (assoc rec :package package)))))
                           archive-contents)))))

(defn upgrade-precautions!
  [{:keys [package-name
           package-version
           package-metadata
           allow-downgrades
           ^Path
           root-path]}
   c
   downloaded-zip
   zip-files]
  (if-let [{exist-pkg-id :id
            exist-pkg-vers :version} (db/package-info! c package-name)]
    (do
      (let [vercmp-result (serovers/debian-vercmp
                           exist-pkg-vers package-version)]
        (when (= vercmp-result 0)
          (throw (ex-info "Cannot upgrade from one package to another of equivalent version."
                          {:existing-version exist-pkg-vers
                           :package-name package-name
                           :new-version package-version})))
        (when (and
               (not allow-downgrades)
               (> vercmp-result 0))
          (throw (ex-info "Option `allow-downgrades` is disabled and downgrade detected."
                          {:existing-version exist-pkg-vers
                           :package-name package-name
                           :new-version package-version}))))
      (let [old-files (group-by :file-class (db/package-files! c exist-pkg-id))
            old-directories (->> old-files
                                 (:directory)
                                 (map :path)
                                 (into #{}))]
        (let [new-nondirs (->> zip-files
                               (filter #(not (:is-directory %)))
                               (map :path)
                               (into #{}))
              usedto-be-dirs (clojure.set/intersection old-directories new-nondirs)]
          (when (seq usedto-be-dirs)
            (throw (ex-info "Cannot update: some directories in old package are not directories in new package."
                            {:usedto-be-dirs usedto-be-dirs}))))
        (let [old-config-sums (->> old-files
                                   (:config-file)
                                   (map #(do [(:path %) (:checksum %)]))
                                   (into (hash-map)))
              old-config-fset (into #{} (keys old-config-sums))
              new-config-fset (into #{} (get-in [:zic :config-files] package-metadata))
              contig-config-files (set/intersection old-config-fset new-config-fset)
              new-checksums (fs/archive-entry-checksums downloaded-zip contig-config-files)
              current-checksums (into (hash-map)
                                      (map (fn [conf-path] [conf-path (fs/file-sha256! (Paths/get conf-path (into-array String [])))]) contig-config-files))
              config-decisions
              (->> contig-config-files
                   (map (fn [x] [x [(get old-config-sums x)
                                    (get current-checksums x)
                                    (get new-checksums x)]]))
                   (group-by #(apply decide-config-fate (second %)))
                   (map (fn [[fate sums]]
                          [fate (mapv (fn [[path _]] path) sums)]))
                   (into (hash-map)))
              incontig-configs (set/difference old-config-fset
                                               contig-config-files)]
          (fs/backup-all! root-path incontig-configs (str package-name "." package-version ".back"))
          (fs/remove-files! root-path (map :path (:normal-file old-files)))
          (fs/try-remove-directories! root-path old-directories)
          (db/remove-files! c exist-pkg-id)
          config-decisions)))
    {}))

(defn remove-without-cascade-internal
  [c
   package-info
   ^Path
   root-path]
  (let [package-files (db/package-files! c (:id package-info))
        old-files (group-by :file-class package-files)]
    (fs/backup-all! root-path (map :path (:config-file old-files))
                    (str (:name package-info) "." (:version package-info) ".back"))
    (fs/remove-files! root-path (map :path (:normal-file old-files)))
    (fs/try-remove-directories! root-path (map :path (:directory old-files)))
    (db/remove-files! c (:id package-info))
    (db/remove-package! c (:id package-info))))

(defn remove-package!
  [{:keys [package-name
           db-connection-string
           ^Path
           root-path
           ^Path
           lock-path]}]
  (session/with-zic-session
    db-connection-string
    lock-path
    (fn [c]
      (let [package-info (db/package-info! c package-name)]
        (remove-without-cascade-internal c package-info root-path)))))

(defn install-package!
  [{:keys [package-name
           package-version
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
      (let [package-files
            (if download-package
              (if-let [downloaded-zip
                       (download-package! options)]
                (let [zip-files (fs/archive-contents downloaded-zip)]
                  (when-let [conflicts (package-file-conflicts c package-name zip-files)]
                    (throw (ex-info (str "Several files are already present in the project which are owned by other packages.")
                                    {:conflicts conflicts})))
                  (let [precautions (upgrade-precautions! options c downloaded-zip zip-files)]
                    (fs/unpack downloaded-zip root-path
                               :put-aside (or (:put-aside precautions) {})
                               :put-aside-ending (str package-name "." package-version ".new")
                               :exclude (or (:do-nothing precautions) #{}))))
                (throw (ex-info (str "Package was not able to be downloaded.")
                                {})))
              [])]
        (db/add-package!
         c
         options
         package-files)))))