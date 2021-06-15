(ns zic.db
  (:require
   [cheshire.core :as json]
   [next.jdbc :as jdbc]))

(def ^:private init-statements
  ["
   PRAGMA foreign_keys = ON
   "
   "
    CREATE TABLE IF NOT EXISTS packages (
      id INTEGER NOT NULL PRIMARY KEY,
      name TEXT UNIQUE NOT NULL UNIQUE,
      version TEXT NOT NULL,
      location TEXT NOT NULL,
      metadata TEXT)
    "
   "
    CREATE TABLE IF NOT EXISTS files (
      id INTEGER NOT NULL PRIMARY KEY,
      pid INTEGER,
      path TEXT NOT NULL UNIQUE,
      size INTEGER NOT NULL,
      file_class INTEGER NOT NULL,
      checksum TEXT,
      CONSTRAINT pid_c FOREIGN KEY (pid) REFERENCES packages(id))
   "
   "
    CREATE TABLE IF NOT EXISTS uses (
      id INTEGER NOT NULL PRIMARY KEY,
      depender INTEGER,
      dependee INTEGER,
      CONSTRAINT depender_c FOREIGN KEY (depender) REFERENCES packages(id),
      CONSTRAINT dependee_c FOREIGN KEY (dependee) REFERENCES packages(id)
    )
    "])

(defn init-database!
  [c]
  (doseq [statement init-statements]
    (jdbc/execute! c [statement])))

(defn serialize-metadata
  [metadata]
  (if (nil? metadata)
    metadata
    (json/generate-string metadata)))

(defn deserialize-metadata
  [metadata]
  (if (or (nil? metadata)
          (empty? metadata))
    nil
    (json/parse-string metadata true)))

(defn deserialize-package
  [pkg]
  {:path (:packages/name pkg)
   :version (:packages/version pkg)
   :location (:packages/location pkg)
   :metadata (deserialize-metadata (:packages/metadata pkg))})

(def file-classes [:normal-file
                   :directory
                   :config-file
                   :ghost-file])

(def file-class-indices
  {:normal-file 0
   :directory 1
   :config-file 2
   :ghost-file 3})

(defn deserialize-file
  [fil]
  {:path (:files/path fil)
   :size (:files/size fil)
   :file-class (get file-classes (:files/file_class fil) :unknown-file-class)
   :checksum (:files/checksum fil)})

(defn get-package-id!
  [c package-name]
  (:packages/id (jdbc/execute-one!
                 c
                 ["
                   SELECT id
                   FROM packages
                   WHERE name = ?
                   "
                  package-name])))

(defn owned-by?!
  "
   Returns the name of the package that owns the file in question, or nil if no such package exists.
   "
  [c file]
  (:name (jdbc/execute-one!
          c
          ["
           SELECT
               packages.name AS name
           FROM
               files
           INNER JOIN
               packages
           ON
               files.pid = packages.id
           WHERE
               files.path = ?
           "
           file])))

(defn package-files!
  [c {:keys [package-name]}]
  (let [package-id (get-package-id! c package-name)]
    (if (nil? package-id)
      nil
      (map
       deserialize-file
       (jdbc/execute! c
                      ["
                        SELECT path, size, file_class, checksum
                        FROM files
                        WHERE pid = ?
                        "
                       package-id])))))

(defn package-info!
  [c {:keys [package-name]}]
  (let [results (jdbc/execute! c
                               ["
                                SELECT name, version, location, metadata
                                FROM packages
                                WHERE name = ?
                                "
                                package-name])]
    (if (empty? results)
      nil
      (deserialize-package
       (get results 0)))))
(defn insert-file!
  [c package-id path size file-class-index checksum]
  (jdbc/execute!
   c
   ["
         INSERT INTO files
         (pid, path, size, file_class, checksum)
         VALUES
         (?,?,?,?,?)
         "
    package-id
    path
    size
    file-class-index
    checksum]))

(defn add-package!
  [c {:keys [package-name
             package-version
             package-location
             package-metadata]}
   package-files]
  (let [metadata-object (json/parse-string package-metadata true)]
    (jdbc/execute! c
                   ["
                  INSERT INTO packages
                  (name, version, location, metadata)
                  VALUES
                  (?,?,?,?)
                  "
                    package-name
                    package-version
                    package-location
                  ;; I know, I know, don't hate me
                    (serialize-metadata metadata-object)])
    (let [package-id (get-package-id! c package-name)
          config-files (into #{} (get-in metadata-object [:zic :config-files]))
          ghost-files (get-in metadata-object [:zic :ghost-files])]
      (doseq [{:keys [path size is-directory checksum]} package-files]
        (let [file-class-index
              (cond is-directory (get file-class-indices :directory)
                    (contains? config-files path) (get file-class-indices :config-file)
                    :else (get file-class-indices :normal-file))]
          (insert-file! c package-id path size file-class-index checksum)))
      (doseq [path ghost-files]
        (insert-file! c package-id path 0 (get file-class-indices :ghost-file) nil)))))
