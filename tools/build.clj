(ns build
  (:require
    [clojure.java.shell :as shell]
    [babashka.fs :as fs]
    ;[bencode.core :as b]
    [clojure.string :as str]
    [clojure.tools.build.api :as build]
    ))

#_(defmacro dbg
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

(defn- shell-out
  "Get output from process"
  [things]
  (-> (apply shell/sh things) :out str/trim))

(def lib 'zic/zic)



;; Get the number of commits reachable since the last tagged commit,
;; And the build number.
(def version
  (let [last-tag (shell-out ["git" "describe" "--tags" "--abbrev=0"])
        milestone-number (Integer/parseInt last-tag)
        since-number (shell-out ["git" "rev-list"
                                 (format "%s..HEAD"
                                         last-tag)
                                 "--count"])
        build-number (if-let [env-build-number
                             (or (System/getenv "APPVEYOR_BUILD_NUMBER")
                              (System/getenv "BUILD_NUMBER"))]
                       (Integer/parseInt env-build-number)
                       0)]
    (format "%d.%d.%d"
            milestone-number
            since-number
            build-number)))

(def class-dir "target/classes")
(def basis (build/create-basis {:aliases [:uberjar]
                                :project "deps.edn"}))
(def uber-file (format "target/uberjar/%s-%s-standalone.jar"
                       (name lib)
                       version))
(defn- clean [_]
  (build/delete {:path "target"}))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn project-name 
  [_]
  (print (name lib)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn project-version
  [_]
  (print version))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn uber [_]
  (println "Building uberjar...")
  (clean nil)
  (build/copy-dir {:src-dirs ["src" "resources"]
                   :target-dir class-dir})
  (build/compile-clj {:basis basis
                      :src-dirs ["src"]
                      :compile-opts {:disable-locals-clearing true
                                     :elide-meta [:doc :file :line :added]
                                     :direct-linking true}
                      :jvm-opts ["-Dclojure.spec.skip-macros=true"
                                 "--add-opens=java.base/java.nio=all-unnamed"
                                 "--add-opens=java.base/sun.nio.ch=all-unnamed"]
                      :class-dir class-dir
                      :use-cp-file :always})
  (build/uber {:class-dir class-dir
               :uber-file uber-file
               :basis basis
               :main 'zic.cli}))

(defn gather-java-calls
  "Gathers all java calls in the code."
  [srcdir]
  (let [java-calls (atom '())]
    (fs/walk-file-tree
      (fs/path srcdir)
      :visit-file
      (fn [^java.nio.file.Path fpath
           _]
        (swap!
          java-calls
          concat
          (->> (fs/read-all-lines fpath)
               (map #(re-seq #"^\s*\((java\.\S+)" %))
               (filter #(not (nil? %)))
               (map #(get % 1))))
        :continue))
    (sorted-set @java-calls)))


(defn get-all-buildtime-packages
  "Get all the uberjar packages to list for native-image"
  []
  (let [discovered-jar-classes
        (->> (shell-out ["jar" "-tf" uber-file])
             (re-seq #"(?m)^\s*(\S+)\.class$")
             (map #(get % 1))
             (filter #(nil? (re-seq #"^(META-INF|classes)" %)))
             (apply sorted-set)
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
    (reduce into (sorted-set) [discovered-jar-classes
                               java-calls
                               verbatim])))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn native-image
  [x]
  (println "Starting native image build...")
  (when (not (fs/exists? uber-file))
    (uber x))
  (let [verbatim-args
        ["--verbose"
         "--enable-url-protocols=https,http"
         "--report-unsupported-elements-at-runtime"
         "--allow-incomplete-classpath"
         "--install-exit-handlers"
         "-H:+ReportExceptionStackTrac"
         "-H:+PrintClassInitialization"
         "-Dfile.encoding=UTF-8"
         "-J-Dclojure.compiler.direct-linking=true"
         "-J-Dclojure.spec.skip-macros=true"
         "-H:-CheckToolchain"
         "-H:+InlineBeforeAnalysis"
         "-H:Log=registerResource:"
         ;  "-H:IncludeResources=.*/org/sqlite/.*|org/sqlite/.*|.*/sqlite-jdbc.properties"
         "-H:+JNI"
         (format "-H:Name=%s" (name lib))
         "-H:-UseServiceLoaderFeature"
         (format "-jar target/%s-%s-standalone.jar" (name lib) version)
         "-J-Xmx4G"]
        packages (get-all-buildtime-packages)]
    (println (format "Recognized %s packages" (count packages)))
    (Thread/sleep 2000)
    (shell/sh
           (reduce into ["native-image"]
                        [verbatim-args
                         (map #(str "--initialize-at-build-time=" %) packages)]))))
