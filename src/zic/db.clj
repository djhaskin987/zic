(ns zic.db
  (:require
   [cheshire.core :as json]
   [next.jdbc :as jdbc]))

(def ^:private init-statements
  [
   ;; \"
   ;; PRAGMA foreign_keys = ON
   ;; \"
   "
   CREATE TABLE IF NOT EXISTS file_classes (
     id INTEGER NOT NULL PRIMARY KEY,
     name TEXT UNIQUE NOT NULL
   )
   "
   "
   INSERT INTO file_classes (id, name) VALUES (1, \"normal-file\"), (2, \"config-file\"), (3, \"ghost-file\")
   "
   "
    CREATE TABLE IF NOT EXISTS packages (
      id INTEGER NOT NULL PRIMARY KEY,
      name TEXT UNIQUE NOT NULL UNIQUE,
      version TEXT NOT NULL,
      location TEXT,
      metadata TEXT)
    "
   "
    CREATE TABLE IF NOT EXISTS files (
      id INTEGER NOT NULL PRIMARY KEY,
      pid INTEGER,
      path TEXT UNIQUE NOT NULL,
      size INTEGER NOT NULL,
      file_class INTEGER NOT NULL,
      checksum TEXT,
      CONSTRAINT pid_c FOREIGN KEY (pid) REFERENCES packages(id),
      CONSTRAINT fc_c FOREIGN KEY (file_class) REFERENCES file_classes(id),
      CONSTRAINT size_positive CHECK (size >= 0),
      CONSTRAINT checksum_used CHECK (file_class = 3 OR checksum IS NOT NULL),
      CONSTRAINT ghost_sum CHECK (file_class != 3 OR checksum IS NULL),
      CONSTRAINT ghost_size CHECK (file_class != 3 OR size = 0)
   )
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
  {:id (:packages/id pkg)
   :name (:packages/name pkg)
   :version (:packages/version pkg)
   :location (:packages/location pkg)
   :metadata (deserialize-metadata (:packages/metadata pkg))})

(def file-classes {1 :normal-file
                   2 :config-file
                   3 :ghost-file})

(def file-class-indices
  {:normal-file 1
   :config-file 2
   :ghost-file 3})

(defn deserialize-file
  [fil]
  {:path (:files/path fil)
   :size (:files/size fil)
   :file-class (get file-classes (:files/file_class fil) :unknown-file-class)
   :checksum (:files/checksum fil)})

#_(zic.session/with-database
    "jdbc:sqlite:.zic.db"
    (fn [c] (get-package-id! c "w")))

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
   Returns the name of the package that owns the file in question,
   or nil if no such package exists.
   "
  [c file]
  (:packages/name (jdbc/execute-one!
                   c
                   ["
          SELECT
              packages.name
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
  [c package-id]
  (map
   deserialize-file
   (jdbc/execute! c
                  ["
                        SELECT path, size, file_class, checksum
                        FROM files
                        WHERE pid = ?
                        "
                   package-id])))

(defn dependers-by-id!
  [c pkg-id]
  (map :uses/depender
       (jdbc/execute! c
                      ["
                    SELECT
                      uses.depender
                    FROM
                      uses
                    WHERE
                      uses.dependee = ?
                    "
                       pkg-id])))

(defn package-dependers!
  [c package-name]
  (map
   :packages/name
   (jdbc/execute! c
                  ["
                        SELECT dependers.name
                        FROM
                          packages AS dependers
                        INNER JOIN
                          uses
                        ON
                          uses.depender = dependers.id
                        INNER JOIN
                          packages AS dependees
                        ON
                          uses.dependee = dependees.id
                        WHERE
                          dependees.name = ?
                        "
                   package-name])))

(defn package-dependees!
  [c package-name]
  (map
   :packages/name
   (jdbc/execute! c
                  ["
                        SELECT dependees.name
                        FROM
                          packages AS dependers
                        INNER JOIN
                          uses
                        ON
                          uses.depender = dependers.id
                        INNER JOIN
                          packages AS dependees
                        ON
                          uses.dependee = dependees.id
                        WHERE
                          dependers.name = ?
                        "
                   package-name])))

(defn package-info-by-id!
  [c pkg-id]
  (let [results (jdbc/execute! c
                               ["
                                SELECT id, name, version, location, metadata
                                FROM packages
                                WHERE id = ?
                                "
                                pkg-id])]
    (if (empty? results)
      nil
      (deserialize-package
       (get results 0)))))

(defn package-info!
  [c package-name]
  (let [results (jdbc/execute! c
                               ["
                                SELECT id, name, version, location, metadata
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

(defn insert-use!
  [c depender-id dependee-id]
  (jdbc/execute!
   c
   ["
        INSERT INTO uses
        (depender, dependee)
        VALUES
        (?,?)
    "
    depender-id
    dependee-id]))

(defn add-package!
  [c {:keys [package-name
             package-version
             package-location
             package-metadata]}
   package-files
   dependency-ids]
                  ;; I know, I know, don't hate me
  (let [serialized-metadata (serialize-metadata package-metadata)]
    (jdbc/execute! c
                   ["
                  INSERT INTO packages
                  (name, version, location, metadata)
                  VALUES
                  (?,?,?,?)
                  ON CONFLICT (name) DO UPDATE SET version=?, location=?, metadata=?
                  "
                    package-name
                    package-version
                    package-location
                    serialized-metadata
                    package-version
                    package-location
                    serialized-metadata]))
  (let [package-id (get-package-id! c package-name)
        config-files (into #{} (get-in package-metadata [:zic :config-files]))
        ghost-files (get-in package-metadata [:zic :ghost-files])]
    (doseq [{:keys [path size _ checksum]} (filter #(not (:is-directory %)) package-files)]
      (let [file-class-index
            (if (contains? config-files path)
              (get file-class-indices :config-file)
              (get file-class-indices :normal-file))]
        (insert-file! c package-id path size file-class-index checksum)))
    (doseq [path ghost-files]
      (insert-file! c package-id path 0 (get file-class-indices :ghost-file) nil))
    (doseq [depid dependency-ids]
      (insert-use! c package-id depid))))

(defn remove-package!
  [c package-id]
  (jdbc/execute-one!
   c
   ["
     DELETE
     FROM
      packages
     WHERE
      packages.id = ?
     "
    package-id]))

(defn remove-files!
  [c package-id]
  (jdbc/execute! c
                 ["
                  DELETE
                  FROM
                    files
                  WHERE
                    files.pid = ?
                  "
                  package-id]))

(defn remove-uses!
  [c package-id]
  (jdbc/execute! c
                 ["
                  DELETE
                  FROM
                    uses
                  WHERE
                    uses.depender = ?
                  "
                  package-id]))
