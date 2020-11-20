(defproject zic "0.1.0-SNAPSHOT"
  :description "Zip files In Concert"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-licenses "0.2.2"]
            [lein-print "0.1.0"]
            [lein-cljfmt "0.7.0"]
            ]
  :dependencies [
                 ;;[serovers "1.6.2"]
                 [org.clojure/clojure "1.10.1"]
                 [seancorfield/next.jdbc "1.0.10"]
                 [org.xerial/sqlite-jdbc "3.28.0"]
                 [onecli "0.6.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 ]
  :main ^:skip-aot zic.cli
  :target-path "target/%s"
  :test-selectors {:default (complement :integration)
                   :integration :integration}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
