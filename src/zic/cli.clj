(ns zic.cli
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [onecli.core :as onecli]
   [zic.db :as db]
   [zic.fs :as fs]
   [zic.package :as package]
   [zic.session :as session])
  (:import
   (java.nio.file
    Paths)))

(defn
  remove!
  "
  Remove a package from the installation.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name of package
    to be removed.
    Configuration item: `package-name`
  "
  [options]
  (package/remove-package! options)
  {:result :successful})

(defn
  add!
  "
  Add a package to the installation.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name.
    Configuration item: `package-name`
  - `-V <version>`, `--set-package-version <version>`: Set package version.
    Configuration item: `package-version`
  - `-l <URL>`, `--set-package-location <URL>`: Set package location.
    Configuration item: `package-location`
  - `-m <JSON>`, `--set-package-metadata <JSON>`: Set package metadata.
    Configuration item: `package-metadata`
  - `-u <dependency>`, `--add-package-dependency <dependency>`: Specify a
    package dependency, or a package that must be present in order for this
    package to be installed.
    Configuration item: `package-dependency`
  - `-w`, `--enable-download-package`: Download the package and install it
    (the default). This option exists for testing purposes. Actually install
    (unpack) the zip file and download it, recording that the package is
    installed. This is the default.
    Configuration item: `download-package`
  - `-W`, `--disable-download-package`: Do not download the package and install
    it; only record that it was installed. This option exists for testing
    purposes. DO NOT actually install (unpack) the zip file and download it,
    only record that the package is installed.
    Configuration item: `download-package`
  "
  [options]
  (package/install-package! options)
  {:result :successful})

(defn files!
  "
  Lists the files owned by a particular package.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name of files to
    list.
    Configuration item: `package-name`
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))
  (let [result (package/get-package-files! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :package-files result})))

(defn info!
  "
  Print immediate information about a particular package.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name for which
    to get information.
    Configuration item: `package-name`
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))

  (let [result (package/get-package-info! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :package-information result})))

(defn init!
  "
  Initialize database in the start directory.
  Non-Global Options:
  - `-d <path>`, `--set-start-directory <path>`: Set start directory. This
    directory is where the file `.zic.db` will be placed.
    Configuration item: `start-directory`
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

(defn used!
  "
  FIXME
  "
  [_]
  {:result :noop})

(defn uses!
  "
  Lists the packages that use the package in question.
  Relevant options:

  - `-k <package>`, `--set-package-name <package>`: Set package name.
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))

  (let [result (package/get-package-uses! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :package-information result})))

(defn verify!
  "
  Verifies the files on the file system for a given package.
  Non-Global Options:
  package-name:          Name of the package.
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))
  (let [result (package/verify-package-files! options)]
    (if (nil? result)
      {:result :package-not-found :onecli {:exit-code 3}}
      (if (seq result)
        {:result :package-found
         :verification-results result
         :onecli {:exit-code 4}}
        {:result :package-found
         :verification-results result}))))

(defn run
  "
  Main entry point for zic, but without System/exit
  and other such non-repl-friendly oddities
  "
  [args]
  (onecli/go!
   {:program-name "zic"
    :env (System/getenv)
    :args args
    :functions
    {["add"] 'zic.cli/add!
     ["files"] 'zic.cli/files!
     ["info"] 'zic.cli/info!
     ["init"] 'zic.cli/init!
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
     "-k" "--set-package-name"
     "-V" "--set-package-version"
     "-l" "--set-package-location"
     "-m" "--set-package-metadata"

     "-W" "--disable-download-package"
     "-w" "--enable-download-package"}

    :defaults
    {:start-directory (System/getProperty "user.dir")
     :download-package true}
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
                ".staging"))
              (assoc
               :lock-path
               (.resolve
                (.getParent marking-file)
                ".zic.lock"))
              (assoc
               :package-metadata
               (json/parse-string (:package-metadata options) true)))
          options)
        options))}))

(defn -main
  "
  Main entry point for zic
  "
  [& args]
  (let [exit-code (run args)]
    ;; when calling shell/sh, we need to use `shutdown-agents`
    ;; https://clojuredocs.org/clojure.core/future
    ;; https://clojuredocs.org/clojure.java.shell/sh
    (shutdown-agents)
    (System/exit exit-code)))
