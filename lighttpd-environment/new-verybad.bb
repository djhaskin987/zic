#!/usr/bin/env bb

(import (java.util.zip ZipOutputStream ZipEntry))

(require '[clojure.java.io :as io])

(with-open [fout (io/output-stream "wwwroot/verybad.zip")
            zout (ZipOutputStream. fout)]
            (let [zipentry (ZipEntry. "a.txt")]
            (.setSize zipentry 2)
            (.putNextEntry zout zipentry)
            (.write zout (.getBytes "hi" "UTF-8") 0 2)
            (.closeEntry zout)
            (.setCrc zipentry 0)
            ))



