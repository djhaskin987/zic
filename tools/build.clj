(ns build
  (:require
    [clojure.java.shell :as shell]
    [clojure.string :as string]
    [babashka.fs :as fs]
    ;[bencode.core :as b]
    [clojure.tools.build.api :as build]))

(defmacro dbg-type
  [body]
  `(let [x# ~body]
     (binding [*out* *err*]
       (println "dbg: type " '~body "=" (pr-str (type x#)))
       (flush)
       x#)))

(defmacro dbg
  [body]
  `(let [x# ~body]
     (binding [*out* *err*]
       (println "dbg: type " '~body "=" (pr-str (type x#)))
       (println "dbg: rep  " '~body "=" (pr-str x#))
       (flush)
       x#)))

(def os
  (let [osstr (System/getProperty "os.name")]
    (cond (re-find #"^[Ww]indows" osstr)
          :windows
          (re-find #"^[Ll]inux" osstr)
          :linux
          (re-find #"^([Mm]ac|[Dd]arwin)" osstr)
          :mac
          :else
          :unkown)))
#_(print-shell-out ["build\\graalvm-ce-java11-21.3.0\\bin\\native-image.exe" "-jar" "target\\uberjar\\*.jar"] {:dir (fs/path "resources")})
(defn- print-shell-out
  "Pipe output from process to stdout"
  [things {:keys [dir] :or {dir nil}}]
  (let [used-dir (if (nil? dir) (System/getProperty "user.dir") dir)
        proc (-> (ProcessBuilder. things)
                 (.directory (fs/file used-dir))
                 (.inheritIO)
                 (.start))]
    (.waitFor proc)))

(defn- shell-out
  "Get output from process"
  [things]
  (-> (apply shell/sh (map str things)) :out string/trim))

(def lib 'zic/zic)

; Concerning the project version.
; It is not semantic versioning. Rich Hickey doesn't like that stuff anyway.
; The real reason for the format, though, is the following:
;
; 1. AppVeyor insists on having a unique version for each build
; 2. I want the build version to match the actual version
; 3. We are building for Linux AND Windows.
; 4. Windows VisualStudio C++ Build Tools insist on a 3-part
;    version number, with each version part
;    fitting into a 16-bit integer.
;
; So, we make the version number
; <milestone>.<#of commits since milestone>.<build#>
; And that's it.
; I kinda like it anyway.
(def version
  (let [last-tag (shell-out ["git" "describe" "--tags" "--abbrev=0"])
        milestone-number (Integer/parseInt last-tag)
        since-number (Integer/parseInt (shell-out ["git" "rev-list"
                                                   (format "%s..HEAD"
                                                           last-tag)
                                                   "--count"]))
        build-number (if-let [env-build-number
                              (or (System/getenv "APPVEYOR_BUILD_NUMBER")
                                  (System/getenv "BUILD_NUMBER"))]
                       (Integer/parseInt env-build-number)
                       0)]
    (format "%d.%d.%d"
            milestone-number
            since-number
            build-number)))

(def class-dir (fs/path "target" "classes"))
(def basis (build/create-basis {:aliases [:uberjar]
                                :project "deps.edn"}))
(def uber-file
  (fs/absolutize
    (fs/path
      "target"
      "uberjar"
      (format
        "%s-%s-standalone.jar"
        (name lib)
        version))))

(def native-image-target-dir
  (fs/absolutize
    (fs/path
      "target"
      "native-image")))

(def resources-path
  (fs/path
    "resources"))

(def native-image-basepath
  (fs/path
    "META-INF"
    "native-image"
    "djhaskin987"
    "zic"))

(def native-image-properties-file
  (fs/path
    native-image-basepath
    "native-image.properties"))

(def cleaned-trees [class-dir
                    native-image-target-dir])
(def cleaned-files [uber-file
                    native-image-properties-file])
(defn- clean [_]
  (print "Cleaning...")
  (doseq [tree cleaned-trees]
    (fs/delete-tree tree))
  (doseq [file cleaned-files]
    (fs/delete file))
  (println "Done."))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn project-name 
  [_]
  (print (name lib)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn project-version
  [_]
  (print version))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn uber [options]
  (clean options)
  (print "Copying resources...")
  (build/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir (str class-dir)})
  (println "Done.")
  (print "Compiling...")
  (build/compile-clj {:basis basis
                      :src-dirs ["src"]
                      :compile-opts {:disable-locals-clearing true
                                     :elide-meta [:doc :file :line :added]
                                     :direct-linking true}
                      :jvm-opts ["-Dclojure.spec.skip-macros=true"
                                 "--add-opens=java.base/java.nio=all-unnamed"
                                 "--add-opens=java.base/sun.nio.ch=all-unnamed"]
                      :class-dir (str class-dir)
                      :use-cp-file :always})
  (println "Done.")
  (print "Making uberjar...")
  (build/uber {:class-dir (str class-dir)
               :uber-file (str uber-file)
               :basis basis
               :main 'zic.cli})
  (println "Done."))

(defn gather-java-calls
  "Gathers all java calls in the code."
  [srcdir]
  (print "Gathering java calls in code...")
  (let [java-calls (atom '())]
    (fs/walk-file-tree
      (fs/path srcdir)
      {:visit-file
       (fn [^java.nio.file.Path fpath
            _]
         (swap!
           java-calls
           concat
           (as-> (fs/read-all-lines fpath) it
             (mapcat #(re-seq #"^\s*\((java\.\S+)" %) it)
             (filter #(not (nil? %)) it)
             (map #(get % 1) it)))
         :continue)})
    (set @java-calls))
  (println "Done."))


(defn get-all-buildtime-packages
  "Get all the uberjar packages to list for native-image"
  []
  (print "Gathering all packages needed at build-time...")
  (let [discovered-jar-classes
        (->> (shell-out ["jar" "-tf" uber-file])
             (re-seq #"(?m)^\s*(\S+)\.class$")
             (map #(get % 1))
             (filter #(nil? (re-seq #"^(META-INF|classes)" %)))
             (map #(string/replace % #"/[^/]+$" ""))
             (map #(string/replace % "/" "."))
             (set))
        java-calls (gather-java-calls "src")
        verbatim ["java.lang"
                  "java.math"
                  "java.lang.reflect"
                  "java.util"
                  "org.graalvm.nativebridge.jni.JNIExceptionWrapperEntryPoints"
                  "java.math.BigInteger"
                  "java.math.BigDecimal"
                  ]]
    (persistent!
      (reduce (fn [c v] (reduce conj! c v))
              (transient (set []))
              [discovered-jar-classes
               java-calls
               verbatim])))
  (println "Done."))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn native-image-properties
  [_]
  (print "Creating native-image properties file...")
  (fs/create-dirs  (fs/path resources-path native-image-basepath))
  (let [verbatim-args
        ["--verbose"
         "--enable-url-protocols=https,http"
         "--report-unsupported-elements-at-runtime"
         "--allow-incomplete-classpath"
         "--install-exit-handlers"
         "-H:+ReportExceptionStackTraces"
         "-H:+PrintClassInitialization"
         "-J-Dclojure.compiler.direct-linking=true"
         "-J-Dclojure.spec.skip-macros=true"
         "-H:-CheckToolchain"
         "-H:+InlineBeforeAnalysis"
         "-H:Log=registerResource:"
         ;  "-H:IncludeResources=.*/org/sqlite/.*|org/sqlite/.*|.*/sqlite-jdbc.properties"
         "-H:+JNI"
         (format "-H:Name=%s" (name lib))
         "-H:-UseServiceLoaderFeature"
         ]
        packages (get-all-buildtime-packages)]
    (print (format "Recognized %s packages..." (count packages)))
    (fs/write-lines (fs/path resources-path native-image-properties-file)
                    (dbg-type (persistent!
                      (reduce
                        (fn [c v] (reduce conj! c (map #(str % " \\") v)))
                        (transient
                          [(str "ImageName=" (name lib)) 
                           "JavaArgs=-Dfile.encoding=UTF-8 -Xmx4G"
                           "Args= \\"])
                        [verbatim-args
                         (pmap #(str "--initialize-at-build-time=" %) packages)]))))
    (println "Done.")))

(defn patch-uberjar-properties
  [options]
  (when (not (fs/exists? (fs/path (fs/absolutize resources-path) native-image-properties-file)))
    (native-image-properties options))
  (print "Patching uberjar with native-image properties...")
    (doseq [mi-thing (map #(.getFileName %) (fs/list-dir (fs/path
                           resources-path
                           native-image-basepath)))]
      (let [{:keys [out err exit]}
             (apply
               shell/sh
               (dbg
                 ["jar"
                  "-uf"
                  (dbg (str (fs/absolutize uber-file)))
                  (dbg (str (fs/path native-image-basepath mi-thing)))
                  :dir (dbg (str (fs/absolutize resources-path)))]))]
        (when (not (nil? out))
          (println out))
        (when (not (nil? err))
          (binding [*out* *err*]
            ( println out)))
        (when (not (= exit 0))
          (System/exit exit))))
    (println "Done."))

(defn- check-for-patch
  []
  (print "Checking for Patch in JAR...")
  (let [search-for (string/join "/" (map str (fs/components native-image-properties-file)))
        {:keys [jar-out jar-err jar-exit]}
        (apply
          shell/sh
            ["jar"
             "-tf"
             (str (fs/absolutize uber-file))])]
    (when (> jar-exit 0)
        (println "Error!")
        (println (str "    " jar-err))
        (System/exit 1))
    let [res (not (nil? (re-seq (dbg (re-pattern (str "(?m)" search-for))))))]
    (if res
      (println "FOUND.")
      (println "NOT FOUND."))
    res))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn native-image
  [{:keys [additional-arguments] :as options}]
  (when (not (fs/exists? uber-file))
    (uber options))
  (when (not (fs/which "native-image"))
    (binding [*out* *err*]
      (println "Could not find native-image on the path"))
    (System/exit 1))
  (when (check-for-patch)
    (patch-uberjar-properties options))
  (when (not (fs/exists? native-image-target-dir))
    (fs/create-dirs native-image-target-dir))

  ;; Usually we employ the print...println pattern, but native-image
  ;; will be putting its own output on our stdout, so we
  ;; do two printlns here instead.
  (println "Starting native image build...")
  (dbg (print-shell-out
         (into [(dbg (str (fs/which "native-image"))) "-jar" (str uber-file)]
               additional-arguments)
         {:dir (dbg (str native-image-target-dir))}))
  (println "native image build complete."))
