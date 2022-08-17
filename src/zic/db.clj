(ns zic.db
  (:require
   [datalevin.core :as d]))

(defn- shear [mp]
  (into {}
        (map (fn [[k v]]
               [(keyword (name k)) v])
             mp)))

(defn- present-package [pkg]
  (reduce (fn [c v]
            (dissoc c v))
          (shear pkg)
          [:id :files :dependencies]))

(defn- present-file [fl] (dissoc (shear fl) :id))

(def clean-pkg-attrs
  '[:package/name
    :package/version
    :package/location
    :package/metadata])

(defn file-class?
  [thing]
  (#{:normal-file
     :config-file
     :ghost-file} thing))

(def schema
  {:package/name #:db{:valueType :db.type/string
                      :cardinality :db.cardinality/one
                      :unique :db.unique/identity
                      :doc "Name of an installed package"}
   :package/version #:db{:valueType :db.type/string
                         :cardinality :db.cardinality/one
                         :doc "Version of the package"}
   :package/location #:db{:valueType :db.type/string
                          :cardinality :db.cardinality/one
                          :doc "Location from which the package was fetched"}
   :package/metadata #:db{:cardinality :db.cardinality/one
                          :doc "Metadata of the package"}
   :package/dependencies #:db{:valueType :db.type/ref
                              :cardinality :db.cardinality/many
                              :isComponent false
                              :doc "Packages that this package depends on"}
   :package/files #:db{:cardinality :db.cardinality/many
                       :valueType :db.type/ref
                       :isComponent true
                       :doc "Files installed by a package"}
   :file/path, #:db{:unique :db.unique/identity,
                    :valueType :db.type/string,
                    :cardinality :db.cardinality/one,
                    :doc "Path to the file on the filesystem relative to the project root"}
   :file/size, #:db{:valueType :db.type/long,
                    :cardinality :db.cardinality/one,
                    :attr/preds 'clojure.core/pos?
                    :doc "Size of the file"}
   :file/class, #:db{:valueType :db.type/keyword,
                     :cardinality :db.cardinality/one,
                     :attr/preds 'zic.db/file-class?
                     :doc "Class of the file"}
   :file/checksum, #:db{:valueType :db.type/string,
                        :cardinality :db.cardinality/one,
                        :doc "SHA-256 checksum of the file"}})

;; Kondo doesn't pick up the conn binding below for some reason
#_:clj-kondo/ignore
(defn with-database
  [connection-string
   f]
  (d/with-conn [conn connection-string schema]
    (f conn)))

(defn package-id
  [c package-name]
  (:db/id (d/entity (d/db c) [:package/name package-name])))

;;#_(
;(def c (d/get-conn "./.zic-db" schema))

;(d/transact! c [[:db.fn/retractEntity [:package/name "a"]]])
;(d/transact! c [[:db.fn/retractEntity [:package/name "b"]]])
;(d/transact! c [[:db.fn/retractEntity [:package/name "c"]]])
;(d/transact!
; c
; [{:package/name "a"
;   :package/version "0.1.0"
;   :package/location "https://djhaskin987.me:8443/a.zip"
;   :package/metadata
;   {:mood :rare}}])
;; This should succeed
;(package-id c "a")
;; This should return nil
;(package-id c "not-exist")
;;   )

(defn owned-by?
  "
   Returns the name of the package that owns the file in question,
   or nil if no such package exists.
   "
  [c file]
  (->>
   (d/pull
    (d/db c)
    '[{:package/_files [:package/name]}]
    (:db/id (d/entity (d/db c) [:file/path file])))
   :package/_files
   :package/name))

