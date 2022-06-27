(defproject zic "0.1.0-SNAPSHOT"
  :description "Zip files In Concert"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-cljfmt "0.7.0"]
            [lein-licenses "0.2.2"]
            [lein-print "0.1.0"]]
  :jvm-opts [
             "--add-opens=java.base/java.nio=ALL-UNNAMED"
             "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
             ]
  :dependencies [
                 [datalevin "0.6.14"]
                 [org.clojure/clojure "1.11.1"]
                 [org.martinklepsch/clj-http-lite "0.4.3"]
                 [buddy/buddy-core "1.10.1"]
                 [serovers "1.6.2"]
                 [onecli "0.9.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [clj-commons/clj-yaml "0.7.108"]
                 ]

  :main zic.cli
  :target-path "target/%s"
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :unit :unit}

  :profiles {
             :test-repl
             {
                         :jvm-opts [
                                    "-Djavax.net.ssl.trustStore=test/resources/test.keystore"
                                    "-Djavax.net.ssl.trustStorePassword=asdfasdf"
                                    "--add-opens=java.base/java.nio=ALL-UNNAMED"
                                    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                                    ]
                         }

             :uberjar {
                       :aot :all
                       :jvm-opts [
                                  "-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.compiler.elide-meta=[:doc :file :line :added]"
                                  "-Dclojure.spec.skip-macros=true"
                                  "--add-opens=java.base/java.nio=ALL-UNNAMED"
                                  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                                  ]
                       :dependencies [
                                      [datalevin-native "0.6.14"]
                                      [org.clojure/clojure "1.11.1"]
                                      [org.martinklepsch/clj-http-lite "0.4.3"]
                                      [buddy/buddy-core "1.10.1"]
                                      [serovers "1.6.2"]
                                      [onecli "0.9.0-SNAPSHOT" :exclusions [org.clojure/clojure]]
                                      [clj-commons/clj-yaml "0.7.108"]
                                      ]
                       }})
