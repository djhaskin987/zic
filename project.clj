(defproject zic "0.1.0-SNAPSHOT"
  :description "Zip files In Concert"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-cljfmt "0.7.0"]
            [lein-licenses "0.2.2"]
            [lein-print "0.1.0"]]
  :dependencies [;;[serovers "1.6.2"]
                 [org.clojure/clojure "1.11.1"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [buddy/buddy-core "1.10.1"]
                 [com.h2database/h2 "1.4.199"]
                 ;;[org.xerial/sqlite-jdbc "3.28.0"]
                 [seancorfield/next.jdbc "1.0.10"]
                 [serovers "1.6.2"]
                 [onecli "0.8.0" :exclusions [org.clojure/clojure]]
                 [clj-commons/clj-yaml "0.7.108"]
                 ;;[borkdude/clj-reflector-graal-java11-fix "0.0.1-graalvm-20.1.0"]
                 ]

  :main zic.cli
  :target-path "target/%s"
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :unit :unit}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.compiler.elide-meta=[:doc :file :line :added]"
                                  "-Dclojure.spec.skip-macros=true"
                                  ]}})
