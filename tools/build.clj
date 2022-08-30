(ns build
  (:require [clojure.tools.build.api :as build]))

(def lib 'zic/zic)

;; Get the number of commits reachable since the last tagged commit.
(def version
  (let [last-tag (build/git-process {:git-args "describe --tags --abbrev=0"})]
    (format "%s.%s"
            last-tag
            (build/git-process {:git-args
                                ["rev-list"
                                 (format "%s..HEAD"
                                         last-tag)
                                 "--count"]}))))

(def class-dir "target/classes")
(def basis (build/create-basis {:aliases [:uberjar]
                                :project "deps.edn"}))
(def uber-file (format "target/uberjar/%s-%s-standalone.jar"
                       (name lib)
                       version))
(defn clean [_]
  (build/delete {:path "target"}))

(defn uber [_]
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