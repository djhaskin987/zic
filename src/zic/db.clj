(ns zic.db
  (:require
   [cheshire.core :as json]
   [datalevin.core :as d]))

(defn file-class?
  [thing]
  (#{:normal-file
     :config-file
     :ghost-file} thing))

(def schema
   {
    :package/name #:db{
                      :valueType :db.type/string
                      :cardinality :db.cardinality/one
                      :unique :db.unique/identity
                      :doc "Name of an installed package"}
   :package/version #:db{
    :valueType :db.type/string
    :cardinality :db.cardinality/one
    :doc "Version of the package"}
   :package/location #:db{
    :valueType :db.type/string
    :cardinality :db.cardinality/one
    :doc "Location from which the package was fetched"}
   :package/metadata #:db{
    :cardinality :db.cardinality/one
    :doc "Metadata of the package"}
   :package/dependencies #:db{
    :valueType :db.type/ref
    :cardinality :db.cardinality/many
    :isComponent false
    :doc "Packages that this package depends on"}
   :package/files #:db{
    :cardinality :db.cardinality/many
    :valueType :db.type/ref
    :isComponent true
    :doc "Files installed by a package"}
   ;;les
   :file/path, #:db{
    :unique :db.unique/identity,
    :valueType :db.type/string,
    :cardinality :db.cardinality/one,
    :doc "Path to the file on the filesystem relative to the project root"}
   :file/size, #:db{
    :valueType :db.type/long,
    :cardinality :db.cardinality/one,
    :attr/preds 'clojure.core/pos?
    :doc "Size of the file"}
   :file/class, #:db{
    :db/valueType :db.type/keyword,
    :db/cardinality :db.cardinality/one,
    :db.attr/preds 'zic.db/file-class?
    :db/doc "Class of the file"}
   })

(defn with-database
  [connection-string
   f]
  (d/with-conn [conn connection-string schema]
    (f conn)))

(defn init-database!
  [c]
  (d/transact! c schema))

(defn package-id
  [c package-name]
  (first
    (first
      (d/q
   '[:find ?e
     :in $ ?pname
     :where
     [?e :package/name ?pname]]
   (d/db c)
   package-name))))

#_(

   (def c (d/get-conn "./.zic-db" schema))
(d/transact!
  c
  [{:package/name "a"
    :package/version "0.1.0"
    :package/location "https://djhaskin987.me:8443/a.zip"
    :package/metadata
    {
     :mood :rare
     }}])
;; This should succeed
(package-id c "a")
;; This should return nil
(package-id c "not-exist")
   )_


(defn owned-by?
  "
   Returns the name of the package that owns the file in question,
   or nil if no such package exists.
   "
  [c file]
  (first
    (first
      (d/q
    '[:find ?n
      :in $ ?fname
      :where
      [?e :package/file ?f]
      [?f :file/path ?fname]]
    (d/db c)
    file))))

#_(

   (d/transact!
     c
     [{:package/name "c"
       :package/version "0.1.0"
       :pacakge/location "https://djhaskin987.me:8443/c.zip"
       :package/metadata {
                          :zic {
                                :config-files ["c/echo.txt"]
                                }
                          }
      :package/files "echo"
      }
      {
       :db/id "echo"
       :file/path "c/echo.txt"
         :file/size 13
         :file/class :config-file}
      ]
     )
   ;; do the above twice
   ;; test that the package name is unique
   (count (d/q '[:find ?e :in $ :where [?e :package/name "c"]] (d/db c)))
   ;; This shouldn't fail and should return just one thing.
  (d/entity (d/db c) [:package/name "c"])
   ;; test that the inserted file is there
   (d/q '[:find (pull ?e {:package/_name ?e}) :in $ :where [?e :file/path "c/echo.txt"]] (d/db c))
       )


$execmd \
    add \
    --json-download-authorizations '{"djhaskin987.me": {"type": "basic", "username": "mode", "password": "code"}}' \
    --set-package-name 'c' \
    --set-package-version 0.1.0 \
    --set-package-location "https://djhaskin987.me:8443/c.zip" \
    --set-package-metadata '{"zic": {"config-files": ["c/echo.txt"]}}'

test "$(cat c/echo.txt)" = "I am NOT JUST an echo."
test -f "c/echo.txt"
test -f "c/echo.txt.c.0.1.0.new"
test -f "c/echo.txt.c.0.1.0.new.1"

   )

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
  (let [package-id (package-id c package-name)
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
