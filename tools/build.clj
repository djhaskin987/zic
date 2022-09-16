(ns build
  (:require
    [clojure.java.shell :as shell]
    [babashka.fs :as fs]
    ;[bencode.core :as b]
    [clojure.string :as str]
    [clojure.tools.build.api :as build])
  (:import
    (java.io InputStreamReader BufferedReader)))

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

(defn- print-shell-out
  "Pipe output from process to stdout"
  [things {:keys [dir env] :or {dir ^java.io.File nil env ^"Ljava.lang.String[;" nil}]
  (let [proc (.exec ^java.lang.Runtime (Runtime/getRuntime)
                    ^"Ljava.lang.String[;" (into-array java.lang.String (map str things))
                    env
                    dir)
        pstdout (BufferedReader. (InputStreamReader. (.getInputStream ^Process proc)))]
    (loop [line (.readLine pstdout)]
      (if (nil? line)
        (do
          (binding [*out* *err*]
            (print (slurp (.getErrorStream proc))))
          (.exitValue proc))
        (do
          (println line)
          (recur (.readLine pstdout)))))))

(defn- shell-out
  "Get output from process"
  [things]
  (-> (apply shell/sh (map str things)) :out str/trim))

(def lib 'zic/zic)



;; Get the number of commits reachable since the last tagged commit,
;; And the build number.
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

(defn- clean [_]
  (build/delete {:path "target"}))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn project-name 
  [_]
  (print (name lib)))

; Concerning the project version.
; It is not semantic versioning. Rich Hickey doesn't like that stuff anyway.
; The real reason for the format, though, is the following:
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
#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn project-version
  [_]
  (print version))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn uber [_]
  (println "Building uberjar...")
  (clean nil)
  (build/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir (str class-dir)})
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
  (build/uber {:class-dir (str class-dir)
               :uber-file (str uber-file)
               :basis basis
               :main 'zic.cli}))

(defn gather-java-calls
  "Gathers all java calls in the code."
  [srcdir]
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
    (set @java-calls)))


(defn get-all-buildtime-packages
  "Get all the uberjar packages to list for native-image"
  []
  (let [discovered-jar-classes
        (->> (shell-out ["jar" "-tf" uber-file])
             (re-seq #"(?m)^\s*(\S+)\.class$")
             (map #(get % 1))
             (filter #(nil? (re-seq #"^(META-INF|classes)" %)))
             (set)
             (map #(str/replace % #"/\.class$" ""))
             (map #(str/replace % #"[/]" ".")))
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
               verbatim]))))

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

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn native-image-properties
  [_]
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
    (println (format "Recognized %s packages" (count packages)))
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
          (System/exit exit))))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn native-image
  [x]
  (when (not (fs/exists? uber-file))
    (uber x))
  (when (not (fs/which "native-image"))
    (binding [*out* *err*]
      (println "Could not find native-image on the path"))
    (System/exit 1))
  (when (not (fs/exists? (fs/path (fs/absolutize resources-path) native-image-properties-file)))
    (native-image-properties x))
  (when (not (fs/exists? native-image-target-dir))
    (fs/create-dirs native-image-target-dir))
  (println "Starting native image build...")
  (print-shell-out
    [(fs/which "native-image") "-jar" (str uber-file)]
    {:dir native-image-target-dir}))
