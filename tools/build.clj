(ns build
  (:require [clojure.tools.build.api :as build]))

(def lib 'zic/zic)
(def version (format "%s.%s"
                     (build/git-process {:git-args "describe --tags --abbrev=0"})
                     (build/git-count-revs nil)))

(def class-dir "target/classes")
(def basis (build/create-basis {:aliases [:uberjar]
                                :project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar"
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
                      :class-dir class-dir})
  (build/uber {:class-dir class-dir
               :uber-file uber-file
               :basis basis
               :main 'zic.cli}))