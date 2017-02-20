(defproject grafter/grafter "0.8.6-SNAPSHOT"
  :description "Tools for the hard graft of data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}

  :deploy-repositories [["releases" :clojars]]

  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.openrdf.sesame/sesame-runtime "2.8.9"]
                 [org.clojure/tools.logging "0.3.1"]
                 [grafter/url "0.2.5"]
                 [grafter/vocabularies "0.2.0"]

                 [commons-logging "1.2"] ;; Shouldn't need this, but somehow excluded and required by SPARQLRepository
                 [org.clojure/data.csv "0.1.3"]
                 [grafter/clj-excel "0.0.9" :exclusions [commons-codec]]
                 [me.raynes/fs "1.4.6"]
                 [potemkin "0.4.3"]
                 [incanter/incanter-core "1.5.7" :exclusions [net.sf.opencsv/opencsv commons-codec]]
                 [com.novemberain/pantomime "2.8.0"]] ;; mimetypes


  :codox {:defaults {:doc "FIXME: write docs"
                     :doc/format :markdown}
          :output-dir "api-docs"
          :sources ["src/common" "src/rdf-repository" "src/tabular"
                   "src/templater" "src/rdf-common" "src/pipeline"
                   ;; Include docs from grafter-url project too
                    "../grafter-url/src"]

          ;; TODO change this when we merge back to master
          :src-dir-uri "http://github.com/Swirrl/grafter/blob/0.8.x-SNAPSHOT/"
          :src-linenum-anchor-prefix "L"}

  :source-paths ["src/common" "src/rdf-repository" "src/tabular"
                 "src/templater" "src/rdf-common" "src/pipeline"]

  ;; Prevent Java process from appearing as a GUI app in OSX when
  ;; Swing classes are loaded.
  :jvm-opts ["-Dapple.awt.UIElement=true"]

  :pedantic? true

  :repack [{:subpackage "rdf.common"
            :dependents #{"templater"}
            :path "src/rdf-common"}
           {:subpackage "templater"
            :path "src/templater"}
           {:type :clojure
            :path "src/pipeline"
            :levels 2}
           {:type :clojure
            :path "src/rdf-repository"
            :levels 2}
           {:type :clojure
            :path "src/tabular"
            :levels 2}]


  :profiles {:uberjar {:aot :all}

             :clj-19 { :dependencies [[org.clojure/clojure "1.9.0-alpha14"]] }

             :dev {:plugins [[com.aphyr/prism "0.1.1"] ;; autotest support simply run: lein prism
                             [codox "0.8.10"]
                             [lein-repack "0.2.10" :exclusions [org.clojure/clojure
                                                                org.codehaus.plexus/plexus-utils]]]

                   :dependencies [[com.aphyr/prism "0.1.3"]
                                  [org.slf4j/slf4j-simple "1.7.21"]
                                  [prismatic/schema "1.1.3"]
                                  [criterium "0.4.4"]]

                   :resource-paths ["dev/resources"]

                   :env {:dev true}}})
