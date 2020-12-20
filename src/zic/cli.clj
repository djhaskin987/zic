(ns zic.cli
  (:gen-class)
  (:require
   [onecli.core :as onecli]
   [zic.db :as db]
   [zic.fs :as fs]
   [zic.package :as package]
   [zic.session :as session]
   [zic.util :as util])
  (:import
   (java.nio.file
    Paths)))


(defn add!
  "
  Add a package to the installation. This is effectively the same as `install`
  but it's easier if you are only installing a single package.
  Non-Global Options:
     package-name:          Name of the package.
     package-version:       Version of the package.
     package-location:      URL to the location of the package.
     package-metadata:      Metadata you wish to associate with the package.
     package-dependencies:  Unsupported as of yet, but getting there!
  "
  [options]
  (package/install-package! options)
  {:result :successful})


(defn files!
  "
  Options:

  - `-k`, `--set-package`: Set package name for which to list files

  "
  [_]
  {:result :noop})


(defn info!
  "
  Print immediate information about a particular package.
  Options:

  - `-e`, `--set-package`: Set package name for which to list files

  "
  [_]
  {:result :noop})


(defn init!
  "
  Initialize database in the start directory.
  Relevant options:

  - `-d <path>`, `--set-start-directory <path>`: Set start directory. This
    directory is where the file `.zic.sqlite3` will be placed.
  "

  [options]
  (session/path-to-connection-string
   (Paths/get
    (:start-directory options)
    (into-array
     [".zic.db"]))
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
  [_]
  {:result :noop})


(defn list!
  "
  FIXME
  "
  [_]
  {:result :noop})


(defn orphans!
  "
  FIXME
  "
  [_]
  {:result :noop})


(defn remove!
  "
  FIXME
  "
  [_]
  {:result :noop})


(defn used!
  "
  FIXME
  "
  [_]
  {:result :noop})


(defn uses!
  "
  FIXME
  "
  [_]
  {:result :noop})


(defn verify!
  "
  FIXME
  "
  [_]
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
                  (Paths/get
                   (:start-directory options)
                   (into-array
                    java.lang.String
                    []))
                  ".zic.db")]
          (-> options
              (assoc
               :db-connection-string
               (session/path-to-connection-string
                marking-file))
              (assoc
               :root-path
               (.getParent marking-file))
              (assoc
               :staging-path
               (.resolve
                (.getParent marking-file)
                ".staging")))
          options)
        options))
     ;; when calling shell/sh, we need to use `shutdown-agents`
     ;; https://clojuredocs.org/clojure.core/future
     ;; https://clojuredocs.org/clojure.java.shell/sh
    :teardown (fn [_] (shutdown-agents))}))
