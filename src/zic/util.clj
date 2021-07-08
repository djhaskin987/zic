(ns zic.util)

(defmacro dbg
  [body]
  `(let [x# ~body]
     (binding [*out* *err*]
       (println "dbg: type " '~body "=" (pr-str (type x#)))
       (println "dbg: rep  " '~body "=" (pr-str x#))
       (flush)
       x#)))

(defn equijoin
  "
  Perform a logical full outer join between two vectors of maps
  based on a common key.
  `a `and `b `are vectors of maps containing keys. When either of them
  contain a value mapped under `common-key `they are added to the result
  vector.
  The vector of maps returned are of either of the form {<akey> <aentry>
                                                         <bkey> <bentry>} such that (= (get <aentry> <common-key>)  (get <bentry> <common-key>))
  "
  [a b & {:keys [akey bkey] :or {akey :a bkey :b}}]
  (when (= akey bkey)
    (throw (ex-info "akey and bkey cannot be equal."
                    {:akey akey
                     :bkey bkey
                     :function "equijoin"})))
  (as-> (transient (hash-map)) it
    (reduce
     (fn [builder [k aentry]]
       (assoc! builder k {akey aentry}))
     it
     a)
    (reduce
     (fn [builder [k bentry]]
       (assoc! builder k (assoc (get builder k) bkey bentry)))
     it
     b)
    (persistent! it)))
