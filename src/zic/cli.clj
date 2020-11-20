(ns zic.cli
  (:require
   [clojure.string :as string]
   [onecli.core :as onecli]
   [zic.util :refer :all]
   [zic.db :as db]
   [zic.fs :as fs]
   [zic.session :as session])
  (:import
   (java.nio.file Paths Path Files))
  (:gen-class))

(defn add!
  "
  Add a package to the installation.
  This is effectively the same as `install` but it's easier if you
  are only installing a single package.
  "
  [{:keys [name version location dependencies staging-directory]
    :as options}]
  (let [fname
        (if-let [[_ fname] (re-matches #"/([^/]+)$" location)]
          fname
          (str
           name
           "-"
           version
           ".zip"))
        staging-path (Paths/get staging-dir (into-array []))
        destination (.resolve staging-path fname)]
    (when (not (Files/exists staging-path))
      (Files/createDirectories staging-path (into-arrary [])))
    (onecli/grab location (str destination))

    (fs/unpack destination)
    ((;(session/with-database
  ;  (:zic-db-connection-string options)
  ;  (fn [c]
  ;  ))


      {:who (:zic-db-connection-string options)}))))

(defn files!
  "
  Options:

  - `-k`, `--set-package`: Set package name for which to list files

  "
  [options]
  {:result :noop})

(defn info!
  "
  Print immediate information about a particular package.
  Options:

  - `-e`, `--set-package`: Set package name for which to list files

  "
  [options]
  {:result :noop})

(defn init!
  "
  Initialize database in the start directory.
  Relevant options:

  - `-d <path>`, `--set-start-directory <path>`: Set start directory. This
    directory is where the file `.zic.sqlite3` will be placed.
  "

  [options]
  (session/with-database
    (session/path-to-connection-string
     (Paths/get
      (:start-directory options)
      (into-array
       [".zic.db"])))
    db/init-database!)
  {:result :successful})

(defn install!
  "
  Install packages into the project rooth path.

  Relevant options:

  - `-g <json-file>`, `--file-install-graph <json-file>`: Set install
    graph by providing a file. The file might be `-` meaning standard
    input.
  "
  [options]
  {:result :noop})

(defn list!
  "
  FIXME
  "
  [options]
  {:result :noop})

(defn orphans!
  "
  FIXME
  "
  [options]
  {:result :noop})

(defn remove!
  "
  FIXME
  "
  [options]
  {:result :noop})

(defn used!
  "
  FIXME
  "
  [options]
  {:result :noop})

(defn uses!
  "
  FIXME
  "
  [options]
  {:result :noop})

(defn verify!
  "
  FIXME
  "
  [options]
  {:result :noop})

(defn -main
  "
  Main entry point for zic
  "
  [& args]
  (onecli/go!
   {:program-name "zic"
    :env (System/getenv)
    :args args
    :functions
    {["add"] 'zic.cli/add!
     ["files"] 'zic.cli/files!
     ["info"] 'zic.cli/info!
     ["init"] 'zic.cli/init!
     ["install"] 'zic.cli/install!
     ["list"] 'zic.cli/list!
     ["orphans"] 'zic.cli/orphans!
     ["remove"] 'zic.cli/remove!
     ["used"] 'zic.cli/used!
     ["uses"] 'zic.cli/uses!
     ["verify"] 'zic.cli/verify!}
    :cli-aliases
    {;; Global
     "-d" "--set-start-directory"

      ;; On install
     "-g" "--file-install-graph"

      ;; On remove
     "-P" "--add-packages"
      ;; On add
     "-p" "--set-package"}
    :defaults
    {:start-directory (System/getProperty "user.dir")}
    :setup
    (fn [options]
      (if (not (= (:commands options) ["init"]))
        (if-let [marking-file
                 (fs/find-marking-file
                  (:start-directory options)
                  ".zic.db")]
          (-> options
              (assoc
               :zic-db-connection-string
               (session/path-to-connection-string
                marking-file))
              (assoc
               :root-path
               (.getParent marking-file))
               ;; TODO: STAGING DIRECTORY SET UP
              (assoc
               :staging-directory
               (string/join
                (.getParent marking-file)

                [".staging"]))))))
     ;; when calling shell/sh, we need to use `shutdown-agents`
     ;; https://clojuredocs.org/clojure.core/future
     ;; https://clojuredocs.org/clojure.java.shell/sh
    :teardown (fn [options] (shutdown-agents))}))
