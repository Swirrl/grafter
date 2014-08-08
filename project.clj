(defproject grafter "0.2.0-SNAPSHOT"
  :description "RDFization tools"
  :url "http://example.com/FIXME"
  :license {:name "TODO"
            :url "http://example.com/TODO"}

  :repositories [["swirrl-private" {:url "s3p://leiningen-private-repo/releases/"
                                    :username :env
                                    :passphrase :env}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.openrdf.sesame/sesame-runtime "2.7.12"

                  ;; For some reason there appears to be a weird
                  ;; version conflict with this sesame library.  So
                  ;; exclude it, as we're not using it.

                  :exclusions [org.openrdf.sesame/sesame-repository-manager]]

                 [org.clojure/algo.monads "0.1.5"]
                 [clj-time "0.7.0"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [clj-excel "0.0.1"]
                 [me.raynes/fs "1.4.4"]
                 [org.marianoguerra/clj-rhino "0.2.1"]
                 [commons-httpclient/commons-httpclient "3.1"] ;; FIXME: bodge to fix strange transitive dependency issue - sesame should bring this in for us
                 [potemkin "0.3.4"]
                 [incanter "1.5.5"] ; Include all of incanter
                 ]


  :codox {:defaults {:doc "FIXME: write docs"}
          :output-dir "api-docs"}

  :source-paths ["src"]
  :jvm-opts ["-Dapple.awt.UIElement=true"] ;; Prevent Java process
                                           ;; from appearing as a GUI
                                           ;; app in OSX when Swing
                                           ;; classes are loaded.

  :plugins [[s3-wagon-private "1.1.2"]] ;; private maven repo's on s3

  :profiles {:uberjar {:aot :all}

             :dev {:plugins [[com.aphyr/prism "0.1.1"]  ;; autotest support simply run: lein prism
                             [codox "0.8.10"]]

                   :dependencies [[com.aphyr/prism "0.1.1"]]


                   :env {:dev true}}})
