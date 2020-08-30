(ns zic.util)

(defmacro dbg [body]
  `(let [x# ~body]
     (binding [*out* *err*]
       (println "dbg:" '~body "=" (pr-str x#))
       (flush)
       x#)))