;;#_(
;;(d/q '[:find ?f :in $ :where [?e :package/name "c"] [?e :package/files ?f]] (d/db c))

;;(d/transact!
;; c
;; [{:package/name "c"
;;   :package/version "0.1.0"
;;   :package/location "https://djhaskin987.me:8443/c.zip"
;;   :package/metadata {:zic {:config-files ["c/echo.txt"]}}
;;
;;   :package/files ["echo" "bravo"]}
;;  {:db/id "bravo"
;;   :file/path "c/echo2.txt"
;;   :file/size 10
;;   :file/class :config-file}
;;  {:db/id "echo"
;;   :file/path "c/echo.txt"
;;   :file/size 13
;;   :file/class :config-file}])
;;
;;   ;; do the above twice
;;   ;; test that the package name is unique
;;
;;
;;(count (d/q '[:find ?e :in $ ?e :where [?e :package/name "c"]] (d/db c)))
;;   ;; This shouldn't fail and should return just one thing.
;;(d/entity (d/db c) [:package/name "c"])
;;   ;; test that the inserted file is there
;;   ;; TODO: This is broken.
;;   ;; TODO: Put all this stuff in a unit test.
;;(d/q '[:find (pull ?e [{:package/_files [:package/name]}]) :in $ :where [?e :file/path "c/echo.txt"]] (d/db c))
;;(util/dbg (map #(dissoc % :db/id)))
   ;;)

(defn package-files!
  [c package-id]
  (->>
   (d/pull
    (d/db c)
    '[{:package/files [*]}]
    package-id)
   :package/files
   (mapv #(dissoc (shear %) :id))))

;;#_(
;(package-files! c (:db/id (d/entity (d/db c) [:package/name "c"])))

;;(d/transact!
;; c
;; [{:package/name "b"
;;   :package/version "0.2.0"
;;   :package/location "https://djhaskin987.me:8443/b.zip"
;;   :package/metadata {}
;;   :package/files []
;;   :package/dependencies [[:package/name "a"]
;;                          [:package/name "c"]]}])
;;     ;;)

;; Returns IDs.
(defn dependers-by-id!
  "
  Returns IDs of dependers.
  "
  [c pkg-id]
  (->>
   (d/pull
    (d/db c)
    '[{:package/dependencies [:db/id]}] pkg-id)
   :package/dependencies
   (mapv :db/id)))

;; (dependers-by-id! c (:db/id (d/entity (d/db c) [:package/name "b"]))) => [1 2]
;(dependers-by-id! c (:db/id (d/entity (d/db c) [:package/name "b"])))

(defn package-dependers!
  [c package-name]
  (->>
   (d/pull
    (d/db c)
    '[{:package/_dependencies [*]}]
    (:db/id (d/entity (d/db c) [:package/name package-name])))
   :package/_dependencies
   (mapv #(dissoc (dissoc (shear %) :id) :dependencies))))

(defn package-dependees!
  [c package-name]
  (->>
   (d/pull
    (d/db c)
    '[{:package/dependencies [*]}]
    (:db/id (d/entity (d/db c) [:package/name package-name])))
   :package/dependencies
   (mapv present-package)))

;;(package-dependers! c "a") =>
;(package-dependers! c "a")
;(package-dependees! c "b")
;
(defn package-info-by-id!
  [c pkg-id]
  (assoc
   (present-package
    (d/pull (d/db c)
            '[*]
            pkg-id))
   :id
   pkg-id))

(defn package-info!
  [c package-name]
  (package-info-by-id!
   c
   (:db/id (d/entity (d/db c) [:package/name package-name]))))

(defn insert-file!
  [c package-id path size file-class checksum]
  (d/transact! c [{:db/id :the-file
                   :file/path path
                   :file/size size
                   :file/class file-class
                   :file/checksum checksum
                   :package/_files package-id}]))

(defn insert-use!
  [c depender-id dependee-id]
  (d/transact! c [[:db/add depender-id :package/dependencies dependee-id]]))

(defn add-package!
  [c {:keys [package-name
             package-version
             package-location
             package-metadata]}
   package-files
   dependency-ids]
  (d/transact! c [{:package/name package-name
                   :package/version package-version
                   :package/location package-location
                   :package/metadata package-metadata}])

                  ;; I know, I know, don't hate me
  (let [package-id (:db/id (d/entity (d/db c) [:package/name package-name]))
        config-files (into #{} (get-in package-metadata [:zic :config-files]))
        ghost-files (get-in package-metadata [:zic :ghost-files])]
    (doseq [{:keys [path size _ checksum]} (filter #(not (:is-directory %)) package-files)]
      (let [file-class
            (if (contains? config-files path)
              :config-file
              :normal-file)]
        (insert-file! c package-id path size file-class checksum)))
    (doseq [path ghost-files]
      (insert-file! c package-id path 0 :ghost-file nil))
    (doseq [depid dependency-ids]
      (insert-use! c package-id depid))))

(defn remove-package!
  [c package-id]
  (d/transact! c [[:db.fn/retractEntity package-id]]))


;;(d/transact! c [[:db/retract 59 :package/files]])


(defn remove-files!
  [c package-id]
  (d/transact! c [[:db/retract package-id :package/files]]))

(defn remove-uses!
  [c package-id]
  (d/transact! c [[:db/retract package-id :package/dependencies]]))
