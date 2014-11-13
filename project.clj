(defproject grafter "0.2.0-SNAPSHOT"
  :description "Tools for the hard graft of data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.openrdf.sesame/sesame-runtime "2.7.12"

                  ;; For some reason there appears to be a weird
                  ;; version conflict with this sesame library.  So
                  ;; exclude it, as we're not using it.

                  :exclusions [org.openrdf.sesame/sesame-repository-manager]]

                 [org.clojure/algo.monads "0.1.5"]
                 [clj-time "0.7.0"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [clj-excel "0.0.1" :exclusions [commons-codec]]
                 [me.raynes/fs "1.4.4"]
                 [org.marianoguerra/clj-rhino "0.2.1"]
                 [potemkin "0.3.4"]
                 [incanter/incanter-core "1.5.5" :exclusions [net.sf.opencsv/opencsv commons-codec]]
                 [com.novemberain/pantomime "2.3.0"]]


  :codox {:defaults {:doc "FIXME: write docs"}
          :output-dir "api-docs"}

  :source-paths ["src"]
  :jvm-opts ["-Dapple.awt.UIElement=true"] ;; Prevent Java process
                                           ;; from appearing as a GUI
                                           ;; app in OSX when Swing
                                           ;; classes are loaded.

  :profiles {:uberjar {:aot :all}

             :dev {:plugins [[com.aphyr/prism "0.1.1"]  ;; autotest support simply run: lein prism
                             [codox "0.8.10"]]

                   :dependencies [[com.aphyr/prism "0.1.1"]]
                   :global-vars {*warn-on-reflection* true}

                   :env {:dev true}}})
