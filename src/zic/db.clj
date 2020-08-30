(ns zic.db
  (:require
    [next.jdbc :as jdbc]))

(def ^:private init-statements
  [
   "
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
    "
    ])

(defn init-database!
  [c]
  (doseq [statement init-statements]
    (jdbc/execute! c [statement])))
