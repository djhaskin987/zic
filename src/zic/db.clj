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
      name TEXT UNIQUE NOT NULL,
      version TEXT NOT NULL,
      location TEXT,
      metadata TEXT)
    "
   "
    CREATE TABLE IF NOT EXISTS files (
      id INTEGER NOT NULL PRIMARY KEY,
      pid INTEGER,
      path TEXT,
      size INTEGER,
      is_directory INTEGER,
      crc INTEGER,
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

(defn deserialize-file
  [fil]
  {:path (:files/path fil)
   :size (:files/size fil)
   :is-directory (if (= (:files/is_directory fil) 1) true false)
   :crc (:files/crc fil)})

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

(defn package-files!
  [c {:keys [package-name]}]
  (let [package-id (get-package-id! c package-name)]
    (if (nil? package-id)
      nil
      (map
       deserialize-file
       (jdbc/execute! c
                      ["
                        SELECT path, size, is_directory, crc
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

(defn add-package!
  [c {:keys [package-name
             package-version
             package-location
             package-metadata]}
   package-files]
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
                  (serialize-metadata (json/parse-string package-metadata true))])
  (let [package-id (get-package-id! c package-name)]
    (doseq [{:keys [path crc size is-directory]} package-files]
      (jdbc/execute!
       c
       ["
         INSERT INTO files
         (pid, path, size, is_directory, crc)
         VALUES
         (?,?,?,?,?)
         "
        package-id
        path
        size
        is-directory
        crc]))))
