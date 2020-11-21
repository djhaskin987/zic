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
  {:name (:package/name pkg)
   :version (:package/version pkg)
   :location (:package/location pkg)
   :metadata (deserialize-metadata (:package/metadata pkg))})

(defn add-package!
  [c {:keys [package-name
             package-version
             package-location
             package-metadata]}]
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
                  (serialize-metadata package-metadata)]))
