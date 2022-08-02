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
    to be removed. This information is required.
    Configuration item: `package-name`
  - `-c`, `--enable-cascade`: Enable removal of this package and all
    packages which depend on it.
    Configuration item: `cascade`
  - `-C`, `--disable-cascade`: Disable removal of this package and all
    packages which depend on it. This is the default behavior.
    Configuration item: `cascade`
  - `-r`, `--enable-dry-run`: Enable dry run. Do not actually remove anything,
    just pretend to do so (presumably to see what packages *would* have been
    removed if the command were run without this option).
    Configuration item: `dry-run`
  - `-R`, `--disable-dry-run`: Disable dry run. Ensures that packages are
    actually removed. This is the default behavior.
    Configuration item: `dry-run`
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))
  (let [result (package/remove-package! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :dry-run (:dry-run options)
       :removed-packages result})))

(defn
  add!
  "
  Add a package to the installation.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name.
    This information is required in order to run.
    Configuration item: `package-name`
  - `-V <version>`, `--set-package-version <version>`: Set package version.
    This information is required in order to run.
    Configuration item: `package-version`
  - `-l <URL>`, `--set-package-location <URL>`: Set package location.
    This information is required in order to run, unless `download-package` is
    set to false (disabled).
    Configuration item: `package-location`
  - `-m <JSON>`, `--set-package-metadata <JSON>`: Set optional package metadata.
    Configuration item: `package-metadata`
  - `-u <dependency>`, `--add-package-dependency <dependency>`: Specify a
    package dependency, or a package that must be present in order for this
    package to be installed.
    Configuration item: `package-dependency`
  - `-w`, `--enable-download-package`: Download the package and install it.
    This is the default behavior. This option exists for testing purposes.
    Actually install (unpack) the zip file and download it, recording that the
    package is installed. This is the default behavior.
    Configuration item: `download-package`
  - `-W`, `--disable-download-package`: Do not download the package and install
    it; only record that it was installed. This option exists for testing
    purposes. DO NOT actually install (unpack) the zip file and download it,
    only record that the package is installed.
    Configuration item: `download-package`
  - `-o`, `--enable-allow-downgrades`: Allow downgrades, or when the new version
    is older than the currently installed version in the case of when there
    is already an installed package of the same name.
    Configuration item: `allow-downgrades`
  - `-O`, `--disable-allow-downgrades`: Disallow downgrades, or when the new
    verison is older than the currently installed version in the case of when
    there is already an installed package of the same name. This is the
    default behavior.
    Configuration item: `allow-downgrades`
  - `-i`, `--enable-allow-inplace`: Allow in-place replacement, or when the
    new version compares as equivalent to the currently installed version in the
    case of when there is already an installed package of the same name.
    Configuration item: `allow-inplace`
  - `-I`, `--disable-allow-inplace`: Disallow in-place replacement, which is
    when the new version compares as equivalent to the currently installed
    version in the case of when there is already an installed package of the
    same name. This is the default behavior.
    Configuration item: `allow-inplace`
  "
  [options]
  (package/install-package! options)
  {:result :successful})

(defn files!
  "
  Lists the files owned by a particular package.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name of files to
    list. This information is required in order to run.
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
    to get information. This information is required in order to run.
    Configuration item: `package-name`
  "
  [options]
  (when (nil? (:package-name options)) (throw (ex-info "Package name (`package-name`) option needs to be specified."
                                                       {:missing-argument :package-name})))

  (let [result (package/get-package-info! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :package-information result})))

(defn dependers!
  "
  Print immediate information about a particular package.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name for which
    to get information. This information is required in order to run.
    Configuration item: `package-name`
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))

  (let [result (package/get-package-dependers! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :package-dependers result})))

(defn dependees!
  "
  Print immediate information about a particular package.
  Non-Global Options:
  - `-k <package>`, `--set-package-name <package>`: Set package name for which
    to get information. This information is required in order to run.
    Configuration item: `package-name`
  "
  [options]
  (when (nil? (:package-name options))
    (throw (ex-info "Package name (`package-name`) option needs to be specified."
                    {:missing-argument :package-name})))

  (let [result (package/get-package-dependees! options)]
    (if (nil? result)
      {:result :not-found}
      {:result :package-found
       :package-dependees result})))

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
  [args env properties]
  (onecli/go!
   {:program-name "zic"
    :env env
    :properties properties
    :args args
    :functions
    {["add"] 'zic.cli/add!
     ["files"] 'zic.cli/files!
     ["info"] 'zic.cli/info!
     ["list"] 'zic.cli/list!
;;     ["orphans"] 'zic.cli/orphans!
     ["remove"] 'zic.cli/remove!
     ["dependers"] 'zic.cli/dependers!
     ["dependees"] 'zic.cli/dependees!
     ["verify"] 'zic.cli/verify!}
    :cli-aliases
    {;; Global
     "-d" "--set-start-directory"
      ;; On remove
     "-P" "--add-packages"
      ;; On add
     "-k" "--set-package-name"
     "-V" "--set-package-version"
     "-l" "--set-package-location"
     "-m" "--set-package-metadata"
     "-u" "--add-package-dependency"
     "-W" "--disable-download-package"
     "-w" "--enable-download-package"
     "-n" "--enable-insecure"
     "-N" "--disable-insecure"
     "-G" "--disable-allow-downgrades"
     "-g" "--enable-allow-downgrades"
     "-I" "--disable-allow-inplace"
     "-i" "--enable-allow-inplace"
     ;; On remove
     "-c" "--enable-cascade"
     "-C" "--disable-cascade"
     "-r" "--enable-dry-run"
     "-R" "--disable-dry-run"}

    :defaults
    {:output-format "yaml"
     :start-directory (get properties "user.dir")
     :cascade false
     :dry-run false
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
  (let [exit-code (run args (System/getenv) (System/getProperties))]
    ;; when calling shell/sh, we need to use `shutdown-agents`
    ;; https://clojuredocs.org/clojure.core/future
    ;; https://clojuredocs.org/clojure.java.shell/sh
    (shutdown-agents)
    (System/exit exit-code)))
