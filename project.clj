(defproject grafter "0.1.0-SNAPSHOT"
  :description "RDFization tools"
  :url "http://example.com/FIXME"
  :license {:name "TODO"
            :url "http://example.com/TODO"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;;[org.apache.jena/jena-core "2.11.1"]
                 [org.clojure/algo.monads "0.1.5"]
                 [clj-time "0.7.0"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [clj-excel "0.0.1"]
                 [me.raynes/fs "1.4.4"]
                 [org.marianoguerra/clj-rhino "0.2.1"]
                 [org.openrdf.sesame/sesame-runtime "2.7.10"]
                 [potemkin "0.3.4"]
                 ;;[incanter/incanter-core "1.5.5"]
                 [incanter "1.5.5"] ; Include all of incanter
                 ]
  ;;:dev-dependencies [[lein-autodoc "0.9.0"]]

  :plugins [[codox "0.8.9"]]

  :codox {:defaults {:doc "FIXME: write docs"}}

  :source-paths ["src"]
  :jvm-opts ["-Dapple.awt.UIElement=true"] ;; Prevent Java process
                                           ;; from appearing as a GUI
                                           ;; app in OSX when Swing
                                           ;; classes are loaded.
)
