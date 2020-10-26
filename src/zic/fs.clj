(ns zic.fs
  (:require
    [zic.util :refer :all])
  (:import
    (java.nio.file Paths Path Files)))

(defn download [options]
  (:staging-dir options)
  (:package-location options)

(defn list-files [p]
  (let [stream (Files/newDirectoryStream p)]
    (into [] (iterator-seq (.iterator stream)))))

(defn all-parents
  ([start-path]
   (let [f (Paths/get start-path (into-array []))
         p (.getParent f)]
     (all-parents f p)))
  ([f p] (if (nil? p)
           '()
           (cons f (lazy-seq (all-parents p (.getParent p)))))))

(defn find-marking-file [start match]
  (let [found
        (some
          (fn [a]
            (some
              #(if (= (str %) match) % nil)
              (list-files a)))
          (all-parents (dbg start)))]
    (if (or
          (nil? found)
          (nil? (.getParent found))
          (nil? (.getParent (.getParent found))))
      nil
      found)))
