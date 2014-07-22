(defproject grafter "0.1.0"
  :description "RDFization tools"
  :url "http://grafter.org/"
  :license {:name "TODO"
            :url "http://todo.org/"}

  :repositories [["swirrl-private" {:url "s3p://leiningen-private-repo/releases/"
                                    :username :env
                                    :passphrase :env}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/algo.monads "0.1.5"]
                 [clj-time "0.7.0"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [org.marianoguerra/clj-rhino "0.2.1"]
                 [org.openrdf.sesame/sesame-runtime "2.7.12"]
                 [commons-httpclient/commons-httpclient "3.1"] ;; FIXME: bodge to fix strange transitive dependency issue - sesame should bring this in for us
                 [potemkin "0.3.4"]
                 [incanter "1.5.5"] ; Include all of incanter
                 ]

  ;;:dev-dependencies [[lein-autodoc "0.9.0"]]

  :codox {:defaults {:doc "FIXME: write docs"}}

  :source-paths ["src"]

  :plugins [[com.aphyr/prism "0.1.1"] ;; autotest style support simply run: lein prism
            [s3-wagon-private "1.1.2"]])
